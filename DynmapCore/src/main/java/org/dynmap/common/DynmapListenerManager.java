package org.dynmap.common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;

/**
 * Simple handler for managing event listeners and dispatch in a neutral fashion
 * 
 */
public class DynmapListenerManager {
    private DynmapCore core;
    
    public DynmapListenerManager(DynmapCore core) {
        this.core = core;
    }
    public interface EventListener {
    }
    public interface WorldEventListener extends EventListener {
        public void worldEvent(DynmapWorld w);
    }
    public interface PlayerEventListener extends EventListener {
        public void playerEvent(DynmapPlayer p);
    }
    public interface ChatEventListener extends EventListener {
        public void chatEvent(DynmapPlayer p, String msg);
    }
    public interface BlockEventListener extends EventListener {
        public void blockEvent(String material, String w, int x, int y, int z);
    }
    public interface SignChangeEventListener extends EventListener {
        public void signChangeEvent(String material, String w, int x, int y, int z, String[] lines, DynmapPlayer p);
    }
    public enum EventType {
        WORLD_LOAD,
        WORLD_UNLOAD,
        WORLD_SPAWN_CHANGE,
        PLAYER_JOIN,
        PLAYER_QUIT,
        PLAYER_BED_LEAVE,
        PLAYER_CHAT,
        BLOCK_BREAK,
        SIGN_CHANGE
    }
    private Map<EventType, ArrayList<EventListener>> listeners = new EnumMap<EventType, ArrayList<EventListener>>(EventType.class);
    
    public void addListener(EventType type, EventListener listener) {
        synchronized(listeners) {
            ArrayList<EventListener> lst = listeners.get(type);
            if(lst == null) {
                lst = new ArrayList<EventListener>();
                listeners.put(type, lst);
                core.getServer().requestEventNotification(type);
            }
            lst.add(listener);
        }
    }
    
    public void processWorldEvent(EventType type, DynmapWorld w) {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof WorldEventListener) {
                try {
                    ((WorldEventListener)el).worldEvent(w);
                } catch (Throwable t) {
                    Log.warning("processWorldEvent(" + type + "," + w + ") - exception", t);
                }
            }
        }
    }
    public void processPlayerEvent(EventType type, DynmapPlayer p) {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof PlayerEventListener) {
                try {
                    ((PlayerEventListener)el).playerEvent(p);
                } catch (Throwable t) {
                    Log.warning("processPlayerEvent(" + type + "," + p + ") - exception", t);
                }
            }
        }
    }
    public void processChatEvent(EventType type, DynmapPlayer p, String msg) {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof ChatEventListener) {
                try {
                    ((ChatEventListener)el).chatEvent(p, msg);
                } catch (Throwable t) {
                    Log.warning("processChatEvent(" + type + "," + msg + ") - exception", t);
                }
            }
        }
    }
    public void processBlockEvent(EventType type, String material, String world, int x, int y, int z)
    {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof BlockEventListener) {
                try {
                    ((BlockEventListener)el).blockEvent(material, world, x, y, z);
                } catch (Throwable t) {
                    Log.warning("processBlockEvent(" + type + "," + material + "," + world + "," + x + "," + y + "," + z + ") - exception", t);
                }
            }
        }
    }
    public void processSignChangeEvent(EventType type, String material, String world, int x, int y, int z, String[] lines, DynmapPlayer p)
    {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof SignChangeEventListener) {
                try {
                    ((SignChangeEventListener)el).signChangeEvent(material, world, x, y, z, lines, p);
                } catch (Throwable t) {
                    Log.warning("processSignChangeEvent(" + type + "," + material + "," + world + "," + x + "," + y + "," + z + ") - exception", t);
                }
            }
        }
    }
    /* Clean up registered listeners */
    public void cleanup() {
        for(ArrayList<EventListener> l : listeners.values())
            l.clear();
        listeners.clear();
    }
}
