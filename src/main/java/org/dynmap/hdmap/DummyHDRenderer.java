package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.io.File;

import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.kzedmap.KzedMapTile;
import org.dynmap.kzedmap.DefaultTileRenderer.BiomeColorOption;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;

public class DummyHDRenderer implements HDMapTileRenderer {
    private ConfigurationNode configuration;
    private String name;
    
    public DummyHDRenderer(ConfigurationNode configuration) {
        this.configuration = configuration;
        name = (String) configuration.get("prefix");
    }
    public boolean isBiomeDataNeeded() { return false; }
    public boolean isRawBiomeDataNeeded() { return false; };
    public boolean isNightAndDayEnabled() { return false; }
    public String getName() { return name; }
    
    public boolean render(MapChunkCache cache, HDMapTile tile, File outputFile) {
        Log.info("DummyHDRenderer(" + tile + ", " + outputFile.getPath());
        return false;
    }
    
    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        ConfigurationNode c = configuration;
        JSONObject o = new JSONObject();
        s(o, "type", "HDMapType");
        s(o, "name", c.getString("name"));
        s(o, "title", c.getString("title"));
        s(o, "icon", c.getString("icon"));
        s(o, "prefix", c.getString("prefix"));
        a(worldObject, "maps", o);
    }
}
