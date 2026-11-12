package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.error.BadRequest
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.util.AvatarImages
import com.panomc.plugins.avatar.util.MinecraftUsernames
import com.panomc.plugins.avatar.config.AvatarType
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import com.panomc.plugins.avatar.db.model.UserAvatar
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Bodies
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.objectSchema
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import java.io.File
import com.panomc.platform.schema.EndpointDoc
import io.vertx.json.schema.common.dsl.Schemas.*
import com.panomc.platform.error.NotLoggedIn

@Endpoint
class UpdateAvatarAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : LoggedInApi() {
    override val paths = listOf(Path("/avatar", RouteType.POST))

    override val doc = EndpointDoc(
        summary = "Sets the signed-in player's avatar source; a multipart upload carries the file for the custom source.",
        tag = "avatar",
        errors = listOf(BadRequest::class, NotLoggedIn::class)
    )

    companion object {
        private const val AVATAR_MAX_DIMENSION = 256
    }

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override fun bodyHandler(): Handler<RoutingContext>? {
        val lazyBodyHandler = BodyHandler.create().setDeleteUploadedFilesOnEnd(true)

        return Handler { ctx ->
            // Update limit dynamically based on latest config
            val maxSize = (configManager.config.maxSizeMb * 1024 * 1024).toLong() + 1024 // extra buffer for form fields
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
            .body(
                Bodies.multipartFormData(
                    objectSchema()
                        .requiredProperty("avatarType", stringSchema())
                        .optionalProperty("minecraftUsername", stringSchema())
                )
            )
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val parameters = getParameters(context)
        val data = parameters.body().jsonObject
        val avatarTypeStr = data.getString("avatarType")

        val avatarType = try {
            AvatarType.valueOf(avatarTypeStr)
        } catch (_: Exception) {
            throw BadRequest()
        }

        val config = configManager.config

        // Validate that the selected source is allowed
        if (!config.allowedSources.contains(avatarType)) {
            throw BadRequest()
        }

        val userId = authProvider.getUserIdFromRoutingContext(context)
        val sqlClient = getSqlClient()

        var fileName: String? = null

        // Only Minotar shows another player's head; any other source forgets the name.
        val minecraftUsername =
            if (avatarType == AvatarType.MINOTAR) MinecraftUsernames.parse(data.getString("minecraftUsername")) else null

        if (avatarType == AvatarType.CUSTOM) {
            val fileUploads = context.fileUploads()
            val fileUpload = fileUploads.firstOrNull { it.name() == "avatar" }

            if (fileUpload != null) {
                // Validate file size
                val maxSize = config.maxSizeMb * 1024 * 1024
                if (fileUpload.size() > maxSize) {
                    throw BadRequest()
                }

                // Validate file type
                // The type is read from the file's content, not from what the client claims.
                val detectedType = AvatarImages.detectMimeType(File(fileUpload.uploadedFileName()))
                if (detectedType == null || !config.allowedTypes.contains(detectedType)) {
                    throw BadRequest()
                }

                // Delete old avatar file if exists
                val existingAvatar = userAvatarDao.getByUserId(userId, sqlClient)
                if (existingAvatar?.fileName != null) {
                    deleteFile(existingAvatar.fileName)
                }

                // Save new file (with optimization)
                fileName = saveUploadedFile(fileUpload, config)
            } else {
                // Keep existing file if no new upload
                val existingAvatar = userAvatarDao.getByUserId(userId, sqlClient)
                fileName = existingAvatar?.fileName
            }
        } else {
            // If switching away from CUSTOM, delete old custom file
            val existingAvatar = userAvatarDao.getByUserId(userId, sqlClient)
            if (existingAvatar?.fileName != null) {
                deleteFile(existingAvatar.fileName)
            }
        }

        val userAvatar = UserAvatar(
            userId = userId,
            avatarType = avatarType.name,
            fileName = fileName,
            minecraftUsername = minecraftUsername
        )

        userAvatarDao.upsert(userAvatar, sqlClient)

        return Successful()
    }

    private fun saveUploadedFile(fileUpload: io.vertx.ext.web.FileUpload, config: AvatarConfig): String {
        val originalFile = File(fileUpload.uploadedFileName())
        val mimeType = AvatarImages.detectMimeType(originalFile) ?: throw BadRequest()

        // Always decoded and re-encoded at avatar size, so oversized images and non-image payloads never get stored.
        val (optimizedBytes, finalMimeType) =
            AvatarImages.normalize(originalFile, mimeType, AVATAR_MAX_DIMENSION) ?: throw BadRequest()

        // Determine final extension based on output mime type
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

