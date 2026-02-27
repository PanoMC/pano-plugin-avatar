package com.panomc.plugins.avatar.log

import com.panomc.platform.db.model.PluginActivityLog
import io.vertx.core.json.JsonObject

class UpdatedPlayerAvatarLog(
    userId: Long,
    username: String,
    targetUsername: String,
    pluginId: String
) : PluginActivityLog(
    userId = userId,
    pluginId = pluginId,
    details = JsonObject().put("username", username).put("targetUsername", targetUsername)
)
