package org.dynmap.storage.mariadb;

import org.dynmap.Log;
import org.dynmap.storage.mysql.MySQLMapStorage;

public class MariaDBMapStorage extends MySQLMapStorage {

    public MariaDBMapStorage() {
    }

    // MariaDB specific driver check
    @Override
    protected boolean checkDriver() {
        connectionString = "jdbc:mariadb://" + hostname + ":" + port + "/" + database + flags;
        Log.info("Opening MariaDB database " + hostname + ":" + port + "/" + database + " as map store");
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException cnfx) {
            Log.severe("MariaDB-JDBC classes not found - MariaDB data source not usable");
            return false; 
        }
        return true;
    }
}
