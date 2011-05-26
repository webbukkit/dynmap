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
}
