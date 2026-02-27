package com.panomc.plugins.avatar.db.dao

import com.panomc.platform.db.Dao
import com.panomc.plugins.avatar.db.model.UserAvatar
import io.vertx.sqlclient.SqlClient

abstract class UserAvatarDao : Dao<UserAvatar>(UserAvatar::class.java) {
    abstract suspend fun getByUserId(userId: Long, sqlClient: SqlClient): UserAvatar?
    abstract suspend fun upsert(userAvatar: UserAvatar, sqlClient: SqlClient)
    abstract suspend fun deleteByUserId(userId: Long, sqlClient: SqlClient)
}
