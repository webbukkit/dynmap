package org.dynmap.hdmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.dynmap.Color;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.hdmap.HDPerspectiveState.BlockStep;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.utils.MapIterator;

/**
 * Loader and processor class for minecraft texture packs
 *  Texture packs are found in dynmap/texturepacks directory, and either are either ZIP files
 *  or are directories whose content matches the structure of a zipped texture pack:
 *    ./terrain.png - main color data (required)
 *    misc/water.png - still water tile (required))
 *    misc/grasscolor.png - tone for grass color, biome sensitive (required)
 *    misc/foliagecolor.png - tone for leaf color, biome sensitive (required)
 *    misc/watercolor.png - tone for water color, biome sensitive (required)
 *    custom_lava_still.png - custom still lava animation (optional)
 *    custom_lava_flowing.png - custom flowing lava animation (optional)
 *    custom_water_still.png - custom still water animation (optional)
 *    custom_water_flowing.png - custom flowing water animation (optional)
 *    BetterGlass/*.png - mod-based improved windows (future optional)
 */
public class TexturePack {
    /* Loaded texture packs */
    private static HashMap<String, TexturePack> packs = new HashMap<String, TexturePack>();
    
    private static final String TERRAIN_PNG = "terrain.png";
    private static final String GRASSCOLOR_PNG = "misc/grasscolor.png";
    private static final String FOLIAGECOLOR_PNG = "misc/foliagecolor.png";
    private static final String WATERCOLOR_PNG = "misc/watercolor.png";
    private static final String WATER_PNG = "misc/water.png";

    /* Color modifier codes (x1000 for value in mapping code) */
    private static final short COLORMOD_GRASSTONED = 1;
    private static final short COLORMOD_FOLIAGETONED = 2;
    private static final short COLORMOD_WATERTONED = 3;
    /* Special tile index values */
    private static final short BLOCKINDEX_BLANK = -1;
    private static final short BLOCKINDEX_STATIONARYWATER = 257;
    private static final short BLOCKINDEX_MOVINGWATER = 258;
    private static final short BLOCKINDEX_STATIONARYLAVA = 259;
    private static final short BLOCKINDEX_MOVINGLAVA = 260;
    private static final int MAX_BLOCKINDEX = 260;
    private static final int BLOCKTABLELEN = MAX_BLOCKINDEX+1;
        
    private int[][]   terrain_argb;
    private int terrain_width, terrain_height;
    private int native_scale;

    private int[]   grasscolor_argb;
    private int grasscolor_width, grasscolor_height;
    private int trivial_grasscolor;

    private int[]   foliagecolor_argb;
    private int foliagecolor_width, foliagecolor_height;
    private int trivial_foliagecolor;

    private int[]   watercolor_argb;
    private int watercolor_width, watercolor_height;
    private int trivial_watercolor;

    private int[]   water_argb;
    private int water_width, water_height;
    
    private HashMap<Integer, TexturePack> scaled_textures;
    
    
    public static class HDTextureMap {
        private short faces[];  /* index in terrain.png of image for each face (indexed by BlockStep.ordinal()) */
        private List<Integer> blockids;
        private int databits;
        private static HDTextureMap[] texmaps;
        
        private static void initializeTable() {
            texmaps = new HDTextureMap[16*BLOCKTABLELEN];
            HDTextureMap blank = new HDTextureMap();
            for(int i = 0; i < texmaps.length; i++)
                texmaps[i] = blank;
        }
        
        private HDTextureMap() {
            blockids = Collections.singletonList(Integer.valueOf(0));
            databits = 0xFFFF;
            faces = new short[] { -1, -1, -1, -1, -1, -1 };
            
            for(int i = 0; i < texmaps.length; i++) {
                texmaps[i] = this;
            }
        }
        
        public HDTextureMap(List<Integer> blockids, int databits, short[] faces) {
            this.faces = faces;
            this.blockids = blockids;
            this.databits = databits;
        }
        
        public void addToTable() {
            /* Add entries to lookup table */
            for(Integer blkid : blockids) {
                for(int i = 0; i < 16; i++) {
                    if((databits & (1 << i)) != 0) {
                        texmaps[16*blkid + i] = this;
                    }
                }
            }
        }
        
        public static HDTextureMap getMap(int blkid, int blkdata) {
            return texmaps[(blkid<<4) + blkdata];
        }
    }
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
            if(ze == null)
                throw new FileNotFoundException();
            is = zf.getInputStream(ze);
            loadGrassColorPNG(is);
            is.close();
            /* Try to find and load misc/foliagecolor.png */
            ze = zf.getEntry(FOLIAGECOLOR_PNG);
            if(ze == null)
                throw new FileNotFoundException();
            is = zf.getInputStream(ze);
            loadFoliageColorPNG(is);
            is.close();
            /* Try to find and load misc/watercolor.png */
            ze = zf.getEntry(WATERCOLOR_PNG);
            if(ze == null)
                throw new FileNotFoundException();
            is = zf.getInputStream(ze);
            loadWaterColorPNG(is);
            is.close();
            /* Try to find and load misc/water.png */
            ze = zf.getEntry(WATER_PNG);
            if(ze == null)
                throw new FileNotFoundException();
            is = zf.getInputStream(ze);
            loadWaterPNG(is);
            is.close();
            
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
            fis = new FileInputStream(f);
            loadGrassColorPNG(fis);
            fis.close();
            /* Check for misc/foliagecolor.png */
            f = new File(texturedir, tpname + "/" + FOLIAGECOLOR_PNG);
            fis = new FileInputStream(f);
            loadFoliageColorPNG(fis);
            fis.close();
            /* Check for misc/waterecolor.png */
            f = new File(texturedir, tpname + "/" + WATERCOLOR_PNG);
            fis = new FileInputStream(f);
            loadWaterColorPNG(fis);
            fis.close();
            /* Check for misc/water.png */
            f = new File(texturedir, tpname + "/" + WATER_PNG);
            fis = new FileInputStream(f);
            loadWaterPNG(fis);
            fis.close();
        } catch (IOException iox) {
            if(fis != null) {
                try { fis.close(); } catch (IOException io) {}
            }
            throw new FileNotFoundException();
        }
    }
    /* Copy texture pack */
    private TexturePack(TexturePack tp) {
        this.terrain_argb = new int[tp.terrain_argb.length][];
        System.arraycopy(tp.terrain_argb, 0, this.terrain_argb, 0, this.terrain_argb.length);
        this.terrain_width = tp.terrain_width;
        this.terrain_height = tp.terrain_height;
        this.native_scale = tp.native_scale;

        this.grasscolor_argb = tp.grasscolor_argb;
        this.grasscolor_height = tp.grasscolor_height;
        this.grasscolor_width = tp.grasscolor_width;
        this.trivial_grasscolor = tp.trivial_grasscolor;

        this.watercolor_argb = tp.watercolor_argb;
        this.watercolor_height = tp.watercolor_height;
        this.watercolor_width = tp.watercolor_width;
        this.trivial_watercolor = tp.trivial_watercolor;

        this.foliagecolor_argb = tp.foliagecolor_argb;
        this.foliagecolor_height = tp.foliagecolor_height;
        this.foliagecolor_width = tp.foliagecolor_width;
        this.trivial_foliagecolor = tp.trivial_foliagecolor;

        this.water_argb = tp.water_argb;
        this.water_height = tp.water_height;
        this.water_width = tp.water_width;
    }
    
    /* Load terrain.png */
    private void loadTerrainPNG(InputStream is) throws IOException {
        int i;
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        terrain_width = img.getWidth();
        terrain_height = img.getHeight();
        native_scale = terrain_width / 16;
        terrain_argb = new int[BLOCKTABLELEN][];
        for(i = 0; i < 256; i++) {
            terrain_argb[i] = new int[native_scale*native_scale];
            img.getRGB((i & 0xF)*native_scale, (i>>4)*native_scale, native_scale, native_scale, terrain_argb[i], 0, native_scale);
        }
        int[] blank = new int[native_scale*native_scale];
        for(i = 256; i < BLOCKTABLELEN; i++) {
            terrain_argb[i] = blank;
        }
        img.flush();
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
        /* Figure out trivial color */
        trivial_grasscolor = grasscolor_argb[grasscolor_height*grasscolor_width*3/4 + grasscolor_width/2];
        boolean same = true;
        for(int j = 0; same && (j < grasscolor_height); j++) {
            for(int i = 0; same && (i <= j); i++) {
                if(grasscolor_argb[grasscolor_width*j+i] != trivial_grasscolor)
                    same = false;
            }
        }
        /* All the same - no biome lookup needed */
        if(same)
            grasscolor_argb = null;
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
        /* Figure out trivial color */
        trivial_foliagecolor = foliagecolor_argb[foliagecolor_height*foliagecolor_width*3/4 + foliagecolor_width/2];
        boolean same = true;
        for(int j = 0; same && (j < foliagecolor_height); j++) {
            for(int i = 0; same && (i <= j); i++) {
                if(foliagecolor_argb[foliagecolor_width*j+i] != trivial_foliagecolor)
                    same = false;
            }
        }
        /* All the same - no biome lookup needed */
        if(same)
            foliagecolor_argb = null;
    }

    /* Load misc/watercolor.png */
    private void loadWaterColorPNG(InputStream is) throws IOException {
        /* Load image */
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        watercolor_width = img.getWidth();
        watercolor_height = img.getHeight();
        watercolor_argb = new int[watercolor_width * watercolor_height];
        img.getRGB(0, 0, watercolor_width, watercolor_height, watercolor_argb, 0, watercolor_width);
        img.flush();        
        /* Figure out trivial color */
        trivial_watercolor = watercolor_argb[watercolor_height*watercolor_width*3/4 + watercolor_width/2];
        boolean same = true;
        for(int j = 0; same && (j < watercolor_height); j++) {
            for(int i = 0; same && (i <= j); i++) {
                if(watercolor_argb[watercolor_width*j+i] != trivial_watercolor)
                    same = false;
            }
        }
        /* All the same - no biome lookup needed */
        if(same)
            watercolor_argb = null;
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
        /* Now, patch in to block table */
        int new_water_argb[] = new int[native_scale*native_scale];
        scaleTerrainPNGSubImage(water_width, native_scale, water_argb, new_water_argb);
        terrain_argb[BLOCKINDEX_STATIONARYWATER] = new_water_argb;
        terrain_argb[BLOCKINDEX_MOVINGWATER] = new_water_argb;
    }
    /* Get texture pack directory */
    private static File getTexturePackDirectory() {
        return new File(DynmapPlugin.dataDirectory, "texturepacks");
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
        tp.terrain_argb = new int[256][];
        /* Terrain.png is 16x16 array of images : process one at a time */
        for(int idx = 0; idx < 256; idx++) {
            tp.terrain_argb[idx] = new int[tp.native_scale*tp.native_scale];
            scaleTerrainPNGSubImage(native_scale, tp.native_scale, terrain_argb[idx],  tp.terrain_argb[idx]);
        }
    }
    private static void scaleTerrainPNGSubImage(int srcscale, int destscale, int[] src_argb, int[] dest_argb) {
        int nativeres = srcscale;
        int res = destscale;
        Color c = new Color();
        /* Same size, so just copy */
        if(res == nativeres) {
            System.arraycopy(src_argb, 0, dest_argb, 0, src_argb.length);
        }
        /* If we're scaling larger source pixels into smaller pixels, each destination pixel
         * receives input from 1 or 2 source pixels on each axis
         */
        else if(res > nativeres) {
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
                    weights[idx] = (offsets[idx]*res + res) - v;
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
                            c.setARGB(src_argb[(ind_y+yy)*nativeres + ind_x + xx]);
                            accum_red += c.getRed() * wx * wy;
                            accum_green += c.getGreen() * wx * wy;
                            accum_blue += c.getBlue() * wx * wy;
                            accum_alpha += c.getAlpha() * wx * wy;
                        }
                    }
                    /* Generate weighted compnents into color */
                    c.setRGBA(accum_red / (nativeres*nativeres), accum_green / (nativeres*nativeres), 
                              accum_blue / (nativeres*nativeres), accum_alpha / (nativeres*nativeres));
                    dest_argb[(y*res) + x] = c.getARGB();
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
                    weights[idx] = (offsets[idx]*nativeres + nativeres) - v;
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
                    c.setARGB(src_argb[(y*nativeres) + x]);
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
                    dest_argb[y*res + x] = c.getARGB();
                }
            }
        }
    }
    public void saveTerrainPNG(File f) throws IOException {
        int[] outbuf = new int[256*native_scale*native_scale];
        for(int i = 0; i < 256; i++) {
            for(int y = 0; y < native_scale; y++) {
                System.arraycopy(terrain_argb[i],native_scale*y,outbuf,((i>>4)*native_scale+y)*terrain_width + (i & 0xF)*native_scale, native_scale);
            }
        }
        BufferedImage img = KzedMap.createBufferedImage(outbuf, terrain_width, terrain_height);
        ImageIO.write(img, "png", f);
    }
    
    /**
     * Load texture pack mappings from texture.txt file
     */
    public static void loadTextureMapping(File plugindir) {
        LineNumberReader rdr = null;
        int cnt = 0;
        /* Initialize map with blank map for all entries */
        HDTextureMap.initializeTable();

        try {
            String line;
            rdr = new LineNumberReader(new FileReader(new File(plugindir, "texture.txt")));
            while((line = rdr.readLine()) != null) {
                if(line.startsWith("block:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    int databits = 0;
                    short faces[] = new short[] { -1, -1, -1, -1, -1, -1 };
                    line = line.substring(6);
                    String[] args = line.split(",");
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            blkids.add(Integer.parseInt(av[1]));
                        }
                        else if(av[0].equals("data")) {
                            if(av[1].equals("*"))
                                databits = 0xFFFF;
                            else
                                databits |= (1 << Integer.parseInt(av[1]));
                        }
                        else if(av[0].equals("top") || av[0].equals("y-")) {
                            faces[BlockStep.Y_MINUS.ordinal()] = Short.parseShort(av[1]);
                        }
                        else if(av[0].equals("bottom") || av[0].equals("y+")) {
                            faces[BlockStep.Y_PLUS.ordinal()] = Short.parseShort(av[1]);
                        }
                        else if(av[0].equals("north") || av[0].equals("x+")) {
                            faces[BlockStep.X_PLUS.ordinal()] = Short.parseShort(av[1]);
                        }
                        else if(av[0].equals("east") || av[0].equals("z-")) {
                            faces[BlockStep.Z_MINUS.ordinal()] = Short.parseShort(av[1]);
                        }
                        else if(av[0].equals("west") || av[0].equals("z+")) {
                            faces[BlockStep.Z_PLUS.ordinal()] = Short.parseShort(av[1]);
                        }
                        else if(av[0].equals("allfaces")) {
                            short id = Short.parseShort(av[1]);
                            for(int i = 0; i < 6; i++) {
                                faces[i] = id;
                            }
                        }
                        else if(av[0].equals("allsides")) {
                            short id = Short.parseShort(av[1]);
                            faces[BlockStep.X_PLUS.ordinal()] = id;
                            faces[BlockStep.X_MINUS.ordinal()] = id;
                            faces[BlockStep.Z_PLUS.ordinal()] = id;
                            faces[BlockStep.Z_MINUS.ordinal()] = id;
                        }
                    }
                    /* If we have everything, build block */
                    if((blkids.size() > 0) && (databits != 0)) {
                        HDTextureMap map = new HDTextureMap(blkids, databits, faces);
                        map.addToTable();
                        cnt++;
                    }
                    else {
                        Log.severe("Texture mapping missing required parameters = line " + rdr.getLineNumber() + " of texture.txt");
                    }
                }
                else if(line.startsWith("#") || line.startsWith(";")) {
                }
            }
            Log.info("Loaded " + cnt + " texture mappings");
        } catch (IOException iox) {
            Log.severe("Error reading texture.txt - " + iox.toString());
        } catch (NumberFormatException nfx) {
            Log.severe("Format error - line " + rdr.getLineNumber() + " of texture.txt");
        } finally {
            if(rdr != null) {
                try {
                    rdr.close();
                    rdr = null;
                } catch (IOException e) {
                }
            }
        }
    }
    /**
     * Read color for given subblock coordinate, with given block id and data and face
     */
    public void readColor(HDPerspectiveState ps, MapIterator mapiter, Color rslt) {
        int blkid = ps.getBlockTypeID();
        int blkdata = ps.getBlockData();
        HDTextureMap map = HDTextureMap.getMap(blkid, blkdata);
        BlockStep laststep = ps.getLastBlockStep();
        int textid = map.faces[laststep.ordinal()]; /* Get index of texture source */
        if(textid < 0) {
            rslt.setTransparent();
            return;
        }
        /* See if not basic block texture */
        int textop = textid / 1000;
        textid = textid % 1000;
        int[] texture = terrain_argb[textid];
        int clrval = 0;
        int[] xyz = new int[3];
        ps.getSubblockCoord(xyz);
        switch(laststep) {
            case X_MINUS:
                clrval = texture[(native_scale-xyz[1]-1)*native_scale + xyz[2]];
                break;
            case X_PLUS:
                clrval = texture[(native_scale-xyz[1]-1)*native_scale + (native_scale-xyz[2]-1)];
                break;
            case Z_MINUS:
                clrval = texture[(native_scale-xyz[1]-1)*native_scale + xyz[0]];
                break;
            case Z_PLUS:
                clrval = texture[(native_scale-xyz[1]-1)*native_scale + (native_scale-xyz[0]-1)];
                break;
            case Y_MINUS:
                clrval = texture[xyz[2]*native_scale + xyz[0]];
                break;
            case Y_PLUS:
                clrval = texture[xyz[2]*native_scale + (native_scale-xyz[0]-1)];
                break;
        }
        rslt.setARGB(clrval);
        if(textop > 0) {
            int tone = 0xFFFFFFFF;
            /* Switch based on texture modifier */
            switch(textop) {
                case COLORMOD_GRASSTONED:
                    if(grasscolor_argb == null) {
                        tone = trivial_grasscolor;
                    }
                    else {
                        tone = biomeLookup(grasscolor_argb, grasscolor_width, mapiter.getRawBiomeRainfall(), mapiter.getRawBiomeTemperature());
                    }
                    break;
                case COLORMOD_FOLIAGETONED:
                    if(foliagecolor_argb == null) {
                        tone = trivial_foliagecolor;
                    }
                    else {
                        tone = biomeLookup(foliagecolor_argb, foliagecolor_width, mapiter.getRawBiomeRainfall(), mapiter.getRawBiomeTemperature());
                    }
                    break;
                case COLORMOD_WATERTONED:
                    if(watercolor_argb == null) {
                        tone = trivial_watercolor;
                    }
                    else {
                        tone = biomeLookup(watercolor_argb, watercolor_width, mapiter.getRawBiomeRainfall(), mapiter.getRawBiomeTemperature());
                    }
                    break;
            }
            rslt.blendColor(tone);
        }
    }
    
    private static final int biomeLookup(int[] argb, int width, double rainfall, double temp) {
        int t = (int)((1.0-temp)*(width-1));
        int h = width - (int)(temp*rainfall*(width-1)) - 1;
        return argb[width*h + t];
    }
    
    public static void main(String[] args) {
        TexturePack.loadTextureMapping(new File("."));
        TexturePack tp = TexturePack.getTexturePack("standard");
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
