package org.dynmap;

public abstract class Component {
    protected DynmapPlugin plugin;
    protected ConfigurationNode configuration;
    public Component(DynmapPlugin plugin, ConfigurationNode configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }
    
    public void dispose() {
    }
    
    /* Substitute proper values for escape sequences */
    public static String unescapeString(String v) {
        /* Replace color code &color; */
        v = v.replaceAll("&color;", "\u00A7");
        
        return v;
    }
}
