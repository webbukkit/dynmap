package org.dynmap.utils;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.dynmap.Log;

public class SnapshotCache {
    private CacheHashMap snapcache;
    private ReferenceQueue<ChunkSnapshot> refqueue;
    private long cache_attempts;
    private long cache_success;

    private static class CacheRec {
        WeakReference<ChunkSnapshot> ref;
        boolean hasbiome;
        boolean hasrawbiome;
        boolean hasblockdata;
        boolean hashighesty;
    }
    
    public class CacheHashMap extends LinkedHashMap<String, CacheRec> {
        private int limit;
        private IdentityHashMap<WeakReference<ChunkSnapshot>, String> reverselookup;

        public CacheHashMap(int lim) {
            super(16, (float)0.75, true);
            limit = lim;
            reverselookup = new IdentityHashMap<WeakReference<ChunkSnapshot>, String>();
        }
        protected boolean removeEldestEntry(Map.Entry<String, CacheRec> last) {
            boolean remove = (size() >= limit);
            if(remove) {
                reverselookup.remove(last.getValue().ref);
            }
            return remove;
        }
    }

    /**
     * Create snapshot cache
     */
    public SnapshotCache(int max_size) {
        snapcache = new CacheHashMap(max_size);
        refqueue = new ReferenceQueue<ChunkSnapshot>();
    }
    private String getKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX()>>4) + ":" + (loc.getBlockZ()>>4);
    }
    private String getKey(String w, int cx, int cz) {
        return w + ":" + cx + ":" + cz;
    }
    /**
     * Invalidate cached snapshot, if in cache
     */
    public void invalidateSnapshot(Location loc) {
        String key = getKey(loc);
        CacheRec rec = snapcache.remove(key);
        if(rec != null) {
            snapcache.reverselookup.remove(rec.ref);
            rec.ref.clear();
        }
        processRefQueue();
    }
    /**
     * Look for chunk snapshot in cache
     */
    public ChunkSnapshot getSnapshot(String w, int chunkx, int chunkz, 
            boolean blockdata, boolean biome, boolean biomeraw, boolean highesty) {
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        ChunkSnapshot ss = null;
        CacheRec rec = snapcache.get(key);
        if(rec != null) {
            ss = rec.ref.get();
            if(ss == null) {
                snapcache.reverselookup.remove(rec.ref);
                snapcache.remove(key);
            }
        }
        if(ss != null) {
            if((blockdata && (!rec.hasblockdata)) ||
                    (biome && (!rec.hasbiome)) ||
                    (biomeraw && (!rec.hasrawbiome)) ||
                    (highesty && (!rec.hashighesty))) {
                ss = null;
            }
        }
        cache_attempts++;
        if(ss != null) cache_success++;

        return ss;
    }
    /**
     * Add chunk snapshot to cache
     */
    public void putSnapshot(String w, int chunkx, int chunkz, ChunkSnapshot ss, 
            boolean blockdata, boolean biome, boolean biomeraw, boolean highesty) {
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        CacheRec rec = new CacheRec();
        rec.hasblockdata = blockdata;
        rec.hasbiome = biome;
        rec.hasrawbiome = biomeraw;
        rec.hashighesty = highesty;
        rec.ref = new WeakReference<ChunkSnapshot>(ss, refqueue);
        CacheRec prevrec = snapcache.put(key, rec);
        if(prevrec != null) {
            snapcache.reverselookup.remove(prevrec.ref);
        }
        snapcache.reverselookup.put(rec.ref, key);
    }
    /**
     * Process reference queue
     */
    private void processRefQueue() {
        Reference<? extends ChunkSnapshot> ref;
        while((ref = refqueue.poll()) != null) {
            String k = snapcache.reverselookup.get(ref);
            if(k != null) snapcache.remove(k);
        }
    }
    /**
     * Get hit rate (percent)
     */
    public double getHitRate() {
        if(cache_attempts > 0) {
            return (100.0*cache_success)/(double)cache_attempts;
        }
        return 0.0;
    }
    /**
     * Reset cache stats
     */
    public void resetStats() {
        cache_attempts = cache_success = 0;
    }
}

