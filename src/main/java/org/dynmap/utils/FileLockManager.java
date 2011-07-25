package org.dynmap.utils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.dynmap.Log;
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
    public static void imageIOWrite(BufferedImage img, String type, File fname) throws IOException {
        int retrycnt = 0;
        boolean done = false;
        byte[] rslt;
        synchronized(baos_lock) {
            baos.reset();
            ImageIO.write(img, type, baos); /* Write to byte array stream - prevent bogus I/O errors */
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
