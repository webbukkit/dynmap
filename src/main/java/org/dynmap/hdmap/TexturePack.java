package org.dynmap.hdmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.dynmap.Color;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.kzedmap.KzedMap;

/**
 * Loader and processor class for minecraft texture packs
 *  Texture packs are found in dynmap/texturepacks directory, and either are either ZIP files
 *  or are directories whose content matches the structure of a zipped texture pack:
 *    ./terrain.png - main color data (required)
 *    misc/water.png - tone for water, biome sensitive (optional)
 *    misc/grasscolor.png - tone for grass color, biome sensitive (optional)
 *    misc/foliagecolor.png - tone for leaf color, biome sensitive (optional)
 *    BetterGlass/*.png - mod-based improved windows (future optional)
 */
public class TexturePack {
    /* Loaded texture packs */
    private static HashMap<String, TexturePack> packs = new HashMap<String, TexturePack>();
    
    private static final String TERRAIN_PNG = "terrain.png";
    private static final String GRASSCOLOR_PNG = "misc/grasscolor.png";
    private static final String FOLIAGECOLOR_PNG = "misc/foliagecolor.png";
    private static final String WATER_PNG = "misc/water.png";
    
    private int[]   terrain_argb;
    private int terrain_width, terrain_height;
    private int native_scale;

    private int[]   grasscolor_argb;
    private int grasscolor_width, grasscolor_height;

    private int[]   foliagecolor_argb;
    private int foliagecolor_width, foliagecolor_height;

    private int[]   water_argb;
    private int water_width, water_height;
    
    private HashMap<Integer, TexturePack> scaled_textures;
    
    /** Get or load texture pack */
    public static TexturePack getTexturePack(String tpname) {
        TexturePack tp = packs.get(tpname);
        if(tp != null)
            return tp;
        try {
            tp = new TexturePack(tpname);   /* Attempt to load pack */
            packs.put(tpname, tp);
            return tp;
        } catch (FileNotFoundException fnfx) {
            Log.severe("Error loading texture pack '" + tpname + "' - not found");
        }
        return null;
    }
    /**
     * Constructor for texture pack, by name
     */
    private TexturePack(String tpname) throws FileNotFoundException {
        ZipFile zf = null;
        File texturedir = getTexturePackDirectory();
        try {
            /* Try to open zip */
            zf = new ZipFile(new File(texturedir, tpname + ".zip"));
            /* Find and load terrain.png */
            ZipEntry ze = zf.getEntry(TERRAIN_PNG); /* Try to find terrain.png */
            if(ze == null) {
                throw new FileNotFoundException();
            }
            InputStream is = zf.getInputStream(ze); /* Get input stream for terrain.png */
            loadTerrainPNG(is);
            is.close();
            /* Try to find and load misc/grasscolor.png */
            ze = zf.getEntry(GRASSCOLOR_PNG);
            if(ze != null) {    /* Found it, so load it */
                is = zf.getInputStream(ze);
                loadGrassColorPNG(is);
                is.close();
            }
            /* Try to find and load misc/foliagecolor.png */
            ze = zf.getEntry(FOLIAGECOLOR_PNG);
            if(ze != null) {    /* Found it, so load it */
                is = zf.getInputStream(ze);
                loadFoliageColorPNG(is);
                is.close();
            }
            /* Try to find and load misc/water.png */
            ze = zf.getEntry(WATER_PNG);
            if(ze != null) {    /* Found it, so load it */
                is = zf.getInputStream(ze);
                loadWaterPNG(is);
                is.close();
            }
            zf.close();
            return;
        } catch (IOException iox) {
            if(zf != null) {
                try { zf.close(); } catch (IOException io) {}
            }
            /* No zip, or bad - try directory next */
        }
        /* Try loading terrain.png from directory of name */
        File f = null;
        FileInputStream fis = null;
        try {
            /* Open and load terrain.png */
            f = new File(texturedir, tpname + "/" + TERRAIN_PNG);
            fis = new FileInputStream(f);
            loadTerrainPNG(fis);
            fis.close();
            /* Check for misc/grasscolor.png */
            f = new File(texturedir, tpname + "/" + GRASSCOLOR_PNG);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadGrassColorPNG(fis);
                fis.close();
            }
            /* Check for misc/foliagecolor.png */
            f = new File(texturedir, tpname + "/" + FOLIAGECOLOR_PNG);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadFoliageColorPNG(fis);
                fis.close();
            }
            /* Check for misc/water.png */
            f = new File(texturedir, tpname + "/" + WATER_PNG);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadWaterPNG(fis);
                fis.close();
            }
        } catch (IOException iox) {
            if(fis != null) {
                try { fis.close(); } catch (IOException io) {}
            }
            throw new FileNotFoundException();
        }
    }
    /* Copy texture pack */
    private TexturePack(TexturePack tp) {
        this.terrain_argb = tp.terrain_argb;
        this.terrain_width = tp.terrain_width;
        this.terrain_height = tp.terrain_height;
        this.native_scale = tp.native_scale;

        this.grasscolor_argb = tp.grasscolor_argb;
        this.grasscolor_height = tp.grasscolor_height;
        this.grasscolor_width = tp.grasscolor_width;

        this.foliagecolor_argb = tp.foliagecolor_argb;
        this.foliagecolor_height = tp.foliagecolor_height;
        this.foliagecolor_width = tp.foliagecolor_width;
        
        this.water_argb = tp.water_argb;
        this.water_height = tp.water_height;
        this.water_width = tp.water_width;
    }
    
    /* Load terrain.png */
    private void loadTerrainPNG(InputStream is) throws IOException {
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        terrain_width = img.getWidth();
        terrain_height = img.getHeight();
        terrain_argb = new int[terrain_width * terrain_height];
        img.getRGB(0, 0, terrain_width, terrain_height, terrain_argb, 0, terrain_width);
        img.flush();
        native_scale = terrain_width / 16;
    }
    
    /* Load misc/grasscolor.png */
    private void loadGrassColorPNG(InputStream is) throws IOException {
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        grasscolor_width = img.getWidth();
        grasscolor_height = img.getHeight();
        grasscolor_argb = new int[grasscolor_width * grasscolor_height];
        img.getRGB(0, 0, grasscolor_width, grasscolor_height, grasscolor_argb, 0, grasscolor_width);
        img.flush();        
    }
    
    /* Load misc/foliagecolor.png */
    private void loadFoliageColorPNG(InputStream is) throws IOException {
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        foliagecolor_width = img.getWidth();
        foliagecolor_height = img.getHeight();
        foliagecolor_argb = new int[foliagecolor_width * foliagecolor_height];
        img.getRGB(0, 0, foliagecolor_width, foliagecolor_height, foliagecolor_argb, 0, foliagecolor_width);
        img.flush();        
    }
    
    /* Load misc/water.png */
    private void loadWaterPNG(InputStream is) throws IOException {
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        water_width = img.getWidth();
        water_height = img.getHeight();
        water_argb = new int[water_width * water_height];
        img.getRGB(0, 0, water_width, water_height, water_argb, 0, water_width);
        img.flush();        
    }
    
    /* Get texture pack directory */
    private static File getTexturePackDirectory() {
//      return new File(DynmapPlugin.dataDirectory, "texturepacks");
        return new File("texturepacks");
    }

    /**
     * Resample terrain pack for given scale, and return copy using that scale
     */
    public TexturePack resampleTexturePack(int scale) {
        if(scaled_textures == null) scaled_textures = new HashMap<Integer, TexturePack>();
        TexturePack stp = scaled_textures.get(scale);
        if(stp != null)
            return stp;
        stp = new TexturePack(this);    /* Make copy */
        /* Scale terrain.png, if needed */
        if(stp.native_scale != scale) {
            stp.native_scale = scale;
            stp.terrain_height = 16*scale;
            stp.terrain_width = 16*scale;
            stp.terrain_argb = new int[stp.terrain_height*stp.terrain_width];
            scaleTerrainPNG(stp);
        }
        /* Remember it */
        scaled_textures.put(scale, stp);
        return stp;
    }
    /**
     * Scale out terrain_argb into the terrain_argb of the provided destination, matching the scale of that destination
     * @param tp
     */
    private void scaleTerrainPNG(TexturePack tp) {
        /* Terrain.png is 16x16 array of images : process one at a time */
        for(int ty = 0; ty < 16; ty++) {
            for(int tx = 0; tx < 16; tx++) {
                int srcoff = ty*native_scale*terrain_width + tx*native_scale;
                int destoff = ty*tp.native_scale*tp.terrain_width + tx*tp.native_scale;
                scaleTerrainPNGSubImage(tp, srcoff, destoff);
            }
        }
    }
    private void scaleTerrainPNGSubImage(TexturePack tp, int srcoff, int destoff) {
        int nativeres = native_scale;        
        int res = tp.native_scale;
        Color c = new Color();
        /* If we're scaling larger source pixels into smaller pixels, each destination pixel
         * receives input from 1 or 2 source pixels on each axis
         */
        if(res > nativeres) {
            int weights[] = new int[res];
            int offsets[] = new int[res];
            /* LCM of resolutions is used as length of line (res * nativeres)
             * Each native block is (res) long, each scaled block is (nativeres) long
             * Each scaled block overlaps 1 or 2 native blocks: starting with native block 'offsets[]' with
             * 'weights[]' of its (res) width in the first, and the rest in the second
             */
            for(int v = 0, idx = 0; v < res*nativeres; v += nativeres, idx++) {
                offsets[idx] = (v/res); /* Get index of the first native block we draw from */
                if((v+nativeres-1)/res == offsets[idx]) {   /* If scaled block ends in same native block */
                    weights[idx] = nativeres;
                }
                else {  /* Else, see how much is in first one */
                    weights[idx] = (offsets[idx] + res) - v;
                }
            }
            /* Now, use weights and indices to fill in scaled map */
            for(int y = 0, off = 0; y < res; y++) {
                int ind_y = offsets[y];
                int wgt_y = weights[y];
                for(int x = 0; x < res; x++, off++) {
                    int ind_x = offsets[x];
                    int wgt_x = weights[x];
                    int accum_red = 0;
                    int accum_green = 0;
                    int accum_blue = 0;
                    int accum_alpha = 0;
                    for(int xx = 0; xx < 2; xx++) {
                        int wx = (xx==0)?wgt_x:(nativeres-wgt_x);
                        if(wx == 0) continue;
                        for(int yy = 0; yy < 2; yy++) {
                            int wy = (yy==0)?wgt_y:(nativeres-wgt_y);
                            if(wy == 0) continue;
                            /* Accumulate */
                            c.setARGB(terrain_argb[srcoff + (ind_y+yy)*terrain_width + ind_x + xx]);
                            accum_red += c.getRed() * wx * wy;
                            accum_green += c.getGreen() * wx * wy;
                            accum_blue += c.getBlue() * wx * wy;
                            accum_alpha += c.getAlpha() * wx * wy;
                        }
                    }
                    /* Generate weighted compnents into color */
                    c.setRGBA(accum_red / (nativeres*nativeres), accum_green / (nativeres*nativeres), 
                              accum_blue / (nativeres*nativeres), accum_alpha / (nativeres*nativeres));
                    tp.terrain_argb[destoff + (y*tp.terrain_width) + x] = c.getARGB();
                }
            }
        }
        else {  /* nativeres > res */
            int weights[] = new int[nativeres];
            int offsets[] = new int[nativeres];
            /* LCM of resolutions is used as length of line (res * nativeres)
             * Each native block is (res) long, each scaled block is (nativeres) long
             * Each native block overlaps 1 or 2 scaled blocks: starting with scaled block 'offsets[]' with
             * 'weights[]' of its (res) width in the first, and the rest in the second
             */
            for(int v = 0, idx = 0; v < res*nativeres; v += res, idx++) {
                offsets[idx] = (v/nativeres); /* Get index of the first scaled block we draw to */
                if((v+res-1)/nativeres == offsets[idx]) {   /* If native block ends in same scaled block */
                    weights[idx] = res;
                }
                else {  /* Else, see how much is in first one */
                    weights[idx] = (offsets[idx] + nativeres) - v;
                }
            }
            int accum_red[] = new int[res*res];
            int accum_green[] = new int[res*res];
            int accum_blue[] = new int[res*res];
            int accum_alpha[] = new int[res*res];
            
            /* Now, use weights and indices to fill in scaled map */
            for(int y = 0; y < nativeres; y++) {
                int ind_y = offsets[y];
                int wgt_y = weights[y];
                for(int x = 0; x < nativeres; x++) {
                    int ind_x = offsets[x];
                    int wgt_x = weights[x];
                    c.setARGB(terrain_argb[srcoff + (y*terrain_width) + x]);
                    for(int xx = 0; xx < 2; xx++) {
                        int wx = (xx==0)?wgt_x:(res-wgt_x);
                        if(wx == 0) continue;
                        for(int yy = 0; yy < 2; yy++) {
                            int wy = (yy==0)?wgt_y:(res-wgt_y);
                            if(wy == 0) continue;
                            accum_red[(ind_y+yy)*res + (ind_x+xx)] += c.getRed() * wx * wy;
                            accum_green[(ind_y+yy)*res + (ind_x+xx)] += c.getGreen() * wx * wy;
                            accum_blue[(ind_y+yy)*res + (ind_x+xx)] += c.getBlue() * wx * wy;
                            accum_alpha[(ind_y+yy)*res + (ind_x+xx)] += c.getAlpha() * wx * wy;
                        }
                    }
                }
            }
            /* Produce normalized scaled values */
            for(int y = 0; y < res; y++) {
                for(int x = 0; x < res; x++) {
                    int off = (y*res) + x;
                    c.setRGBA(accum_red[off]/(nativeres*nativeres), accum_green[off]/(nativeres*nativeres),
                          accum_blue[off]/(nativeres*nativeres), accum_alpha[off]/(nativeres*nativeres));
                    tp.terrain_argb[destoff + y*tp.terrain_width + x] = c.getARGB();
                }
            }
        }
    }
    public void saveTerrainPNG(File f) throws IOException {
        BufferedImage img = KzedMap.createBufferedImage(terrain_argb, terrain_width, terrain_height);
        ImageIO.write(img, "png", f);
    }
    public static void main(String[] args) {
        TexturePack tp = TexturePack.getTexturePack("test");
        TexturePack tp2 = tp.resampleTexturePack(4);
        try {
            tp2.saveTerrainPNG(new File("test_terrain_4.png"));
        } catch (IOException iox) {}
        tp2 = tp.resampleTexturePack(16);
        try {
            tp2.saveTerrainPNG(new File("test_terrain_16.png"));
        } catch (IOException iox) {}
        tp2 = tp.resampleTexturePack(24);
        try {
            tp2.saveTerrainPNG(new File("test_terrain_24.png"));
        } catch (IOException iox) {}
        tp2 = tp.resampleTexturePack(64);
        try {
            tp2.saveTerrainPNG(new File("test_terrain_64.png"));
        } catch (IOException iox) {}
        tp2 = tp.resampleTexturePack(1);
        try {
            tp2.saveTerrainPNG(new File("test_terrain_1.png"));
        } catch (IOException iox) {}
    }
}
