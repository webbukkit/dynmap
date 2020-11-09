package org.dynmap;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.dynmap.utils.TileFlags;
import org.json.simple.JSONObject;

public abstract class MapType {
    private boolean is_protected;
    protected int tileupdatedelay;
    
    public enum ImageVariant {
        STANDARD(""),   // Typical image
        DAY("day");     // Day (no shadow) image
        public final String variantSuffix;
        public final String variantID;
        
        ImageVariant(String varid) {
            if (varid.length() > 0) {
                variantSuffix = "_" + varid;
            }
            else {
                variantSuffix = "";
            }
            variantID = varid;
        }
    }
    
    public enum ImageEncoding {
        PNG("png", "image/png"), JPG("jpg", "image/jpeg"), WEBP("webp", "image/webp");
        public final String ext;
        public final String mimetype;
        
        ImageEncoding(String ext, String mime) {
            this.ext = ext;
            this.mimetype = mime;
        }
        public String getFileExt() { return ext; }
        public String getContentType() { return mimetype; }
        
        public static ImageEncoding fromOrd(int ix) {
            ImageEncoding[] v = values();
            if ((ix >= 0) && (ix < v.length))
                return v[ix];
            return null;
        }
        public static ImageEncoding fromExt(String x) {
            ImageEncoding[] v = values();
            for (int i = 0; i < v.length; i++) {
                if (v[i].ext.equalsIgnoreCase(x)) {
                    return v[i];
                }
            }
            return null;
        }
    }
    
    public enum ImageFormat {
        FORMAT_PNG("png", 0.0f, ImageEncoding.PNG),
        FORMAT_JPG75("jpg-q75", 0.75f, ImageEncoding.JPG),
        FORMAT_JPG80("jpg-q80", 0.80f, ImageEncoding.JPG),
        FORMAT_JPG85("jpg-q85", 0.85f, ImageEncoding.JPG),
        FORMAT_JPG("jpg", 0.85f, ImageEncoding.JPG),
        FORMAT_JPG90("jpg-q90", 0.90f, ImageEncoding.JPG),
        FORMAT_JPG95("jpg-q95", 0.95f, ImageEncoding.JPG),
        FORMAT_JPG100("jpg-q100", 1.00f, ImageEncoding.JPG),
        FORMAT_WEBP75("webp-q75", 75, ImageEncoding.WEBP),
        FORMAT_WEBP80("webp-q80", 80, ImageEncoding.WEBP),
        FORMAT_WEBP85("webp-q85", 85, ImageEncoding.WEBP),
        FORMAT_WEBP("webp", 85, ImageEncoding.WEBP),
        FORMAT_WEBP90("webp-q90", 90, ImageEncoding.WEBP),
        FORMAT_WEBP95("webp-q95", 95, ImageEncoding.WEBP),
        FORMAT_WEBP100("webp-q100", 100, ImageEncoding.WEBP);
        String id;
        float qual;
        ImageEncoding enc;
        
        ImageFormat(String id, float quality, ImageEncoding enc) {
            this.id = id;
            this.qual = quality;
            this.enc = enc;
        }
        public String getID() { return id; }
        public String getFileExt() { return enc.getFileExt(); }
        public float getQuality() { return qual; }
        public ImageEncoding getEncoding() { return enc; }

        public static ImageFormat fromID(String imgfmt) {
            for(ImageFormat i_f : MapType.ImageFormat.values()) {
                if(i_f.getID().equals(imgfmt)) {
                    return i_f;
                }
            }
            return null;
        }
    };
    
    public static class ZoomInfo {
        public String prefix;
        public int  background_argb;
        public ZoomInfo(String pre, int bg) { prefix = pre; background_argb = bg; }
    }

    public abstract void addMapTiles(List<MapTile> list, DynmapWorld w, int tx, int ty);
    
    public abstract List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z);

    public abstract List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int minx, int miny, int minz, int maxx, int maxy, int maxz);

    public abstract MapTile[] getAdjecentTiles(MapTile tile);

    public abstract List<DynmapChunk> getRequiredChunks(MapTile tile);
    
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld w) {
    }

    public List<MapTile> getTiles(DynmapWorld w, int x, int y, int z) {
        List<TileFlags.TileCoord> coords = this.getTileCoords(w, x, y, z);
        ArrayList<MapTile> tiles = new ArrayList<MapTile>();
        for(TileFlags.TileCoord c : coords) {
            this.addMapTiles(tiles, w, c.x, c.y);
        }
        return tiles;
    }

    public abstract String getName();

    /* Get maps rendered concurrently with this map in this world */
    public abstract List<MapType> getMapsSharingRender(DynmapWorld w);
    /* Get names of maps rendered concurrently with this map type in this world */
    public abstract List<String> getMapNamesSharingRender(DynmapWorld w);
    
    /* Return number of zoom levels needed by this map (before extra levels from extrazoomout) */
    public int getMapZoomOutLevels() { return 0; }
    
    public ImageFormat getImageFormat() { return ImageFormat.FORMAT_PNG; }

    public int getBackgroundARGBNight() { return 0; }

    public int getBackgroundARGBDay() { return 0; }

    public int getBackgroundARGB(ImageVariant var) {
        if (var == ImageVariant.DAY)
            return getBackgroundARGBDay();
        else
            return getBackgroundARGBNight();
    }
 
    public void purgeOldTiles(DynmapWorld world, TileFlags rendered) { }
 
    public interface FileCallback {
        public void fileFound(File f, File parent, boolean day);
    }
    
    protected void walkMapTree(File root, FileCallback cb, boolean day) {
        LinkedList<File> dirs = new LinkedList<File>();
        String ext = "." + getImageFormat().getFileExt();
        dirs.add(root);
        while(dirs.isEmpty() == false) {
            File dir = dirs.pop();
            String[] lst = dir.list();
            if(lst == null) continue;
            for(String fn : lst) {
                if(fn.equals(".") || fn.equals(".."))
                    continue;
                File f = new File(dir, fn);
                if(f.isDirectory()) {   /* If directory, add to list to process */
                    dirs.add(f);
                }
                else if(fn.endsWith(ext)) {  /* Else, if matches suffix */
                    cb.fileFound(f, dir, day);
                }
            }
        }
    }
    
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode cn = new ConfigurationNode();
        cn.put("class", this.getClass().getName()); /* Add class */
        cn.put("name", getName());  /* Get map name */
        return cn;
    }
    public boolean isProtected() {
        return is_protected;
    }
    public boolean setProtected(boolean p) {
        if(is_protected != p) {
            is_protected = p;
            return true;
        }
        return false;
    }
    public abstract String getPrefix();
    
    public int getTileUpdateDelay(DynmapWorld w) {
        if(tileupdatedelay > 0)
            return tileupdatedelay;
        else
            return w.getTileUpdateDelay();
    }
    public boolean setTileUpdateDelay(int delay) {
        if(tileupdatedelay != delay) {
            tileupdatedelay = delay;
            return true;
        }
        return false;
    }
    private static final ImageVariant[] defVariant = { ImageVariant.STANDARD };
    
    public ImageVariant[] getVariants() {
        return defVariant;
    }
}
