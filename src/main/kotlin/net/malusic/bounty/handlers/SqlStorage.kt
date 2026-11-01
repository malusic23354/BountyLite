package net.malusic.bounty

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class SqlStorage(private val plugin: JavaPlugin) : AutoCloseable {

    private val lock = Any()
    private var connection: Connection? = null

    private val mysql: Boolean
    private val jdbcUrl: String
    private val username: String
    private val password: String
    private val table: String

    init {
        val config = plugin.config

        mysql = when (val type = config.getString("storage.type", "sqlite")?.trim()?.lowercase()) {
            "mysql" -> true
            "sqlite" -> false
            else -> throw IllegalArgumentException("Invalid storage.type '$type'. Use 'sqlite' or 'mysql'.")
        }

        table = clean(config.getString("storage.table-prefix", "bounty_")) + "bounties"

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
        } else {
            Class.forName("org.sqlite.JDBC")

            val file = File(plugin.dataFolder, config.getString("storage.sqlite.file", "bounties.db") ?: "bounties.db")
            file.parentFile?.mkdirs()

            username = ""
            password = ""
            jdbcUrl = "jdbc:sqlite:${file.absolutePath}?busy_timeout=5000"
        }

        withConnection { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `$table` " +
                        "(`player_uuid` VARCHAR(36) NOT NULL, `amount` DOUBLE NOT NULL, PRIMARY KEY (`player_uuid`))"
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

    private val upsertSql = "INSERT INTO `$table` (`player_uuid`, `amount`) VALUES (?, ?) " +
        if (mysql) "ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`)"
        else "ON CONFLICT(`player_uuid`) DO UPDATE SET `amount` = excluded.`amount`"

    fun loadAll(): Map<UUID, Double> = withConnection { conn ->
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT `player_uuid`, `amount` FROM `$table`").use { rows ->
                buildMap<UUID, Double> {
                    while (rows.next()) {
                        val uuid = runCatching { UUID.fromString(rows.getString(1)) }.getOrNull() ?: continue
                        put(uuid, rows.getDouble(2))
                    }
                }
            }
        }
    }

    fun set(uuid: UUID, amount: Double) {
        withConnection { conn ->
            conn.prepareStatement(upsertSql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setDouble(2, amount)
                statement.executeUpdate()
            }
        }
    }

    fun remove(uuid: UUID) {
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM `$table` WHERE `player_uuid` = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
        synchronized(lock) { closeQuietly() }
    }

    private fun <T> withConnection(block: (Connection) -> T): T = synchronized(lock) {
        try {
            block(connect())
        } catch (exception: SQLException) {
            plugin.logger.fine("Database connection failed, reconnecting: ${exception.message}")
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
