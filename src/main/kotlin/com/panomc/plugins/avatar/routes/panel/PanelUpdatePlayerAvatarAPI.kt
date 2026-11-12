package com.panomc.plugins.avatar.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.BadRequest
import com.panomc.platform.error.NotFound
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.util.AvatarImages
import com.panomc.plugins.avatar.util.MinecraftUsernames
import com.panomc.plugins.avatar.config.AvatarType
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import com.panomc.plugins.avatar.db.model.UserAvatar
import com.panomc.plugins.avatar.log.UpdatedPlayerAvatarLog
import com.panomc.plugins.avatar.permission.ManagePlayerAvatarPermission
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Bodies
import com.panomc.platform.schema.dsl.Parameters
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.objectSchema
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import java.io.File

@Endpoint
class PanelUpdatePlayerAvatarAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : PanelApi() {
    override val paths = listOf(Path("/avatar/player/:username", RouteType.POST))

    companion object {
        private const val AVATAR_MAX_DIMENSION = 256
    }

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override fun bodyHandler(): Handler<RoutingContext>? {
        val lazyBodyHandler = BodyHandler.create().setDeleteUploadedFilesOnEnd(true)

        return Handler { ctx ->
            val maxSize = (configManager.config.maxSizeMb * 1024 * 1024).toLong() + 1024
            lazyBodyHandler.setBodyLimit(maxSize)
            lazyBodyHandler.handle(ctx)
        }
    }

    override suspend fun getFailureHandler(context: RoutingContext) {
        if (context.failure() == null) {
            throw BadRequest()
        }
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("username", stringSchema()))
            .body(
                Bodies.multipartFormData(
                    objectSchema()
                        .requiredProperty("avatarType", stringSchema())
                        .optionalProperty("minecraftUsername", stringSchema())
                )
            )
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManagePlayerAvatarPermission(), context)

        val parameters = getParameters(context)
        val username = parameters.pathParameter("username").string
        val data = parameters.body().jsonObject
        val avatarTypeStr = data.getString("avatarType")

        val avatarType = try {
            AvatarType.valueOf(avatarTypeStr)
        } catch (_: Exception) {
            throw BadRequest()
        }

        val config = configManager.config

        if (!config.allowedSources.contains(avatarType)) {
            throw BadRequest()
        }

        val sqlClient = getSqlClient()

        val targetUserId = databaseManager.userDao.getUserIdFromUsername(username, sqlClient) ?: throw NotFound()

        var fileName: String? = null

        // Only Minotar shows another player's head; any other source forgets the name.
        val minecraftUsername =
            if (avatarType == AvatarType.MINOTAR) MinecraftUsernames.parse(data.getString("minecraftUsername")) else null

        if (avatarType == AvatarType.CUSTOM) {
            val fileUploads = context.fileUploads()
            val fileUpload = fileUploads.firstOrNull { it.name() == "avatar" }

            if (fileUpload != null) {
                val maxSize = config.maxSizeMb * 1024 * 1024
                if (fileUpload.size() > maxSize) {
                    throw BadRequest()
                }

                // The type is read from the file's content, not from what the client claims.
                val detectedType = AvatarImages.detectMimeType(File(fileUpload.uploadedFileName()))
                if (detectedType == null || !config.allowedTypes.contains(detectedType)) {
                    throw BadRequest()
                }

                val existingAvatar = userAvatarDao.getByUserId(targetUserId, sqlClient)
                if (existingAvatar?.fileName != null) {
                    deleteFile(existingAvatar.fileName)
                }

                fileName = saveUploadedFile(fileUpload, config)
            } else {
                val existingAvatar = userAvatarDao.getByUserId(targetUserId, sqlClient)
                fileName = existingAvatar?.fileName
            }
        } else {
            val existingAvatar = userAvatarDao.getByUserId(targetUserId, sqlClient)
            if (existingAvatar?.fileName != null) {
                deleteFile(existingAvatar.fileName)
            }
        }

        val userAvatar = UserAvatar(
            userId = targetUserId,
            avatarType = avatarType.name,
            fileName = fileName,
            minecraftUsername = minecraftUsername
        )

        userAvatarDao.upsert(userAvatar, sqlClient)

        val adminUserId = authProvider.getUserIdFromRoutingContext(context)
        val adminUsername = databaseManager.userDao.getUsernameFromUserId(adminUserId, sqlClient)!!

        databaseManager.panelActivityLogDao.add(
            UpdatedPlayerAvatarLog(adminUserId, adminUsername, username, plugin.pluginId),
            sqlClient
        )

        return Successful()
    }

    private fun saveUploadedFile(fileUpload: io.vertx.ext.web.FileUpload, config: AvatarConfig): String {
        val originalFile = File(fileUpload.uploadedFileName())
        val mimeType = AvatarImages.detectMimeType(originalFile) ?: throw BadRequest()

        // Always decoded and re-encoded at avatar size, so oversized images and non-image payloads never get stored.
        val (optimizedBytes, finalMimeType) =
            AvatarImages.normalize(originalFile, mimeType, AVATAR_MAX_DIMENSION) ?: throw BadRequest()

        val extension = AvatarImages.extensionOf(finalMimeType) ?: throw BadRequest()

        val fileName = "${System.currentTimeMillis()}-${fileUpload.uploadedFileName().split(File.separator).last()}.$extension"
        val destFile = File(plugin.uploadsDir, fileName)

        plugin.uploadsDir.mkdirs()

        destFile.writeBytes(optimizedBytes)

        return fileName
    }

    private fun deleteFile(fileName: String) {
        val file = File(plugin.uploadsDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }
}
