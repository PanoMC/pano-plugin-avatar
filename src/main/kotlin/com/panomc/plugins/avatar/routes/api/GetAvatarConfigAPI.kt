package com.panomc.plugins.avatar.routes.api

import com.panomc.platform.annotation.Endpoint
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.model.*
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder
import io.vertx.json.schema.SchemaRepository

@Endpoint
class GetAvatarConfigAPI(
    private val plugin: AvatarPlugin
) : Api() {
    override val paths = listOf(Path("/api/avatar/config", RouteType.GET))

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override fun getValidationHandler(schemaRepository: SchemaRepository): ValidationHandler =
        ValidationHandlerBuilder.create(schemaRepository)
            .build()

    override suspend fun handle(context: RoutingContext): Result {
        val config = configManager.config
        val result = JsonObject()
            .put("maxSizeMb", config.maxSizeMb)
            .put("allowedTypes", JsonArray(config.allowedTypes))
            .put("allowedSources", JsonArray(config.allowedSources.map { it.name }))
            .put("customSources", JsonArray(config.customSources.map {
                JsonObject()
                    .put("title", it.title)
                    .put("urlTemplate", it.urlTemplate)
                    .put("identifierField", it.identifierField)
            }))

        return Successful(result.map)
    }
}
