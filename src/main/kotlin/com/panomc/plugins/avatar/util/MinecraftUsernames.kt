package com.panomc.plugins.avatar.util

import com.panomc.platform.error.BadRequest

/** The Minecraft player whose head a Minotar avatar shows, when it is not the user's own username. */
object MinecraftUsernames {
    private val VALID = Regex("^[A-Za-z0-9_]{1,16}$")

    /** Null for a blank value (the user's own username is used), else the trimmed name; BadRequest when invalid. */
    fun parse(value: String?): String? {
        val trimmed = value?.trim()

        if (trimmed.isNullOrEmpty()) {
            return null
        }

        if (!VALID.matches(trimmed)) {
            throw BadRequest()
        }

        return trimmed
    }
}
