package org.dynmap.common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;

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
        public void blockEvent(int blkid, String w, int x, int y, int z);
    }
    public interface SignChangeEventListener extends EventListener {
        public void signChangeEvent(int blkid, String w, int x, int y, int z, String[] lines, DynmapPlayer p);
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
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) {
            lst = new ArrayList<EventListener>();
            listeners.put(type, lst);
            core.getServer().requestEventNotification(type);
        }
        lst.add(listener);
    }
    
    public void processWorldEvent(EventType type, DynmapWorld w) {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof WorldEventListener) {
                ((WorldEventListener)el).worldEvent(w);
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
                ((PlayerEventListener)el).playerEvent(p);
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
                ((ChatEventListener)el).chatEvent(p, msg);
            }
        }
    }
    public void processBlockEvent(EventType type, int blkid, String world, int x, int y, int z)
    {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof BlockEventListener) {
                ((BlockEventListener)el).blockEvent(blkid, world, x, y, z);
            }
        }
    }
    public void processSignChangeEvent(EventType type, int blkid, String world, int x, int y, int z, String[] lines, DynmapPlayer p)
    {
        ArrayList<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        int sz = lst.size();
        for(int i = 0; i < sz; i++) {
            EventListener el = lst.get(i);
            if(el instanceof SignChangeEventListener) {
                ((SignChangeEventListener)el).signChangeEvent(blkid, world, x, y, z, lines, p);
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
