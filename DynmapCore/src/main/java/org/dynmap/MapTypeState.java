package org.dynmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dynmap.utils.TileFlags;

public class MapTypeState {
    public static final long DEF_INV_PERIOD = 30;
    public static final long NANOS_PER_SECOND = 1000000000L;
    public MapType type;
    private Object invTileLock = new Object();
    private TileFlags pendingInvTiles = new TileFlags();
    private TileFlags pendingInvTilesAlt = new TileFlags();
    private TileFlags invTiles = new TileFlags();
    private TileFlags.Iterator invTilesIter = invTiles.getIterator();
    private long nextInvTS;
    private long invTSPeriod;
    private ArrayList<TileFlags> zoomOutInvAccum = new ArrayList<TileFlags>();
    private ArrayList<TileFlags> zoomOutInv = new ArrayList<TileFlags>();
    private TileFlags.Iterator zoomOutInvIter = null;
    private int zoomOutInvIterLevel = -1;
    private final int zoomOutLevels;
    
    public MapTypeState(DynmapWorld world, MapType mt) {
        type = mt;
        invTSPeriod = DEF_INV_PERIOD * NANOS_PER_SECOND;
        nextInvTS = System.nanoTime() + invTSPeriod;
        zoomOutLevels = world.getExtraZoomOutLevels() + mt.getMapZoomOutLevels();
        for (int i = 0; i < zoomOutLevels; i++) {
            zoomOutInv.add(null);
            zoomOutInvAccum.add(null);
        }
    }
    public void setInvalidatePeriod(long inv_per_in_secs) {
        invTSPeriod = inv_per_in_secs * NANOS_PER_SECOND;
    }

    public boolean invalidateTile(int tx, int ty) {
        boolean done;
        synchronized(invTileLock) {
            done = !pendingInvTiles.setFlag(tx, ty, true);
        }
        return done;
    }

    public int invalidateTiles(List<TileFlags.TileCoord> coords) {
        int cnt = 0;
        synchronized(invTileLock) {
            for(TileFlags.TileCoord c : coords) {
                if(!pendingInvTiles.setFlag(c.x, c.y, true)) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public void tickMapTypeState(long now_nano) {
        if(nextInvTS < now_nano) {
            synchronized(invTileLock) {
                TileFlags tmp = pendingInvTilesAlt;
                pendingInvTilesAlt = pendingInvTiles;
                pendingInvTiles = tmp;
                invTiles.union(tmp);
                tmp.clear();
                nextInvTS = now_nano + invTSPeriod;
            }
        }
    }
    
    public boolean getNextInvalidTileCoord(TileFlags.TileCoord coord) {
        boolean match;
        synchronized(invTileLock) {
            match = invTilesIter.next(coord);
        }
        return match;
    }
    
    public void validateTile(int tx, int ty) {
        synchronized(invTileLock) {
            invTiles.setFlag(tx, ty,  false);
            pendingInvTiles.setFlag(tx, ty, false);
            pendingInvTilesAlt.setFlag(tx, ty, false);
        }
    }
    
    public boolean isInvalidTile(int tx, int ty) {
        synchronized(invTileLock) {
            return invTiles.getFlag(tx, ty);
        }
    }
    
    public List<String> save() {
        synchronized(invTileLock) {
            invTiles.union(pendingInvTiles);
            invTiles.union(pendingInvTilesAlt);
            pendingInvTiles.clear();
            pendingInvTilesAlt.clear();
            return invTiles.save();
        }
    }
    public void restore(List<String> saved) {
        synchronized(invTileLock) {
            TileFlags tf = new TileFlags();
            tf.load(saved);
            invTiles.union(tf);
        }
    }
    
    public List<List<String>> saveZoomOut() {
        ArrayList<List<String>> rslt = new ArrayList<List<String>>();
        synchronized(invTileLock) {
            boolean empty = true;
            for (TileFlags tf : zoomOutInv) {
                List<String> val;
                if (tf == null) {
                    val = Collections.emptyList();
                }
                else {
                    val = tf.save();
                    if (val == null) {
                        val = Collections.emptyList();
                    }
                    else {
                        empty = false;
                    }
                }
                rslt.add(val);
            }
            for (TileFlags tf : zoomOutInvAccum) {
                List<String> val;
                if (tf == null) {
                    val = Collections.emptyList();
                }
                else {
                    val = tf.save();
                    if (val == null) {
                        val = Collections.emptyList();
                    }
                    else {
                        empty = false;
                    }
                }
                rslt.add(val);
            }
            if (empty) {
                rslt = null;
            }
        }
        return rslt;
    }

    public void restoreZoomOut(List<List<String>> dat) {
        synchronized(invTileLock) {
            int cnt = dat.size();
            int cntaccum = 0;
            if (cnt > zoomOutInv.size()) {
                if (cnt == (2*zoomOutInv.size())) {
                    cntaccum = cnt / 2;
                }
                cnt = zoomOutInv.size();
            }
            for (int i = 0; i < cnt; i++) {
                List<String> lst = dat.get(i);
                TileFlags tf = null;
                if ((lst != null) && (lst.size() > 0)) {
                    tf = new TileFlags();
                    tf.load(lst);
                }
                zoomOutInv.set(i, tf);
            }
            for (int i = 0; i < cntaccum; i++) {
                List<String> lst = dat.get(i + cnt);
                TileFlags tf = null;
                if ((lst != null) && (lst.size() > 0)) {
                    tf = new TileFlags();
                    tf.load(lst);
                }
                zoomOutInvAccum.set(i, tf);
            }
        }
    }

    public int getInvCount() {
        synchronized(invTileLock) {
            return invTiles.countFlags();
        }
    }
    public void clear() {
        synchronized(invTileLock) {
            invTiles.clear();
        }
    }
    // Set to zoom out accum
    public void setZoomOutInv(int x, int y, int zoomlevel) {
        if (zoomlevel >= zoomOutLevels) {
            return;
        }
        synchronized(invTileLock) {
            TileFlags tf = zoomOutInvAccum.get(zoomlevel);
            if (tf == null) {
                tf = new TileFlags();
                zoomOutInvAccum.set(zoomlevel, tf);
            }
            if ((((x >> zoomlevel) << zoomlevel) != x) ||
                    (((y >> zoomlevel) << zoomlevel) != y)) {
                Log.info("setZoomOutInv(" + x + "," + y + "," + zoomlevel + ")");
            }
            tf.setFlag(x >> zoomlevel, y >> zoomlevel, true);
        }
    }
    // Clear flag in active zoom out flags
    public boolean clearZoomOutInv(int x, int y, int zoomlevel) {
        if (zoomlevel >= zoomOutLevels) {
            return false;
        }
        synchronized(invTileLock) {
            TileFlags tf = zoomOutInv.get(zoomlevel);
            if (tf == null) {
                return false;
            }
            return tf.setFlag(x >> zoomlevel, y >> zoomlevel, false);
        }
    }
    public static class ZoomOutCoord extends TileFlags.TileCoord {
        public int zoomlevel;
    }
    // Start zoom out iteration (stash and reset accumulator)
    public void startZoomOutIter() {
        synchronized(invTileLock) {
            ArrayList<TileFlags> tmplist = zoomOutInv;
            zoomOutInv = zoomOutInvAccum;
            for (int i = 0; i < tmplist.size(); i++) {
                tmplist.set(i, null);
            }
            zoomOutInvAccum = tmplist;
            zoomOutInvIter = null;
            zoomOutInvIterLevel = 0;
        }
    }
    public boolean nextZoomOutInv(ZoomOutCoord coord) {
        synchronized(invTileLock) {
            // Try existing iterator
            if (zoomOutInvIter != null) {
                if (zoomOutInvIter.hasNext()) {
                    zoomOutInvIter.next(coord);
                    coord.zoomlevel = zoomOutInvIterLevel;
                    coord.x = coord.x << zoomOutInvIterLevel;
                    coord.y = coord.y << zoomOutInvIterLevel;
                    return true;
                }
                zoomOutInvIter = null;
            }
            for (; zoomOutInvIterLevel < zoomOutInv.size(); zoomOutInvIterLevel++) {
                TileFlags tf = zoomOutInv.get(zoomOutInvIterLevel);
                if (tf != null) {
                    zoomOutInvIter = tf.getIterator();
                    if (zoomOutInvIter.hasNext()) {
                        zoomOutInvIter.next(coord);
                        coord.zoomlevel = zoomOutInvIterLevel;
                        coord.x = coord.x << zoomOutInvIterLevel;
                        coord.y = coord.y << zoomOutInvIterLevel;
                        return true;
                    }
                    else {
                        zoomOutInvIter = null;
                    }
                }
            }
        }
        return false;
    }
}
