package org.dynmap.storage.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.WebAuthManager;
import org.dynmap.storage.mysql.MySQLMapStorage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PostgreSQLHikariMapStorage extends MySQLMapStorage {
    String flags = null;


    public PostgreSQLHikariMapStorage() {
        super("postgresql");
    }

    public boolean init(DynmapCore core) {
        if (!initSuper(core)) {
            return false;
        }
        database = core.configuration.getString("storage/database", "dynmap");
        hostname = core.configuration.getString("storage/hostname", "localhost");
        port = core.configuration.getInteger("storage/port", 5432);
        userid = core.configuration.getString("storage/userid", "dynmap");
        password = core.configuration.getString("storage/password", "dynmap");
        prefix = core.configuration.getString("storage/prefix", "");
        flags = core.configuration.getString("storage/flags", "?allowReconnect=true");
        int poolSize = core.configuration.getInteger("storage/poolSize", 12);
        tableTiles = prefix + "Tiles";
        tableMaps = prefix + "Maps";
        tableFaces = prefix + "Faces";
        tableMarkerIcons = prefix + "MarkerIcons";
        tableMarkerFiles = prefix + "MarkerFiles";
        tableStandaloneFiles = prefix + "StandaloneFiles";
        tableSchemaVersion = prefix + "SchemaVersion";

        String connectionString = "jdbc:postgresql://" + hostname + ":" + port + "/" + database + flags;
        Log.info("Opening PostgreSQL database " + hostname + ":" + port + "/" + database + " as map store");
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException cnfx) {
            Log.severe("PostgreSQL-JDBC classes not found - PostgreSQL data source not usable");
            return false;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setUsername(this.userid);
        config.setPassword(this.password);
        config.setMaximumPoolSize(poolSize);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        datasource = new HikariDataSource(config);

        this.prepareSQL();

        // Initialize/update tables, if needed
        if (!initializeTables()) {
            return false;
        }

        return writeConfigPHP(core);
    }

    @Override
    protected void prepareSQL() {
        super.prepareSQL();
        // SQL diff
        this.sqlStatements.put("initializeTables1",
                "CREATE TABLE " +
                        this.tableMaps +
                        " (" +
                        "ID SERIAL PRIMARY KEY, " +
                        "WorldID VARCHAR(64) NOT NULL, " +
                        "MapID VARCHAR(64) NOT NULL, " +
                        "Variant VARCHAR(16) NOT NULL, " +
                        "ServerID BIGINT NOT NULL DEFAULT 0" +
                        ")"
        );
        this.sqlStatements.put("initializeTables2",
                "CREATE TABLE " +
                        this.tableTiles +
                        " (" +
                        "MapID INT NOT NULL, " +
                        "x INT NOT NULL, " +
                        "y INT NOT NULL, " +
                        "zoom INT NOT NULL, " +
                        "HashCode BIGINT NOT NULL, " +
                        "LastUpdate BIGINT NOT NULL, " +
                        "Format INT NOT NULL, Image BYTEA, " +
                        "PRIMARY KEY(MapID, x, y, zoom)" +
                        ")"
        );
        this.sqlStatements.put("initializeTables3",
                "CREATE TABLE " +
                        this.tableFaces +
                        " (" +
                        "PlayerName VARCHAR(64) NOT NULL, " +
                        "TypeID INT NOT NULL, " +
                        "Image BYTEA, " +
                        "PRIMARY KEY(PlayerName, TypeID)" +
                        ")"
        );
        this.sqlStatements.put("initializeTables4",
                "CREATE TABLE " +
                        this.tableMarkerIcons +
                        " (" +
                        "IconName VARCHAR(128) PRIMARY KEY NOT NULL, " +
                        "Image BYTEA" +
                        ")"
        );
        this.sqlStatements.put("initializeTables5",
                "CREATE TABLE " +
                        this.tableMarkerFiles +
                        " (" +
                        "FileName VARCHAR(128) PRIMARY KEY NOT NULL, " +
                        "Content BYTEA" +
                        ")"
        );
        this.sqlStatements.put("initializeTables6",
                "CREATE TABLE " +
                        this.tableStandaloneFiles +
                        " (" +
                        "FileName VARCHAR(128) NOT NULL, " +
                        "ServerID BIGINT NOT NULL DEFAULT 0, " +
                        "Content TEXT, " +
                        "PRIMARY KEY (FileName, ServerID)" +
                        ")"
        );
        this.sqlStatements.put("initializeTables9",
                "CREATE TABLE " +
                        this.tableStandaloneFiles +
                        " (" +
                        "FileName VARCHAR(128) NOT NULL, " +
                        "ServerID BIGINT NOT NULL DEFAULT 0, " +
                        "Content TEXT, " +
                        "PRIMARY KEY (FileName, ServerID)" +
                        ")"
        );
        this.sqlStatements.put("initializeTables14",
                "ALTER TABLE " +
                        this.tableStandaloneFiles +
                        " " +
                        "ADD COLUMN Content " +
                        "TEXT" +
                        ";"
        );
        this.sqlStatements.put("initializeTables15",
                "UPDATE " +
                        this.tableSchemaVersion +
                        " " +
                        "SET level=3 " +
                        "WHERE level = 2" +
                        ";"
        );
    }

    private boolean writeConfigPHP(DynmapCore core) {
        try (FileWriter fw = new FileWriter(new File(baseStandaloneDir, "PostgreSQL_config.php"))) {
            fw.write("<?php\n$dbname = '");
            fw.write(WebAuthManager.esc(database));
            fw.write("';\n");
            fw.write("$dbhost = '");
            fw.write(WebAuthManager.esc(hostname));
            fw.write("';\n");
            fw.write("$dbport = ");
            fw.write(Integer.toString(port));
            fw.write(";\n");
            fw.write("$dbuserid = '");
            fw.write(WebAuthManager.esc(userid));
            fw.write("';\n");
            fw.write("$dbpassword = '");
            fw.write(WebAuthManager.esc(password));
            fw.write("';\n");
            fw.write("$dbprefix = '");
            fw.write(WebAuthManager.esc(prefix));
            fw.write("';\n");
            fw.write("$loginenabled = ");
            fw.write(core.isLoginSupportEnabled() ? "true;\n" : "false;\n");
            fw.write("?>\n");
        } catch (IOException iox) {
            Log.severe("Error writing PostgreSQL_config.php", iox);
            return false;
        }
        return true;
    }

    @Override
    public String getMarkersURI(boolean login_enabled) {
        return "standalone/PostgreSQL_markers.php?marker=";
    }

    @Override
    public String getTilesURI(boolean login_enabled) {
        return "standalone/PostgreSQL_tiles.php?tile=";
    }

    @Override
    public String getConfigurationJSONURI(boolean login_enabled) {
        return "standalone/PostgreSQL_configuration.php"; // ?serverid={serverid}";
    }

    @Override
    public String getUpdateJSONURI(boolean login_enabled) {
        return "standalone/PostgreSQL_update.php?world={world}&ts={timestamp}"; // &serverid={serverid}";
    }

    @Override
    public String getSendMessageURI() {
        return "standalone/PostgreSQL_sendmessage.php";
    }

    @Override
    public String getStandaloneLoginURI() {
        return "standalone/PostgreSQL_login.php";
    }

    @Override
    public String getStandaloneRegisterURI() {
        return "standalone/PostgreSQL_register.php";
    }

}
