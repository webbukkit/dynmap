package org.dynmap.bukkit;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.dynmap.Log;
import org.getspout.spoutapi.block.design.BlockDesign;
import org.getspout.spoutapi.material.CustomBlock;
import org.getspout.spoutapi.material.MaterialData;

/**
 * Handler for pulling spout-defined custom blocks into dynmap
 * 
 * Generates and maintains custom render data file (renderdata/spout-texture.txt) and
 * directory of downloaded image files (texturepacks/standard/spout/plugin.blockid.png)
 */
public class SpoutPluginBlocks {

    /* Process spout blocks - return true if something changed */
    public boolean processSpoutBlocks(File datadir) {
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
            File imgfile = new File(f, blkid + ".png");
            BufferedImage img = null;
            boolean urlloaded = false;
            String txname = bd.getTexureURL();
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
                    Log.severe("Error loading texture for custom block '" + blkid + "' (" + b.getCustomId() + ") from " + bd.getTexureURL() + "(" + iox.getMessage() + ")");
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
                    blks.add(b);
                    int w = img.getWidth();
                    int h = img.getHeight();
                    /* If width >= 6 times height, we're using custom for each side */
                    sb.append("texturefile:id=" + blkid + ",filename=spout/" + blkid + ".png,xcount=" + w/h + ",ycount=1\n");
                    if(w >= (6*h)) {
                        sb.append("block:id=" + b.getCustomId() + ",data=*,bottom=0,north=1,south=2,east=3,west=4,top=5,txtid=" + blkid + "\n");
                    }
                    else {
                        sb.append("block:id=" + b.getCustomId() + ",data=*,allfaces=0,txtid=" + blkid + "\n");
                    }
                    cnt++;
                } catch (IOException iox) {
                    Log.severe("Error writing " + blkid + ".png");
                } finally {
                    img.flush();
                }
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
