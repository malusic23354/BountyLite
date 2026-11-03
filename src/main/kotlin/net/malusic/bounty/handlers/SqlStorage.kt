package net.malusic.bounty

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

data class Contribution(val target: UUID, val bettor: UUID, val placedAt: Long, val cents: Long)

class SqlStorage(private val plugin: JavaPlugin) : AutoCloseable {

    private val lock = Any()
    private var connection: Connection? = null

    private val mysql: Boolean
    private val jdbcUrl: String
    private val username: String
    private val password: String
    private val table: String
    private val contributions: String
    val description: String

    init {
        val config = plugin.config

        mysql = when (val type = config.getString("storage.type", "sqlite")?.trim()?.lowercase()) {
            "mysql" -> true
            "sqlite" -> false
            else -> throw IllegalArgumentException("Invalid storage.type '$type'. Use 'sqlite' or 'mysql'.")
        }

        table = clean(config.getString("storage.table-prefix", "bounty_")) + "bounties"
        contributions = clean(config.getString("storage.table-prefix", "bounty_")) + "contributions"

        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver")

            val host = config.getString("storage.mysql.host", "localhost")
            val port = config.getInt("storage.mysql.port", 3306)
            val database = clean(config.getString("storage.mysql.database"), "bounty")
            val ssl = if (config.getBoolean("storage.mysql.use-ssl")) "REQUIRED" else "DISABLED"
            val options = "sslMode=$ssl&allowPublicKeyRetrieval=true&serverTimezone=UTC"

            username = config.getString("storage.mysql.username", "root") ?: "root"
            password = config.getString("storage.mysql.password") ?: ""

            createDatabase("jdbc:mysql://$host:$port/?$options", database)
            jdbcUrl = "jdbc:mysql://$host:$port/$database?$options" +
                    "&characterEncoding=utf8&useUnicode=true&cachePrepStmts=true&useServerPrepStmts=true"
            description = "MySQL ($host:$port/$database)"
        } else {
            Class.forName("org.sqlite.JDBC")

            val file = File(plugin.dataFolder, config.getString("storage.sqlite.file", "bounties.db") ?: "bounties.db")
            file.parentFile?.mkdirs()

            username = ""
            password = ""
            jdbcUrl = "jdbc:sqlite:${file.absolutePath}?busy_timeout=5000"
            description = "SQLite (${file.name})"
        }

        withConnection { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `$table` " +
                            "(`player_uuid` VARCHAR(36) NOT NULL, `amount` DOUBLE NOT NULL, PRIMARY KEY (`player_uuid`))"
                )
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `$contributions` " +
                            "(`target_uuid` VARCHAR(36) NOT NULL, `bettor_uuid` VARCHAR(36) NOT NULL, " +
                            "`placed_at` BIGINT NOT NULL, `amount` DOUBLE NOT NULL, " +
                            "PRIMARY KEY (`target_uuid`, `bettor_uuid`, `placed_at`))"
                )
                if (!mysql) {
                    runCatching {
                        statement.execute("PRAGMA journal_mode=WAL")
                        statement.execute("PRAGMA synchronous=NORMAL")
                    }
                }
            }
        }
    }

    private val contributionUpsertSql = "INSERT INTO `$contributions` " +
            "(`target_uuid`, `bettor_uuid`, `placed_at`, `amount`) VALUES (?, ?, ?, ?) " +
            if (mysql) "ON DUPLICATE KEY UPDATE `amount` = `amount` + VALUES(`amount`)"
            else "ON CONFLICT(`target_uuid`, `bettor_uuid`, `placed_at`) DO UPDATE SET `amount` = `amount` + excluded.`amount`"

    private val upsertSql = "INSERT INTO `$table` (`player_uuid`, `amount`) VALUES (?, ?) " +
            if (mysql) "ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`)"
            else "ON CONFLICT(`player_uuid`) DO UPDATE SET `amount` = excluded.`amount`"

    fun loadAll(): Map<UUID, Long> = withConnection { conn ->
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT `player_uuid`, `amount` FROM `$table`").use { rows ->
                buildMap<UUID, Long> {
                    while (rows.next()) {
                        val uuid = rows.uuid(1) ?: continue
                        put(uuid, EconomyService.toCents(rows.getDouble(2)))
                    }
                }
            }
        }
    }

    fun set(uuid: UUID, cents: Long) = update(upsertSql, uuid, cents / 100.0)

    fun remove(uuid: UUID) = update("DELETE FROM `$table` WHERE `player_uuid` = ?", uuid)

    fun addContribution(contribution: Contribution) =
        update(contributionUpsertSql, contribution.target, contribution.bettor, contribution.placedAt, contribution.cents / 100.0)

    fun expiredContributions(cutoff: Long): List<Contribution> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT `target_uuid`, `bettor_uuid`, `placed_at`, `amount` FROM `$contributions` WHERE `placed_at` < ?"
        ).use { statement ->
            statement.setLong(1, cutoff)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val target = rows.uuid(1) ?: continue
                        val bettor = rows.uuid(2) ?: continue
                        add(
                            Contribution(
                                target,
                                bettor,
                                rows.getLong(3),
                                EconomyService.toCents(rows.getDouble(4))
                            )
                        )
                    }
                }
            }
        }
    }

    fun topBettors(limit: Int): List<Pair<UUID, Long>> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT `bettor_uuid`, SUM(`amount`) AS `total` FROM `$contributions` " +
                    "GROUP BY `bettor_uuid` ORDER BY `total` DESC LIMIT ?"
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val bettor = rows.uuid(1) ?: continue
                        add(bettor to EconomyService.toCents(rows.getDouble(2)))
                    }
                }
            }
        }
    }

    fun deleteContribution(contribution: Contribution) = update(
        "DELETE FROM `$contributions` WHERE `target_uuid` = ? AND `bettor_uuid` = ? AND `placed_at` = ?",
        contribution.target,
        contribution.bettor,
        contribution.placedAt
    )

    fun clearContributions(target: UUID) =
        update("DELETE FROM `$contributions` WHERE `target_uuid` = ?", target)

    private fun update(sql: String, vararg params: Any) {
        withConnection { conn ->
            conn.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value ->
                    when (value) {
                        is UUID -> statement.setString(index + 1, value.toString())
                        is Long -> statement.setLong(index + 1, value)
                        is Double -> statement.setDouble(index + 1, value)
                        else -> statement.setObject(index + 1, value)
                    }
                }
                statement.executeUpdate()
            }
        }
    }

    private fun ResultSet.uuid(index: Int): UUID? =
        runCatching { UUID.fromString(getString(index)) }.getOrNull()

    override fun close() {
        synchronized(lock) { closeQuietly() }
    }

    private fun <T> withConnection(block: (Connection) -> T): T = synchronized(lock) {
        try {
            block(connect())
        } catch (exception: SQLException) {
            // Only retry when the connection itself dropped; retrying other
            // failures (constraints, bad SQL) would just repeat the error.
            if (exception.sqlState?.startsWith("08") != true) throw exception
            plugin.logger.fine("Database connection lost, reconnecting: ${exception.message}")
            closeQuietly()
            block(connect())
        }
    }

    private fun connect(): Connection {
        connection?.let { if (runCatching { !it.isClosed }.getOrDefault(false)) return it }
        closeQuietly()

        val created = if (mysql) {
            DriverManager.getConnection(jdbcUrl, username, password)
        } else {
            DriverManager.getConnection(jdbcUrl)
        }
        return created.also { connection = it }
    }

    private fun closeQuietly() {
        runCatching { connection?.close() }
        connection = null
    }

    private fun createDatabase(serverUrl: String, database: String) {
        try {
            DriverManager.getConnection(serverUrl, username, password).use { conn ->
                conn.createStatement().use { it.executeUpdate("CREATE DATABASE IF NOT EXISTS `$database`") }
            }
        } catch (exception: SQLException) {
            plugin.logger.warning("Could not create MySQL database '$database'; assuming it exists: ${exception.message}")
        }
    }

    private fun clean(value: String?, fallback: String = ""): String =
        value.orEmpty().filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { fallback }
}