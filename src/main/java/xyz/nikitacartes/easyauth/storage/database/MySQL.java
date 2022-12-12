package xyz.nikitacartes.easyauth.storage.database;

import com.mysql.cj.jdbc.exceptions.CommunicationsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nikitacartes.easyauth.storage.AuthConfig;
import xyz.nikitacartes.easyauth.storage.PlayerCache;

import java.sql.*;
import java.util.HashMap;


public class MySQL implements DbApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("MySQL");

    private final AuthConfig config;
    private Connection MySQLConnection;

    /**
     * Connects to the MySQL.
     */
    public MySQL(AuthConfig config) {
        if (config.experimental.debugMode) {
            LOGGER.info("You are using MySQL DB");
        }
        this.config = config;
    }

    public void connect() throws DBApiException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String uri = "jdbc:mysql://" + config.main.MySQLHost + "/" + config.main.MySQLDatabase + "?autoReconnect=true";
            LOGGER.debug("connecting to {}", uri);
            MySQLConnection = DriverManager.getConnection(uri, config.main.MySQLUser, config.main.MySQLPassword);
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?;");
            preparedStatement.setString(1, config.main.MySQLTableName);
            if (!preparedStatement.executeQuery().next()) {
                MySQLConnection.createStatement().executeUpdate(
                        String.format("""
                                        CREATE TABLE `%s`.`%s` (
                                            `id` INT NOT NULL AUTO_INCREMENT,
                                            `uuid` VARCHAR(36) NOT NULL,
                                            `data` JSON NOT NULL,
                                            PRIMARY KEY (`id`), UNIQUE (`uuid`)
                                        ) ENGINE = InnoDB;""",
                                config.main.MySQLDatabase,
                                config.main.MySQLTableName
                        )
                );
            }
        } catch (ClassNotFoundException | SQLException e) {
            MySQLConnection = null;
            throw new DBApiException("Failed setting up mysql DB", e);
        }
    }

    private void reConnect() {
        try {
            if (MySQLConnection != null && MySQLConnection.isValid(0)) {
                LOGGER.warn("Reconnecting to a valid MySQL connection?");
            }
            connect();
        } catch (DBApiException | SQLException e) {
            LOGGER.error("Mysql reconnect failed", e);
        }
    }

    /**
     * Closes database connection.
     */
    public void close() {
        try {
            if (MySQLConnection != null) {
                MySQLConnection.close();
                MySQLConnection = null;
                LOGGER.info("Database connection closed successfully.");
            }
        } catch (CommunicationsException e) {
            LOGGER.error("Can't connect to database while closing", e);
        } catch (SQLException e) {
            LOGGER.error("Database connection not closed", e);
        }
    }

    /**
     * Tells whether DbApi connection is closed.
     *
     * @return false if connection is open, otherwise false
     */
    public boolean isClosed() {
        return MySQLConnection == null;
    }


    /**
     * Inserts the data for the player.
     *
     * @param uuid uuid of the player to insert data for
     * @param data data to put inside database
     * @return true if operation was successful, otherwise false
     */
    @Deprecated
    public boolean registerUser(String uuid, String data) {
        try {
            reConnect();
            if (!isUserRegistered(uuid)) {
                PreparedStatement preparedStatement = MySQLConnection.prepareStatement("INSERT INTO " + config.main.MySQLTableName + " (uuid, data) VALUES (?, ?);");
                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, data);
                preparedStatement.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Register error ", e);
        }
        return false;
    }

    /**
     * Checks if player is registered.
     *
     * @param uuid player's uuid
     * @return true if registered, otherwise false
     */
    public boolean isUserRegistered(String uuid) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT * FROM " + config.main.MySQLTableName + " WHERE uuid = ?;");
            preparedStatement.setString(1, uuid);
            return preparedStatement.executeQuery().next();
        } catch (SQLException e) {
            LOGGER.error("isUserRegistered error", e);
        }
        return false;
    }

    /**
     * Deletes data for the provided uuid.
     *
     * @param uuid uuid of player to delete data for
     */
    public void deleteUserData(String uuid) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("DELETE FROM " + config.main.MySQLTableName + " WHERE uuid = ?;");
            preparedStatement.setString(1, uuid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("deleteUserData error", e);
        }
    }

    /**
     * Updates player's data.
     *
     * @param uuid uuid of the player to update data for
     * @param data data to put inside database
     */
    @Deprecated
    public void updateUserData(String uuid, String data) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("UPDATE " + config.main.MySQLTableName + " SET data = ? WHERE uuid = ?;");
            preparedStatement.setString(1, data);
            preparedStatement.setString(1, uuid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("updateUserData error", e);
        }
    }

    /**
     * Gets the hashed password from DbApi.
     *
     * @param uuid uuid of the player to get data for.
     * @return data as string if player has it, otherwise empty string.
     */
    public String getUserData(String uuid) {
        try {
            reConnect();
            if (isUserRegistered(uuid)) {
                PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT data FROM " + config.main.MySQLTableName + " WHERE uuid = ?;");
                preparedStatement.setString(1, uuid);
                ResultSet query = preparedStatement.executeQuery();
                query.next();
                return query.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.error("getUserData error", e);
        }
        return "";
    }

    public void saveAll(HashMap<String, PlayerCache> playerCacheMap) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("INSERT INTO " + config.main.MySQLTableName + " (uuid, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?;");
            // Updating player data.
            playerCacheMap.forEach((uuid, playerCache) -> {
                String data = playerCache.toJson();
                try {
                    preparedStatement.setString(1, uuid);
                    preparedStatement.setString(2, data);
                    preparedStatement.setString(3, data);

                    preparedStatement.addBatch();
                } catch (SQLException e) {
                    LOGGER.error("Error saving player data!", e);
                }
            });
            preparedStatement.executeBatch();
        } catch (SQLException | NullPointerException e) {
            LOGGER.error("Error saving players data", e);
        }
    }
}
