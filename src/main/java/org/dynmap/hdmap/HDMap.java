package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;

public class HDMap extends MapType {
    private String name;
    private String prefix;
    private HDPerspective perspective;
    private HDShader shader;
    private ConfigurationNode configuration;
    
    public HDMap(ConfigurationNode configuration) {
        name = configuration.getString("name", null);
        if(name == null) {
            Log.severe("HDMap missing required attribute 'name' - disabled");
            return;
        }
        String perspectiveid = configuration.getString("perspective", "default");
        perspective = MapManager.mapman.hdmapman.perspectives.get(perspectiveid);
        if(perspective == null) {
            Log.severe("HDMap '"+name+"' loading invalid perspective '" + perspectiveid + "' - map disabled");
            name = null;
            return;
        }
        String shaderid = configuration.getString("shader", "default");
        shader = MapManager.mapman.hdmapman.shaders.get(shaderid);
        if(shader == null) {
            Log.severe("HDMap '"+name+"' loading invalid shader '" + shaderid + "' - map disabled");
            name = null;
            return;
        }
        prefix = configuration.getString("prefix", name);
        this.configuration = configuration;
    }   

    public HDShader getShader() { return shader; }
    public HDPerspective getPerspective() { return perspective; }
    
    @Override
    public MapTile[] getTiles(Location loc) {
        return perspective.getTiles(loc);
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
    public boolean render(MapChunkCache cache, MapTile tile, File bogus) {
        if(tile instanceof HDMapTile)
            return perspective.render(cache, (HDMapTile)tile);
        else
            return false;
    }

    @Override
    public List<String> baseZoomFilePrefixes() {
        ArrayList<String> s = new ArrayList<String>();
        s.add(prefix);
        if(shader.isNightAndDayEnabled())
            s.add(prefix + "_day");
        return s;
    }

    public int baseZoomFileStepSize() { return 1; }

    private static final int[] stepseq = { 3, 1, 2, 0 };
    
    public MapStep zoomFileMapStep() { return MapStep.X_PLUS_Y_MINUS; }

    public int[] zoomFileStepSequence() { return stepseq; }

    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 5; }

    @Override
    public String getName() {
        return name;
    }
    
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
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
        
        perspective.addClientConfiguration(o);
        shader.addClientConfiguration(o);
        
        a(worldObject, "maps", o);

    }
}
