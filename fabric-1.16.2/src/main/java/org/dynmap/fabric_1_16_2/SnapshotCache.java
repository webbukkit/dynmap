package org.dynmap.fabric_1_16_2;

import org.dynmap.utils.DynIntHashMap;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SnapshotCache {
    public static class SnapshotRec {
        public ChunkSnapshot ss;
        public DynIntHashMap tileData;
    }

    private CacheHashMap snapcache;
    private ReferenceQueue<SnapshotRec> refqueue;
    private long cache_attempts;
    private long cache_success;
    private boolean softref;

    private static class CacheRec {
        Reference<SnapshotRec> ref;
        boolean hasbiome;
        boolean hasrawbiome;
        boolean hasblockdata;
        boolean hashighesty;
    }

    @SuppressWarnings("serial")
    public class CacheHashMap extends LinkedHashMap<String, CacheRec> {
        private int limit;
        private IdentityHashMap<Reference<SnapshotRec>, String> reverselookup;

        public CacheHashMap(int lim) {
            super(16, (float) 0.75, true);
            limit = lim;
            reverselookup = new IdentityHashMap<Reference<SnapshotRec>, String>();
        }

        protected boolean removeEldestEntry(Map.Entry<String, CacheRec> last) {
            boolean remove = (size() >= limit);
            if (remove && (last != null) && (last.getValue() != null)) {
                reverselookup.remove(last.getValue().ref);
            }
            return remove;
        }
    }

    /**
     * Create snapshot cache
     */
    public SnapshotCache(int max_size, boolean softref) {
        snapcache = new CacheHashMap(max_size);
        refqueue = new ReferenceQueue<SnapshotRec>();
        this.softref = softref;
    }

    private String getKey(String w, int cx, int cz) {
        return w + ":" + cx + ":" + cz;
    }

    /**
     * Invalidate cached snapshot, if in cache
     */
    public void invalidateSnapshot(String w, int x, int y, int z) {
        String key = getKey(w, x >> 4, z >> 4);
        synchronized (snapcache) {
            CacheRec rec = snapcache.remove(key);
            if (rec != null) {
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
        for (int xx = (x0 >> 4); xx <= (x1 >> 4); xx++) {
            for (int zz = (z0 >> 4); zz <= (z1 >> 4); zz++) {
                String key = getKey(w, xx, zz);
                synchronized (snapcache) {
                    CacheRec rec = snapcache.remove(key);
                    if (rec != null) {
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
    public SnapshotRec getSnapshot(String w, int chunkx, int chunkz,
                                   boolean blockdata, boolean biome, boolean biomeraw, boolean highesty) {
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        SnapshotRec ss = null;
        CacheRec rec;
        synchronized (snapcache) {
            rec = snapcache.get(key);
            if (rec != null) {
                ss = rec.ref.get();
                if (ss == null) {
                    snapcache.reverselookup.remove(rec.ref);
                    snapcache.remove(key);
                }
            }
        }
        if (ss != null) {
            if ((blockdata && (!rec.hasblockdata)) ||
                    (biome && (!rec.hasbiome)) ||
                    (biomeraw && (!rec.hasrawbiome)) ||
                    (highesty && (!rec.hashighesty))) {
                ss = null;
            }
        }
        cache_attempts++;
        if (ss != null) cache_success++;

        return ss;
    }

    /**
     * Add chunk snapshot to cache
     */
    public void putSnapshot(String w, int chunkx, int chunkz, SnapshotRec ss,
                            boolean blockdata, boolean biome, boolean biomeraw, boolean highesty) {
        String key = getKey(w, chunkx, chunkz);
        processRefQueue();
        CacheRec rec = new CacheRec();
        rec.hasblockdata = blockdata;
        rec.hasbiome = biome;
        rec.hasrawbiome = biomeraw;
        rec.hashighesty = highesty;
        if (softref)
            rec.ref = new SoftReference<SnapshotRec>(ss, refqueue);
        else
            rec.ref = new WeakReference<SnapshotRec>(ss, refqueue);
        synchronized (snapcache) {
            CacheRec prevrec = snapcache.put(key, rec);
            if (prevrec != null) {
                snapcache.reverselookup.remove(prevrec.ref);
            }
            snapcache.reverselookup.put(rec.ref, key);
        }
    }

    /**
     * Process reference queue
     */
    private void processRefQueue() {
        Reference<? extends SnapshotRec> ref;
        while ((ref = refqueue.poll()) != null) {
            synchronized (snapcache) {
                String k = snapcache.reverselookup.remove(ref);
                if (k != null) {
                    snapcache.remove(k);
                }
            }
        }
    }

    /**
     * Get hit rate (percent)
     */
    public double getHitRate() {
        if (cache_attempts > 0) {
            return (100.0 * cache_success) / (double) cache_attempts;
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
        if (snapcache != null) {
            snapcache.clear();
            snapcache.reverselookup.clear();
            snapcache.reverselookup = null;
            snapcache = null;
        }
    }
}
