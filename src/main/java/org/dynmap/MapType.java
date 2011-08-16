package org.dynmap;

import java.util.List;

import org.bukkit.Location;
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

    public abstract MapTile[] getTiles(Location l);

    public abstract MapTile[] getTiles(Location l0, Location l1);

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
    public abstract List<String> baseZoomFilePrefixes();
    public abstract int baseZoomFileStepSize();
    /* How many bits of coordinate are shifted off to make big world directory name */
    public abstract int getBigWorldShift();
    /* Returns true if big world file structure is in effect for this map */
    public abstract boolean isBigWorldMap(DynmapWorld w);
    /* Return number of zoom levels needed by this map (before extra levels from extrazoomout) */
    public int getMapZoomOutLevels() { return 0; }
    
    public ImageFormat getImageFormat() { return ImageFormat.FORMAT_PNG; }
    
    /**
     * Step sequence for creating zoomed file: first index is top-left, second top-right, third bottom-left, forth bottom-right
     * Values correspond to tile X,Y (0), X+step,Y (1), X,Y+step (2), X+step,Y+step (3) 
     */
    public abstract int[] zoomFileStepSequence();
}
