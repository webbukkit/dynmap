package org.dynmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.Location;
import org.dynmap.debug.Debug;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.kzedmap.KzedMap.KzedBufferedImage;
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
    public AutoGenerateOption do_autogenerate;
    public MapChunkCache.HiddenChunkStyle hiddenchunkstyle;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean bigworld;    /* If true, deeper directory hierarchy */
    private int extrazoomoutlevels;  /* Number of additional zoom out levels to generate */
    public File worldtilepath;
    private Object lock = new Object();
    private HashSet<String> zoomoutupdates[];
    private boolean checkts = true;	/* Check timestamps on first run with new configuration */
    
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
        if(level >= extrazoomoutlevels)
            return;
        synchronized(lock) {
            zoomoutupdates[level].add(f.getPath());
        }
    }
    
    private boolean popQueuedUpdate(File f, int level) {
        if(level >= extrazoomoutlevels)
            return false;
        synchronized(lock) {
            return zoomoutupdates[level].remove(f.getPath());
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

    private static class PNGFileFilter implements FilenameFilter {
        String prefix;
        public PNGFileFilter(String pre) { prefix = pre; }
        public boolean accept(File f, String n) {
            if(n.endsWith(".png") && n.startsWith(prefix)) {
                File fn = new File(f, n);
                return fn.isFile();
            }
            return false;
        }
    }

    public void freshenZoomOutFiles() {
        for(int i = 0; i < extrazoomoutlevels; i++) {
            freshenZoomOutFilesByLevel(i);
        }
        checkts = false;	/* Just handle queued updates after first scan */
    }
    
    private static class PrefixData {
    	int stepsize;
    	int[] stepseq;
    	boolean neg_step_x;
    	String baseprefix;
    	int zoomlevel;
    	String zoomprefix;
    	String fnprefix;
        String zfnprefix;
        int bigworldshift;
    }
    
    public void freshenZoomOutFilesByLevel(int zoomlevel) {
        int cnt = 0;
        Debug.debug("freshenZoomOutFiles(" + world.getName() + "," + zoomlevel + ")");
        if(worldtilepath.exists() == false) /* Quit if not found */
            return;
        HashMap<String, PrefixData> maptab = buildPrefixData(zoomlevel);

        if(bigworld) {  /* If big world, next directories are map name specific */
            DirFilter df = new DirFilter();
            for(String pfx : maptab.keySet()) { /* Walk through prefixes, as directories */
                PrefixData pd = maptab.get(pfx);
                File dname = new File(worldtilepath, pfx);
                /* Now, go through subdirectories under this one, and process them */
                String[] subdir = dname.list(df);
                if(subdir == null) continue;
                for(String s : subdir) {
                    File sdname = new File(dname, s);
                    cnt += processZoomDirectory(sdname, pd);
                }
            }
        }
        else {  /* Else, classic file layout */
            for(String pfx : maptab.keySet()) { /* Walk through prefixes, as directories */
                cnt += processZoomDirectory(worldtilepath, maptab.get(pfx));
            }
        }
        Debug.debug("freshenZoomOutFiles(" + world.getName() + "," + zoomlevel + ") - done (" + cnt + " updated files)");
    }
    
    private HashMap<String, PrefixData> buildPrefixData(int zoomlevel) {
        HashMap<String, PrefixData> maptab = new HashMap<String, PrefixData>();
        /* Build table of file prefixes and step sizes */
        for(MapType mt : maps) {
            List<String> pfx = mt.baseZoomFilePrefixes();
            int stepsize = mt.baseZoomFileStepSize();
            int bigworldshift = mt.getBigWorldShift();
            boolean neg_step_x = false;
            if(stepsize < 0) {
                stepsize = -stepsize;
                neg_step_x = true;
            }
            int[] stepseq = mt.zoomFileStepSequence();
            for(String p : pfx) {
                PrefixData pd = new PrefixData();
                pd.stepsize = stepsize;
                pd.neg_step_x = neg_step_x;
                pd.stepseq = stepseq;
                pd.baseprefix = p;
                pd.zoomlevel = zoomlevel;
                pd.zoomprefix = "zzzzzzzzzzzz".substring(0, zoomlevel);
                pd.bigworldshift = bigworldshift;
                if(bigworld) {
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
    }

    private String makeFilePath(PrefixData pd, int x, int y, boolean zoomed) {
        if(bigworld)
            return pd.baseprefix + "/" + (x >> pd.bigworldshift) + "_" + (y >> pd.bigworldshift) + "/" + (zoomed?pd.zfnprefix:pd.fnprefix) + x + "_" + y + ".png";
        else
            return (zoomed?pd.zfnprefix:pd.fnprefix) + "_" + x + "_" + y + ".png";            
    }
    
    private int processZoomDirectory(File dir, PrefixData pd) {
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.baseprefix + ")");
        HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
        String[] files = dir.list(new PNGFileFilter(pd.fnprefix));
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
            processZoomTile(pd, dir, s.zf, s.zfname, s.x, s.y);
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
        if(y >= 0)
            y = y - (y % (2*step));
        else
            y = y + (y % (2*step));
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
        Debug.debug("Process " + zf.getPath() + " due to " + f.getPath());
        return rec;
    }
    
    private void processZoomTile(PrefixData pd, File dir, File zf, String zfname, int tx, int ty) {
        Debug.debug("processZoomFile(" + pd.baseprefix + "," + dir.getPath() + "," + zf.getPath() + "," + tx + "," + ty + ")");
        int width = 128, height = 128;
        BufferedImage zIm = null;
        KzedBufferedImage kzIm = null;
        int[] argb = new int[width*height];
        int step = pd.stepsize << pd.zoomlevel;
        int ztx = tx;
        tx = tx - (pd.neg_step_x?step:0);	/* Adjust for negative step */ 

        /* create image buffer */
        kzIm = KzedMap.allocateBufferedImage(width, height);
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
                }
                FileLockManager.releaseReadLock(f);
                if(im != null) {
                    im.getRGB(0, 0, width, height, argb, 0, width);    /* Read data */
                    im.flush();
                    /* Do binlinear scale to 64x64 */
                    Color c1 = new Color();
                    for(int y = 0; y < height; y += 2) {
                        for(int x = 0; x < width; x += 2) {
                            int red = 0;
                            int green = 0;
                            int blue = 0;
                            int alpha = 0;
                            for(int yy = y; yy < y+2; yy++) {
                                for(int xx = x; xx < x+2; xx++) {
                                    c1.setARGB(argb[(yy*width)+xx]);
                                    red += c1.getRed();
                                    green += c1.getGreen();
                                    blue += c1.getBlue();
                                    alpha += c1.getAlpha();
                                }
                            }
                            c1.setRGBA(red>>2, green>>2, blue>>2, alpha>>2);
                            argb[(y*width/2) + (x/2)] = c1.getARGB();
                        }
                    }
                    /* blit scaled rendered tile onto zoom-out tile */
                    zIm.setRGB(((i>>1) != 0)?0:width/2, (i & 1) * height/2, width/2, height/2, argb, 0, width);
                }
            }
        }
        FileLockManager.getWriteLock(zf);
        TileHashManager hashman = MapManager.mapman.hashman;
        long crc = hashman.calculateTileHash(kzIm.argb_buf); /* Get hash of tile */
        int tilex = ztx/step/2;
        int tiley = ty/step/2;
        String key = world.getName()+".z"+pd.zoomprefix+pd.baseprefix;
        if((!zf.exists()) || (crc != MapManager.mapman.hashman.getImageHashCode(key, null, tilex, tiley))) {
            try {
                if(!zf.getParentFile().exists())
                    zf.getParentFile().mkdirs();
                FileLockManager.imageIOWrite(zIm, "png", zf);
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
        FileLockManager.releaseWriteLock(zf);
        KzedMap.freeBufferedImage(kzIm);
    }
}
