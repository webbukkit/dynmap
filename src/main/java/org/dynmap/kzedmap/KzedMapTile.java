package org.dynmap.kzedmap;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.MapType;

import java.io.File;
import java.util.List;

import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;

public class KzedMapTile extends MapTile {
    public KzedMap map;
    public MapTileRenderer renderer;
    public int px, py;
    private String fname;
    private String fname_day;

    // Hack.
    public File file = null;

    public KzedMapTile(DynmapWorld world, KzedMap map, MapTileRenderer renderer, int px, int py) {
        super(world);
        this.map = map;
        this.renderer = renderer;
        this.px = px;
        this.py = py;
    }

    public KzedMapTile(DynmapWorld world, String parm) throws Exception {
        super(world);
        
        String[] parms = parm.split(",");
        if(parms.length < 4) throw new Exception("wrong parameter count");
        this.px = Integer.parseInt(parms[0]);
        this.py = Integer.parseInt(parms[1]);
        
        for(MapType t : world.maps) {
            if(t.getName().equals(parms[2]) && (t instanceof KzedMap)) {
                this.map = (KzedMap)t;
                break;
            }
        }
        if(this.map == null) throw new Exception("invalid map");
        for(MapTileRenderer r : map.renderers) {
            if(r.getName().equals(parms[3])) {
                this.renderer = r;
                break;
            }
        }
        if(this.renderer == null) throw new Exception("invalid renderer");
    }
    
    @Override
    protected String saveTileData() {
        return String.format("%d,%d,%s,%s", px, py, map.getName(), renderer.getName());
    }

    @Override
    public String getFilename() {
        if(fname == null) {
            if(map.isBigWorldMap(world))        
                fname = renderer.getPrefix() + "/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname = renderer.getPrefix() + "_" + px + "_" + py + ".png";            
        }
        return fname;
    }

    @Override
    public String getDayFilename() {
        if(fname_day == null) {
            if(map.isBigWorldMap(world))        
                fname_day = renderer.getPrefix() + "_day/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname_day = renderer.getPrefix() + "_day_" + px + "_" + py + ".png";            
        }
        return fname_day;
    }

    @Override
    public int hashCode() {
        return px ^ py ^ map.getName().hashCode() ^ world.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KzedMapTile) {
            return equals((KzedMapTile) obj);
        }
        return false;
    }

    public boolean equals(KzedMapTile o) {
        return o.px == px && o.py == py && (o.map == map) && (o.world == world);
    }

    public String getKey(String prefix) {
        return world.getName() + "." + prefix;
    }

    public String toString() {
        return world.getName() + ":" + getFilename();
    }
    
    public boolean render(MapChunkCache cache, String mapname) {
    	boolean rslt = false;
    	for(MapTileRenderer r : map.renderers) {
    		if((mapname == null) || (r.getName().equals(mapname))) {
    			KzedMapTile t = new KzedMapTile(world, map, r, px, py);
    			rslt |= map.render(cache, t, MapManager.mapman.getTileFile(t));
    		}
    	}
        return rslt;
    }
    
    public List<DynmapChunk> getRequiredChunks() {
        return map.getRequiredChunks(this);
    }
    
    public MapTile[] getAdjecentTiles() {
        return map.getAdjecentTiles(this);
    }
    
    public boolean isBiomeDataNeeded() { return map.isBiomeDataNeeded(); }
    public boolean isHightestBlockYDataNeeded() { return false; }
    public boolean isRawBiomeDataNeeded() { return map.isRawBiomeDataNeeded(); }
    public boolean isBlockTypeDataNeeded() { return true; }
    public int tileOrdinalX() { return px >> 7; }
    public int tileOrdinalY() { return py >> 7; }
}
