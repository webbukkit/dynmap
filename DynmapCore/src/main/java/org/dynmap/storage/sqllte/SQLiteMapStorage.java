package org.dynmap.storage.sqllte;

import java.io.File;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.WebAuthManager;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.PlayerFaces.FaceType;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTileEnumCB;
import org.dynmap.storage.MapStorageBaseTileEnumCB;
import org.dynmap.storage.MapStorageTileSearchEndCB;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

public class SQLiteMapStorage extends MapStorage {
    private String connectionString;
    private String databaseFile;
    private static final int POOLSIZE = 5;
    private Connection[] cpool = new Connection[POOLSIZE];
    private int cpoolCount = 0;
    private static final Charset UTF8 = Charset.forName("UTF-8");
        
    public class StorageTile extends MapStorageTile {
        private Integer mapkey;
        private String uri;
        protected StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);
            
            mapkey = getMapKey(world, map, var);

            if (zoom > 0) {
                uri = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            }
            else {
                uri = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            }
        }

        @Override
        public boolean exists() {
            if (mapkey == null) return false;
            boolean rslt = false;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                Statement stmt = c.createStatement();
                //ResultSet rs = stmt.executeQuery("SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                ResultSet rs = doExecuteQuery(stmt, "SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                rslt = rs.next();
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile exists error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return rslt;
        }

        @Override
        public boolean matchesHashCode(long hash) {
            if (mapkey == null) return false;
            boolean rslt = false;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                Statement stmt = c.createStatement();
                //ResultSet rs = stmt.executeQuery("SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                ResultSet rs = doExecuteQuery(stmt, "SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                if (rs.next()) {
                    long v = rs.getLong("HashCode");
                    rslt = (v == hash);
                }
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile matches hash error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return rslt;
        }

        @Override
        public TileRead read() {
            if (mapkey == null) return null;
            TileRead rslt = null;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                Statement stmt = c.createStatement();
                //ResultSet rs = stmt.executeQuery("SELECT HashCode,LastUpdate,Format,Image FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                ResultSet rs = doExecuteQuery(stmt, "SELECT HashCode,LastUpdate,Format,Image,ImageLen FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";");
                if (rs.next()) {
                    rslt = new TileRead();
                    rslt.hashCode = rs.getLong("HashCode");
                    rslt.lastModified = rs.getLong("LastUpdate");
                    rslt.format = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                    byte[] img = rs.getBytes("Image");
                    int len = rs.getInt("ImageLen");
                    if (len <= 0) {
                    	len = img.length;
                    	// Trim trailing zeros from padding by BLOB field
                    	while((len > 0) && (img[len-1] == '\0')) len--;
                    }
                    rslt.image = new BufferInputStream(img, len);
                }
                rs.close();
                stmt.close();
            } catch (SQLException x) {
                Log.severe("Tile read error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return rslt;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage) {
            if (mapkey == null) return false;
            Connection c = null;
            boolean err = false;
            boolean exists = exists();
            // If delete, and doesn't exist, quit
            if ((encImage == null) && (!exists)) return false;
            
            try {
                c = getConnection();
                PreparedStatement stmt;
                if (encImage == null) { // If delete
                    stmt = c.prepareStatement("DELETE FROM Tiles WHERE MapID=? AND x=? and y=? AND zoom=?;");
                    stmt.setInt(1, mapkey);
                    stmt.setInt(2, x);
                    stmt.setInt(3, y);
                    stmt.setInt(4, zoom);
                }
                else if (exists) {
                    stmt = c.prepareStatement("UPDATE Tiles SET HashCode=?, LastUpdate=?, Format=?, Image=?, ImageLen=? WHERE MapID=? AND x=? and y=? AND zoom=?;");
                    stmt.setLong(1, hash);
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setInt(3, map.getImageFormat().getEncoding().ordinal());
                    stmt.setBytes(4, encImage.buf);
                    stmt.setInt(5, encImage.len);
                    stmt.setInt(6, mapkey);
                    stmt.setInt(7, x);
                    stmt.setInt(8, y);
                    stmt.setInt(9, zoom);
                }
                else {
                    stmt = c.prepareStatement("INSERT INTO Tiles (MapID,x,y,zoom,HashCode,LastUpdate,Format,Image,ImageLen) VALUES (?,?,?,?,?,?,?,?,?);");
                    stmt.setInt(1, mapkey);
                    stmt.setInt(2, x);
                    stmt.setInt(3, y);
                    stmt.setInt(4, zoom);
                    stmt.setLong(5, hash);
                    stmt.setLong(6, System.currentTimeMillis());
                    stmt.setInt(7, map.getImageFormat().getEncoding().ordinal());
                    stmt.setBytes(8, encImage.buf);
                    stmt.setInt(9, encImage.len);
                }
                //stmt.executeUpdate();
                doExecuteUpdate(stmt);
                stmt.close();
                // Signal update for zoom out
                if (zoom == 0) {
                    world.enqueueZoomOutUpdate(this);
                }
            } catch (SQLException x) {
                Log.severe("Tile write error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return !err;
        }

        @Override
        public boolean getWriteLock() {
            return SQLiteMapStorage.this.getWriteLock(uri);
        }

        @Override
        public void releaseWriteLock() {
            SQLiteMapStorage.this.releaseWriteLock(uri);
        }

        @Override
        public boolean getReadLock(long timeout) {
            return SQLiteMapStorage.this.getReadLock(uri, timeout);
        }

        @Override
        public void releaseReadLock() {
            SQLiteMapStorage.this.releaseReadLock(uri);
        }

        @Override
        public void cleanup() {
        }

        @Override
        public String getURI() {
            return uri;
        }

        @Override
        public void enqueueZoomOutUpdate() {
            world.enqueueZoomOutUpdate(this);
        }

        @Override
        public MapStorageTile getZoomOutTile() {
            int xx, yy;
            int step = 1 << zoom;
            if(x >= 0)
                xx = x - (x % (2*step));
            else
                xx = x + (x % (2*step));
            yy = -y;
            if(yy >= 0)
                yy = yy - (yy % (2*step));
            else
                yy = yy + (yy % (2*step));
            yy = -yy;
            return new StorageTile(world, map, xx, yy, zoom+1, var);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof StorageTile) {
                StorageTile st = (StorageTile) o;
                return uri.equals(st.uri);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }
    }
    
    public SQLiteMapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        File dbfile = core.getFile(core.configuration.getString("storage/dbfile", "dynmap.db"));
        databaseFile = dbfile.getAbsolutePath();
        connectionString = "jdbc:sqlite:" + databaseFile;
        Log.info("Opening SQLite file " + databaseFile + " as map store");
        try {
            Class.forName("org.sqlite.JDBC");
            // Initialize/update tables, if needed
            return initializeTables();
        } catch (ClassNotFoundException cnfx) {
            Log.severe("SQLite-JDBC classes not found - sqlite data source not usable");
            return false; 
        }
    }

    private int getSchemaVersion() {
        int ver = 0;
        boolean err = false;
        Connection c = null;
        try {
            c = getConnection();    // Get connection (create DB if needed)
            Statement stmt = c.createStatement();
            //ResultSet rs = stmt.executeQuery( "SELECT level FROM SchemaVersion;");
            ResultSet rs = doExecuteQuery(stmt, "SELECT level FROM SchemaVersion;");
            if (rs.next()) {
                ver = rs.getInt("level");
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            err = true;
        } finally {
            if (c != null) { releaseConnection(c, err); }
        }
        return ver;
    }
    
    private void doUpdate(Connection c, String sql) throws SQLException {
        Statement stmt = c.createStatement();
        //stmt.executeUpdate(sql);
        doExecuteUpdate(stmt, sql);
        stmt.close();
    }
    
    private HashMap<String, Integer> mapKey = new HashMap<String, Integer>();
    
    private void doLoadMaps() {
        Connection c = null;
        boolean err = false;
        
        mapKey.clear();
        // Read the maps table - cache results
        try {
            c = getConnection();
            Statement stmt = c.createStatement();
            //ResultSet rs = stmt.executeQuery("SELECT * from Maps;");
            ResultSet rs = doExecuteQuery(stmt, "SELECT * from Maps;");
            while (rs.next()) {
                int key = rs.getInt("ID");
                String worldID = rs.getString("WorldID");
                String mapID = rs.getString("MapID");
                String variant = rs.getString("Variant");
                mapKey.put(worldID + ":" + mapID + ":" + variant, key);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Error loading map table - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
            c = null;
        }
    }
    
    private Integer getMapKey(DynmapWorld w, MapType mt, ImageVariant var) {
        String id = w.getName() + ":" + mt.getPrefix() + ":" + var.toString();
        synchronized(mapKey) {
            Integer k = mapKey.get(id);
            if (k == null) {    // No hit: new value so we need to add it to table
                Connection c = null;
                boolean err = false;
                try {
                    c = getConnection();
                    // Insert row
                    PreparedStatement stmt = c.prepareStatement("INSERT INTO Maps (WorldID,MapID,Variant) VALUES (?, ?, ?);");
                    stmt.setString(1, w.getName());
                    stmt.setString(2, mt.getPrefix());
                    stmt.setString(3, var.toString());
                    //stmt.executeUpdate();
                    doExecuteUpdate(stmt);
                    stmt.close();
                    //  Query key assigned
                    stmt = c.prepareStatement("SELECT ID FROM Maps WHERE WorldID = ? AND MapID = ? AND Variant = ?;");
                    stmt.setString(1, w.getName());
                    stmt.setString(2, mt.getPrefix());
                    stmt.setString(3, var.toString());
                    //ResultSet rs = stmt.executeQuery();
                    ResultSet rs = doExecuteQuery(stmt);
                    if (rs.next()) {
                        k = rs.getInt("ID");
                        mapKey.put(id, k);
                    }
                    rs.close();
                    stmt.close();
                } catch (SQLException x) {
                    Log.severe("Error updating Maps table - " + x.getMessage());
                    err = true;
                } finally {
                    releaseConnection(c, err);
                }
            }

            return k;
        }
    }
    
    private boolean initializeTables() {
        Connection c = null;
        boolean err = false;
        int version = getSchemaVersion();   // Get the existing schema version for the DB (if any)
        // If new, add our tables
        if (version == 0) {
            try {
                c = getConnection();
                doUpdate(c, "CREATE TABLE Maps (ID INTEGER PRIMARY KEY AUTOINCREMENT, WorldID STRING NOT NULL, MapID STRING NOT NULL, Variant STRING NOT NULL)");
                doUpdate(c, "CREATE TABLE Tiles (MapID INT NOT NULL, x INT NOT NULL, y INT NOT NULL, zoom INT NOT NULL, HashCode INT NOT NULL, LastUpdate INT NOT NULL, Format INT NOT NULL, Image BLOB, ImageLen INT, PRIMARY KEY(MapID, x, y, zoom))");
                doUpdate(c, "CREATE TABLE Faces (PlayerName STRING NOT NULL, TypeID INT NOT NULL, Image BLOB, ImageLen INT, PRIMARY KEY(PlayerName, TypeID))");
                doUpdate(c, "CREATE TABLE MarkerIcons (IconName STRING PRIMARY KEY NOT NULL, Image BLOB, ImageLen INT)");
                doUpdate(c, "CREATE TABLE MarkerFiles (FileName STRING PRIMARY KEY NOT NULL, Content CLOB)");
                doUpdate(c, "CREATE TABLE SchemaVersion (level INT PRIMARY KEY NOT NULL)");
                doUpdate(c, "INSERT INTO SchemaVersion (level) VALUES (2)");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                err = true;
                return false;
            } finally {
                releaseConnection(c, err);
                c = null;
            }
        }
        else if (version == 1) {	// Add ImageLen columns
            try {
                c = getConnection();
                doUpdate(c, "ALTER TABLE Tiles ADD ImageLen INT");
                doUpdate(c, "ALTER TABLE Faces ADD ImageLen INT");
                doUpdate(c, "ALTER TABLE MarkerIcons ADD ImageLen INT");
                doUpdate(c, "UPDATE SchemaVersion SET level=2");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                err = true;
                return false;
            } finally {
                releaseConnection(c, err);
                c = null;
            }
        }
        // Load maps table - cache results
        doLoadMaps();
        
        return true;
    }
    
    private Connection getConnection() throws SQLException {
        Connection c = null;
        synchronized (cpool) {
            while (c == null) {
                for (int i = 0; i < cpool.length; i++) {    // See if available connection
                    if (cpool[i] != null) { // Found one
                        c = cpool[i];
                        cpool[i] = null;
                    }
                }
                if (c == null) {
                    if (cpoolCount < POOLSIZE) {  // Still more we can have
                        c = DriverManager.getConnection(connectionString);
                        configureConnection(c);
                        cpoolCount++;
                    }
                    else {
                        try {
                            cpool.wait();
                        } catch (InterruptedException e) {
                            throw new SQLException("Interruped");
                        }
                    }
                }
            }
        }
        return c;
    }
    
    private static Connection configureConnection(Connection conn) throws SQLException {
        final Statement statement = conn.createStatement();
        statement.execute("PRAGMA journal_mode = WAL;");
        statement.close();
        return conn;
    }
    
    private void releaseConnection(Connection c, boolean err) {
        if (c == null) return;
        synchronized (cpool) {
            if (!err)  {  // Find slot to keep it in pool
                for (int i = 0; i < POOLSIZE; i++) {
                    if (cpool[i] == null) {
                        cpool[i] = c;
                        c = null; // Mark it recovered (no close needed
                        cpool.notifyAll();
                        break;
                    }
                }
            }
            if (c != null) {  // If broken, just toss it
                try { c.close(); } catch (SQLException x) {}
                cpoolCount--;   // And reduce count
                cpool.notifyAll();
            }
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
        String fname = suri[suri.length-1];
        String[] coord = fname.split("[_\\.]");
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
            }
            else {
                x = Integer.parseInt(coord[0]);
                y = Integer.parseInt(coord[1]);
            }
            return getTile(world, mt, x, y, zoom, imgvar);
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
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, cb, null, null);
            }
        }
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, null, cbBase, cbEnd);
            }
        }
    }

    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        Connection c = null;
        boolean err = false;
        Integer mapkey = getMapKey(world, map, var);
        if (mapkey == null) {
            if(cbEnd != null)
                cbEnd.searchEnded();
            return;
        }
        try {
            c = getConnection();
            // Query tiles for given mapkey
            Statement stmt = c.createStatement();
            //ResultSet rs = stmt.executeQuery("SELECT x,y,zoom,Format FROM Tiles WHERE MapID=" + mapkey + ";");
            ResultSet rs = doExecuteQuery(stmt, "SELECT x,y,zoom,Format FROM Tiles WHERE MapID=" + mapkey + ";");
            while (rs.next()) {
                StorageTile st = new StorageTile(world, map, rs.getInt("x"), rs.getInt("y"), rs.getInt("zoom"), var);
                final MapType.ImageEncoding encoding = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                if(cb != null)
                    cb.tileFound(st, encoding);
                if(cbBase != null && st.zoom == 0)
                    cbBase.tileFound(st, encoding);
                st.cleanup();
            }
            if(cbEnd != null)
                cbEnd.searchEnded();
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Tile enum error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processPurgeMapTiles(world, mt, var);
            }
        }
    }
    private void processPurgeMapTiles(DynmapWorld world, MapType map, ImageVariant var) {
        Connection c = null;
        boolean err = false;
        Integer mapkey = getMapKey(world, map, var);
        if (mapkey == null) return;
        try {
            c = getConnection();
            // Query tiles for given mapkey
            Statement stmt = c.createStatement();
            //stmt.executeUpdate("DELETE FROM Tiles WHERE MapID=" + mapkey + ";");
            doExecuteUpdate(stmt, "DELETE FROM Tiles WHERE MapID=" + mapkey + ";");
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Tile purge error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, FaceType facetype,
            BufferOutputStream encImage) {
        Connection c = null;
        boolean err = false;
        boolean exists = hasPlayerFaceImage(playername, facetype);
        // If delete, and doesn't exist, quit
        if ((encImage == null) && (!exists)) return false;
        
        try {
            c = getConnection();
            PreparedStatement stmt;
            if (encImage == null) { // If delete
                stmt = c.prepareStatement("DELETE FROM Faces WHERE PlayerName=? AND TypeIDx=?;");
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);
            }
            else if (exists) {
                stmt = c.prepareStatement("UPDATE Faces SET Image=?,ImageLen=? WHERE PlayerName=? AND TypeID=?;");
                stmt.setBytes(1, encImage.buf);
                stmt.setInt(2, encImage.len);
                stmt.setString(3, playername);
                stmt.setInt(4, facetype.typeID);
            }
            else {
                stmt = c.prepareStatement("INSERT INTO Faces (PlayerName,TypeID,Image,ImageLen) VALUES (?,?,?,?);");
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);
                stmt.setBytes(3, encImage.buf);
                stmt.setInt(4, encImage.len);
            }
            //stmt.executeUpdate();
            doExecuteUpdate(stmt);
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face write error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
            FaceType facetype) {
        Connection c = null;
        boolean err = false;
        BufferInputStream image = null;
        try {
            c = getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Image,ImageLen FROM Faces WHERE PlayerName=? AND TypeID=?;");
            stmt.setString(1, playername);
            stmt.setInt(2, facetype.typeID);
            //ResultSet rs = stmt.executeQuery();
            ResultSet rs = doExecuteQuery(stmt);
            if (rs.next()) {
                byte[] img = rs.getBytes("Image");
                int len = rs.getInt("imageLen");
                if (len <= 0) {
                	len = img.length;
                	// Trim trailing zeros from padding by BLOB field
                	while((len > 0) && (img[len-1] == '\0')) len--;
                }
                image = new BufferInputStream(img, len);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return image;
    }

    @Override
    public boolean hasPlayerFaceImage(String playername, FaceType facetype) {
        Connection c = null;
        boolean err = false;
        boolean exists = false;
        try {
            c = getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT TypeID FROM Faces WHERE PlayerName=? AND TypeID=?;");
            stmt.setString(1, playername);
            stmt.setInt(2, facetype.typeID);
            //ResultSet rs = stmt.executeQuery();
            ResultSet rs = doExecuteQuery(stmt);
            if (rs.next()) {
                exists = true;
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Face exists error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
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
            c = getConnection();
            boolean exists = false;
            stmt = c.prepareStatement("SELECT IconName FROM MarkerIcons WHERE IconName=?;");
            stmt.setString(1, markerid);
            rs = doExecuteQuery(stmt);
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
                stmt = c.prepareStatement("DELETE FROM MarkerIcons WHERE IconName=?;");
                stmt.setString(1, markerid);
                //stmt.executeUpdate();
                doExecuteUpdate(stmt);
            }
            else if (exists) {
                stmt = c.prepareStatement("UPDATE MarkerIcons SET Image=?,ImageLen=? WHERE IconName=?;");
                stmt.setBytes(1, encImage.buf);
                stmt.setInt(2, encImage.len);
                stmt.setString(3, markerid);
            }
            else {
                stmt = c.prepareStatement("INSERT INTO MarkerIcons (IconName,Image,ImageLen) VALUES (?,?,?);");
                stmt.setString(1, markerid);
                stmt.setBytes(2, encImage.buf);
                stmt.setInt(3, encImage.len);
            }
            doExecuteUpdate(stmt);
            stmt.close();
            stmt = null;
        } catch (SQLException x) {
            Log.severe("Marker write error - " + x.getMessage());
            err = true;
        } finally {
            if (rs != null) { try { rs.close(); } catch (SQLException sx) {} }
            if (stmt != null) { try { stmt.close(); } catch (SQLException sx) {} }
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
        Connection c = null;
        boolean err = false;
        BufferInputStream image = null;
        try {
            c = getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Image,ImageLen FROM MarkerIcons WHERE IconName=?;");
            stmt.setString(1, markerid);
            //ResultSet rs = stmt.executeQuery();
            ResultSet rs = doExecuteQuery(stmt);
            if (rs.next()) {
                byte[] img = rs.getBytes("Image");
                int len = rs.getInt("ImageLen");
                if (len <= 0) {
                	len = img.length;
                	// Trim trailing zeros from padding by BLOB field
                	while((len > 0) && (img[len-1] == '\0')) len--;
                }
                image = new BufferInputStream(img);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Marker read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
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
            c = getConnection();
            boolean exists = false;
            stmt = c.prepareStatement("SELECT FileName FROM MarkerFiles WHERE FileName=?;");
            stmt.setString(1, world);
            rs = doExecuteQuery(stmt);
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
                stmt = c.prepareStatement("DELETE FROM MarkerFiles WHERE FileName=?;");
                stmt.setString(1, world);
                doExecuteUpdate(stmt);
            }
            else if (exists) {
                stmt = c.prepareStatement("UPDATE MarkerFiles SET Content=? WHERE FileName=?;");
                stmt.setBytes(1, content.getBytes(UTF8));
                stmt.setString(2, world);
            }
            else {
                stmt = c.prepareStatement("INSERT INTO MarkerFiles (FileName,Content) VALUES (?,?);");
                stmt.setString(1, world);
                stmt.setBytes(2, content.getBytes(UTF8));
            }
            //stmt.executeUpdate();
            doExecuteUpdate(stmt);
        } catch (SQLException x) {
            Log.severe("Marker file write error - " + x.getMessage());
            err = true;
        } finally {
            if (rs != null) { try { rs.close(); } catch (SQLException sx) {} }
            if (stmt != null) { try { stmt.close(); } catch (SQLException sx) {} }
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public String getMarkerFile(String world) {
        Connection c = null;
        boolean err = false;
        String content = null;
        try {
            c = getConnection();
            PreparedStatement stmt = c.prepareStatement("SELECT Content FROM MarkerFiles WHERE FileName=?;");
            stmt.setString(1, world);
            //ResultSet rs = stmt.executeQuery();
            ResultSet rs = doExecuteQuery(stmt);
            if (rs.next()) {
                byte[] img = rs.getBytes("Content");
                content = new String(img, UTF8);
            }
            rs.close();
            stmt.close();
        } catch (SQLException x) {
            Log.severe("Marker file read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return content;
    }

    @Override
    // External web server only
    public String getMarkersURI(boolean login_enabled) {
        return "standalone/SQLite_markers.php?marker=";
   }

    @Override
    // External web server only
    public String getTilesURI(boolean login_enabled) {
        return "standalone/SQLite_tiles.php?tile=";
    }
    
    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        sb.append("$dbfile = \'");
        sb.append(WebAuthManager.esc(databaseFile));
        sb.append("\';\n");
        
        // Need to call base to add webpath
        super.addPaths(sb, core);
    }
    
    private ResultSet doExecuteQuery(PreparedStatement statement) throws SQLException {
        while (true) {
            try {
                return statement.executeQuery();
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private ResultSet doExecuteQuery(Statement statement, String sql) throws SQLException {
        while (true) {
            try {
                return statement.executeQuery(sql);
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private int doExecuteUpdate(PreparedStatement statement) throws SQLException {
        while (true) {
            try {
                return statement.executeUpdate();
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private int doExecuteUpdate(Statement statement, String sql) throws SQLException {
        while (true) {
            try {
                return statement.executeUpdate(sql);
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
}
