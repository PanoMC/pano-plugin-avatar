package com.panomc.plugins.avatar.db.migration

import com.panomc.platform.annotation.Migration
import com.panomc.platform.db.DatabaseMigration
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple

@Migration
class DatabaseMigration1To2 :
    DatabaseMigration(1, 2, "Add minecraftUsername column to user avatar table.") {
    override val handlers: List<suspend (SqlClient) -> Unit> = listOf(
        addMinecraftUsernameColumn()
    )

    // Checked by hand instead of the host's helpers, so the plugin also runs on Pano versions without them.
    private fun addMinecraftUsernameColumn(): suspend (sqlClient: SqlClient) -> Unit = { sqlClient: SqlClient ->
        val table = "${getTablePrefix()}user_avatar"

        val exists = sqlClient
            .preparedQuery(
                "SELECT COUNT(*) FROM `information_schema`.`COLUMNS` " +
                        "WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = ? AND `COLUMN_NAME` = ?"
            )
            .execute(Tuple.of(table, "minecraftUsername"))
            .coAwait()
            .first()
            .getLong(0) > 0

        if (!exists) {
            sqlClient
                .query("ALTER TABLE `$table` ADD COLUMN `minecraftUsername` VARCHAR(16) DEFAULT NULL AFTER `fileName`")
                .execute()
                .coAwait()
        }
    }
}
