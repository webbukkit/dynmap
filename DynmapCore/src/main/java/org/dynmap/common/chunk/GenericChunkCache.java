package org.dynmap.common.chunk;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.ref.SoftReference;
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
    private ReferenceQueue<ChunkCacheRec> refqueue;
    private long cache_attempts;
    private long cache_success;
    private boolean softref;

    private static class CacheRec {
        Reference<ChunkCacheRec> ref;
    }
    
    @SuppressWarnings("serial")
    public class CacheHashMap extends LinkedHashMap<String, CacheRec> {
        private int limit;
        private IdentityHashMap<Reference<ChunkCacheRec>, String> reverselookup;

        public CacheHashMap(int lim) {
            super(16, (float)0.75, true);
            limit = lim;
            reverselookup = new IdentityHashMap<Reference<ChunkCacheRec>, String>();
        }
        protected boolean removeEldestEntry(Map.Entry<String, CacheRec> last) {
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
        snapcache = new CacheHashMap(max_size);
        refqueue = new ReferenceQueue<ChunkCacheRec>();
        this.softref = softref;
    }
    private String getKey(String w, int cx, int cz) {
        return w + ":" + cx + ":" + cz;
    }
    /**
     * Invalidate cached snapshot, if in cache
     */
    public void invalidateSnapshot(String w, int x, int y, int z) {
        String key = getKey(w, x>>4, z>>4);
        synchronized(snapcache) {
            CacheRec rec = snapcache.remove(key);
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
        for(int xx = (x0>>4); xx <= (x1>>4); xx++) {
            for(int zz = (z0>>4); zz <= (z1>>4); zz++) {
                String key = getKey(w, xx, zz);
                synchronized(snapcache) {
                    CacheRec rec = snapcache.remove(key);
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
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        ChunkCacheRec ss = null;
        CacheRec rec;
        synchronized(snapcache) {
            rec = snapcache.get(key);
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
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        CacheRec rec = new CacheRec();
        if (softref)
            rec.ref = new SoftReference<ChunkCacheRec>(ss, refqueue);
        else
            rec.ref = new WeakReference<ChunkCacheRec>(ss, refqueue);
        synchronized(snapcache) {
            CacheRec prevrec = snapcache.put(key, rec);
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
            synchronized(snapcache) {
                String k = snapcache.reverselookup.remove(ref);
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
        if(snapcache != null) {
            snapcache.clear();
            snapcache.reverselookup.clear();
            snapcache.reverselookup = null;
            snapcache = null;
        }
    }
}
