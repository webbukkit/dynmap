package org.dynmap.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.dynmap.*;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.PlayerFaces.FaceType;
import org.dynmap.storage.*;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MySQLMapStorage extends MapStorage {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final HashMap<String, Integer> mapKey = new HashMap<>();
    private HikariDataSource datasource;
    private String userid;
    private String password;
    private String database;
    private String hostname;
    private String prefix = "";
    private String tableTiles;
    private String tableMaps;
    private String tableFaces;
    private String tableMarkerIcons;
    private String tableMarkerFiles;
    private String tableStandaloneFiles;
    private String tableSchemaVersion;
    private int port;
    private int poolSize;

    public MySQLMapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        this.database = core.configuration.getString("storage/database", "dynmap");
        this.hostname = core.configuration.getString("storage/hostname", "localhost");
        this.port = core.configuration.getInteger("storage/port", 3306);
        this.userid = core.configuration.getString("storage/userid", "dynmap");
        this.password = core.configuration.getString("storage/password", "dynmap");
        this.prefix = core.configuration.getString("storage/prefix", "");
        String flags = core.configuration.getString("storage/flags", "?allowReconnect=true");
        this.poolSize = core.configuration.getInteger("storage/poolSize", 8);
        this.tableTiles = this.prefix + "Tiles";
        this.tableMaps = this.prefix + "Maps";
        this.tableFaces = this.prefix + "Faces";
        this.tableMarkerIcons = this.prefix + "MarkerIcons";
        this.tableMarkerFiles = this.prefix + "MarkerFiles";
        this.tableStandaloneFiles = this.prefix + "StandaloneFiles";
        this.tableSchemaVersion = this.prefix + "SchemaVersion";

        String connectionString = "jdbc:mysql://" + this.hostname + ":" + this.port + "/" + this.database + flags;
        Log.info("Opening MySQL database " + this.hostname + ":" + this.port + "/" + this.database + " as map store");
        try {
            Class.forName("com.mysql.jdbc.Driver");
            // Initialize/update tables, if needed
            if (!this.initializeTables()) {
                return false;
            }
        } catch (ClassNotFoundException cnfx) {
            Log.severe("MySQL-JDBC classes not found - MySQL data source not usable");
            return false;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setUsername(this.userid);
        config.setPassword(this.password);
        config.setMaximumPoolSize(this.poolSize);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        datasource = new HikariDataSource(config);


        return this.writeConfigPHP(core);
    }

    public boolean shutdown() {
        if (this.datasource != null) {
            datasource.close();
            return true;
        }
        return false;
    }

    private boolean writeConfigPHP(DynmapCore core) {
        try (FileWriter fw = new FileWriter(new File(this.baseStandaloneDir, "MySQL_config.php"))) {
            fw.write("<?php\n$dbname = '");
            fw.write(WebAuthManager.esc(this.database));
            fw.write("';\n");
            fw.write("$dbhost = '");
            fw.write(WebAuthManager.esc(this.hostname));
            fw.write("';\n");
            fw.write("$dbport = ");
            fw.write(Integer.toString(this.port));
            fw.write(";\n");
            fw.write("$dbuserid = '");
            fw.write(WebAuthManager.esc(this.userid));
            fw.write("';\n");
            fw.write("$dbpassword = '");
            fw.write(WebAuthManager.esc(this.password));
            fw.write("';\n");
            fw.write("$dbprefix = '");
            fw.write(WebAuthManager.esc(this.prefix));
            fw.write("';\n");
            fw.write("$loginenabled = ");
            fw.write(core.isLoginSupportEnabled() ? "true;\n" : "false;\n");
            fw.write("?>\n");
        } catch (IOException iox) {
            Log.severe("Error writing MySQL_config.php", iox);
            return false;
        }
        return true;
    }

    private int getSchemaVersion() {
        int ver = 0;
        Connection c = null;
        try {
            c = this.getConnection();    // Get connection (create DB if needed)
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT level FROM " + this.tableSchemaVersion + ";");
            if (rs.next()) {
                ver = rs.getInt("level");
            }
            rs.close();
            stmt.close();
        } catch (SQLException ignored) {
        } finally {
            if (c != null) {
                this.releaseConnection(c);
            }
        }
        return ver;
    }

    private void doUpdate(Connection c, String sql) throws SQLException {
        Statement stmt = c.createStatement();
        stmt.executeUpdate(sql);
        stmt.close();
    }

    private void doLoadMaps() {
        Connection c = null;

        this.mapKey.clear();
        // Read the maps table - cache results
        try {
            c = this.getConnection();
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * from " + this.tableMaps + ";");
            while (rs.next()) {
                int key = rs.getInt("ID");
                String worldID = rs.getString("WorldID");
                String mapID = rs.getString("MapID");
                String variant = rs.getString("Variant");
                long serverid = rs.getLong("ServerID");
                if (serverid == this.serverID) { // One of ours
                    this.mapKey.put(worldID + ":" + mapID + ":" + variant, key);
                }
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Error loading map table - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
    }

    private Integer getMapKey(@NotNull DynmapWorld w, @NotNull MapType mt, @NotNull ImageVariant var) {
        String id = w.getName() + ":" + mt.getPrefix() + ":" + var.toString();
        synchronized (this.mapKey) {
            Integer k = this.mapKey.get(id);
            if (k == null) {    // No hit: new value so we need to add it to table
                Connection c = null;
                try {
                    c = this.getConnection();
                    // Insert row
                    PreparedStatement stmt = c.prepareStatement("INSERT INTO " + this.tableMaps + " (WorldID,MapID,Variant,ServerID) VALUES (?, ?, ?, ?);");
                    stmt.setString(1, w.getName());
                    stmt.setString(2, mt.getPrefix());
                    stmt.setString(3, var.toString());
                    stmt.setLong(4, this.serverID);
                    stmt.executeUpdate();
                    stmt.close();
                    //  Query key assigned
                    stmt = c.prepareStatement("SELECT ID FROM " + this.tableMaps + " WHERE WorldID = ? AND MapID = ? AND Variant = ? AND ServerID = ?;");
                    stmt.setString(1, w.getName());
                    stmt.setString(2, mt.getPrefix());
                    stmt.setString(3, var.toString());
                    stmt.setLong(4, this.serverID);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        k = rs.getInt("ID");
                        this.mapKey.put(id, k);
                    }
                    rs.close();
                    stmt.close();
                } catch (SQLException x) {
                    Log.severe("Error updating Maps table - " + x.getMessage());
                } finally {
                    assert c != null;
                    this.releaseConnection(c);
                }
            }

            return k;
        }
    }

    private boolean initializeTables() {
        Connection c = null;
        int version = this.getSchemaVersion();   // Get the existing schema version for the DB (if any)
        // If new, add our tables
        if (version == 0) {
            try {
                c = this.getConnection();
                this.doUpdate(c, "CREATE TABLE " + this.tableMaps + " (ID INTEGER PRIMARY KEY AUTO_INCREMENT, WorldID VARCHAR(64) NOT NULL, MapID VARCHAR(64) NOT NULL, Variant VARCHAR(16) NOT NULL, ServerID BIGINT NOT NULL DEFAULT 0)");
                this.doUpdate(c, "CREATE TABLE " + this.tableTiles + " (MapID INT NOT NULL, x INT NOT NULL, y INT NOT NULL, zoom INT NOT NULL, HashCode BIGINT NOT NULL, LastUpdate BIGINT NOT NULL, Format INT NOT NULL, Image BLOB, PRIMARY KEY(MapID, x, y, zoom))");
                this.doUpdate(c, "CREATE TABLE " + this.tableFaces + " (PlayerName VARCHAR(64) NOT NULL, TypeID INT NOT NULL, Image BLOB, PRIMARY KEY(PlayerName, TypeID))");
                this.doUpdate(c, "CREATE TABLE " + this.tableMarkerIcons + " (IconName VARCHAR(128) PRIMARY KEY NOT NULL, Image BLOB)");
                this.doUpdate(c, "CREATE TABLE " + this.tableMarkerFiles + " (FileName VARCHAR(128) PRIMARY KEY NOT NULL, Content MEDIUMTEXT)");
                this.doUpdate(c, "CREATE TABLE " + this.tableStandaloneFiles + " (FileName VARCHAR(128) NOT NULL, ServerID BIGINT NOT NULL DEFAULT 0, Content MEDIUMTEXT, PRIMARY KEY (FileName, ServerID))");
                this.doUpdate(c, "CREATE TABLE " + this.tableSchemaVersion + " (level INT PRIMARY KEY NOT NULL)");
                this.doUpdate(c, "INSERT INTO " + this.tableSchemaVersion + " (level) VALUES (3)");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                return false;
            } finally {
                assert c != null;
                this.releaseConnection(c);
            }
        } else if (version == 1) {
            try {
                c = this.getConnection();
                this.doUpdate(c, "CREATE TABLE " + this.tableStandaloneFiles + " (FileName VARCHAR(128) NOT NULL, ServerID BIGINT NOT NULL DEFAULT 0, Content MEDIUMTEXT, PRIMARY KEY (FileName, ServerID))");
                this.doUpdate(c, "ALTER TABLE " + this.tableMaps + " ADD COLUMN ServerID BIGINT NOT NULL DEFAULT 0 AFTER Variant");
                this.doUpdate(c, "UPDATE " + this.tableSchemaVersion + " SET level=3 WHERE level = 1;");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                return false;
            } finally {
                assert c != null;
                this.releaseConnection(c);
            }
        } else if (version == 2) {
            try {
                c = this.getConnection();
                this.doUpdate(c, "DELETE FROM " + this.tableStandaloneFiles + ";");
                this.doUpdate(c, "ALTER TABLE " + this.tableStandaloneFiles + " DROP COLUMN Content;");
                this.doUpdate(c, "ALTER TABLE " + this.tableStandaloneFiles + " ADD COLUMN Content MEDIUMTEXT;");
                this.doUpdate(c, "UPDATE " + this.tableSchemaVersion + " SET level=3 WHERE level = 2;");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                return false;
            } finally {
                assert c != null;
                this.releaseConnection(c);
            }
        }
        // Load maps table - cache results
        this.doLoadMaps();

        return true;
    }

    @NotNull
    private Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        if (connection == null) throw new NullPointerException();
        return connection;
    }

    private void releaseConnection(@NotNull Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y,
                                  int zoom, ImageVariant var) {
        return new StorageTile(world, map, x, y, zoom, var);
    }

    @Override
    public MapStorageTile getTile(DynmapWorld world, String uri) {
        String[] suri = uri.split("/");
        if (suri.length < 2) return null;
        String mname = suri[0]; // Map URI - might include variant
        MapType mt = null;
        ImageVariant imgvar = null;
        // Find matching map type and image variant
        for (int mti = 0; (mt == null) && (mti < world.maps.size()); mti++) {
            MapType type = world.maps.get(mti);
            ImageVariant[] var = type.getVariants();
            for (int ivi = 0; (imgvar == null) && (ivi < var.length); ivi++) {
                if (mname.equals(type.getPrefix() + var[ivi].variantSuffix)) {
                    mt = type;
                    imgvar = var[ivi];
                }
            }
        }
        if (mt == null) {   // Not found?
            return null;
        }
        // Now, take the last section and parse out coordinates and zoom
        String fname = suri[suri.length - 1];
        String[] coord = fname.split("[_.]");
        if (coord.length < 3) { // 3 or 4
            return null;
        }
        int zoom = 0;
        int x, y;
        try {
            if (coord[0].charAt(0) == 'z') {
                zoom = coord[0].length();
                x = Integer.parseInt(coord[1]);
                y = Integer.parseInt(coord[2]);
            } else {
                x = Integer.parseInt(coord[0]);
                y = Integer.parseInt(coord[1]);
            }
            return this.getTile(world, mt, x, y, zoom, imgvar);
        } catch (NumberFormatException nfx) {
            return null;
        }
    }

    @Override
    public void enumMapTiles(DynmapWorld world, MapType map,
                             MapStorageTileEnumCB cb) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                this.processEnumMapTiles(world, mt, var, cb, null, null);
            }
        }
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                this.processEnumMapTiles(world, mt, var, null, cbBase, cbEnd);
            }
        }
    }

    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        Connection c = null;
        Integer mapkey = this.getMapKey(world, map, var);
        if (mapkey == null) {
            if (cbEnd != null)
                cbEnd.searchEnded();
            return;
        }
        try {
            c = this.getConnection();
            // Query tiles for given mapkey
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT x,y,zoom,Format FROM " + this.tableTiles + " WHERE MapID=" + mapkey + ";");
            while (rs.next()) {
                StorageTile st = new StorageTile(world, map, rs.getInt("x"), rs.getInt("y"), rs.getInt("zoom"), var);
                final MapType.ImageEncoding encoding = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                if (cb != null)
                    cb.tileFound(st, encoding);
                if (cbBase != null && st.zoom == 0)
                    cbBase.tileFound(st, encoding);
                st.cleanup();
            }
            if (cbEnd != null)
                cbEnd.searchEnded();
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Tile enum error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                this.processPurgeMapTiles(world, mt, var);
            }
        }
    }

    private void processPurgeMapTiles(DynmapWorld world, MapType map, ImageVariant var) {
        Connection c = null;
        Integer mapkey = this.getMapKey(world, map, var);
        if (mapkey == null) return;
        try {
            c = this.getConnection();
            // Query tiles for given mapkey
            Statement stmt = c.createStatement();
            stmt.executeUpdate("DELETE FROM " + this.tableTiles + " WHERE MapID=" + mapkey + ";");
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Tile purge error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, FaceType facetype,
                                      BufferOutputStream encImage) {
        Connection c = null;
        boolean err = false;
        boolean exists = this.hasPlayerFaceImage(playername, facetype);
        // If delete, and doesn't exist, quit
        if ((encImage == null) && (!exists)) return false;

        try {
            c = this.getConnection();
            PreparedStatement stmt;
            if (encImage == null) { // If delete
                stmt = c.prepareStatement("DELETE FROM " + this.tableFaces + " WHERE PlayerName=? AND TypeIDx=?;");
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);
            } else if (exists) {
                stmt = c.prepareStatement("UPDATE " + this.tableFaces + " SET Image=? WHERE PlayerName=? AND TypeID=?;");
                stmt.setBinaryStream(1, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
                stmt.setString(2, playername);
                stmt.setInt(3, facetype.typeID);
            } else {
                stmt = c.prepareStatement("INSERT INTO " + this.tableFaces + " (PlayerName,TypeID,Image) VALUES (?,?,?);");
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);
                stmt.setBinaryStream(3, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
            }
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face write error - " + x.getMessage());
            err = true;
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return !err;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
                                                FaceType facetype) {
        Connection c = null;
        BufferInputStream image = null;
        try {
            c = this.getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Image FROM " + this.tableFaces + " WHERE PlayerName=? AND TypeID=?;");
            stmt.setString(1, playername);
            stmt.setInt(2, facetype.typeID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                byte[] img = rs.getBytes("Image");
                image = new BufferInputStream(img);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face read error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return image;
    }

    @Override
    public boolean hasPlayerFaceImage(String playername, FaceType facetype) {
        Connection c = null;
        boolean exists = false;
        try {
            c = this.getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT TypeID FROM " + this.tableFaces + " WHERE PlayerName=? AND TypeID=?;");
            stmt.setString(1, playername);
            stmt.setInt(2, facetype.typeID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                exists = true;
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face exists error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
        Connection c = null;
        boolean err = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            c = this.getConnection();
            boolean exists = false;
            stmt = c.prepareStatement("SELECT IconName FROM " + this.tableMarkerIcons + " WHERE IconName=?;");
            stmt.setString(1, markerid);
            rs = stmt.executeQuery();
            if (rs.next()) {
                exists = true;
            }
            rs.close();
            rs = null;
            stmt.close();
            stmt = null;
            if (encImage == null) { // If delete
                // If delete, and doesn't exist, quit
                if (!exists) return false;
                stmt = c.prepareStatement("DELETE FROM " + this.tableMarkerIcons + " WHERE IconName=?;");
                stmt.setString(1, markerid);
                stmt.executeUpdate();
            } else if (exists) {
                stmt = c.prepareStatement("UPDATE " + this.tableMarkerIcons + " SET Image=? WHERE IconName=?;");
                stmt.setBinaryStream(1, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
                stmt.setString(2, markerid);
            } else {
                stmt = c.prepareStatement("INSERT INTO " + this.tableMarkerIcons + " (IconName,Image) VALUES (?,?);");
                stmt.setString(1, markerid);
                stmt.setBinaryStream(2, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
            }
            stmt.executeUpdate();
        } catch (SQLException x) {
            Log.severe("Marker write error - " + x.getMessage());
            err = true;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ignored) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {
                }
            }
            assert c != null;
            this.releaseConnection(c);
        }
        return !err;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
        Connection c = null;
        BufferInputStream image = null;
        try {
            c = this.getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Image FROM " + this.tableMarkerIcons + " WHERE IconName=?;");
            stmt.setString(1, markerid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                byte[] img = rs.getBytes("Image");
                image = new BufferInputStream(img);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Marker read error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return image;
    }

    @Override
    public boolean setMarkerFile(String world, String content) {
        Connection c = null;
        boolean err = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            c = this.getConnection();
            boolean exists = false;
            stmt = c.prepareStatement("SELECT FileName FROM " + this.tableMarkerFiles + " WHERE FileName=?;");
            stmt.setString(1, world);
            rs = stmt.executeQuery();
            if (rs.next()) {
                exists = true;
            }
            rs.close();
            rs = null;
            stmt.close();
            stmt = null;
            if (content == null) { // If delete
                // If delete, and doesn't exist, quit
                if (!exists) return false;
                stmt = c.prepareStatement("DELETE FROM " + this.tableMarkerFiles + " WHERE FileName=?;");
                stmt.setString(1, world);
                stmt.executeUpdate();
            } else if (exists) {
                stmt = c.prepareStatement("UPDATE " + this.tableMarkerFiles + " SET Content=? WHERE FileName=?;");
                stmt.setBytes(1, content.getBytes(UTF8));
                stmt.setString(2, world);
            } else {
                stmt = c.prepareStatement("INSERT INTO " + this.tableMarkerFiles + " (FileName,Content) VALUES (?,?);");
                stmt.setString(1, world);
                stmt.setBytes(2, content.getBytes(UTF8));
            }
            stmt.executeUpdate();
        } catch (SQLException x) {
            Log.severe("Marker file write error - " + x.getMessage());
            err = true;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ignored) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {
                }
            }
            assert c != null;
            this.releaseConnection(c);
        }
        return !err;
    }

    @Override
    public String getMarkerFile(String world) {
        Connection c = null;
        String content = null;
        try {
            c = this.getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Content FROM " + this.tableMarkerFiles + " WHERE FileName=?;");
            stmt.setString(1, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                byte[] img = rs.getBytes("Content");
                content = new String(img, UTF8);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Marker file read error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return content;
    }

    @Override
    public String getMarkersURI(boolean login_enabled) {
        return "standalone/MySQL_markers.php?marker=";
    }

    @Override
    public String getTilesURI(boolean login_enabled) {
        return "standalone/MySQL_tiles.php?tile=";
    }

    @Override
    public String getConfigurationJSONURI(boolean login_enabled) {
        return "standalone/MySQL_configuration.php"; // ?serverid={serverid}";
    }

    @Override
    public String getUpdateJSONURI(boolean login_enabled) {
        return "standalone/MySQL_update.php?world={world}&ts={timestamp}"; // &serverid={serverid}";
    }

    @Override
    public String getSendMessageURI() {
        return "standalone/MySQL_sendmessage.php";
    }

    @Override
    public BufferInputStream getStandaloneFile(String fileid) {
        Connection c = null;
        BufferInputStream content = null;
        try {
            c = this.getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Content FROM " + this.tableStandaloneFiles + " WHERE FileName=? AND ServerID=?;");
            stmt.setString(1, fileid);
            stmt.setLong(2, this.serverID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                byte[] img = rs.getBytes("Content");
                content = new BufferInputStream(img);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Standalone file read error - " + x.getMessage());
        } finally {
            assert c != null;
            this.releaseConnection(c);
        }
        return content;
    }

    @Override
    public boolean setStandaloneFile(String fileid, BufferOutputStream content) {
        Connection c = null;
        boolean err = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            c = this.getConnection();
            boolean exists = false;
            stmt = c.prepareStatement("SELECT FileName FROM " + this.tableStandaloneFiles + " WHERE FileName=? AND ServerID=?;");
            stmt.setString(1, fileid);
            stmt.setLong(2, this.serverID);
            rs = stmt.executeQuery();
            if (rs.next()) {
                exists = true;
            }
            rs.close();
            rs = null;
            stmt.close();
            stmt = null;
            if (content == null) { // If delete
                // If delete, and doesn't exist, quit
                if (!exists) return true;
                stmt = c.prepareStatement("DELETE FROM " + this.tableStandaloneFiles + " WHERE FileName=? AND ServerID=?;");
                stmt.setString(1, fileid);
                stmt.setLong(2, this.serverID);
                stmt.executeUpdate();
            } else if (exists) {
                stmt = c.prepareStatement("UPDATE " + this.tableStandaloneFiles + " SET Content=? WHERE FileName=? AND ServerID=?;");
                stmt.setBinaryStream(1, new BufferInputStream(content.buf, content.len), content.len);
                stmt.setString(2, fileid);
                stmt.setLong(3, this.serverID);
            } else {
                stmt = c.prepareStatement("INSERT INTO " + this.tableStandaloneFiles + " (FileName,ServerID,Content) VALUES (?,?,?);");
                stmt.setString(1, fileid);
                stmt.setLong(2, this.serverID);
                stmt.setBinaryStream(3, new BufferInputStream(content.buf, content.len), content.len);
            }
            stmt.executeUpdate();
        } catch (SQLException x) {
            Log.severe("Standalone file write error - " + x.getMessage());
            err = true;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ignored) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {
                }
            }
            assert c != null;
            this.releaseConnection(c);
        }
        return !err;
    }

    @Override
    public boolean wrapStandaloneJSON(boolean login_enabled) {
        return false;
    }

    @Override
    public boolean wrapStandalonePHP() {
        return false;
    }

    @Override
    public String getStandaloneLoginURI() {
        return "standalone/MySQL_login.php";
    }

    @Override
    public String getStandaloneRegisterURI() {
        return "standalone/MySQL_register.php";
    }

    @Override
    public void setLoginEnabled(DynmapCore core) {
        this.writeConfigPHP(core);
    }

    public class StorageTile extends MapStorageTile {
        private Integer mapkey;
        private String uri;

        protected StorageTile(DynmapWorld world, MapType map, int x, int y,
                              int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);

            this.mapkey = MySQLMapStorage.this.getMapKey(world, map, var);

            if (zoom > 0) {
                this.uri = map.getPrefix() + var.variantSuffix + "/" + (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            } else {
                this.uri = map.getPrefix() + var.variantSuffix + "/" + (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            }
        }

        @Override
        public boolean exists() {
            if (this.mapkey == null) return false;
            boolean rslt = false;
            Connection c = null;
            try {
                c = MySQLMapStorage.this.getConnection();
                Statement stmt = c.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT HashCode FROM " + MySQLMapStorage.this.tableTiles + " WHERE MapID=" + this.mapkey + " AND x=" + this.x + " AND y=" + this.y + " AND zoom=" + this.zoom + ";");
                rslt = rs.next();
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile exists error - " + x.getMessage());
            } finally {
                assert c != null;
                MySQLMapStorage.this.releaseConnection(c);
            }
            return rslt;
        }

        @Override
        public boolean matchesHashCode(long hash) {
            if (this.mapkey == null) return false;
            boolean rslt = false;
            Connection c = null;
            try {
                c = MySQLMapStorage.this.getConnection();
                Statement stmt = c.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT HashCode FROM " + MySQLMapStorage.this.tableTiles + " WHERE MapID=" + this.mapkey + " AND x=" + this.x + " AND y=" + this.y + " AND zoom=" + this.zoom + ";");
                if (rs.next()) {
                    long v = rs.getLong("HashCode");
                    rslt = (v == hash);
                }
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile matches hash error - " + x.getMessage());
            } finally {
                assert c != null;
                MySQLMapStorage.this.releaseConnection(c);
            }
            return rslt;
        }

        @Override
        public TileRead read() {
            if (this.mapkey == null) return null;
            TileRead rslt = null;
            Connection c = null;
            try {
                c = MySQLMapStorage.this.getConnection();
                Statement stmt = c.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT HashCode,LastUpdate,Format,Image FROM " + MySQLMapStorage.this.tableTiles + " WHERE MapID=" + this.mapkey + " AND x=" + this.x + " AND y=" + this.y + " AND zoom=" + this.zoom + ";");
                if (rs.next()) {
                    rslt = new TileRead();
                    rslt.hashCode = rs.getLong("HashCode");
                    rslt.lastModified = rs.getLong("LastUpdate");
                    rslt.format = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                    byte[] img = rs.getBytes("Image");
                    rslt.image = new BufferInputStream(img);
                }
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile read error - " + x.getMessage());
            } finally {
                assert c != null;
                MySQLMapStorage.this.releaseConnection(c);
            }
            return rslt;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage) {
            if (this.mapkey == null) return false;
            Connection c = null;
            boolean err = false;
            boolean exists = this.exists();
            // If delete, and doesn't exist, quit
            if ((encImage == null) && (!exists)) return false;

            try {
                c = MySQLMapStorage.this.getConnection();
                PreparedStatement stmt;
                if (encImage == null) { // If delete
                    stmt = c.prepareStatement("DELETE FROM " + MySQLMapStorage.this.tableTiles + " WHERE MapID=? AND x=? and y=? AND zoom=?;");
                    stmt.setInt(1, this.mapkey);
                    stmt.setInt(2, this.x);
                    stmt.setInt(3, this.y);
                    stmt.setInt(4, this.zoom);
                } else if (exists) {
                    stmt = c.prepareStatement("UPDATE " + MySQLMapStorage.this.tableTiles + " SET HashCode=?, LastUpdate=?, Format=?, Image=? WHERE MapID=? AND x=? and y=? AND zoom=?;");
                    stmt.setLong(1, hash);
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setInt(3, this.map.getImageFormat().getEncoding().ordinal());
                    stmt.setBinaryStream(4, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
                    stmt.setInt(5, this.mapkey);
                    stmt.setInt(6, this.x);
                    stmt.setInt(7, this.y);
                    stmt.setInt(8, this.zoom);
                } else {
                    stmt = c.prepareStatement("INSERT INTO " + MySQLMapStorage.this.tableTiles + " (MapID,x,y,zoom,HashCode,LastUpdate,Format,Image) VALUES (?,?,?,?,?,?,?,?);");
                    stmt.setInt(1, this.mapkey);
                    stmt.setInt(2, this.x);
                    stmt.setInt(3, this.y);
                    stmt.setInt(4, this.zoom);
                    stmt.setLong(5, hash);
                    stmt.setLong(6, System.currentTimeMillis());
                    stmt.setInt(7, this.map.getImageFormat().getEncoding().ordinal());
                    stmt.setBinaryStream(8, new BufferInputStream(encImage.buf, encImage.len), encImage.len);
                }
                stmt.executeUpdate();
                stmt.close();
                // Signal update for zoom out
                if (this.zoom == 0) {
                    this.world.enqueueZoomOutUpdate(this);
                }
            } catch (SQLException x) {
                Log.severe("Tile write error - " + x.getMessage());
                err = true;
            } finally {
                assert c != null;
                MySQLMapStorage.this.releaseConnection(c);
            }
            return !err;
        }

        @Override
        public boolean getWriteLock() {
            return MySQLMapStorage.this.getWriteLock(this.uri);
        }

        @Override
        public void releaseWriteLock() {
            MySQLMapStorage.this.releaseWriteLock(this.uri);
        }

        @Override
        public boolean getReadLock(long timeout) {
            return MySQLMapStorage.this.getReadLock(this.uri, timeout);
        }

        @Override
        public void releaseReadLock() {
            MySQLMapStorage.this.releaseReadLock(this.uri);
        }

        @Override
        public void cleanup() {
        }

        @Override
        public String getURI() {
            return this.uri;
        }

        @Override
        public void enqueueZoomOutUpdate() {
            this.world.enqueueZoomOutUpdate(this);
        }

        @Override
        public MapStorageTile getZoomOutTile() {
            int xx, yy;
            int step = 1 << this.zoom;
            if (this.x >= 0)
                xx = this.x - (this.x % (2 * step));
            else
                xx = this.x + (this.x % (2 * step));
            yy = -this.y;
            if (yy >= 0)
                yy = yy - (yy % (2 * step));
            else
                yy = yy + (yy % (2 * step));
            yy = -yy;
            return new StorageTile(this.world, this.map, xx, yy, this.zoom + 1, this.var);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof StorageTile) {
                StorageTile st = (StorageTile) o;
                return this.uri.equals(st.uri);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.uri.hashCode();
        }
    }

}
