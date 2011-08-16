package org.dynmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.Location;
import org.dynmap.debug.Debug;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import javax.imageio.ImageIO;

public class DynmapWorld {
    public enum AutoGenerateOption {
        NONE,
        FORMAPONLY,
        PERMANENT
    }
    public World world;
    public List<MapType> maps = new ArrayList<MapType>();
    public UpdateQueue updates = new UpdateQueue();
    public ConfigurationNode configuration;
    public List<Location> seedloc;
    public List<MapChunkCache.VisibilityLimit> visibility_limits;
    public List<MapChunkCache.VisibilityLimit> hidden_limits;
    public AutoGenerateOption do_autogenerate;
    public MapChunkCache.HiddenChunkStyle hiddenchunkstyle;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean bigworld;    /* If true, deeper directory hierarchy */
    private int extrazoomoutlevels;  /* Number of additional zoom out levels to generate */
    public File worldtilepath;
    private Object lock = new Object();
    @SuppressWarnings("unchecked")
    private HashSet<String> zoomoutupdates[] = new HashSet[0];
    private boolean checkts = true;	/* Check timestamps on first run with new configuration */
    private boolean cancelled;
    
    @SuppressWarnings("unchecked")
    public void setExtraZoomOutLevels(int lvl) {
        extrazoomoutlevels = lvl;
        zoomoutupdates = new HashSet[lvl];
        for(int i = 0; i < lvl; i++)
            zoomoutupdates[i] = new HashSet<String>();
    	checkts = true;
    }
    public int getExtraZoomOutLevels() { return extrazoomoutlevels; }
    
    public void enqueueZoomOutUpdate(File f) {
        enqueueZoomOutUpdate(f, 0);
    }
    
    private void enqueueZoomOutUpdate(File f, int level) {
        synchronized(lock) {
            if(level >= zoomoutupdates.length) {
               @SuppressWarnings("unchecked")
               HashSet<String> new_zoomout[] = new HashSet[level+1];
               System.arraycopy(zoomoutupdates, 0, new_zoomout, 0, zoomoutupdates.length);
               for(int i = 0; i < new_zoomout.length; i++) {
                   if(i < zoomoutupdates.length)
                       new_zoomout[i] = zoomoutupdates[i];
                   else
                       new_zoomout[i] = new HashSet<String>();
               }
               zoomoutupdates = new_zoomout;
            }
            zoomoutupdates[level].add(f.getPath());
        }
    }
    
    private boolean popQueuedUpdate(File f, int level) {
        if(level >= zoomoutupdates.length)
            return false;
        synchronized(lock) {
            return zoomoutupdates[level].remove(f.getPath());
        }
    }
    
    private String[]	peekQueuedUpdates(int level) {
        if(level >= zoomoutupdates.length)
            return new String[0];
        synchronized(lock) {
            return zoomoutupdates[level].toArray(new String[zoomoutupdates[level].size()]);
        }
    }
    
    private static class DirFilter implements FilenameFilter {
        public boolean accept(File f, String n) {
            if(!n.equals("..") && !n.equals(".")) {
                File fn = new File(f, n);
                return fn.isDirectory();
            }
            return false;
        }
    }

    private static final String COORDSTART = "-0123456789";
    private static class PNGFileFilter implements FilenameFilter {
        String prefix;
        String suffix;
        public PNGFileFilter(String pre, MapType.ImageFormat fmt) {
            if((pre != null) && (pre.length() > 0))
                prefix = pre; 
            suffix = "." + fmt.getFileExt();
        }
        public boolean accept(File f, String n) {
            if(n.endsWith(suffix)) {
                if((prefix != null) && (!n.startsWith(prefix)))
                    return false;
                if((prefix == null) && (COORDSTART.indexOf(n.charAt(0)) < 0))
                    return false;
                File fn = new File(f, n);
                return fn.isFile();
            }
            return false;
        }
    }

    public void freshenZoomOutFiles() {
        boolean done = false;
        int last_done = 0;
        for(int i = 0; (!cancelled) && (!done); i++) {
            done = freshenZoomOutFilesByLevel(i);
            last_done = i;
        }
        /* Purge updates for levels above what any map needs */
        for(int i = last_done; i < zoomoutupdates.length; i++) {
            zoomoutupdates[i].clear();
        }
        checkts = false;	/* Just handle queued updates after first scan */
    }
    
    public void cancelZoomOutFreshen() {
        cancelled = true;
    }
    
    private static class PrefixData {
    	int stepsize;
    	int[] stepseq;
    	boolean neg_step_x;
        boolean neg_step_y;
    	String baseprefix;
    	int zoomlevel;
    	String zoomprefix;
    	String fnprefix;
        String zfnprefix;
        int bigworldshift;
        boolean isbigmap;
        MapType.ImageFormat fmt;
    }
    
    public boolean freshenZoomOutFilesByLevel(int zoomlevel) {
        int cnt = 0;
        Debug.debug("freshenZoomOutFiles(" + world.getName() + "," + zoomlevel + ")");
        if(worldtilepath.exists() == false) /* Quit if not found */
            return true;
        HashMap<String, PrefixData> maptab = buildPrefixData(zoomlevel);

        if(checkts) {	/* If doing timestamp based scan (initial) */
        	DirFilter df = new DirFilter();
        	for(String pfx : maptab.keySet()) { /* Walk through prefixes */
                if(cancelled) return true;
        		PrefixData pd = maptab.get(pfx);
        		if(pd.isbigmap) { /* If big world, next directories are map name specific */
        			File dname = new File(worldtilepath, pfx);
        			/* Now, go through subdirectories under this one, and process them */
        			String[] subdir = dname.list(df);
        			if(subdir == null) continue;
        			for(String s : subdir) {
        			    if(cancelled) return true;
        				File sdname = new File(dname, s);
        				cnt += processZoomDirectory(sdname, pd);
        			}
        		}
        		else {  /* Else, classic file layout */
        			cnt += processZoomDirectory(worldtilepath, maptab.get(pfx));
        		}
        	}
        	Debug.debug("freshenZoomOutFiles(" + world.getName() + "," + zoomlevel + ") - done (" + cnt + " updated files)");
        }
        else {	/* Else, only process updates */
            String[] paths = peekQueuedUpdates(zoomlevel);	/* Get pending updates */
            HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
            /* Accumulate zoomed tiles to be processed (combine triggering subtiles) */
            for(String p : paths) {
                if(cancelled) return true;
            	File f = new File(p);	/* Make file */
            	/* Find matching prefix */
            	for(PrefixData pd : maptab.values()) { /* Walk through prefixes */
                    if(cancelled) return true;
            		ProcessTileRec tr = null;
            		/* If big map and matches name pattern */
            		if(pd.isbigmap && f.getName().startsWith(pd.fnprefix) && 
            				f.getParentFile().getParentFile().getName().equals(pd.baseprefix)) {
                        tr = processZoomFile(f, pd);
            		}
                    /* If not big map and matches name pattern */
                    else if((!pd.isbigmap) && f.getName().startsWith(pd.fnprefix)) {
                        tr = processZoomFile(f, pd);
                    }
                    if(tr != null) {
                        String zfpath = tr.zf.getPath();
                        if(!toprocess.containsKey(zfpath))  {
                            toprocess.put(zfpath, tr);
                        }
                    }
                }
            }
            /* Do processing */
            for(ProcessTileRec s : toprocess.values()) {
                if(cancelled) return true;
                processZoomTile(s.pd, s.zf, s.zfname, s.x, s.y);
            }
        }
        /* Return true when we have none left at the level */
        return (maptab.size() == 0);
    }
    
    private HashMap<String, PrefixData> buildPrefixData(int zoomlevel) {
        HashMap<String, PrefixData> maptab = new HashMap<String, PrefixData>();
        /* Build table of file prefixes and step sizes */
        for(MapType mt : maps) {
            /* If level is above top needed for this map, skip */
            if(zoomlevel > (this.extrazoomoutlevels + mt.getMapZoomOutLevels()))
                continue;
            List<String> pfx = mt.baseZoomFilePrefixes();
            int stepsize = mt.baseZoomFileStepSize();
            int bigworldshift = mt.getBigWorldShift();
            boolean neg_step_x = false;
            boolean neg_step_y = false;
            switch(mt.zoomFileMapStep()) {
                case X_PLUS_Y_PLUS:
                    break;
                case X_MINUS_Y_PLUS:
                    neg_step_x = true;
                    break;
                case X_PLUS_Y_MINUS:
                    neg_step_y = true;
                    break;
                case X_MINUS_Y_MINUS:
                    neg_step_x = neg_step_y = true;
                    break;
            }
            int[] stepseq = mt.zoomFileStepSequence();
            for(String p : pfx) {
                PrefixData pd = new PrefixData();
                pd.stepsize = stepsize;
                pd.neg_step_x = neg_step_x;
                pd.neg_step_y = neg_step_y;
                pd.stepseq = stepseq;
                pd.baseprefix = p;
                pd.zoomlevel = zoomlevel;
                pd.zoomprefix = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".substring(0, zoomlevel);
                pd.bigworldshift = bigworldshift;
                pd.isbigmap = mt.isBigWorldMap(this);
                pd.fmt = mt.getImageFormat();
                if(pd.isbigmap) {
                    if(zoomlevel > 0) {
                        pd.zoomprefix += "_";
                        pd.zfnprefix = "z" + pd.zoomprefix;
                    }
                    else {
                        pd.zfnprefix = "z_";
                    }
                    pd.fnprefix = pd.zoomprefix;
                }
                else {
                    pd.fnprefix = pd.zoomprefix + pd.baseprefix;
                    pd.zfnprefix = "z" + pd.fnprefix;
                }
                
                maptab.put(p, pd);
            }
        }
        return maptab;
    }
    
    private static class ProcessTileRec {
        File zf;
        String zfname;
        int x, y;
        PrefixData pd;
    }

    private String makeFilePath(PrefixData pd, int x, int y, boolean zoomed) {
        if(pd.isbigmap)
            return pd.baseprefix + "/" + (x >> pd.bigworldshift) + "_" + (y >> pd.bigworldshift) + "/" + (zoomed?pd.zfnprefix:pd.fnprefix) + x + "_" + y + "." + pd.fmt.getFileExt();
        else
            return (zoomed?pd.zfnprefix:pd.fnprefix) + "_" + x + "_" + y + "." + pd.fmt.getFileExt();            
    }
    
    private int processZoomDirectory(File dir, PrefixData pd) {
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.baseprefix + ")");
        HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
        String[] files = dir.list(new PNGFileFilter(pd.fnprefix, pd.fmt));
        if(files == null)
            return 0;
        for(String fn : files) {
            ProcessTileRec tr = processZoomFile(new File(dir, fn), pd);
            if(tr != null) {
                String zfpath = tr.zf.getPath();
                if(!toprocess.containsKey(zfpath))  {
                    toprocess.put(zfpath, tr);
                }
            }
        }
        int cnt = 0;
        /* Do processing */
        for(ProcessTileRec s : toprocess.values()) {
            processZoomTile(s.pd, s.zf, s.zfname, s.x, s.y);
            cnt++;
        }
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.baseprefix + ") - done (" + cnt + " files)");
        return cnt;
    }
    
    private ProcessTileRec processZoomFile(File f, PrefixData pd) {
    	/* If not checking timstamp, we're out if nothing queued for this file */
    	if(!checkts) {
    		if(!popQueuedUpdate(f, pd.zoomlevel))
    			return null;
    	}
        int step = pd.stepsize << pd.zoomlevel;
        String fn = f.getName();
        /* Parse filename to predict zoomed out file */
        fn = fn.substring(0, fn.lastIndexOf('.'));  /* Strip off extension */
        String[] tok = fn.split("_");   /* Split by underscores */
        int x = 0;
        int y = 0;
        boolean parsed = false;
        if(tok.length >= 2) {
            try {
                x = Integer.parseInt(tok[tok.length-2]);
                y = Integer.parseInt(tok[tok.length-1]);
                parsed = true;
            } catch (NumberFormatException nfx) {
            }
        }
        if(!parsed)
            return null;
        if(pd.neg_step_x) x = -x;
        if(x >= 0)
            x = x - (x % (2*step));
        else
            x = x + (x % (2*step));
        if(pd.neg_step_x) x = -x;
        if(pd.neg_step_y) y = -y;
        if(y >= 0)
            y = y - (y % (2*step));
        else
            y = y + (y % (2*step));
        if(pd.neg_step_y) y = -y;
        /* Make name of corresponding zoomed tile */
        String zfname = makeFilePath(pd, x, y, true);
        File zf = new File(worldtilepath, zfname);
        if(checkts) {	/* If checking timestamp, see if we need update based on enqueued update OR old file time */
        	/* If we're not updated, and zoom file exists and is older than our file, nothing to do */
        	if((!popQueuedUpdate(f, pd.zoomlevel)) && zf.exists() && (zf.lastModified() >= f.lastModified())) {
        		return null;
        	}
        }
        ProcessTileRec rec = new ProcessTileRec();
        rec.zf = zf;
        rec.x = x;
        rec.y = y;
        rec.zfname = zfname;
        rec.pd = pd;
        Debug.debug("Process " + zf.getPath() + " due to " + f.getPath());
        return rec;
    }
    
    private void processZoomTile(PrefixData pd, File zf, String zfname, int tx, int ty) {
        Debug.debug("processZoomFile(" + pd.baseprefix + "," + zf.getPath() + "," + tx + "," + ty + ")");
        int width = 128, height = 128;
        BufferedImage zIm = null;
        DynmapBufferedImage kzIm = null;
        int[] argb = new int[width*height];
        int step = pd.stepsize << pd.zoomlevel;
        int ztx = tx;
        int zty = ty;
        tx = tx - (pd.neg_step_x?step:0);	/* Adjust for negative step */ 
        ty = ty - (pd.neg_step_y?step:0);   /* Adjust for negative step */ 

        /* create image buffer */
        kzIm = DynmapBufferedImage.allocateBufferedImage(width, height);
        zIm = kzIm.buf_img;

        for(int i = 0; i < 4; i++) {
            File f = new File(worldtilepath, makeFilePath(pd, (tx + step*(1&pd.stepseq[i])), (ty + step*(pd.stepseq[i]>>1)), false));
            if(f.exists()) {
                BufferedImage im = null;
            	FileLockManager.getReadLock(f);
                popQueuedUpdate(f, pd.zoomlevel);
                try {
                    im = ImageIO.read(f);
                } catch (IOException e) {
                } catch (IndexOutOfBoundsException e) {
                } finally {
                    FileLockManager.releaseReadLock(f);
                }
                if(im != null) {
                    im.getRGB(0, 0, width, height, argb, 0, width);    /* Read data */
                    im.flush();
                    /* Do binlinear scale to 64x64 */
                    int off = 0;
                    for(int y = 0; y < height; y += 2) {
                        off = y*width;
                        for(int x = 0; x < width; x += 2, off += 2) {
                            int p0 = argb[off];
                            int p1 = argb[off+1];
                            int p2 = argb[off+width];
                            int p3 = argb[off+width+1];
                            int alpha = ((p0 >> 24) & 0xFF) + ((p1 >> 24) & 0xFF) + ((p2 >> 24) & 0xFF) + ((p3 >> 24) & 0xFF);
                            int red = ((p0 >> 16) & 0xFF) + ((p1 >> 16) & 0xFF) + ((p2 >> 16) & 0xFF) + ((p3 >> 16) & 0xFF);
                            int green = ((p0 >> 8) & 0xFF) + ((p1 >> 8) & 0xFF) + ((p2 >> 8) & 0xFF) + ((p3 >> 8) & 0xFF);
                            int blue = (p0 & 0xFF) + (p1 & 0xFF) + (p2 & 0xFF) + (p3 & 0xFF);
                            argb[off>>1] = (((alpha>>2)&0xFF)<<24) | (((red>>2)&0xFF)<<16) | (((green>>2)&0xFF)<<8) | ((blue>>2)&0xFF);
                        }
                    }
                    /* blit scaled rendered tile onto zoom-out tile */
                    zIm.setRGB(((i>>1) != 0)?0:width/2, (i & 1) * height/2, width/2, height/2, argb, 0, width);
                }
            }
        }
        FileLockManager.getWriteLock(zf);
        try {
            MapManager mm = MapManager.mapman;
            if(mm == null)
                return;
            TileHashManager hashman = mm.hashman;
            long crc = hashman.calculateTileHash(kzIm.argb_buf); /* Get hash of tile */
            int tilex = ztx/step/2;
            int tiley = zty/step/2;
            String key = world.getName()+".z"+pd.zoomprefix+pd.baseprefix;
            if((!zf.exists()) || (crc != mm.hashman.getImageHashCode(key, null, tilex, tiley))) {
                try {
                    if(!zf.getParentFile().exists())
                        zf.getParentFile().mkdirs();
                    FileLockManager.imageIOWrite(zIm, pd.fmt, zf);
                    Debug.debug("Saved zoom-out tile at " + zf.getPath());
                } catch (IOException e) {
                    Debug.error("Failed to save zoom-out tile: " + zf.getName(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save zoom-out tile (NullPointerException): " + zf.getName(), e);
                }
                hashman.updateHashCode(key, null, tilex, tiley, crc);
                MapManager.mapman.pushUpdate(this.world, new Client.Tile(zfname));
                enqueueZoomOutUpdate(zf, pd.zoomlevel+1);
            }
        } finally {
            FileLockManager.releaseWriteLock(zf);
            DynmapBufferedImage.freeBufferedImage(kzIm);
        }
    }
}
