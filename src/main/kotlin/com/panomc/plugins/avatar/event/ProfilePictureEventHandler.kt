package com.panomc.plugins.avatar.event

import com.panomc.platform.api.annotation.EventListener
import com.panomc.platform.api.config.PluginConfigManager
import com.panomc.platform.api.event.ProfilePictureEventListener
import com.panomc.platform.db.model.User
import com.panomc.plugins.avatar.AvatarPlugin
import com.panomc.plugins.avatar.config.AvatarConfig
import com.panomc.plugins.avatar.config.AvatarType
import com.panomc.plugins.avatar.db.dao.UserAvatarDao

@EventListener
class ProfilePictureEventHandler(
    private val plugin: AvatarPlugin,
    private val userAvatarDao: UserAvatarDao
) : ProfilePictureEventListener {

    private val configManager by lazy {
        plugin.pluginBeanContext.getBean(PluginConfigManager::class.java) as PluginConfigManager<AvatarConfig>
    }

    override suspend fun resolveProfilePictureUrl(user: User): String? {
        val sqlClient = plugin.applicationContext.getBean(
            com.panomc.platform.db.DatabaseManager::class.java
        ).getSqlClient()

        val userAvatar = userAvatarDao.getByUserId(user.id, sqlClient) ?: return null

        val avatarType = try {
            AvatarType.valueOf(userAvatar.avatarType)
        } catch (_: Exception) {
            return null
        }

        // Check if the selected source is still allowed in config
        if (!configManager.config.allowedSources.contains(avatarType)) {
            return null // Fall back to default
        }

        return when (avatarType) {
            AvatarType.MINOTAR -> "https://minotar.net/avatar/${user.username}"
            AvatarType.GRAVATAR -> {
                val email = user.email ?: return null
                val emailHash = md5Hex(email.trim().lowercase())
                "https://www.gravatar.com/avatar/$emailHash?s=80&d=identicon"
            }
            AvatarType.CUSTOM -> {
                if (userAvatar.fileName != null) {
                    "/api/avatar/image/${userAvatar.fileName}"
                } else {
                    null // No custom file uploaded, fall back
                }
            }
        }
    }

    private fun md5Hex(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
