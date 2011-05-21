package org.dynmap;

import org.json.simple.JSONObject;

public class ClientUpdateEvent {
    public long timestamp;
    public DynmapWorld world;
    public JSONObject update;
    
    public ClientUpdateEvent(long timestamp, DynmapWorld world, JSONObject update) {
        this.timestamp = timestamp;
        this.world = world;
        this.update = update;
    }
}
