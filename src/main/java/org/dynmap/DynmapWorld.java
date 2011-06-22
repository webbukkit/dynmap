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

import javax.imageio.ImageIO;

public class DynmapWorld {
    public World world;
    public List<MapType> maps = new ArrayList<MapType>();
    public UpdateQueue updates = new UpdateQueue();
    public ConfigurationNode configuration;
    public List<Location> seedloc;
    public List<MapChunkCache.VisibilityLimit> visibility_limits;
    public MapChunkCache.HiddenChunkStyle hiddenchunkstyle;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean bigworld;    /* If true, deeper directory hierarchy */
    public int extrazoomoutlevels;  /* Number of additional zoom out levels to generate */
    
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

    public void freshenZoomOutFiles(File tilepath) {
        for(int i = 0; i < extrazoomoutlevels; i++) {
            freshenZoomOutFilesByLevel(tilepath, i);
        }
    }
    
    private static class PrefixData {
    	int stepsize;
    	int[] stepseq;
    	boolean neg_step_x;
    }
    
    public void freshenZoomOutFilesByLevel(File tilepath, int zoomlevel) {
        Debug.debug("freshenZoomOutFiles(" + tilepath.getPath() + "," + zoomlevel + ")");
        File worldpath = new File(tilepath, world.getName());   /* Make path to our world */
        if(worldpath.exists() == false) /* Quit if not found */
            return;
        HashMap<String, PrefixData> maptab = new HashMap<String, PrefixData>();
        /* Build table of file prefixes and step sizes */
        for(MapType mt : maps) {
        	PrefixData pd = new PrefixData();
            List<String> pfx = mt.baseZoomFilePrefixes();
            pd.stepsize = mt.baseZoomFileStepSize();
            if(pd.stepsize < 0) {
            	pd.stepsize = -pd.stepsize;
            	pd.neg_step_x = true;
            }
            pd.stepseq = mt.zoomFileStepSequence();
            for(String p : pfx) {
                maptab.put(p, pd);
            }
        }
        if(bigworld) {  /* If big world, next directories are map name specific */
            DirFilter df = new DirFilter();
            for(String pfx : maptab.keySet()) { /* Walk thrugh prefixes, as directories */
                File dname = new File(worldpath, pfx);
                /* Now, go through subdirectories under this one, and process them */
                String[] subdir = dname.list(df);
                for(String s : subdir) {
                    File sdname = new File(dname, s);
                    /* Each middle tier directory is redundant - just go through them */
                    String[] ssubdir = sdname.list(df);
                    for(String ss : ssubdir) {
                        File ssdname = new File(sdname, ss);
                        processZoomDirectory(ssdname, maptab.get(pfx), "", zoomlevel);
                    }
                }
            }
        }
        else {  /* Else, classic file layout */
            for(String pfx : maptab.keySet()) { /* Walk thrugh prefixes, as directories */
                processZoomDirectory(worldpath, maptab.get(pfx), pfx + "_", zoomlevel);
            }
        }
    }
    
    
    private static class ProcessTileRec {
        File zf;
        int x, y;
    }
    private void processZoomDirectory(File dir, PrefixData pd, String prefix, int zoomlevel) {
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.stepsize + "," + prefix + "," + zoomlevel + ")");
        String zoomprefix = "zzzzzzzzzzzzzzzzzzzz".substring(0, zoomlevel);
        HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
        if(prefix.equals("")) {
            if(zoomlevel > 0)
                prefix = zoomprefix + "_";
        }
        else
            prefix = zoomprefix + prefix;
        int step = pd.stepsize << zoomlevel;
        zoomlevel++;
        String[] files = dir.list(new PNGFileFilter(prefix));
        for(String fn : files) {
            /* Build file object */
            File f = new File(dir, fn);
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
                continue;
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
            File zf;
            if(prefix.equals(""))
                zf = new File(dir, "z_" + x + "_" + y + ".png");
            else
                zf = new File(dir, "z" + prefix + x + "_" + y + ".png");
            /* If zoom file exists and is older than our file, nothing to do */
            if(zf.exists() && (zf.lastModified() >= f.lastModified())) {
                continue;
            }
            String zfpath = zf.getPath();
            if(!toprocess.containsKey(zfpath))  {
                ProcessTileRec rec = new ProcessTileRec();
                rec.zf = zf;
                rec.x = x;
                rec.y = y;
                toprocess.put(zfpath, rec);
            }
        }
        /* Do processing */
        for(ProcessTileRec s : toprocess.values()) {
            processZoomTile(dir, s.zf, s.x - (pd.neg_step_x?step:0), s.y, step, prefix, pd.stepseq);
        }
    }
    private void processZoomTile(File dir, File zf, int tx, int ty, int stepsize, String prefix, int[] stepseq) {
        Debug.debug("processZoomFile(" + dir.getPath() + "," + zf.getPath() + "," + tx + "," + ty + "," + stepsize + "," + prefix);
        int width = 128, height = 128;
        BufferedImage zIm = null;
        KzedBufferedImage kzIm = null;
        int[] argb = new int[width*height];

        /* create image buffer */
        kzIm = KzedMap.allocateBufferedImage(width, height);
        zIm = kzIm.buf_img;

        for(int i = 0; i < 4; i++) {
            File f = new File(dir, prefix + (tx + stepsize*(1&stepseq[i])) + "_" + (ty + stepsize*(stepseq[i]>>1)) + ".png");
            if(f.exists()) {
                BufferedImage im = null;
            	FileLockManager.getReadLock(f);
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
        try {
            FileLockManager.imageIOWrite(zIm, "png", zf);
            Debug.debug("Saved zoom-out tile at " + zf.getName());
        } catch (IOException e) {
            Debug.error("Failed to save zoom-out tile: " + zf.getName(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save zoom-out tile (NullPointerException): " + zf.getName(), e);
        }
        FileLockManager.releaseWriteLock(zf);
        KzedMap.freeBufferedImage(kzIm);
    }
}
