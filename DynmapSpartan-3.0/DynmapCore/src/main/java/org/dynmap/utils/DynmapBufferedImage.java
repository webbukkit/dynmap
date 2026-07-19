package org.dynmap.utils;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class DynmapBufferedImage {
    public BufferedImage buf_img;
    public int[] argb_buf;
    public int width;
    public int height;
    
    /* BufferedImage cache - we use the same things a lot... */
    private static Object lock = new Object();
    private static HashMap<Long, LinkedList<DynmapBufferedImage>> imgcache = 
        new HashMap<Long, LinkedList<DynmapBufferedImage>>(); /* Indexed by resolution - X<<32+Y */
    private static final int CACHE_LIMIT = 10;

    /**
     * Allocate buffered image from pool, if possible
     * @param x - x dimension
     * @param y - y dimension
     * @return buffer from pool
     */
    public static DynmapBufferedImage allocateBufferedImage(int x, int y) {
        DynmapBufferedImage img = null;
        synchronized(lock) {
            long k = (x<<16) + y;
            LinkedList<DynmapBufferedImage> ll = imgcache.get(k);
            if(ll != null) {
                img = ll.poll();
            }
        }
        if(img != null) {   /* Got it - reset it for use */
            Arrays.fill(img.argb_buf, 0);
        }
        else {
            img = new DynmapBufferedImage();
            img.width = x;
            img.height = y;
            img.argb_buf = new int[x*y];
        }
        img.buf_img = createBufferedImage(img.argb_buf, img.width, img.height);
        return img;
    }
    
    /**
     * Return buffered image to pool
     * @param img - image to return to pool
     */
    public static void freeBufferedImage(DynmapBufferedImage img) {
        img.buf_img.flush();
        img.buf_img = null; /* Toss bufferedimage - seems to hold on to other memory */
        synchronized(lock) {
            long k = (img.width<<16) + img.height;
            LinkedList<DynmapBufferedImage> ll = imgcache.get(k);
            if(ll == null) {
                ll = new LinkedList<DynmapBufferedImage>();
                imgcache.put(k, ll);
            }
            if(ll.size() < CACHE_LIMIT) {
                ll.add(img);
                img = null;
            }
        }
    }    
    
    /* ARGB band masks */
    private static final int [] band_masks = {0xFF0000, 0xFF00, 0xff, 0xff000000};

    /**
     * Build BufferedImage from provided ARGB array and dimensions
     * @param argb_buf - ARGB buffer
     * @param w - width
     * @param h - height
     * @return image
     */
    public static BufferedImage createBufferedImage(int[] argb_buf, int w, int h) {
        /* Create integer-base data buffer */
        DataBuffer db = new DataBufferInt (argb_buf, w*h);
        /* Create writable raster */
        WritableRaster raster = Raster.createPackedRaster(db, w, h, w, band_masks, null);
        /* RGB color model */
        ColorModel color_model = ColorModel.getRGBdefault ();
        /* Return buffered image */
        return new BufferedImage (color_model, raster, false, null);
    }
}