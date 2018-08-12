package org.dynmap;

public abstract class Component {
    protected DynmapCore core;
    protected ConfigurationNode configuration;
    public Component(DynmapCore core, ConfigurationNode configuration) {
        this.core = core;
        this.configuration = configuration;
    }
    
    public void dispose() {
    }
    
    /* Substitute proper values for escape sequences */
    public static String unescapeString(String v) {
        /* Replace color code &color; */
        v = v.replace("&color;", "\u00A7");
        
        return v;
    }
}
