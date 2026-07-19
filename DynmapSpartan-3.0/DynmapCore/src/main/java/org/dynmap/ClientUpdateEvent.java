package org.dynmap;

import org.json.simple.JSONObject;

public class ClientUpdateEvent {
    public long timestamp;
    public DynmapWorld world;
    public JSONObject update;
    public String user;
    public boolean include_all_users;
    
    public ClientUpdateEvent(long timestamp, DynmapWorld world, JSONObject update) {
        this.timestamp = timestamp;
        this.world = world;
        this.update = update;
    }
}
