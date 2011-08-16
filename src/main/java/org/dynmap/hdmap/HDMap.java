package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.kzedmap.MapTileRenderer;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;

public class HDMap extends MapType {

    private String name;
    private String prefix;
    private HDPerspective perspective;
    private HDShader shader;
    private HDLighting lighting;
    private ConfigurationNode configuration;
    private int mapzoomout;
    private MapType.ImageFormat imgformat;

    public static final String IMGFORMAT_PNG = "png";
    public static final String IMGFORMAT_JPG = "jpg";
    
    
    public HDMap(ConfigurationNode configuration) {
        name = configuration.getString("name", null);
        if(name == null) {
            Log.severe("HDMap missing required attribute 'name' - disabled");
            return;
        }
        String perspectiveid = configuration.getString("perspective", "default");
        perspective = MapManager.mapman.hdmapman.perspectives.get(perspectiveid);
        if(perspective == null) {
            /* Try to use default */
            perspective = MapManager.mapman.hdmapman.perspectives.get("default");
            if(perspective == null) {
                Log.severe("HDMap '"+name+"' loaded invalid perspective '" + perspectiveid + "' - map disabled");
                name = null;
                return;
            }
            else {
                Log.severe("HDMap '"+name+"' loaded invalid perspective '" + perspectiveid + "' - using 'default' perspective");
            }
        }
        String shaderid = configuration.getString("shader", "default");
        shader = MapManager.mapman.hdmapman.shaders.get(shaderid);
        if(shader == null) {
            shader = MapManager.mapman.hdmapman.shaders.get("default");
            if(shader == null) {
                Log.severe("HDMap '"+name+"' loading invalid shader '" + shaderid + "' - map disabled");
                name = null;
                return;
            }
            else {
                Log.severe("HDMap '"+name+"' loading invalid shader '" + shaderid + "' - using 'default' shader");
            }
        }
        String lightingid = configuration.getString("lighting", "default");
        lighting = MapManager.mapman.hdmapman.lightings.get(lightingid);
        if(lighting == null) {
            lighting = MapManager.mapman.hdmapman.lightings.get("default");
            if(lighting == null) {
                Log.severe("HDMap '"+name+"' loading invalid lighting '" + lighting + "' - map disabled");
                name = null;
                return;
            }
            else {
                Log.severe("HDMap '"+name+"' loading invalid lighting '" + lighting + "' - using 'default' lighting");
            }
        }
        prefix = configuration.getString("prefix", name);
        this.configuration = configuration;
        
        /* Compute extra zoom outs needed for this map */
        double scale = perspective.getScale();
        mapzoomout = 0;
        while(scale >= 1.0) {
            mapzoomout++;
            scale = scale / 2.0;
        }
        String fmt = configuration.getString("image-format", "png");
        /* Only allow png or jpg */
        for(ImageFormat f : ImageFormat.values()) {
            if(fmt.equals(f.getID())) {
                imgformat = f;
                break;
            }
        }
        if(imgformat == null) {
            Log.severe("HDMap '"+name+"' set invalid image-format: " + fmt);
            imgformat = ImageFormat.FORMAT_PNG;
        }   
    }

    public HDShader getShader() { return shader; }
    public HDPerspective getPerspective() { return perspective; }
    public HDLighting getLighting() { return lighting; }
    
    @Override
    public MapTile[] getTiles(Location loc) {
        return perspective.getTiles(loc);
    }

    @Override
    public MapTile[] getTiles(Location loc0, Location loc1) {
        return perspective.getTiles(loc0, loc1);
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        return perspective.getAdjecentTiles(tile);
    }
    
    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        return perspective.getRequiredChunks(tile);
    }

    @Override
    public List<String> baseZoomFilePrefixes() {
        ArrayList<String> s = new ArrayList<String>();
        s.add(prefix);
        if(lighting.isNightAndDayEnabled())
            s.add(prefix + "_day");
        return s;
    }

    public int baseZoomFileStepSize() { return 1; }

    private static final int[] stepseq = { 3, 1, 2, 0 };
    
    public MapStep zoomFileMapStep() { return MapStep.X_PLUS_Y_MINUS; }

    public int[] zoomFileStepSequence() { return stepseq; }

    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 5; }

    /* Returns true if big world file structure is in effect for this map */
    @Override
    public boolean isBigWorldMap(DynmapWorld w) { return true; } /* We always use it on these maps */

    /* Return number of zoom levels needed by this map (before extra levels from extrazoomout) */
    public int getMapZoomOutLevels() {
        return mapzoomout;
    }

    @Override
    public String getName() {
        return name;
    }
    
    public String getPrefix() {
        return prefix;
    }

    /* Get maps rendered concurrently with this map in this world */
    public List<MapType> getMapsSharingRender(DynmapWorld w) {
        ArrayList<MapType> maps = new ArrayList<MapType>();
        for(MapType mt : w.maps) {
            if(mt instanceof HDMap) {
                HDMap hdmt = (HDMap)mt;
                if(hdmt.perspective == this.perspective) {  /* Same perspective */
                    maps.add(hdmt);
                }
            }
        }
        return maps;
    }
    
    /* Get names of maps rendered concurrently with this map type in this world */
    public List<String> getMapNamesSharingRender(DynmapWorld w) {
        ArrayList<String> lst = new ArrayList<String>();
        for(MapType mt : w.maps) {
            if(mt instanceof HDMap) {
                HDMap hdmt = (HDMap)mt;
                if(hdmt.perspective == this.perspective) {  /* Same perspective */
                    if(hdmt.lighting.isNightAndDayEnabled())
                        lst.add(hdmt.getName() + "(night/day)");
                    else
                        lst.add(hdmt.getName());
                }
            }
        }
        return lst;
    }

    @Override
    public ImageFormat getImageFormat() { return imgformat; }
    
    @Override
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld world) {
        ConfigurationNode c = configuration;
        JSONObject o = new JSONObject();
        s(o, "type", "HDMapType");
        s(o, "name", name);
        s(o, "title", c.getString("title"));
        s(o, "icon", c.getString("icon"));
        s(o, "prefix", prefix);
        s(o, "background", c.getString("background"));
        s(o, "backgroundday", c.getString("backgroundday"));
        s(o, "backgroundnight", c.getString("backgroundnight"));
        s(o, "bigmap", true);
        s(o, "mapzoomout", (world.getExtraZoomOutLevels()+mapzoomout));
        s(o, "mapzoomin", c.getInteger("mapzoomin", 2));
        s(o, "image-format", imgformat.getFileExt());
        perspective.addClientConfiguration(o);
        shader.addClientConfiguration(o);
        lighting.addClientConfiguration(o);
        
        a(worldObject, "maps", o);

    }
}
