package com.panomc.plugins.avatar.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.db.DatabaseManager
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.log.UpdatedAvatarSettingsLog
import com.panomc.plugins.avatar.permission.ManageAvatarSettingsPermission
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Bodies
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository
import io.vertx.json.schema.common.dsl.Schemas.*

@Endpoint
class PanelUpdateAvatarConfigAPI(
    private val plugin: AvatarPlugin
) : PanelApi() {
    override val paths = listOf(Path("/api/panel/avatar/config", RouteType.PUT))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val databaseManager by lazy {
        plugin.applicationContext.getBean(DatabaseManager::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .body(
                Bodies.json(
                    objectSchema()
                        .optionalProperty("maxSizeMb", intSchema())
                        .optionalProperty("allowedTypes", arraySchema().items(stringSchema()))
                        .optionalProperty("allowedSources", arraySchema().items(stringSchema()))
                        .optionalProperty("customSources", arraySchema().items(
                            objectSchema()
                                .requiredProperty("title", stringSchema())
                                .requiredProperty("urlTemplate", stringSchema())
                                .requiredProperty("identifierField", stringSchema())
                        ))
                )
            )
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManageAvatarSettingsPermission(), context)

        val body = context.body().asJsonObject()
        configManager.saveConfig(body)

        val sqlClient = getSqlClient()
        val userId = authProvider.getUserIdFromRoutingContext(context)
        val username = databaseManager.userDao.getUsernameFromUserId(userId, sqlClient)!!

        databaseManager.panelActivityLogDao.add(
            UpdatedAvatarSettingsLog(userId, username, plugin.pluginId),
            sqlClient
        )

        return Successful()
    }
}
