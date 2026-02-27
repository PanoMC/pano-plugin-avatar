package com.panomc.plugins.avatar.db.impl

import com.panomc.platform.annotation.Dao
import com.panomc.plugins.avatar.db.dao.UserAvatarDao
import com.panomc.plugins.avatar.db.model.UserAvatar
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Scope

@Dao
@Lazy
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class UserAvatarDaoImpl : UserAvatarDao() {

    override suspend fun init(sqlClient: SqlClient) {
        sqlClient
            .query(
                """
                    CREATE TABLE IF NOT EXISTS `${getTablePrefix() + tableName}` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `userId` bigint NOT NULL,
                      `avatarType` VARCHAR(32) NOT NULL DEFAULT 'MINOTAR',
                      `fileName` VARCHAR(255) DEFAULT NULL,
                      `createdAt` BIGINT(20) NOT NULL,
                      `updatedAt` BIGINT(20) NOT NULL,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `unique_user` (`userId`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User avatar settings.';
                """
            )
            .execute()
            .coAwait()
    }

    override suspend fun getByUserId(userId: Long, sqlClient: SqlClient): UserAvatar? {
        val query = "SELECT ${fields.toTableQuery()} FROM `${getTablePrefix() + tableName}` WHERE `userId` = ?"
        val rows = sqlClient.preparedQuery(query).execute(Tuple.of(userId)).coAwait()
        return rows.toEntities().getOrNull(0)
    }

    override suspend fun upsert(userAvatar: UserAvatar, sqlClient: SqlClient) {
        val query = """
            INSERT INTO `${getTablePrefix() + tableName}` (`userId`, `avatarType`, `fileName`, `createdAt`, `updatedAt`)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE `avatarType` = VALUES(`avatarType`), `fileName` = VALUES(`fileName`), `updatedAt` = VALUES(`updatedAt`)
        """.trimIndent()
        sqlClient.preparedQuery(query)
            .execute(
                Tuple.of(
                    userAvatar.userId,
                    userAvatar.avatarType,
                    userAvatar.fileName,
                    userAvatar.createdAt,
                    userAvatar.updatedAt
                )
            )
            .coAwait()
    }

    override suspend fun deleteByUserId(userId: Long, sqlClient: SqlClient) {
        val query = "DELETE FROM `${getTablePrefix() + tableName}` WHERE `userId` = ?"
        sqlClient.preparedQuery(query).execute(Tuple.of(userId)).coAwait()
    }

    override suspend fun uninstall(sqlClient: SqlClient) {
        sqlClient.query("DROP TABLE IF EXISTS `${getTablePrefix() + tableName}`").execute().coAwait()
    }
}
