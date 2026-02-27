package com.panomc.plugins.avatar.routes.panel

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.auth.AuthProvider
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.permission.ManageAvatarSettingsPermission
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository

@Endpoint
class PanelGetAvatarConfigAPI(
    private val plugin: AvatarPlugin
) : PanelApi() {
    override val paths = listOf(Path("/api/panel/avatar/config", RouteType.GET))

    private val authProvider by lazy {
        plugin.applicationContext.getBean(AuthProvider::class.java)
    }

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        authProvider.requirePermission(ManageAvatarSettingsPermission(), context)
        return Successful(JsonObject.mapFrom(configManager.config).map)
    }
}
