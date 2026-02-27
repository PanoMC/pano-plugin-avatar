package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.error.NotFound
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Parameters
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import org.springframework.beans.factory.getBean

@Endpoint
class GetAvatarAPI(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao,
) : Api() {
    override val paths = listOf(Path("/api/avatar/user/:username", RouteType.GET))

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .pathParameter(Parameters.param("username", stringSchema()))
            .build()

    private val databaseManager by lazy {
        plugin.applicationContext.getBean<DatabaseManager>()
    }

    override suspend fun handle(context: RoutingContext): Result {
        val parameters = getParameters(context)
        val username = parameters.pathParameter("username").string

        val sqlClient = getSqlClient()
        val userId = databaseManager.userDao.getUserIdFromUsername(username, sqlClient) ?: throw NotFound()

        val userAvatar = userAvatarDao.getByUserId(userId, sqlClient)

        val result = if (userAvatar != null) {
            JsonObject()
                .put("avatarType", userAvatar.avatarType)
                .put("fileName", userAvatar.fileName)
        } else {
            JsonObject()
                .put("avatarType", "MINOTAR")
                .put("fileName", null as String?)
        }

        return Successful(result.map)
    }
}
