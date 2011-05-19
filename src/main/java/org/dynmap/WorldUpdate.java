package org.dynmap;

public class WorldUpdate {
    public DynmapWorld world;
    public Client.Update update;
    
    public WorldUpdate(DynmapWorld world, Client.Update update) {
        this.world = world;
        this.update = update;
    }
}
