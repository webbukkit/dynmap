package org.dynmap.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.zip.CRC32;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.PlayerFaces;
import org.dynmap.WebAuthManager;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

/**
 * Generic interface for map data storage (image tiles, and associated hash codes)
 */
public abstract class MapStorage {
    private static Object lock = new Object();
    private static HashMap<String, Integer> filelocks = new HashMap<String, Integer>();
    private static final Integer WRITELOCK = new Integer(-1);
    protected File baseStandaloneDir;

    protected long serverID;
    
    protected MapStorage() {
        this.serverID = 0;
    }
    
    // Proper modulo - versus the bogus Java behavior of negative modulo for negative numerators
    protected static final int modulo(int x, int y) {
        return ((x % y) + y) % y;
    }

    /**
     * Initialize with core
     * @param core - core instance
     * @return true if success
     */
    public boolean init(DynmapCore core) {
        baseStandaloneDir = new File(core.configuration.getString("webpath", "web"), "standalone");
        if (!baseStandaloneDir.isAbsolute()) {
            baseStandaloneDir = new File(core.getDataFolder(), baseStandaloneDir.toString());
        }
        return true;
    }
    
    /**
     * Set server ID for map storage instance
     * @param serverID - server ID (default is zero)
     */
    public void setServerID(long serverID) {
        this.serverID = serverID;
    }
    
    /**
     * Get tile reference for given tile
     *
     * @param world - world
     * @param map - map
     * @param x - tile X coordinate
     * @param y - tile Y coordinate
     * @param zoom - tile zoom level (0=base rendered tiles)
     * @param var - tile variant (standard, day, etc)
     * @return MapStorageTile for given coordinate (whether or not tile exists)
     */
    public abstract MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y, int zoom, MapType.ImageVariant var);

    /**
     * Get tile reference for given tile, by world and URI
     *
     * @param world - world
     * @param uri - tile URI
     * @return MapStorageTile for given coordinate (whether or not tile exists)
     */
    public abstract MapStorageTile getTile(DynmapWorld world, String uri);

    /**
     * Enumerate existing map tiles, matching given constraints
     * @param world - specific world
     * @param map - specific map (if non-null)
     * @param cb - callback to receive matching tiles
     */
    public abstract void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb);

    /**
     * Enumerate existing map tiles, matching given constraints, with zoom at 0
     * @param world - specific world
     * @param map - specific map (if non-null)
     * @param cbBase - callback to receive matching tiles
     * @param cbEnd - callback to receive end-of-search event
     */
    public abstract void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd);

    /**
     * Purge existing map tiles, matching given constraints
     * @param world - specific world
     * @param map - specific map (if non-null)
     */
    public abstract void purgeMapTiles(DynmapWorld world, MapType map);

    /**
     * Set player face image
     * @param playername - player name
     * @param facetype - face type
     * @param encImage - encoded image (PNG)
     * @return true if successful
     */
    public abstract boolean setPlayerFaceImage(String playername, PlayerFaces.FaceType facetype, BufferOutputStream encImage);
    
    /**
     * Get player face image
     * @param playername - player name
     * @param facetype - face type
     * @return encoded image (PNG)
     */
    public abstract BufferInputStream getPlayerFaceImage(String playername, PlayerFaces.FaceType facetype);

    /**
     * Test if player face image available
     * @param playername - player name
     * @param facetype - face type
     * @return true if found, false if not
     */
    public abstract boolean hasPlayerFaceImage(String playername, PlayerFaces.FaceType facetype);

    /**
     * Set marker image
     * @param markerid - marker ID
     * @param encImage - encoded image (PNG)
     * @return true if successful
     */
    public abstract boolean setMarkerImage(String markerid, BufferOutputStream encImage);
    
    /**
     * Get marker image
     * @param markerid - marker ID
     * @return encoded image (PNG)
     */
    public abstract BufferInputStream getMarkerImage(String markerid);

    /**
     * Set marker file for world
     * @param world - world ID
     * @param content - JSON content for marker file
     * @return true if successful
     */
    public abstract boolean setMarkerFile(String world, String content);
    
    /**
     * Get marker file for world
     * @param world - world ID
     * @return JSON content for marker file
     */
    public abstract String getMarkerFile(String world);

    /**
     * Calculate hashcode for raw image buffer
     * @param buf - ARGB array
     * @param off - offset of start in array
     * @param len - length of image data
     * @return hashcode (greater than or equals to 0)
     */
    public static long calculateImageHashCode(int[] buf, int off, int len) {
        CRC32 crc32 = new CRC32();
        final int perCall = 256;
        int accum = 0;
        byte[] crcworkbuf = new byte[4 * perCall];
        for (int i = 0; i < len; i++) {
            int v = buf[i + off];
            crcworkbuf[accum++] = (byte)v;
            crcworkbuf[accum++] = (byte)(v>>8);
            crcworkbuf[accum++] = (byte)(v>>16);
            crcworkbuf[accum++] = (byte)(v>>24);
            if (accum == crcworkbuf.length) {
                crc32.update(crcworkbuf, 0, accum);
                accum = 0;
            }
        }
        if (accum > 0) {    // Remainder?
            crc32.update(crcworkbuf, 0, accum);
            accum = 0;
        }
        return crc32.getValue();
    }
    
    /**
     * URI to use for loading marker data (for external web server)
     * 
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public abstract String getMarkersURI(boolean login_enabled);
    /**
     * URI to use for loading tiles (for external web server only)
     * 
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public abstract String getTilesURI(boolean login_enabled);
    /**
     * Test if standalone JSON files should be PHP wrapped
     * @param login_enabled - selects based on login security enabled
     * @return whether to wrap JSON
     */
    public boolean wrapStandaloneJSON(boolean login_enabled) {
        return login_enabled;
    }
    /**
     * Get sendmessage URI (for external web server only)
     * @return URI
     */
    public String getSendMessageURI() {
        return "standalone/sendmessage.php";
    }
    /**
     * URI to use for loading configuration JSON files (for external web server only)
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getConfigurationJSONURI(boolean login_enabled) {
        return login_enabled?"standalone/configuration.php":"standalone/dynmap_config.json?_={timestamp}";
    }
    /**
     * URI to use for loading update JSON files (for external web server only)
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getUpdateJSONURI(boolean login_enabled) {
        return login_enabled?"standalone/update.php?world={world}&ts={timestamp}":"standalone/dynmap_{world}.json?_={timestamp}";
    }
    /**
     * Add settings to dynmap_access.php needed for external server scripts
     * @param sb - string builder for PHP file
     * @param core - core object
     */
    public void addPaths(StringBuilder sb, DynmapCore core) {
        File wpath = core.getFile(core.getWebPath());
        String p = wpath.getAbsolutePath();
        if(!p.endsWith("/"))
            p += "/";
        sb.append("$webpath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
    }

    private static final int RETRY_LIMIT = 4;
    /**
     * Set standalone file content
     * @param fileid - standalone file ID
     * @param content - content for file
     * @return true if successful
     */
    public boolean setStandaloneFile(String fileid, BufferOutputStream content) {
        RandomAccessFile fos = null;
        boolean good = false;
        boolean done = false;
        File f = new File(baseStandaloneDir, fileid);
        File fnew = new File(baseStandaloneDir, fileid + ".new");
        File fold = new File(baseStandaloneDir, fileid + ".old");
        int retrycnt = 0;
        getWriteLock(fileid);
        while (!done) {
            try {
                if (fnew.exists()) {
                    fnew.delete();
                }
                if (content != null) {
                    fos = new RandomAccessFile(fnew, "rw");
                    fos.write(content.buf, 0, content.len);
                }
                good = true;
                done = true;
            } catch (IOException ioe) {
                if(retrycnt < RETRY_LIMIT) {
                    try { Thread.sleep(20 * (1 << retrycnt)); } catch (InterruptedException ix) {}
                    retrycnt++;
                }
                else {
                    Log.severe("Exception while writing JSON-file - " + fnew.getPath(), ioe);
                    done = true;
                }
            } finally {
                if(fos != null) {
                    try {
                        fos.close();
                    } catch (IOException iox) {
                    }
                    fos = null;
                }
                if(good) {
                    f.renameTo(fold);
                    if (content != null) {
                        fnew.renameTo(f);
                    }
                    fold.delete();
                }
            }
        }
        releaseWriteLock(fileid);

        return good;
    }
    
    /**
     * Get standalone file content
     * @param fileid - standalone file ID
     * @return content for file
     */
    public BufferInputStream getStandaloneFile(String fileid) {
        RandomAccessFile fos = null;
        BufferInputStream bis = null;
        boolean done = false;
        File f = new File(baseStandaloneDir, fileid);
        if (getReadLock(fileid, 5000)) {
            int retrycnt = 0;
            if (f.exists() == false) 
                done = true;
            while (!done) {
                byte[] b = new byte[(int) f.length()];
                try {
                    fos = new RandomAccessFile(f, "r");
                    fos.read(b, 0, b.length);
                    done = true;
                    bis = new BufferInputStream(b);
                } catch (IOException ioe) {
                    if(retrycnt < RETRY_LIMIT) {
                        try { Thread.sleep(20 * (1 << retrycnt)); } catch (InterruptedException ix) {}
                        retrycnt++;
                    }
                    else {
                        Log.severe("Exception while reading standalone - " + f.getPath(), ioe);
                        done = true;
                    }
                } finally {
                    if(fos != null) {
                        try {
                            fos.close();
                        } catch (IOException iox) {
                        }
                        fos = null;
                    }
                }
            }
            releaseReadLock(fileid);
        }
        return bis;
    }

    
    protected void releaseWriteLock(String baseFilename) {
        synchronized(lock) {
            Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
            if(lockcnt == null)
                Log.severe("releaseWriteLock(" + baseFilename + ") on unlocked file");
            else if(lockcnt.equals(WRITELOCK)) {
                filelocks.remove(baseFilename);   /* Remove lock */
                lock.notifyAll();   /* Wake up folks waiting for locks */
            }
            else
                Log.severe("releaseWriteLock(" + baseFilename + ") on read-locked file");
        }
    }

    protected boolean getWriteLock(String baseFilename) {
        synchronized(lock) {
            boolean got_lock = false;
            while(!got_lock) {
                Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                if(lockcnt != null) {   /* If any locks, can't get write lock */
                    try {
                        lock.wait(); 
                    } catch (InterruptedException ix) {
                        Log.severe("getWriteLock(" + baseFilename + ") interrupted");
                        return false;
                    }
                }
                else {
                    filelocks.put(baseFilename, WRITELOCK);
                    got_lock = true;
                }
            }
        }
        return true;
    }
    
    protected boolean getReadLock(String baseFilename, long timeout) {
        synchronized(lock) {
            boolean got_lock = false;
            long starttime = 0;
            if(timeout > 0)
                starttime = System.currentTimeMillis();
            while(!got_lock) {
                Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                if(lockcnt == null) {
                    filelocks.put(baseFilename, Integer.valueOf(1));  /* First lock */
                    got_lock = true;
                }
                else if(!lockcnt.equals(WRITELOCK)) {   /* Other read locks */
                    filelocks.put(baseFilename, Integer.valueOf(lockcnt+1));
                    got_lock = true;
                }
                else {  /* Write lock in place */
                    try {
                        if(timeout < 0) {
                            lock.wait();
                        }
                        else {
                            long now = System.currentTimeMillis();
                            long elapsed = now-starttime; 
                            if(elapsed > timeout) {   /* Give up on timeout */
                                return false;
                            }
                            lock.wait(timeout-elapsed);
                        }
                    } catch (InterruptedException ix) {
                        Log.severe("getReadLock(" + baseFilename + ") interrupted");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected void releaseReadLock(String baseFilename) {
        synchronized(lock) {
            Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
            if(lockcnt == null)
                Log.severe("releaseReadLock(" + baseFilename + ") on unlocked file");
            else if(lockcnt.equals(WRITELOCK))
                Log.severe("releaseReadLock(" + baseFilename + ") on write-locked file");
            else if(lockcnt > 1) {
                filelocks.put(baseFilename, Integer.valueOf(lockcnt-1));
            }
            else {
                filelocks.remove(baseFilename);   /* Remove lock */
                lock.notifyAll();   /* Wake up folks waiting for locks */
            }
        }
    }
    public boolean wrapStandalonePHP() {
        return true;
    }
    // For external web server only
    public String getStandaloneLoginURI() {
        return "standalone/login.php";
    }
    // For external web server only
    public String getStandaloneRegisterURI() {
        return "standalone/register.php";
    }
    public void setLoginEnabled(DynmapCore core) {
        
    }
}
