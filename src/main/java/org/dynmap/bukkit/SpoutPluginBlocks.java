package org.dynmap.bukkit;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.dynmap.Log;
import org.getspout.spoutapi.block.design.BlockDesign;
import org.getspout.spoutapi.block.design.GenericBlockDesign;
import org.getspout.spoutapi.block.design.GenericCuboidBlockDesign;
import org.getspout.spoutapi.block.design.Texture;
import org.getspout.spoutapi.material.CustomBlock;
import org.getspout.spoutapi.material.MaterialData;

/**
 * Handler for pulling spout-defined custom blocks into dynmap
 * 
 * Generates and maintains custom render data file (renderdata/spout-texture.txt) and
 * directory of downloaded image files (texturepacks/standard/spout/plugin.blockid.png)
 */
public class SpoutPluginBlocks {
    private Field textXPosField;    /* float[][] textXPos */
    private Field textYPosField;    /* float[][] textYPos */

    private boolean initSpoutAccess() {
        boolean success = false;
        try {
            textXPosField = GenericBlockDesign.class.getDeclaredField("textXPos");
            textXPosField.setAccessible(true);
            textYPosField = GenericBlockDesign.class.getDeclaredField("textYPos");
            textYPosField.setAccessible(true);
            success = true;
        } catch (NoSuchFieldException nsfx) {
            Log.severe("Cannot access needed Spout custom block fields!");
            Log.severe(nsfx);
        }
        return success;
    }
    private void addDefaultBlock(StringBuilder sb, CustomBlock blk) {
        if(blk.isOpaque())
            sb.append("block:id=" + blk.getCustomId() + ",data=*,allfaces=1\n");
        else
            sb.append("block:id=" + blk.getCustomId() + ",allfaces=12049,transparency=TRANSPARENT\n");
    }
    
    /* Process spout blocks - return true if something changed */
    public boolean processSpoutBlocks(File datadir) {
        if(textYPosField == null) {
            if(initSpoutAccess() == false)
                return false;
        }
        HashMap<String, String> texturelist = new HashMap<String, String>();
        
        int cnt = 0;
        File f = new File(datadir, "texturepacks/standard/spout");
        if(f.exists() == false)
            f.mkdirs();
        List<CustomBlock> blks = new ArrayList<CustomBlock>();
        CustomBlock[] cb = MaterialData.getCustomBlocks();
        /* Build new texture file as string */
        StringBuilder sb = new StringBuilder();
        /* Loop through blocks - try to freshen files, if needed */
        for(CustomBlock b : cb) {
            BlockDesign bd = b.getBlockDesign();
            String blkid = bd.getTexturePlugin() + "." + b.getName();
            blkid = blkid.replace(' ', '_');
            /* If not GenericCubiodBlockDesign, we don't handle it */
            if((bd instanceof GenericCuboidBlockDesign) == false) {
                Log.info("Block " + blkid + " not suppored - only cubiod blocks");
                addDefaultBlock(sb, b);
                continue;
            }
            /* Get texture info */
            Texture txt = bd.getTexture();
            int w = txt.getWidth();
            int h = txt.getHeight();
            int sz = txt.getSpriteSize();
            GenericCuboidBlockDesign gbd = (GenericCuboidBlockDesign)bd;
            int[] txtidx = new int[6];
            /* Fetch quad fields - figure out which texture for which side */
            try {
                float[][] textXPos = (float[][])textXPosField.get(gbd);
                float[][] textYPos = (float[][])textYPosField.get(gbd);
                /* Quads on cuboid are bottom, west, south, east, north, top */
                for(int i = 0; i < 6; i++) {
                    float minx = 1.0F;
                    float miny = 1.0F;
                    for(int j = 0; j < 4; j++) {
                        minx = Math.min(minx, textXPos[i][j]);
                        miny = Math.min(miny, textYPos[i][j]);
                    }
                    txtidx[i] = (int)((minx * w)/sz) + (w/sz)*(int)((miny * h)/sz); 
                }
            } catch (Exception iax) {
                addDefaultBlock(sb, b);
                continue;
            }
            String txname = bd.getTexureURL();
            
            String txtid = texturelist.get(txname);    /* Get texture */
            if(txtid == null) { /* Not found yet */
                File imgfile = new File(f, blkid + ".png");
                BufferedImage img = null;
                boolean urlloaded = false;
                try {
                    URL url = new URL(txname);
                    img = ImageIO.read(url);    /* Load skin for player */
                    urlloaded = true;
                } catch (IOException iox) {
                    if(txname.startsWith("http") == false) {   /* Not URL - try file */
                        File tf = new File(txname);
                        if(tf.exists() == false) {
                            /* Horrible hack - try to find temp file (some SpoutMaterials versions) */
                            try {
                                File tmpf = File.createTempFile("dynmap", "test");
                        
                                tf = new File(tmpf.getParent(), txname);
                                tmpf.delete();
                            } catch (IOException iox2) {}
                        }
                        if(tf.exists()) {
                            try {
                                img = ImageIO.read(tf);
                                urlloaded = true;
                            } catch (IOException iox3) {
                            }
                        }
                    }
                    if(img == null) {
                        Log.severe("Error loading texture for custom block '" + blkid + "' (" + b.getCustomId() + ") from " + txname + "(" + iox.getMessage() + ")");
                        if(imgfile.exists()) {
                            try {
                                img = ImageIO.read(imgfile);    /* Load existing */
                                Log.info("Loaded cached texture file for " + blkid);
                            } catch (IOException iox2) {
                                Log.severe("Error loading cached texture file for " + blkid + " - " + iox2.getMessage());
                            }
                        }
                    }
                }
                if(img != null) {
                    try {
                        if(urlloaded)
                            ImageIO.write(img, "png", imgfile);
                    } catch (IOException iox) {
                        Log.severe("Error writing " + blkid + ".png");
                    } finally {
                        img.flush();
                    }
                    String tfid = "txtid" + texturelist.size();
                    sb.append("texturefile:id=" + tfid + ",filename=spout/" + blkid + ".png,xcount=" + w/sz + ",ycount=" + h/sz + "\n");                                   
                    texturelist.put(txname, tfid);
                }
            }
            String txfileid = texturelist.get(txname);
            if(txfileid != null) {
                blks.add(b);
                    
                sb.append("block:id=" + b.getCustomId() + ",data=*,bottom=" + txtidx[0] + ",west=" +txtidx[1] + ",south=" + txtidx[2] + ",east=" + txtidx[3] + ",north="+txtidx[4]+",top="+txtidx[5]);
                if(b.getBlockId() == Material.GLASS.getId())
                    sb.append(",transparency=TRANSPARENT");
                sb.append(",txtid=" + txfileid + "\n");
                cnt++;
            }
        }
        String rslt = sb.toString();
        /* Now, generate spout texture file - see if changed */
        File st = new File(datadir, "renderdata/spout-texture.txt");
        if(st.exists()) {
            FileReader fr = null;
            StringBuilder sbold = new StringBuilder();
            try {
                fr = new FileReader(st);
                int len;
                char[] buf = new char[512];
                while((len = fr.read(buf)) > 0) {
                    sbold.append(buf, 0, len);
                }
            } catch (IOException iox) {
            } finally {
                if(fr != null) { try { fr.close(); } catch (IOException iox) {} }
            }
            /* If same, no changes */
            if(sbold.equals(rslt)) {
                Log.info("Loaded " + cnt + " Spout custom blocks");
                return false;
            }
        }
        FileWriter fw = null;
        try {
            fw = new FileWriter(st);
            fw.write(rslt);
        } catch (IOException iox) {
            Log.severe("Error opening spout texture file - " + st.getPath());
            return false;
        } finally {
            if(fw != null) { try { fw.close(); } catch (IOException iox) {} }
        }
        Log.info("Loaded " + cnt + " Spout custom blocks");
        return false;
    }
}
