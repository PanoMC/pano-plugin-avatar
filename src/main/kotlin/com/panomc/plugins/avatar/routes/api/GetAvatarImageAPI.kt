package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.util.AvatarImages
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import com.panomc.platform.schema.dsl.Parameters
import com.panomc.platform.schema.dsl.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import java.io.File
import com.panomc.platform.schema.EndpointDoc
import io.vertx.json.schema.common.dsl.Schemas.*
import com.panomc.platform.error.NotFound

@Endpoint
class GetAvatarImageAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : Api() {
    override val paths = listOf(Path("/avatar/image/:fileName", RouteType.GET))

    override val doc = EndpointDoc(
        summary = "An uploaded avatar file.",
        tag = "avatar",
        binary = true,
        errors = listOf(NotFound::class)
    )

    companion object {
        private const val CACHE_TTL_SECONDS = 7 * 24 * 60 * 60 // 1 week

        private val SAFE_FILE_NAME = Regex("^[A-Za-z0-9._-]+$")
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("fileName", stringSchema()))
            .build()

    override suspend fun handle(context: RoutingContext): Result? {
        val parameters = getParameters(context)
        val fileName = parameters.pathParameter("fileName").string

        // Security: only a plain file name directly inside the uploads dir, checked before the disk is touched, and
        // every miss answers the same 404 so nothing can be learned about other paths on the server.
        val uploadsDir = plugin.uploadsDir.canonicalFile
        val file = if (SAFE_FILE_NAME.matches(fileName) && !fileName.contains("..")) File(uploadsDir, fileName) else null

        if (file == null || file.canonicalFile.parentFile != uploadsDir || !file.isFile) {
            context.response().setStatusCode(404).end()
            return null
        }

        // Only supported raster images are served; an avatar stored with any other extension is never rendered.
        val mimeType = AvatarImages.mimeTypeOfFile(file.name)

        if (mimeType == null) {
            context.response().setStatusCode(404).end()
            return null
        }

        val response = context.response()
        response.putHeader("Content-Type", mimeType)
        response.putHeader("X-Content-Type-Options", "nosniff")
        response.putHeader("Content-Security-Policy", "default-src 'none'; sandbox")
        response.putHeader("Cache-Control", "public, max-age=$CACHE_TTL_SECONDS, immutable")

        try {
            response.sendFile(file.absolutePath)
        } catch (_: Exception) {}

        return null
    }
}
