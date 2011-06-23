package org.dynmap.utils;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.dynmap.Log;
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
    public static void getWriteLock(File f) {
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
                    }
                }
                else {
                    filelocks.put(fn, WRITELOCK);
                    got_lock = true;
                }
            }
        }
        //Log.info("getWriteLock(" + f + ")");
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
    public static void getReadLock(File f) {
        String fn = f.getPath();
        synchronized(lock) {
            boolean got_lock = false;
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
                        lock.wait();
                    } catch (InterruptedException ix) {
                        Log.severe("getReadLock(" + fn + ") interrupted");
                    }
                }
            }
        }        
        //Log.info("getReadLock(" + f + ")");
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
    /**
     * Wrapper for IOImage.write - implements retries for busy files
     */
    public static void imageIOWrite(BufferedImage img, String type, File fname) throws IOException {
        int retrycnt = 0;
        boolean done = false;
        
        while(!done) {
            try {
                ImageIO.write(img, type, fname);
                done = true;
            } catch (IOException fnfx) {
                if(retrycnt < MAX_WRITE_RETRIES) {
                    Log.info("Image file " + fname.getPath() + " - unable to write - retry #" + retrycnt);
                    try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { throw fnfx; }
                    retrycnt++;
                }
                else {
                    Log.info("Image file " + fname.getPath() + " - unable to write - failed");
                    throw fnfx;
                }
            }
        }
    }
}
