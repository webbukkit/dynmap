package org.dynmap;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.dynmap.utils.TileFlags;
import org.json.simple.JSONObject;

public abstract class MapType {
    public enum ImageFormat {
        FORMAT_PNG("png", "png", 0.0f),
        FORMAT_JPG75("jpg-q75", "jpg", 0.75f),
        FORMAT_JPG80("jpg-q80", "jpg", 0.80f),
        FORMAT_JPG85("jpg-q85", "jpg", 0.85f),
        FORMAT_JPG("jpg", "jpg", 0.85f),
        FORMAT_JPG90("jpg-q90", "jpg", 0.90f),
        FORMAT_JPG95("jpg-q95", "jpg", 0.95f),
        FORMAT_JPG100("jpg-q100", "jpg", 1.00f);
        String id;
        String ext;
        float qual;
        
        ImageFormat(String id, String ext, float quality) {
            this.id = id;
            this.ext = ext;
            this.qual = quality;
        }
        public String getID() { return id; }
        public String getFileExt() { return ext; }
        public float getQuality() { return qual; }
    };
    
    public static class ZoomInfo {
        public String prefix;
        public int  background_argb;
        public ZoomInfo(String pre, int bg) { prefix = pre; background_argb = bg; }
    }

    public abstract MapTile[] getTiles(DynmapWorld w, int x, int y, int z);

    public abstract MapTile[] getTiles(DynmapWorld w, int minx, int miny, int minz, int maxx, int maxy, int maxz);

    public abstract MapTile[] getAdjecentTiles(MapTile tile);

    public abstract List<DynmapChunk> getRequiredChunks(MapTile tile);
    
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld w) {
    }
    
    public abstract String getName();

    /* Get maps rendered concurrently with this map in this world */
    public abstract List<MapType> getMapsSharingRender(DynmapWorld w);
    /* Get names of maps rendered concurrently with this map type in this world */
    public abstract List<String> getMapNamesSharingRender(DynmapWorld w);
    
    public enum MapStep {
        X_PLUS_Y_PLUS,
        X_PLUS_Y_MINUS,
        X_MINUS_Y_PLUS,
        X_MINUS_Y_MINUS
    }
    public abstract MapStep zoomFileMapStep();
    public abstract List<ZoomInfo> baseZoomFileInfo();
    public abstract int baseZoomFileStepSize();
    /* How many bits of coordinate are shifted off to make big world directory name */
    public abstract int getBigWorldShift();
    /* Returns true if big world file structure is in effect for this map */
    public abstract boolean isBigWorldMap(DynmapWorld w);
    /* Return number of zoom levels needed by this map (before extra levels from extrazoomout) */
    public int getMapZoomOutLevels() { return 0; }
    
    public ImageFormat getImageFormat() { return ImageFormat.FORMAT_PNG; }

    public int getBackgroundARGBNight() { return 0; }

    public int getBackgroundARGBDay() { return 0; }

    /**
     * Step sequence for creating zoomed file: first index is top-left, second top-right, third bottom-left, forth bottom-right
     * Values correspond to tile X,Y (0), X+step,Y (1), X,Y+step (2), X+step,Y+step (3) 
     */
    public abstract int[] zoomFileStepSequence();
 
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
}
