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
import java.awt.*;
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
    
    private void copyLayersToTarget(BufferedImage srcimg, int layer1x, int layer1y, int layer2x, int layer2y, int w, int h, int[] dest, int destoff, int destscansize)
    {
    	int[] l1 = new int[w * h];
    	int[] l2 = new int[w * h];
    	int imgh = srcimg.getHeight();
    	// Read layer 1
    	if (imgh >= (layer1y+h))
    		srcimg.getRGB(layer1x, layer1y, w, h, l1, 0, w);
        // Read layer 2
    	if (imgh >= (layer2y+h))
    		srcimg.getRGB(layer2x, layer2y, w, h, l2, 0, w);
        // Apply layer1 to layer 1
        boolean transp = false;
        int v = l2[0];
        for (int i = 0; i < (w*h); i++) {
        	if ((l2[i] & 0xFF000000) == 0) {
        		transp = true;
        		break;
        	}
        	/* If any different values, render face too */
        	else if (l2[i] != v) {
        	    transp = true;
        	    break;
        	}
        }
        if(transp) {
            for (int i = 0; i < (w*h); i++) {
            	if ((l2[i] & 0xFF000000) != 0)
            		l1[i] = l2[i];
            }
        }
    	// Write to dest
        for (int y = 0; y < h; y++) {
        	for (int x = 0; x < w; x++) {
        		dest[destoff + (y*destscansize + x)] = l1[(y*w)+x];
        	}
        }
    }

    private void copyLayersToTarget(BufferedImage srcimg, int layer1x, int layer1y, int layer2x, int layer2y, int w, int h, BufferedImage dest, int destoff, int destscansize)
    {
        int[] tmp = new int[w*h];
        copyLayersToTarget(srcimg,layer1x,layer1y,layer2x,layer2y,w,h,tmp,0,w);
        dest.setRGB(0,0,w,h,tmp,destoff,destscansize);
    }
    
    private class LoadPlayerImages implements Runnable {
        private SkinUrlProvider mSkinUrlProvider;
        public final String playername;
        public final String playerskinurl;

        public LoadPlayerImages(String playername, String playerskinurl, UUID playeruuid, SkinUrlProvider skinUrlProvider) {
            this.playername = playername;
            this.playerskinurl = playerskinurl;
            mSkinUrlProvider = skinUrlProvider;
        }

        public void run() {
            boolean has_8x8 = storage.hasPlayerFaceImage(playername, FaceType.FACE_8X8);
            boolean has_16x16 = storage.hasPlayerFaceImage(playername, FaceType.FACE_16X16);
            boolean has_32x32 = storage.hasPlayerFaceImage(playername, FaceType.FACE_32X32);
            boolean has_body = storage.hasPlayerFaceImage(playername, FaceType.BODY_32X32);
            boolean missing_any = !(has_8x8 && has_16x16 && has_32x32 && has_body);
            boolean is_64x32_skin = false;
            
            BufferedImage img = null;
            try {
                if(fetchskins && (refreshskins || missing_any)) {
                    URL url = null;

                    if (mSkinUrlProvider == null) {
                        if (!skinurl.equals("")) {
                            url = new URL(skinurl.replace("%player%", URLEncoder.encode(playername, "UTF-8")));
                        } else if (playerskinurl != null) {
                            url = new URL(playerskinurl);
                        }
                    } else {
                        url = mSkinUrlProvider.getSkinUrl(playername);
                    }

                    if (url != null)
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
            if((img.getWidth() < 64) || (img.getHeight() < 32)) {
                img.flush();
                return;
            }
            else if( (img.getWidth() / img.getHeight()) == 2 ) { /* Is single layer skin? */
                is_64x32_skin = true;
            }

            /* Get buffered image for face at original size */
            int scale = img.getWidth()/8 /8;
            BufferedImage faceOriginal = new BufferedImage(8*scale, 8*scale, BufferedImage.TYPE_INT_ARGB);
            // Copy face and overlay to icon
            copyLayersToTarget(img, 8*scale, 8*scale, 40*scale, 8*scale, 8*scale, 8*scale, faceOriginal, 0, 8*scale);

            int[] faceaccessory = new int[64];  /* 8x8 of face accessory */
            /* Get buffered image for face at 8x8 */
            DynmapBufferedImage face8x8 = DynmapBufferedImage.allocateBufferedImage(8, 8);
            Image face8x8_image =  faceOriginal.getScaledInstance(8,8,BufferedImage.SCALE_SMOOTH);
            BufferedImage face8x8_buff = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);

            face8x8_buff.getGraphics().drawImage(face8x8_image,0,0,null);
            face8x8_buff.getRGB(0,0,8,8,face8x8.argb_buf,0,8);
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
                Image face16x16_image =  faceOriginal.getScaledInstance(16,16,BufferedImage.SCALE_SMOOTH);
                BufferedImage face16x16_buff = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

                face16x16_buff.getGraphics().drawImage(face16x16_image,0,0,null);
                face16x16_buff.getRGB(0,0,16,16,face16x16.argb_buf,0,16);

                BufferOutputStream bos = ImageIOManager.imageIOEncode(face16x16.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.FACE_16X16, bos);
                }
                DynmapBufferedImage.freeBufferedImage(face16x16);
                face16x16_buff.flush();
            }

            /* Write 32x32 file */
            if(refreshskins || (!has_32x32)) {
                /* Make 32x32 version */
                DynmapBufferedImage face32x32 = DynmapBufferedImage.allocateBufferedImage(32, 32);
                Image face32x32_image =  faceOriginal.getScaledInstance(32,32,BufferedImage.SCALE_SMOOTH);
                BufferedImage face32x32_buff = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

                face32x32_buff.getGraphics().drawImage(face32x32_image,0,0,null);
                face32x32_buff.getRGB(0,0,32,32,face32x32.argb_buf,0,32);

                BufferOutputStream bos = ImageIOManager.imageIOEncode(face32x32.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.FACE_32X32, bos);
                }
                DynmapBufferedImage.freeBufferedImage(face32x32);
                face32x32_buff.flush();
            }

            /* Write body file */
            if(refreshskins || (!has_body)) {

                Image skin_image = null;
                BufferedImage skin_buff = null;

                if (is_64x32_skin){

                    skin_image =  img.getScaledInstance(64,32,BufferedImage.SCALE_SMOOTH);
                    skin_buff = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);

                    skin_buff.getGraphics().drawImage(skin_image,0,0,null);

                } else {

                    skin_image =  img.getScaledInstance(64,64,BufferedImage.SCALE_SMOOTH);
                    skin_buff = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

                    skin_buff.getGraphics().drawImage(skin_image,0,0,null);

                }

                /* Make 32x32 version */
                DynmapBufferedImage body32x32 = DynmapBufferedImage.allocateBufferedImage(32, 32);
                /* Copy face at 12,0 to 20,8 (already handled accessory) */
                for(int i = 0; i < 8; i++) {
                    for(int j = 0; j < 8; j++) {
                        body32x32.argb_buf[i*32+j+12] = face8x8.argb_buf[i*8 + j];
                    }
                }
                /* Copy body at 20,20 and chest at 20,36 to 8,12 */
                copyLayersToTarget(skin_buff, 20, 20, 20, 36, 8, 12, body32x32.argb_buf, 8*32+12, 32);
                /* Copy right leg at 4,20 and 4,36 to 20,12 */
                copyLayersToTarget(skin_buff, 4, 20, 4, 36, 4, 12, body32x32.argb_buf, 20*32+12, 32);
                /* Copy left leg at 4,20 if old format or 20,52 and 4,53 to 20,16 */
                if(is_64x32_skin) {
                    skin_buff.getRGB(4, 20, 4, 12, body32x32.argb_buf, 20*32+16, 32);
                }
                else {
                    copyLayersToTarget(skin_buff, 20, 52, 4, 52, 4, 12, body32x32.argb_buf, 20 * 32 + 16, 32);
                }
                /* Copy right arm at 44,20 and 44,36 to 8,8 */
                copyLayersToTarget(skin_buff, 44, 20, 44, 36, 4, 12, body32x32.argb_buf, 8*32+8, 32);
                /* Copy left arm at 44,20 if old format or 36,52 and 52,52 to 8,20 */
                if(is_64x32_skin) {
                    skin_buff.getRGB(44, 20, 4, 12, body32x32.argb_buf, 8*32+20, 32);
                }
                else {
                    copyLayersToTarget(skin_buff, 36, 52, 52, 52, 4, 12, body32x32.argb_buf, 8 * 32 + 20, 32);
                }
                
                BufferOutputStream bos = ImageIOManager.imageIOEncode(body32x32.buf_img, ImageFormat.FORMAT_PNG);
                if (bos != null) {
                    storage.setPlayerFaceImage(playername, FaceType.BODY_32X32, bos);
                }

                DynmapBufferedImage.freeBufferedImage(body32x32);
                skin_buff.flush();
            }

            DynmapBufferedImage.freeBufferedImage(face8x8);
            face8x8_buff.flush();
            faceOriginal.flush();
            img.flush();
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
                Runnable job = new LoadPlayerImages(p.getName(), p.getSkinURL(), p.getUUID(), core.skinUrlProvider);
                MapManager.scheduleDelayedJob(job, 0);
            }
        });
        storage = core.getDefaultMapStorage();
    }
}
