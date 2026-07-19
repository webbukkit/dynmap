package org.dynmap.common.chunk;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dynmap.utils.DynIntHashMap;

// Generic chunk cache
public class GenericChunkCache {
    public static class ChunkCacheRec {
        public GenericChunk ss;
        public DynIntHashMap tileData;
    };

    private CacheHashMap snapcache;
    private final Object snapcachelock;
    private final ReferenceQueue<ChunkCacheRec> refqueue;
    private long cache_attempts;
    private long cache_success;
    private final boolean softref;
    // World name -> small integer ID, used to build long cache keys without String concatenation.
    // Accessed only while holding snapcachelock.
    private final HashMap<String, Integer> worldIds = new HashMap<>();
    private int nextWorldId = 0;

    private static class CacheRec {
        Reference<ChunkCacheRec> ref;
    }

    @SuppressWarnings("serial")
    public class CacheHashMap extends LinkedHashMap<Long, CacheRec> {
        private final int limit;
        private IdentityHashMap<Reference<ChunkCacheRec>, Long> reverselookup;

        public CacheHashMap(int lim) {
            super(16, (float)0.75, true);
            limit = lim;
            reverselookup = new IdentityHashMap<>();
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CacheRec> last) {
            boolean remove = (size() >= limit);
            if(remove && (last != null) && (last.getValue() != null)) {
                reverselookup.remove(last.getValue().ref);
            }
            return remove;
        }
    }

    /**
     * Create snapshot cache
     */
    public GenericChunkCache(int max_size, boolean softref) {
    	snapcachelock = new Object();
        snapcache = new CacheHashMap(max_size);
        refqueue = new ReferenceQueue<>();
        this.softref = softref;
    }
    /**
     * Encode world name + chunk coords as a single long key.
     * Worlds are assigned small integer IDs (10 bits) on first use.
     * cx and cz are each encoded in 27 bits (signed, supports ±8M chunks / ±128M blocks).
     * Must be called while holding snapcachelock.
     */
    private long getKey(String w, int cx, int cz) {
        Integer wid = worldIds.get(w);
        if (wid == null) {
            wid = nextWorldId++;
            worldIds.put(w, wid);
        }
        return ((long)(wid & 0x3FF) << 54) | ((long)(cx & 0x7FFFFFF) << 27) | (long)(cz & 0x7FFFFFF);
    }
    /**
     * Invalidate cached snapshot, if in cache
     */
    public void invalidateSnapshot(String w, int x, int y, int z) {
        synchronized(snapcachelock) {
            long key = getKey(w, x>>4, z>>4);
            CacheRec rec = (snapcache != null) ? snapcache.remove(key) : null;
            if(rec != null) {
                snapcache.reverselookup.remove(rec.ref);
                rec.ref.clear();
            }
        }
        //processRefQueue();
    }
    /**
     * Invalidate cached snapshot, if in cache
     */
    public void invalidateSnapshot(String w, int x0, int y0, int z0, int x1, int y1, int z1) {
        synchronized(snapcachelock) {
            for(int xx = (x0>>4); xx <= (x1>>4); xx++) {
                for(int zz = (z0>>4); zz <= (z1>>4); zz++) {
                    long key = getKey(w, xx, zz);
                    CacheRec rec = (snapcache != null) ? snapcache.remove(key) : null;
                    if(rec != null) {
                        snapcache.reverselookup.remove(rec.ref);
                        rec.ref.clear();
                    }
                }
            }
        }
        //processRefQueue();
    }
    /**
     * Look for chunk snapshot in cache
     */
    public ChunkCacheRec getSnapshot(String w, int chunkx, int chunkz) {
        processRefQueue();
        ChunkCacheRec ss = null;
        CacheRec rec;
        synchronized(snapcachelock) {
            long key = getKey(w, chunkx, chunkz);
            rec = (snapcache != null) ? snapcache.get(key) : null;
            if(rec != null) {
                ss = rec.ref.get();
                if(ss == null) {
                    snapcache.reverselookup.remove(rec.ref);
                    snapcache.remove(key);
                }
            }
        }
        cache_attempts++;
        if(ss != null) cache_success++;

        return ss;
    }
    /**
     * Add chunk snapshot to cache
     */
    public void putSnapshot(String w, int chunkx, int chunkz, ChunkCacheRec ss) {
        processRefQueue();
        CacheRec rec = new CacheRec();
        if (softref)
            rec.ref = new SoftReference<>(ss, refqueue);
        else
            rec.ref = new WeakReference<>(ss, refqueue);
        synchronized(snapcachelock) {
            long key = getKey(w, chunkx, chunkz);
            CacheRec prevrec = (snapcache != null) ? snapcache.put(key, rec) : null;
            if(prevrec != null) {
                snapcache.reverselookup.remove(prevrec.ref);
            }
            snapcache.reverselookup.put(rec.ref, key);
        }
    }
    /**
     * Process reference queue
     */
    private void processRefQueue() {
        Reference<? extends ChunkCacheRec> ref;
        while((ref = refqueue.poll()) != null) {
            synchronized(snapcachelock) {
                Long k = (snapcache != null) ? snapcache.reverselookup.remove(ref) : null;
                if(k != null) {
                    snapcache.remove(k);
                }
            }
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
    /**
     * Cleanup
     */
    public void cleanup() {
		synchronized(snapcachelock) {
	        if(snapcache != null) {
	            snapcache.clear();
	            snapcache.reverselookup.clear();
	            snapcache.reverselookup = null;
	            snapcache = null;
	        }
		}
    }
}
