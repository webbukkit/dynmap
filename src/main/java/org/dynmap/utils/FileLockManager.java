package org.dynmap.utils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.dynmap.Log;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.debug.Debug;
/**
 * Implements soft-locks for prevent concurrency issues with file updates
 */
public class FileLockManager {
    private static Object lock = new Object();
    private static HashMap<String, Integer> filelocks = new HashMap<String, Integer>();
    private static final Integer WRITELOCK = new Integer(-1);
    /**
     * Get write lock on file - exclusive lock, no other writers or readers
     * @throws InterruptedException
     */
    public static boolean getWriteLock(File f) {
        String fn = f.getPath();
        synchronized(lock) {
            boolean got_lock = false;
            while(!got_lock) {
                Integer lockcnt = filelocks.get(fn);    /* Get lock count */
                if(lockcnt != null) {   /* If any locks, can't get write lock */
                    try {
                        lock.wait(); 
                    } catch (InterruptedException ix) {
                        Log.severe("getWriteLock(" + fn + ") interrupted");
                        return false;
                    }
                }
                else {
                    filelocks.put(fn, WRITELOCK);
                    got_lock = true;
                }
            }
        }
        //Log.info("getWriteLock(" + f + ")");
        return true;
    }
    /**
     * Release write lock
     */
    public static void releaseWriteLock(File f) {
        String fn = f.getPath();
        synchronized(lock) {
            Integer lockcnt = filelocks.get(fn);    /* Get lock count */
            if(lockcnt == null)
                Log.severe("releaseWriteLock(" + fn + ") on unlocked file");
            else if(lockcnt.equals(WRITELOCK)) {
                filelocks.remove(fn);   /* Remove lock */
                lock.notifyAll();   /* Wake up folks waiting for locks */
            }
            else
                Log.severe("releaseWriteLock(" + fn + ") on read-locked file");
        }
        //Log.info("releaseWriteLock(" + f + ")");
    }
    /**
     * Get read lock on file - multiple readers allowed, blocks writers
     */
    public static boolean getReadLock(File f) {
        return getReadLock(f, -1);
    }
    /**
     * Get read lock on file - multiple readers allowed, blocks writers - with timeout (msec)
     */
    public static boolean getReadLock(File f, long timeout) {
        String fn = f.getPath();
        synchronized(lock) {
            boolean got_lock = false;
            long starttime = 0;
            if(timeout > 0)
                starttime = System.currentTimeMillis();
            while(!got_lock) {
                Integer lockcnt = filelocks.get(fn);    /* Get lock count */
                if(lockcnt == null) {
                    filelocks.put(fn, Integer.valueOf(1));  /* First lock */
                    got_lock = true;
                }
                else if(!lockcnt.equals(WRITELOCK)) {   /* Other read locks */
                    filelocks.put(fn, Integer.valueOf(lockcnt+1));
                    got_lock = true;
                }
                else {  /* Write lock in place */
                    try {
                        if(timeout < 0) {
                            lock.wait();
                        }
                        else {
                            long now = System.currentTimeMillis();
                            long elapsed = now-starttime; 
                            if(elapsed > timeout)   /* Give up on timeout */
                                return false;
                            lock.wait(timeout-elapsed);
                        }
                    } catch (InterruptedException ix) {
                        Log.severe("getReadLock(" + fn + ") interrupted");
                        return false;
                    }
                }
            }
        }        
        //Log.info("getReadLock(" + f + ")");
        return true;
    }
    /**
     * Release read lock
     */
    public static void releaseReadLock(File f) {
        String fn = f.getPath();
        synchronized(lock) {
            Integer lockcnt = filelocks.get(fn);    /* Get lock count */
            if(lockcnt == null)
                Log.severe("releaseReadLock(" + fn + ") on unlocked file");
            else if(lockcnt.equals(WRITELOCK))
                Log.severe("releaseReadLock(" + fn + ") on write-locked file");
            else if(lockcnt > 1) {
                filelocks.put(fn, Integer.valueOf(lockcnt-1));
            }
            else {
                filelocks.remove(fn);   /* Remove lock */
                lock.notifyAll();   /* Wake up folks waiting for locks */
            }
        }
        //Log.info("releaseReadLock(" + f + ")");
    }
    private static final int MAX_WRITE_RETRIES = 6;
    
    private static ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private static Object baos_lock = new Object();
    /**
     * Wrapper for IOImage.write - implements retries for busy files
     */
    public static void imageIOWrite(BufferedImage img, ImageFormat fmt, File fname) throws IOException {
        int retrycnt = 0;
        boolean done = false;
        byte[] rslt;
        synchronized(baos_lock) {
            baos.reset();
            ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
            if(fmt.getFileExt().equals("jpg")) {
                WritableRaster raster = img.getRaster();
                WritableRaster newRaster = raster.createWritableChild(0, 0, img.getWidth(),
                          img.getHeight(), 0, 0, new int[] {0, 1, 2});
                DirectColorModel cm = (DirectColorModel)img.getColorModel();
                DirectColorModel newCM = new DirectColorModel(cm.getPixelSize(),
                cm.getRedMask(), cm.getGreenMask(), cm.getBlueMask());
                // now create the new buffer that is used ot write the image:
                BufferedImage rgbBuffer = new BufferedImage(newCM, newRaster, false, null);
                
                // Find a jpeg writer
                ImageWriter writer = null;
                Iterator iter = ImageIO.getImageWritersByFormatName("jpg");
                if (iter.hasNext()) {
                    writer = (ImageWriter)iter.next();
                }
                if(writer == null) {
                    Log.severe("No JPEG ENCODER - Java VM does not support JPEG encoding");
                    return;
                }
                ImageWriteParam iwp = writer.getDefaultWriteParam();
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(fmt.getQuality());

                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);

                writer.write(null, new IIOImage(rgbBuffer, null, null), iwp);
                writer.dispose();

                rgbBuffer.flush();
            }
            else {
                ImageIO.write(img, fmt.getFileExt(), baos); /* Write to byte array stream - prevent bogus I/O errors */
            }
            rslt = baos.toByteArray();
        }
        while(!done) {
            RandomAccessFile f = null;
            try {
                f = new RandomAccessFile(fname, "rw");
                f.write(rslt);
                done = true;
            } catch (IOException fnfx) {
                if(retrycnt < MAX_WRITE_RETRIES) {
                    Debug.debug("Image file " + fname.getPath() + " - unable to write - retry #" + retrycnt);
                    try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { throw fnfx; }
                    retrycnt++;
                }
                else {
                    Log.info("Image file " + fname.getPath() + " - unable to write - failed");
                    throw fnfx;
                }
            } finally {
                if(f != null) {
                    try { f.close(); } catch (IOException iox) {}
                }
            }
        }
    }
}
