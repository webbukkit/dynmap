package org.dynmap.storage.filetree;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.PlayerFaces.FaceType;
import org.dynmap.WebAuthManager;
import org.dynmap.debug.Debug;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTileEnumCB;
import org.dynmap.storage.MapStorageBaseTileEnumCB;
import org.dynmap.storage.MapStorageTileSearchEndCB;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

public class FileTreeMapStorage extends MapStorage {
    private File baseTileDir;
    private TileHashManager hashmap;
    private static final int MAX_WRITE_RETRIES = 6;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public class StorageTile extends MapStorageTile {
        private final String baseFilename;
        private final String uri;
        private File f; // cached file
        private ImageEncoding f_fmt;
        
        StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);
            String baseURI;
            if (zoom > 0) {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y;
            }
            else {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y;
            }
            baseFilename = world.getName() + "/" + baseURI;
            uri = baseURI + "." + map.getImageFormat().getFileExt();
        }
        private File getTileFile(ImageEncoding fmt) {
            if ((f == null) || (fmt != f_fmt)) {
                f = new File(baseTileDir, baseFilename + "." + fmt.getFileExt());
                f_fmt = fmt;
            }
            return f;
        }
        private File getTileFile() {
            ImageEncoding fmt = map.getImageFormat().getEncoding();
            File ff = getTileFile(fmt);
            if (ff.exists() == false) {
                if (fmt == ImageEncoding.PNG) {
                    fmt = ImageEncoding.JPG;
                }
                else {
                    fmt = ImageEncoding.PNG;
                }
                ff = getTileFile(fmt);
            }
            return ff;
        }
        private List<File> getTileFilesAltFormats() {
            ImageEncoding fmt = map.getImageFormat().getEncoding();

            List<File> files = new ArrayList<File>();
            for (ImageEncoding ie: ImageEncoding.values()) {
                if (ie != fmt) {
                    files.add(getTileFile(ie));
                }
            }

            return files;
        }
        @Override
        public boolean exists() {
            File ff = getTileFile();
            return ff.isFile() && ff.canRead();
        }

        @Override
        public boolean matchesHashCode(long hash) {
            File ff = getTileFile(map.getImageFormat().getEncoding());
            return ff.isFile() && ff.canRead() && (hash == hashmap.getImageHashCode(world.getName() + "." + map.getPrefix(), x, y));
        }

        @Override
        public TileRead read() {
            ImageEncoding fmt = map.getImageFormat().getEncoding();
            File ff = getTileFile(fmt);
            if (ff.exists() == false) { // Fallback and try to read other format
                if (fmt == ImageEncoding.PNG) {
                    fmt = ImageEncoding.JPG;
                }
                else {
                    fmt = ImageEncoding.PNG;
                }
                ff = getTileFile(fmt);
            }
            if (ff.isFile()) {
                TileRead tr = new TileRead();
                byte[] buf = new byte[(int) ff.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ff);
                    fis.read(buf, 0, buf.length);   // Read whole thing
                } catch (IOException iox) {
                    Log.info("read (" + ff.getPath() + ") failed = " + iox.getMessage());
                    return null;
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                        fis = null;
                    }
                }
                tr.image = new BufferInputStream(buf);
                tr.format = fmt;
                tr.hashCode = hashmap.getImageHashCode(world.getName() + "." + map.getPrefix(), x, y);
                tr.lastModified = ff.lastModified();
                return tr;
            }
            return null;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage) {
            File ff = getTileFile(map.getImageFormat().getEncoding());
            List<File> ffalt = getTileFilesAltFormats();
            File ffpar = ff.getParentFile();
            // Always clean up old alternate files, if they exist
            for (File file: ffalt) {
                if (file.exists()) {
                    file.delete();
                }
            }
            if (encImage == null) { // Delete?
                ff.delete();
                hashmap.updateHashCode(world.getName() + "." + map.getPrefix(), x, y, -1);
                // Signal update for zoom out
                if (zoom == 0) {
                    world.enqueueZoomOutUpdate(this);
                }
                return true;
            }
            if (ffpar.exists() == false) {
                ffpar.mkdirs();
            }
            if (replaceFile(ff, encImage.buf, encImage.len) == false) {
                return false;
            }
            hashmap.updateHashCode(world.getName() + "." + map.getPrefix(), x, y, hash);
            // Signal update for zoom out
            if (zoom == 0) {
                world.enqueueZoomOutUpdate(this);
            }
            return true;
        }

        @Override
        public boolean getWriteLock() {
            return FileTreeMapStorage.this.getWriteLock(baseFilename);
        }

        @Override
        public void releaseWriteLock() {
            FileTreeMapStorage.this.releaseWriteLock(baseFilename);
        }

        @Override
        public boolean getReadLock(long timeout) {
            return FileTreeMapStorage.this.getReadLock(baseFilename, timeout);
        }

        @Override
        public void releaseReadLock() {
            FileTreeMapStorage.this.releaseReadLock(baseFilename);
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
                return baseFilename.equals(st.baseFilename);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return baseFilename.hashCode();
        }
        @Override
        public String toString() {
            return baseFilename;
        }
    }
    
    public FileTreeMapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        baseTileDir = core.getTilesFolder();
        hashmap = new TileHashManager(baseTileDir, true);
        return true;
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


    private void processEnumMapTiles(DynmapWorld world, MapType map, File base, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        File bdir = new File(base, map.getPrefix() + var.variantSuffix);
        if (bdir.isDirectory() == false) {
            if(cbEnd != null)
                cbEnd.searchEnded();
            return;
        }

        LinkedList<File> dirs = new LinkedList<File>(); // List to traverse
        dirs.add(bdir);   // Directory for map
        // While more paths to handle
        while (dirs.isEmpty() == false) {
            File dir = dirs.pop();
            String[] dirlst = dir.list();
            if (dirlst == null) continue;
            for(String fn : dirlst) {
                if (fn.equals(".") || fn.equals(".."))
                    continue;
                File f = new File(dir, fn);
                if (f.isDirectory()) {   /* If directory, add to list to process */
                    dirs.add(f);
                }
                else {  /* Else, file - see if tile */
                    String ext = null;
                    int extoff = fn.lastIndexOf('.');
                    if (extoff >= 0) {
                        ext = fn.substring(extoff+1);
                        fn = fn.substring(0, extoff);
                    }
                    ImageEncoding fmt = ImageEncoding.fromExt(ext);
                    if (fmt == null) {
                        continue;
                    }
                    // See if zoom tile
                    int zoom = 0;
                    if (fn.startsWith("z")) {
                        while (fn.startsWith("z")) {
                            fn = fn.substring(1);
                            zoom++;
                        }
                        if (fn.startsWith("_")) {
                            fn = fn.substring(1);
                        }
                    }
                    // Split remainder to get coords
                    String[] coord = fn.split("_");
                    if (coord.length == 2) {    // Must be 2 to be a tile
                        try {
                            int x = Integer.parseInt(coord[0]);
                            int y = Integer.parseInt(coord[1]);
                            // Invoke callback
                            MapStorageTile t = new StorageTile(world, map, x, y, zoom, var);
                            if(cb != null)
                                cb.tileFound(t, fmt);
                            if(cbBase != null && t.zoom == 0)
                                cbBase.tileFound(t, fmt);
                            t.cleanup();
                        } catch (NumberFormatException nfx) {
                        }
                    }
                }
            }
        }
        if(cbEnd != null) {
            cbEnd.searchEnded();
        }
    }

    @Override
    public void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb) {
        File base = new File(baseTileDir, world.getName()); // Get base directory for world
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
                processEnumMapTiles(world, mt, base, var, cb, null, null);
            }
        }
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        File base = new File(baseTileDir, world.getName()); // Get base directory for world
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
                processEnumMapTiles(world, mt, base, var, null, cbBase, cbEnd);
            }
        }
    }

    private void processPurgeMapTiles(DynmapWorld world, MapType map, File base, ImageVariant var) {
        String mname = map.getPrefix() + var.variantSuffix;
        // Clean up hash files
        String[] hlist = base.list();
        if (hlist != null) {
            for (String h : hlist) {
                if (h.endsWith(".hash") == false) continue;
                if (h.startsWith(mname + "_")) continue;
                File f = new File(base, h);
                f.delete();
            }
        }
        File bdir = new File(base, mname);
        if (bdir.isDirectory() == false) return;

        LinkedList<File> dirs = new LinkedList<File>(); // List to traverse
        LinkedList<File> dirsdone = new LinkedList<File>();
        dirs.add(bdir);   // Directory for map
        // While more paths to handle
        while (dirs.isEmpty() == false) {
            File dir = dirs.pop();
            dirsdone.add(dir);
            String[] dirlst = dir.list();
            if (dirlst == null) continue;
            for(String fn : dirlst) {
                if (fn.equals(".") || fn.equals(".."))
                    continue;
                File f = new File(dir, fn);
                if (f.isDirectory()) {   /* If directory, add to list to process */
                    dirs.add(f);
                }
                else {  /* Else, file - cleanup */
                    f.delete();
                }
            }
        }
        // Clean up directories, in reverse order of traverse
        int cnt = dirsdone.size();
        for (int i = cnt-1; i >= 0; i--) {
            File f = dirsdone.get(i);
            f.delete();
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        File base = new File(baseTileDir, world.getName()); // Get base directory for world
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
                processPurgeMapTiles(world, mt, base, var);
            }
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, FaceType facetype,
            BufferOutputStream encImage) {
        String baseFilename = "faces/" + facetype.id + "/" + playername + ".png";
        File ff = new File(baseTileDir, baseFilename);
        File ffpar = ff.getParentFile();
        if (encImage == null) { // Delete?
            ff.delete();
            return true;
        }
        if (ffpar.exists() == false) {
            ffpar.mkdirs();
        }
        getWriteLock(baseFilename);
        boolean done = replaceFile(ff, encImage.buf, encImage.len);
        releaseWriteLock(baseFilename);
        return done;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
            FaceType facetype) {
        String baseFilename = "faces/" + facetype.id + "/" + playername + ".png";
        File ff = new File(baseTileDir, baseFilename);
        if (ff.exists()) {
            if (getReadLock(baseFilename, 5000)) {
                byte[] buf = new byte[(int) ff.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ff);
                    fis.read(buf, 0, buf.length);   // Read whole thing
                } catch (IOException iox) {
                    Log.info("read (" + ff.getPath() + ") failed = " + iox.getMessage());
                    return null;
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                        fis = null;
                    }
                    releaseReadLock(baseFilename);
                }
                return new BufferInputStream(buf);
            }
        }
        return null;
    }
    

    @Override
    public boolean hasPlayerFaceImage(String playername, FaceType facetype) {
        String baseFilename = "faces/" + facetype.id + "/" + playername + ".png";
        File ff = new File(baseTileDir, baseFilename);
        return ff.exists();
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
        String baseFilename = "_markers_/" + markerid + ".png";
        File ff = new File(baseTileDir, baseFilename);
        File ffpar = ff.getParentFile();
        if (encImage == null) { // Delete?
            ff.delete();
            return true;
        }
        if (ffpar.exists() == false) {
            ffpar.mkdirs();
        }
        getWriteLock(baseFilename);
        boolean done = replaceFile(ff, encImage.buf, encImage.len);
        releaseWriteLock(baseFilename);
        return done;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
        String baseFilename = "_markers_/" + markerid + ".png";
        File ff = new File(baseTileDir, baseFilename);
        if (ff.exists()) {
            if (getReadLock(baseFilename, 5000)) {
                byte[] buf = new byte[(int) ff.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ff);
                    fis.read(buf, 0, buf.length);   // Read whole thing
                } catch (IOException iox) {
                    Log.info("read (" + ff.getPath() + ") failed = " + iox.getMessage());
                    return null;
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                        fis = null;
                    }
                    releaseReadLock(baseFilename);
                }
                return new BufferInputStream(buf);
            }
        }
        return null;
    }

    @Override
    public boolean setMarkerFile(String world, String content) {
        String baseFilename = "_markers_/marker_" + world + ".json";
        File ff = new File(baseTileDir, baseFilename);
        File ffpar = ff.getParentFile();
        if (content == null) { // Delete?
            ff.delete();
            return true;
        }
        if (ffpar.exists() == false) {
            ffpar.mkdirs();
        }
        getWriteLock(baseFilename);
        byte[] buf = content.getBytes(UTF8);
        boolean done = replaceFile(ff, buf, buf.length);
        releaseWriteLock(baseFilename);
        return done;
    }

    @Override
    public String getMarkerFile(String world) {
        String baseFilename = "_markers_/marker_" + world + ".json";
        File ff = new File(baseTileDir, baseFilename);
        if (ff.exists()) {
            if (getReadLock(baseFilename, 5000)) {
                byte[] buf = new byte[(int) ff.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ff);
                    fis.read(buf, 0, buf.length);   // Read whole thing
                } catch (IOException iox) {
                    Log.info("read (" + ff.getPath() + ") failed = " + iox.getMessage());
                    return null;
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                        fis = null;
                    }
                    releaseReadLock(baseFilename);
                }
                return new String(buf, UTF8);
            }
        }
        return null;
    }
    
    @Override
    // For external web server only
    public String getMarkersURI(boolean login_enabled) {
        return login_enabled?"standalone/markers.php?marker=":"tiles/";
    }

    @Override
    // For external web server only
    public String getTilesURI(boolean login_enabled) {
        return login_enabled?"standalone/tiles.php?tile=":"tiles/";
    }
    
    private boolean replaceFile(File f, byte[] b, int len) {
        boolean done = false;
        File fold = new File(f.getPath() + ".old");
        File fnew = new File(f.getPath() + ".new");
        int retrycnt = 0;
        while (!done) {
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(fnew, "rw");
                raf.write(b, 0, len);
                raf.close();
                raf = null;
                // Now swap names
                if (f.exists()) {
                    f.renameTo(fold);
                    fnew.renameTo(f);
                    fold.delete();
                }
                else {
                    fnew.renameTo(f);
                }
                done = true;
            } catch (IOException iox) {
                if (raf != null) { try { raf.close(); } catch (IOException x) {} }
                if(retrycnt < MAX_WRITE_RETRIES) {
                    Debug.debug("Image file " + f.getPath() + " - unable to write - retry #" + retrycnt);
                    try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { return false; }
                    retrycnt++;
                }
                else {
                    Log.info("Image file " + f.getPath() + " - unable to write - failed");
                    return false;
                }
            }
        }
        return true;
    }
    
    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        String p = core.getTilesFolder().getAbsolutePath();
        if(!p.endsWith("/"))
            p += "/";
        sb.append("$tilespath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        sb.append("$markerspath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        
        // Need to call base to add webpath
        super.addPaths(sb, core);
    }

}
