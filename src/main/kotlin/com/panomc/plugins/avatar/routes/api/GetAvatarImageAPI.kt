package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.model.*
import com.panomc.platform.util.MimeTypeUtil
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Parameters
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import java.io.File

@Endpoint
class GetAvatarImageAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : Api() {
    override val paths = listOf(Path("/api/avatar/image/:fileName", RouteType.GET))

    companion object {
        private const val CACHE_TTL_SECONDS = 7 * 24 * 60 * 60 // 1 week
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("fileName", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result? {
        val parameters = getParameters(context)
        val fileName = parameters.pathParameter("fileName").string

        val file = File(plugin.uploadsDir, fileName)

        if (!file.exists()) {
            context.response().setStatusCode(404).end()
            return null
        }

        // Security: ensure file is within uploads dir (prevent path traversal)
        if (!file.canonicalPath.startsWith(plugin.uploadsDir.canonicalPath)) {
            context.response().setStatusCode(403).end()
            return null
        }

        val mimeType = MimeTypeUtil.getMimeTypeFromFileName(file.absolutePath)

        val response = context.response()
        response.putHeader("Content-Type", mimeType)
        response.putHeader("Cache-Control", "public, max-age=$CACHE_TTL_SECONDS, immutable")

        try {
            response.sendFile(file.absolutePath)
        } catch (_: Exception) {}

        return null
    }
}
