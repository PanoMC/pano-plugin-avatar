package com.panomc.plugins.avatar.config

import com.panomc.platform.api.config.PluginConfig

class AvatarConfig(
    val maxSizeMb: Int = 1,
    val allowedTypes: List<String> = listOf("image/png", "image/jpeg", "image/gif"),
    val allowedSources: List<AvatarType> = listOf(AvatarType.MINOTAR, AvatarType.GRAVATAR, AvatarType.CUSTOM),
    val customSources: List<CustomAvatarSource> = emptyList(),
    // Minotar may show another Minecraft player's head when the player types that name. Off = always their own.
    // A config written before this key existed has no value for it and reads the default.
    val allowCustomMinecraftUsername: Boolean = true,
    version: Int = 1
) : PluginConfig(version) {

    companion object {
        class CustomAvatarSource(
            val title: String = "",
            val urlTemplate: String = "",
            val identifierField: String = "username" // "username" or "email"
        )
    }
}
