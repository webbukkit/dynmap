package org.dynmap;

import org.dynmap.MapType.ImageFormat;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapListenerManager.PlayerEventListener;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.debug.Debug;
import org.dynmap.storage.MapStorage;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.ImageIOManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * Listen for player logins, and process player faces by fetching skins *
 */
public class PlayerFaces {
    private boolean fetchskins;
    private boolean refreshskins;
    private String skinurl;
    public MapStorage storage;
    
    public enum FaceType {
        FACE_8X8("8x8", 0),
        FACE_16X16("16x16", 1),
        FACE_32X32("32x32", 2),
        BODY_32X32("body", 3);
        
        public final String id;
        public final int typeID;
        
        FaceType(String id, int typeid) {
            this.id = id;
            this.typeID = typeid;
        }
        public static FaceType byID(String i_d) {
            for (FaceType ft : values()) {
                if (ft.id.equals(i_d)) {
                    return ft;
                }
            }
            return null;
        }
        public static FaceType byTypeID(int tid) {
            for (FaceType ft : values()) {
                if (ft.typeID == tid) {
                    return ft;
                }
            }
            return null;
        }
    }
    
    private class LoadPlayerImages implements Runnable {
        public final String playername;
        public final String playerskinurl;
        public final UUID playeruuid;
        public LoadPlayerImages(String playername, String playerskinurl, UUID playeruuid) {
            this.playername = playername;
            this.playerskinurl = playerskinurl;
            this.playeruuid = playeruuid;
        }
        public void run() {
            boolean has_8x8 = storage.hasPlayerFaceImage(playername, FaceType.FACE_8X8);
            boolean has_16x16 = storage.hasPlayerFaceImage(playername, FaceType.FACE_16X16);
            boolean has_32x32 = storage.hasPlayerFaceImage(playername, FaceType.FACE_32X32);
            boolean has_body = storage.hasPlayerFaceImage(playername, FaceType.BODY_32X32);
            boolean missing_any = !(has_8x8 && has_16x16 && has_32x32 && has_body);
            
            BufferedImage img = null;
            try {
                if(fetchskins && (refreshskins || missing_any)) {
                	URL url = null;
                	if (skinurl.equals("") == false) {
                		url = new URL(skinurl.replace("%player%", URLEncoder.encode(playername, "UTF-8")));
                	}
                	else if (playerskinurl != null) {
                		url = new URL(playerskinurl);
                	}
                	if (url != null) {
                		img = ImageIO.read(url);    /* Load skin for player */
                	}
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
            if((img.getWidth() < 64) || (img.getHeight() < 32)) {
                img.flush();
                return;
            }
            int[] faceaccessory = new int[64];  /* 8x8 of face accessory */
            /* Get buffered image for face at 8x8 */
            DynmapBufferedImage face8x8 = DynmapBufferedImage.allocateBufferedImage(8, 8);
            img.getRGB(8, 8, 8, 8, face8x8.argb_buf, 0, 8); /* Read face from image */
            img.getRGB(40, 8, 8, 8, faceaccessory, 0, 8); /* Read face accessory from image */
            /* Apply accessory to face: see if anything is transparent (if so, apply accessory */
            boolean transp = false;
            int v = faceaccessory[0];
            for(int i = 0; i < 64; i++) {
            	if((faceaccessory[i] & 0xFF000000) == 0) {
            		transp = true;
            		break;
            	}
            	/* If any different values, render face too */
            	else if(faceaccessory[i] != v) {
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
                BufferOutputStream bos = ImageIOManager.imageIOEncode(face8x8.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.FACE_8X8, bos);
                }
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
                BufferOutputStream bos = ImageIOManager.imageIOEncode(face16x16.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.FACE_16X16, bos);
                }
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
                BufferOutputStream bos = ImageIOManager.imageIOEncode(face32x32.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.FACE_32X32, bos);
                }
                DynmapBufferedImage.freeBufferedImage(face32x32);
            }

            /* Write body file */
            if(refreshskins || (!has_body)) {
                /* Make 32x32 version */
                DynmapBufferedImage body32x32 = DynmapBufferedImage.allocateBufferedImage(32, 32);
                /* Copy face at 12,0 to 20,8 (already handled accessory) */
                for(int i = 0; i < 8; i++) {
                    for(int j = 0; j < 8; j++) {
                        body32x32.argb_buf[i*32+j+12] = face8x8.argb_buf[i*8 + j];
                    }
                }
                /* Copy body at 12,8 to 20,20 */
                img.getRGB(20, 20, 8, 12, body32x32.argb_buf, 8*32+12, 32); /* Read body from image */
                /* Copy legs at 12,20 to 16,32 and 16,20 to 20,32 */
                img.getRGB(4, 20, 4, 12, body32x32.argb_buf, 20*32+12, 32); /* Read right leg from image */
                img.getRGB(4, 20, 4, 12, body32x32.argb_buf, 20*32+16, 32); /* Read left leg from image */
                /* Copy arms at 8,8 to 12,20 and 20,8 to 24,20 */
                img.getRGB(44, 20, 4, 12, body32x32.argb_buf, 8*32+8, 32); /* Read right leg from image */
                img.getRGB(44, 20, 4, 12, body32x32.argb_buf, 8*32+20, 32); /* Read left leg from image */
                
                BufferOutputStream bos = ImageIOManager.imageIOEncode(body32x32.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.BODY_32X32, bos);
                }
                DynmapBufferedImage.freeBufferedImage(body32x32);
            }

            DynmapBufferedImage.freeBufferedImage(face8x8);
            img.flush();
            /* TODO: signal update for player icon to client */
        }
    }
    public PlayerFaces(DynmapCore core) {
        fetchskins = core.configuration.getBoolean("fetchskins", true);    /* Control whether to fetch skins */ 
        refreshskins = core.configuration.getBoolean("refreshskins", true);    /* Control whether to update existing fetched skins or faces */ 
        skinurl = core.configuration.getString("skin-url", "");
        // These don't work anymore - Mojang retired them
        if (skinurl.equals("http://s3.amazonaws.com/MinecraftSkins/%player%.png") ||
        		skinurl.equals("http://skins.minecraft.net/MinecraftSkins/%player%.png")) {
            skinurl = "";
        }
        core.listenerManager.addListener(EventType.PLAYER_JOIN, new PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                Runnable job = new LoadPlayerImages(p.getName(), p.getSkinURL(), p.getUUID());
                if(fetchskins)
                    MapManager.scheduleDelayedJob(job, 0);
                else
                    job.run();
            }
        });
        storage = core.getDefaultMapStorage();
    }
}
