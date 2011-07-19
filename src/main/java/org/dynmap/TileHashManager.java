package org.dynmap;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.io.IOException;
import java.util.zip.CRC32;

import org.dynmap.utils.LRULinkedHashMap;

/**
 * Image hash code manager - used to reduce compression and notification of updated tiles that do not actually yield new content
 *
 */
public class TileHashManager {
    private File    tiledir;    /* Base tile directory */    
    private boolean enabled;
    
    /**
     * Each tile hash file is a 32x32 tile grid, with each file having a CRC32 hash code generated from its pre-compression frame buffer
     */
    private static class TileHashFile {
        final String key;
        final String subtype;
        final int x;  /* minimum tile coordinate / 32 */
        final int y;  /* minimum tile coordinate / 32 */
        private File hf;
        TileHashFile(String key, String subtype, int x, int y) {
            this.key = key;
            if(subtype != null)
                this.subtype = subtype;
            else
                this.subtype = "";
            this.x = x;
            this.y = y;
        }
        @Override
        public boolean equals(Object o) {
            if(!(o instanceof TileHashFile))
                return false;
            TileHashFile fo = (TileHashFile)o;
            return (x == fo.x) && (y == fo.y) && key.equals(fo.key) && (subtype.equals(fo.subtype)); 
        }
        @Override
        public int hashCode() {
            return key.hashCode() ^ subtype.hashCode() ^ (x << 16) ^ y;
        }
        
        public File getHashFile(File tiledir) {
            if(hf == null) {
                String k;
                int idx = key.indexOf('.'); /* Find first '.' - world name split */
                if(idx > 0)
                    k = key.substring(0, idx) + File.separatorChar + key.substring(idx+1);
                else
                    k = key;
                hf = new File(tiledir, k + (subtype.equals("")?"":("." + subtype)) + "_" + x + "_" + y + ".hash");
            }
            return hf;
        }
        /* Write to file */
        public void writeToFile(File tiledir, byte[] crcbuf) {
            try {
                RandomAccessFile fd = new RandomAccessFile(getHashFile(tiledir), "rw");
                fd.seek(0);
                fd.write(crcbuf);
                fd.close();
            } catch (IOException iox) {
                Log.severe("Error writing hash file - " + getHashFile(tiledir).getPath());
            }
        }
        
        /* Read from file */
        public void readFromFile(File tiledir, byte[] crcbuf) {
            try {
                RandomAccessFile fd = new RandomAccessFile(getHashFile(tiledir), "r");
                fd.seek(0);
                fd.read(crcbuf);
                fd.close();
            } catch (IOException iox) {
                Arrays.fill(crcbuf, (byte)0xFF);
                writeToFile(tiledir, crcbuf);
            }
        }
        /* Read CRC */
        public long getCRC(int tx, int ty, byte[] crcbuf) {
            int off = (128 * (ty & 0x1F)) + (4 * (tx & 0x1F));
            long crc = 0;
            for(int i = 0; i < 4; i++)
                crc = (crc << 8) + (0xFF & (int)crcbuf[off+i]);
            return crc;
        }
        /* Set CRC */
        public void setCRC(int tx, int ty, byte[] crcbuf, long crc) {
            int off = (128 * (ty & 0x1F)) + (4 * (tx & 0x1F));
            for(int i = 0; i < 4; i++)
                crcbuf[off+i] = (byte)((crc >> ((3-i)*8)) & 0xFF);
        }
    }
    
    private static final int MAX_CACHED_TILEHASHFILES = 25;
    private Object lock = new Object();
    private LRULinkedHashMap<TileHashFile, byte[]> tilehash = new LRULinkedHashMap<TileHashFile, byte[]>(MAX_CACHED_TILEHASHFILES);
    private CRC32 crc32 = new CRC32();
    private byte[] crcworkbuf = new byte[8192];
    
    public TileHashManager(File tileroot, boolean enabled) {
        tiledir = tileroot;
        this.enabled = enabled;
    }
    
    /* Read cached hashcode for given tile */
    public long getImageHashCode(String key, String subtype, int tx, int ty) {
        if(!enabled) {
            return -1;  /* Return value that never matches */
        }
        TileHashFile thf = new TileHashFile(key, subtype, tx >> 5, ty >> 5);
        synchronized(lock) {
            byte[] crcbuf = tilehash.get(thf);  /* See if we have it cached */
            if(crcbuf == null) {    /* If not in cache, load it */
                crcbuf = new byte[32*32*4]; /* Get our space */
                Arrays.fill(crcbuf, (byte)0xFF);    /* Fill with -1 */
                tilehash.put(thf, crcbuf);  /* Add to cache */
                thf.readFromFile(tiledir, crcbuf);
            }
            return thf.getCRC(tx & 0x1F, ty & 0x1F, crcbuf);
        }
    }
    /* Calculate hash code for given buffer */
    public long calculateTileHash(int[] newbuf) {
        if(!enabled) {
            return 0;   /* Return value that doesn't match */
        }
        synchronized(lock) {
            if(crcworkbuf.length < (4*newbuf.length)){
                crcworkbuf = new byte[4*newbuf.length];
            }
            for(int i = 0, off = 0; i < newbuf.length; i++) {
                int v = newbuf[i];
                crcworkbuf[off++] = (byte)v;
                crcworkbuf[off++] = (byte)(v>>8);
                crcworkbuf[off++] = (byte)(v>>16);
                crcworkbuf[off++] = (byte)(v>>24);
            }
            /* Calculate CRC-32 for buffer */
            crc32.reset();
            crc32.update(crcworkbuf, 0, 4*newbuf.length);
            return crc32.getValue();
        }
    }
    /* Update hashcode for given tile */
    public void updateHashCode(String key, String subtype, int tx, int ty, long newcrc) {
        if(!enabled)
            return;
        synchronized(lock) {
            /* Now, find and check existing value */
            TileHashFile thf = new TileHashFile(key, subtype, tx >> 5, ty >> 5);
            byte[] crcbuf = tilehash.get(thf);  /* See if we have it cached */
            if(crcbuf == null) {    /* If not in cache, load it */
                crcbuf = new byte[32*32*4]; /* Get our space */
                tilehash.put(thf, crcbuf);  /* Add to cache */
                thf.readFromFile(tiledir, crcbuf);
            }
            thf.setCRC(tx & 0x1F, ty & 0x1F, crcbuf, newcrc);   /* Update field */
            thf.writeToFile(tiledir, crcbuf);   /* And write it out */
        }
    }    
}
