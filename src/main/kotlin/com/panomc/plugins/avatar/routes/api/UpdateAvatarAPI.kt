package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.error.BadRequest
import com.panomc.platform.model.*
import com.panomc.platform.util.ImageCompressionUtil
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.config.AvatarType
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import com.panomc.plugins.avatar.db.model.UserAvatar
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Bodies
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.objectSchema
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import java.io.File

@Endpoint
class UpdateAvatarAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : LoggedInApi() {
    override val paths = listOf(Path("/api/avatar", RouteType.POST))

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
                if (!config.allowedTypes.contains(fileUpload.contentType())) {
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
            fileName = fileName
        )

        userAvatarDao.upsert(userAvatar, sqlClient)

        return Successful()
    }

    private fun saveUploadedFile(fileUpload: io.vertx.ext.web.FileUpload, config: AvatarConfig): String {
        val originalFile = File(fileUpload.uploadedFileName())
        val originalBytes = originalFile.readBytes()
        val mimeType = fileUpload.contentType() ?: "image/png"

        // Skip optimization for GIF (animated GIFs would break)
        val (optimizedBytes, finalMimeType) = if (mimeType == "image/gif") {
            originalBytes to mimeType
        } else {
            val maxSizeBytes = (config.maxSizeMb * 1024 * 1024).toInt()
            ImageCompressionUtil.compressImage(
                originalBytes = originalBytes,
                mimeType = mimeType,
                maxSizeBytes = maxSizeBytes,
                maxDimension = AVATAR_MAX_DIMENSION
            )
        }

        // Determine final extension based on output mime type
        val extension = when (finalMimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> fileUpload.fileName().split(".").last()
        }

        val fileName = "${System.currentTimeMillis()}-${fileUpload.uploadedFileName().split(File.separator).last()}.$extension"
        val destFile = File(plugin.uploadsDir, fileName)

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

