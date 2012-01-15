package org.dynmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.ImageIO;

import org.dynmap.MapType.ImageFormat;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapListenerManager.PlayerEventListener;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.debug.Debug;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.FileLockManager;

/**
 * Listen for player logins, and process player faces by fetching skins *
 */
public class PlayerFaces {
    private File facesdir;
    private File faces8x8dir;
    private File faces16x16dir;
    private File faces32x32dir;
    private boolean fetchskins;
    private boolean refreshskins;
    
    private class LoadPlayerImages implements Runnable {
        public String playername;
        public LoadPlayerImages(String playername) {
            this.playername = playername;
        }
        public void run() {
            File img_8x8 = new File(faces8x8dir, playername + ".png");
            File img_16x16 = new File(faces16x16dir, playername + ".png");
            File img_32x32 = new File(faces32x32dir, playername + ".png");
            boolean has_8x8 = img_8x8.exists();
            boolean has_16x16 = img_16x16.exists();
            boolean has_32x32 = img_32x32.exists();
            boolean missing_any = !(has_8x8 && has_16x16 && has_32x32);
            
            BufferedImage img = null;
            try {
                if(fetchskins && (refreshskins || missing_any)) {
                    URL url = new URL("http://s3.amazonaws.com/MinecraftSkins/" + playername + ".png");
                    img = ImageIO.read(url);    /* Load skin for player */
                }
            } catch (IOException iox) {
                Debug.debug("Error loading skin for '" + playername + "' - " + iox);
            }
            if(img == null) {
                try {
                    InputStream in = getClass().getResourceAsStream("/char.png");
                    img = ImageIO.read(in);    /* Load generic skin for player */
                    in.close();
                } catch (IOException iox) {
                    Debug.debug("Error loading default skin for '" + playername + "' - " + iox);
                }
            }
            if(img == null) {   /* No image to process?  Quit */
                return;
            }
            int[] faceaccessory = new int[64];  /* 8x8 of face accessory */
            /* Get buffered image for face at 8x8 */
            DynmapBufferedImage face8x8 = DynmapBufferedImage.allocateBufferedImage(8, 8);
            img.getRGB(8, 8, 8, 8, face8x8.argb_buf, 0, 8); /* Read face from image */
            img.getRGB(40, 8, 8, 8, faceaccessory, 0, 8); /* Read face accessory from image */
            /* Apply accessory to face: see if anything is transparent (if so, apply accessory */
            boolean transp = false;
            for(int i = 0; i < 64; i++) {
            	if((faceaccessory[i] & 0xFF000000) == 0) {
            		transp = true;
            		break;
            	}
            }
            if(transp) {
                for(int i = 0; i < 64; i++) {
                	if((faceaccessory[i] & 0xFF000000) != 0)
                		face8x8.argb_buf[i] = faceaccessory[i];
                }
            }
            /* Write 8x8 file */
            if(refreshskins || (!has_8x8)) {
                FileLockManager.getWriteLock(img_8x8);
                try {
                    FileLockManager.imageIOWrite(face8x8.buf_img, ImageFormat.FORMAT_PNG, img_8x8);
                } catch (IOException iox) {
                    Log.severe("Cannot write player icon " + img_8x8.getPath());
                }
                FileLockManager.releaseWriteLock(img_8x8);
            }
            /* Write 16x16 file */
            if(refreshskins || (!has_16x16)) {
                /* Make 16x16 version */
                DynmapBufferedImage face16x16 = DynmapBufferedImage.allocateBufferedImage(16, 16);
                for(int i = 0; i < 16; i++) {
                    for(int j = 0; j < 16; j++) {
                        face16x16.argb_buf[i*16+j] = face8x8.argb_buf[(i/2)*8 + (j/2)];
                    }
                }
                FileLockManager.getWriteLock(img_16x16);
                try {
                    FileLockManager.imageIOWrite(face16x16.buf_img, ImageFormat.FORMAT_PNG, img_16x16);
                } catch (IOException iox) {
                    Log.severe("Cannot write player icon " + img_16x16.getPath());
                }
                FileLockManager.releaseWriteLock(img_16x16);
                DynmapBufferedImage.freeBufferedImage(face16x16);
            }

            /* Write 32x32 file */
            if(refreshskins || (!has_32x32)) {
                /* Make 32x32 version */
                DynmapBufferedImage face32x32 = DynmapBufferedImage.allocateBufferedImage(32, 32);
                for(int i = 0; i < 32; i++) {
                    for(int j = 0; j < 32; j++) {
                        face32x32.argb_buf[i*32+j] = face8x8.argb_buf[(i/4)*8 + (j/4)];
                    }
                }
                FileLockManager.getWriteLock(img_32x32);
                try {
                    FileLockManager.imageIOWrite(face32x32.buf_img, ImageFormat.FORMAT_PNG, img_32x32);
                } catch (IOException iox) {
                    Log.severe("Cannot write player icon " + img_32x32.getPath());
                }
                FileLockManager.releaseWriteLock(img_32x32);
                DynmapBufferedImage.freeBufferedImage(face32x32);
            }
            
            DynmapBufferedImage.freeBufferedImage(face8x8);
            /* TODO: signal update for player icon to client */
        }
    }
    public PlayerFaces(DynmapCore core) {
        fetchskins = core.configuration.getBoolean("fetchskins", true);    /* Control whether to fetch skins */ 
        refreshskins = core.configuration.getBoolean("refreshskins", true);    /* Control whether to update existing fetched skins or faces */ 

        core.listenerManager.addListener(EventType.PLAYER_JOIN, new PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                Runnable job = new LoadPlayerImages(p.getName());
                if(fetchskins)
                    MapManager.scheduleDelayedJob(job, 0);
                else
                    job.run();
            }
        });
        facesdir = new File(core.getTilesFolder(), "faces");
        
        facesdir.mkdirs();  /* Make sure directory exists */
        faces8x8dir = new File(facesdir, "8x8");
        faces8x8dir.mkdirs();
        faces16x16dir = new File(facesdir, "16x16");
        faces16x16dir.mkdirs();
        faces32x32dir = new File(facesdir, "32x32");
        faces32x32dir.mkdirs();
    }
}
