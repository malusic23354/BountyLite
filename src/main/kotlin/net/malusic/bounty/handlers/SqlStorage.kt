package net.malusic.bounty

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class SqlStorage(private val plugin: JavaPlugin) : BountyStorage {

    private enum class Type {
        SQLITE,
        MYSQL
    }

    private val lock = Any()

    private val type: Type
    private val jdbcUrl: String
    private val username: String
    private val password: String
    private val table: String
    private val upsertSql: String

    private var connection: Connection? = null

    init {
        val config = plugin.config

        type = when (config.getString("storage.type", "sqlite")?.trim()?.lowercase()) {
            "mysql" -> Type.MYSQL
            "sqlite" -> Type.SQLITE
            else -> throw IllegalArgumentException(
                "Invalid storage.type '${config.getString("storage.type")}'. Use 'sqlite' or 'mysql'."
            )
        }

        table = sanitizePrefix(config.getString("storage.table-prefix", "bounty_") ?: "bounty_") + "bounties"

        val driver = when (type) {
            Type.SQLITE -> "org.sqlite.JDBC"
            Type.MYSQL -> "com.mysql.cj.jdbc.Driver"
        }
        Class.forName(driver)

        when (type) {
            Type.SQLITE -> {
                val configuredFile = config.getString("storage.sqlite.file", "bounties.db") ?: "bounties.db"
                val file = File(plugin.dataFolder, configuredFile)
                file.parentFile?.mkdirs()

                jdbcUrl = "jdbc:sqlite:${file.absolutePath}?busy_timeout=5000"
                username = ""
                password = ""
            }

            Type.MYSQL -> {
                val host = config.getString("storage.mysql.host", "localhost") ?: "localhost"
                val port = config.getInt("storage.mysql.port", 3306)
                val database = sanitizeIdentifier(
                    config.getString("storage.mysql.database", "bounty") ?: "bounty",
                    "bounty"
                )
                val useSsl = config.getBoolean("storage.mysql.use-ssl", false)
                val sslMode = if (useSsl) "REQUIRED" else "DISABLED"

                username = config.getString("storage.mysql.username", "root") ?: "root"
                password = config.getString("storage.mysql.password", "") ?: ""

                createMysqlDatabaseIfNeeded(host, port, database, sslMode, username, password)

                jdbcUrl = "jdbc:mysql://$host:$port/$database" +
                    "?sslMode=$sslMode" +
                    "&allowPublicKeyRetrieval=true" +
                    "&serverTimezone=UTC" +
                    "&characterEncoding=utf8" +
                    "&useUnicode=true" +
                    "&cachePrepStmts=true" +
                    "&useServerPrepStmts=true"
            }
        }

        createTable()

        if (type == Type.SQLITE) {
            enableSqliteOptimizations()
        }

        upsertSql = when (type) {
            Type.SQLITE ->
                "INSERT INTO `$table` (`player_uuid`, `amount`) VALUES (?, ?) " +
                    "ON CONFLICT(`player_uuid`) DO UPDATE SET `amount` = excluded.`amount`"

            Type.MYSQL ->
                "INSERT INTO `$table` (`player_uuid`, `amount`) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE `amount` = ?"
        }
    }

    override fun loadAll(): Map<UUID, Double> = withConnection { connection ->
        val bounties = mutableMapOf<UUID, Double>()

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT `player_uuid`, `amount` FROM `$table`").use { resultSet ->
                while (resultSet.next()) {
                    val uuid = runCatching {
                        UUID.fromString(resultSet.getString("player_uuid"))
                    }.getOrNull() ?: continue

                    bounties[uuid] = resultSet.getDouble("amount")
                }
            }
        }

        bounties
    }

    override fun set(uuid: UUID, amount: Double) {
        withConnection { connection ->
            connection.prepareStatement(upsertSql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setDouble(2, amount)
                if (type == Type.MYSQL) {
                    statement.setDouble(3, amount)
                }
                statement.executeUpdate()
            }
        }
    }

    override fun remove(uuid: UUID) {
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM `$table` WHERE `player_uuid` = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closeConnectionQuietly()
        }
    }

    private fun createTable() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS `$table` (
                        `player_uuid` VARCHAR(36) NOT NULL,
                        `amount` DOUBLE NOT NULL,
                        PRIMARY KEY (`player_uuid`)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun enableSqliteOptimizations() {
        try {
            withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA journal_mode=WAL")
                    statement.execute("PRAGMA synchronous=NORMAL")
                }
            }
        } catch (exception: SQLException) {
            plugin.logger.fine("Could not enable SQLite WAL mode: ${exception.message}")
        }
    }

    /**
     * Runs [block] against the shared connection, reconnecting and retrying
     * once if the connection has gone stale.
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        synchronized(lock) {
            try {
                return block(connection())
            } catch (exception: SQLException) {
                plugin.logger.fine("Database connection failed, reconnecting: ${exception.message}")
                closeConnectionQuietly()
                return block(connection())
            }
        }
    }

    private fun connection(): Connection {
        val current = connection
        if (current != null) {
            try {
                if (!current.isClosed) return current
            } catch (_: SQLException) {
                // Fall through and reconnect.
            }
        }

        closeConnectionQuietly()

        val newConnection = if (type == Type.MYSQL) {
            DriverManager.getConnection(jdbcUrl, username, password)
        } else {
            DriverManager.getConnection(jdbcUrl)
        }

        connection = newConnection
        return newConnection
    }

    private fun closeConnectionQuietly() {
        val current = connection
        connection = null

        if (current != null) {
            try {
                current.close()
            } catch (_: SQLException) {
                // Nothing useful to do while closing.
            }
        }
    }

    private fun createMysqlDatabaseIfNeeded(
        host: String,
        port: Int,
        database: String,
        sslMode: String,
        username: String,
        password: String
    ) {
        val serverUrl = "jdbc:mysql://$host:$port/" +
            "?sslMode=$sslMode" +
            "&allowPublicKeyRetrieval=true" +
            "&serverTimezone=UTC"

        try {
            DriverManager.getConnection(serverUrl, username, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `$database`")
                }
            }
        } catch (exception: SQLException) {
            plugin.logger.warning(
                "Could not create MySQL database '$database'; assuming it already exists: ${exception.message}"
            )
        }
    }

    private fun sanitizePrefix(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '_' }

    private fun sanitizeIdentifier(value: String, fallback: String): String =
        value.filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { fallback }
}
