package com.panomc.plugins.avatar.db.model

import com.panomc.platform.db.DBEntity

open class UserAvatar(
    var id: Long = -1,
    val userId: Long = -1,
    val avatarType: String = "MINOTAR",
    val fileName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : DBEntity()
