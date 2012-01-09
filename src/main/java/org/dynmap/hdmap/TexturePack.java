package org.dynmap.hdmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.bukkit.block.Biome;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.MapIterator.BlockStep;
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
 */
public class TexturePack {
    /* Loaded texture packs */
    private static HashMap<String, TexturePack> packs = new HashMap<String, TexturePack>();
    
    private static final String TERRAIN_PNG = "terrain.png";
    private static final String GRASSCOLOR_PNG = "misc/grasscolor.png";
    private static final String FOLIAGECOLOR_PNG = "misc/foliagecolor.png";
    private static final String WATERCOLOR_PNG = "misc/watercolor.png";
    private static final String WATER_PNG = "misc/water.png";
    private static final String CUSTOMLAVASTILL_PNG = "custom_lava_still.png";
    private static final String CUSTOMLAVAFLOWING_PNG = "custom_lava_flowing.png";
    private static final String CUSTOMWATERSTILL_PNG = "custom_water_still.png";
    private static final String CUSTOMWATERFLOWING_PNG = "custom_water_flowing.png";

	private static final String STANDARDTP = "standard";
    /* Color modifier codes (x1000 for value in mapping code) */
    private static final int COLORMOD_NONE = 0;
    private static final int COLORMOD_GRASSTONED = 1;
    private static final int COLORMOD_FOLIAGETONED = 2;
    private static final int COLORMOD_WATERTONED = 3;
    private static final int COLORMOD_ROT90 = 4;
    private static final int COLORMOD_ROT180 = 5;
    private static final int COLORMOD_ROT270 = 6;
    private static final int COLORMOD_FLIPHORIZ = 7;
    private static final int COLORMOD_SHIFTDOWNHALF = 8;
    private static final int COLORMOD_SHIFTDOWNHALFANDFLIPHORIZ = 9;
    private static final int COLORMOD_INCLINEDTORCH = 10;
    private static final int COLORMOD_GRASSSIDE = 11;
    private static final int COLORMOD_CLEARINSIDE = 12;
    private static final int COLORMOD_PINETONED = 13;
    private static final int COLORMOD_BIRCHTONED = 14;
    private static final int COLORMOD_LILYTONED = 15;
    private static final int COLORMOD_OLD_WATERSHADED = 16;
    
    /* Special tile index values */
    private static final int BLOCKINDEX_BLANK = -1;
    private static final int BLOCKINDEX_GRASS = 0;
    private static final int BLOCKINDEX_GRASSMASK = 38;
    private static final int BLOCKINDEX_SNOW = 66;
    private static final int BLOCKINDEX_SNOWSIDE = 68;
    private static final int BLOCKINDEX_PISTONSIDE = 108;
    private static final int BLOCKINDEX_GLASSPANETOP = 148;
    private static final int BLOCKINDEX_AIRFRAME = 158;
    private static final int BLOCKINDEX_REDSTONE_NSEW_TONE = 164;
    private static final int BLOCKINDEX_REDSTONE_EW_TONE = 165;
    private static final int BLOCKINDEX_EYEOFENDER = 174;
    private static final int BLOCKINDEX_REDSTONE_NSEW = 180;
    private static final int BLOCKINDEX_REDSTONE_EW = 181;
    private static final int BLOCKINDEX_STATIONARYWATER = 257;
    private static final int BLOCKINDEX_MOVINGWATER = 258;
    private static final int BLOCKINDEX_STATIONARYLAVA = 259;
    private static final int BLOCKINDEX_MOVINGLAVA = 260;
    private static final int BLOCKINDEX_PISTONEXTSIDE = 261;
    private static final int BLOCKINDEX_PISTONSIDE_EXT = 262;
    private static final int BLOCKINDEX_PANETOP_X = 263;
    private static final int BLOCKINDEX_AIRFRAME_EYE = 264;
    private static final int BLOCKINDEX_FIRE = 265;
    private static final int MAX_BLOCKINDEX = 265;  /* Index of last static tile definition */
    private static final int BLOCKTABLELEN = 1000;  /* Leave room for dynmaic tiles */

    private static int next_dynamic_tile = MAX_BLOCKINDEX+1;
    private static class DynamicTileFile {
        String filename;
        int tilecnt_x, tilecnt_y;   /* Number of tiles horizontally and vertically */
        int tile_to_dyntile[];      /* Mapping from tile index in tile file to dynamic ID in global tile table (terrain_argb): 0=unassigned */
    }
    private static ArrayList<DynamicTileFile> addonfiles = new ArrayList<DynamicTileFile>();

    private static class LoadedImage {
        int[] argb;
        int width, height;
        int trivial_color;
    }    
    
    private int[][]   terrain_argb;
    private int terrain_width, terrain_height;
    private int native_scale;

    private int water_toned_op = COLORMOD_WATERTONED;
    
    private static final int IMG_GRASSCOLOR = 0;
    private static final int IMG_FOLIAGECOLOR = 1;
    private static final int IMG_WATER = 2;
    private static final int IMG_CUSTOMWATERMOVING = 3;
    private static final int IMG_CUSTOMWATERSTILL = 4;
    private static final int IMG_CUSTOMLAVAMOVING = 5;
    private static final int IMG_CUSTOMLAVASTILL = 6;
    private static final int IMG_WATERCOLOR = 7;
    private static final int IMG_WATERMOVING = 8;
    private static final int IMG_LAVA = 9;
    private static final int IMG_LAVAMOVING = 10;
    private static final int IMG_FIRE = 11;
    private static final int IMG_CNT = 12;
    /* 0-(IMG_CNT-1) are fixed, IMG_CNT+x is dynamic file x */
    private LoadedImage[] imgs;

    private HashMap<Integer, TexturePack> scaled_textures;
    
    public enum BlockTransparency {
        OPAQUE, /* Block is opaque - blocks light - lit by light from adjacent blocks */
        TRANSPARENT,    /* Block is transparent - passes light - lit by light level in own block */ 
        SEMITRANSPARENT /* Opaque block that doesn't block all rays (steps, slabs) - use light above for face lighting on opaque blocks */
    }
    public static class HDTextureMap {
        private int faces[];  /* index in terrain.png of image for each face (indexed by BlockStep.ordinal()) */
        private List<Integer> blockids;
        private int databits;
        private BlockTransparency bt;
        private boolean userender;
        private static HDTextureMap[] texmaps;
        private static BlockTransparency transp[];
        private static boolean userenderdata[];
        
        private static void initializeTable() {
            texmaps = new HDTextureMap[16*BLOCKTABLELEN];
            transp = new BlockTransparency[BLOCKTABLELEN];
            userenderdata = new boolean[BLOCKTABLELEN];
            HDTextureMap blank = new HDTextureMap();
            for(int i = 0; i < texmaps.length; i++)
                texmaps[i] = blank;
            for(int i = 0; i < transp.length; i++)
                transp[i] = BlockTransparency.OPAQUE;
        }
        
        private HDTextureMap() {
            blockids = Collections.singletonList(Integer.valueOf(0));
            databits = 0xFFFF;
            userender = false;
            faces = new int[] { BLOCKINDEX_BLANK, BLOCKINDEX_BLANK, BLOCKINDEX_BLANK, BLOCKINDEX_BLANK, BLOCKINDEX_BLANK, BLOCKINDEX_BLANK };
            
            for(int i = 0; i < texmaps.length; i++) {
                texmaps[i] = this;
            }

        }
        
        public HDTextureMap(List<Integer> blockids, int databits, int[] faces, BlockTransparency trans, boolean userender) {
            this.faces = faces;
            this.blockids = blockids;
            this.databits = databits;
            this.bt = trans;
            this.userender = userender;
        }
        
        public void addToTable() {
            /* Add entries to lookup table */
            for(Integer blkid : blockids) {
                for(int i = 0; i < 16; i++) {
                    if((databits & (1 << i)) != 0) {
                        texmaps[16*blkid + i] = this;
                    }
                }
                transp[blkid] = bt; /* Transparency is only blocktype based right now */
                userenderdata[blkid] = userender;	/* Ditto for using render data */
            }
        }
        
        public static HDTextureMap getMap(int blkid, int blkdata, int blkrenderdata) {
        	if(userenderdata[blkid])
        		return texmaps[(blkid<<4) + blkrenderdata];
        	else
        		return texmaps[(blkid<<4) + blkdata];
        }
        
        public static BlockTransparency getTransparency(int blkid) {
            return transp[blkid];
        }
        
        private static void remapTexture(int id, int srcid) {
            for(int i = 0; i < 16; i++) {
                texmaps[(id<<4)+i] = texmaps[(srcid<<4)+i];
            }
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
        boolean use_generate = HDMapManager.usegeneratedtextures;
        if(HDMapManager.biomeshadingfix == false)
            water_toned_op = COLORMOD_OLD_WATERSHADED;

        /* Set up for enough files */
        imgs = new LoadedImage[IMG_CNT + addonfiles.size()];

        /* Generate still and flowing water defaults */
        if(use_generate) {
            generateWater();
            generateWaterFlowing();
            generateLava();
            generateLavaFlow();
        }
        generateFire();
        
        File f = new File(texturedir, tpname);
        try {
            /* Try to open zip */
            zf = new ZipFile(f);
            /* Find and load terrain.png */
            InputStream is;
            ZipEntry ze = zf.getEntry(TERRAIN_PNG); /* Try to find terrain.png */
            if(ze == null) {
                /* Check for terrain.png under standard texture pack*/
                File ff = new File(texturedir, STANDARDTP + "/" + TERRAIN_PNG);
                is = new FileInputStream(ff);
            }
            else {
            	is = zf.getInputStream(ze); /* Get input stream for terrain.png */
            }
            loadTerrainPNG(is);
            is.close();
            /* If not generating water, load it */
            if(!use_generate) {
                ze = zf.getEntry(WATER_PNG);
                if(ze == null) {
                    File ff = new File(texturedir, STANDARDTP + "/" + WATER_PNG);
                    is = new FileInputStream(ff);
                }
                else {
                    is = zf.getInputStream(ze);
                }
                loadImage(is, IMG_WATER);
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_STATIONARYWATER);
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_MOVINGWATER);
                is.close();                
            }
            else {
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_STATIONARYWATER);
                patchTextureWithImage(IMG_WATERMOVING, BLOCKINDEX_MOVINGWATER);
                patchTextureWithImage(IMG_LAVA, BLOCKINDEX_STATIONARYLAVA);
                patchTextureWithImage(IMG_LAVAMOVING, BLOCKINDEX_MOVINGLAVA);
            }
            patchTextureWithImage(IMG_FIRE, BLOCKINDEX_FIRE);

            /* Try to find and load misc/grasscolor.png */
            ze = zf.getEntry(GRASSCOLOR_PNG);
            if(ze == null) {	/* Fall back to standard file */
                /* Check for misc/grasscolor.png under standard texture pack*/
                File ff = new File(texturedir, STANDARDTP + "/" + GRASSCOLOR_PNG);
                is = new FileInputStream(ff);
            }
            else {
            	is = zf.getInputStream(ze);
            }
        	loadBiomeShadingImage(is, IMG_GRASSCOLOR);
        	is.close();
            /* Try to find and load misc/foliagecolor.png */
            ze = zf.getEntry(FOLIAGECOLOR_PNG);
            if(ze == null) {
                /* Check for misc/foliagecolor.png under standard texture pack*/
                File ff = new File(texturedir, STANDARDTP + "/" + FOLIAGECOLOR_PNG);
                is = new FileInputStream(ff);
            }
            else {
            	is = zf.getInputStream(ze);
            }
        	loadBiomeShadingImage(is, IMG_FOLIAGECOLOR);
        	is.close();
            /* Try to find and load misc/watercolor.png */
            ze = zf.getEntry(WATERCOLOR_PNG);
            if(ze == null) {    /* Fall back to standard file */
                /* Check for misc/watercolor.png under standard texture pack*/
                File ff = new File(texturedir, STANDARDTP + "/" + WATERCOLOR_PNG);
                is = new FileInputStream(ff);
            }
            else {
                is = zf.getInputStream(ze);
            }
            loadBiomeShadingImage(is, IMG_WATERCOLOR);
            is.close();

            /* Optional files - process if they exist */
            ze = zf.getEntry(CUSTOMLAVAFLOWING_PNG);
            if(ze != null) {
                is = zf.getInputStream(ze);
                loadImage(is, IMG_CUSTOMLAVAMOVING);
                patchTextureWithImage(IMG_CUSTOMLAVAMOVING, BLOCKINDEX_MOVINGLAVA);
            }
            ze = zf.getEntry(CUSTOMLAVASTILL_PNG);
            if(ze != null) {
                is = zf.getInputStream(ze);
                loadImage(is, IMG_CUSTOMLAVASTILL);
                patchTextureWithImage(IMG_CUSTOMLAVASTILL, BLOCKINDEX_STATIONARYLAVA);
            }
            ze = zf.getEntry(CUSTOMWATERFLOWING_PNG);
            if(ze != null) {
                is = zf.getInputStream(ze);
                loadImage(is, IMG_CUSTOMWATERMOVING);
                patchTextureWithImage(IMG_CUSTOMWATERMOVING, BLOCKINDEX_MOVINGWATER);
            }
            ze = zf.getEntry(CUSTOMWATERSTILL_PNG);
            if(ze != null) {
                is = zf.getInputStream(ze);
                loadImage(is, IMG_CUSTOMWATERSTILL);
                patchTextureWithImage(IMG_CUSTOMWATERSTILL, BLOCKINDEX_STATIONARYWATER);
            }

            /* Loop through dynamic files */
            for(int i = 0; i < addonfiles.size(); i++) {
                DynamicTileFile dtf = addonfiles.get(i);
                ze = zf.getEntry(dtf.filename);
                if(ze == null) {
                    File ff = new File(texturedir, STANDARDTP + "/" + dtf.filename);
                    is = new FileInputStream(ff);
                }
                else {
                    is = zf.getInputStream(ze);
                }
                loadDynamicImage(is, i);
                is.close();
            }
            
            zf.close();
            return;
        } catch (IOException iox) {
            if(zf != null) {
                try { zf.close(); } catch (IOException io) {}
            }
        }
        /* Try loading terrain.png from directory of name */
        FileInputStream fis = null;
        try {
            /* Open and load terrain.png */
            f = new File(texturedir, tpname + "/" + TERRAIN_PNG);
            if(!f.canRead()) {
                f = new File(texturedir, STANDARDTP + "/" + TERRAIN_PNG);            	
            }
            fis = new FileInputStream(f);
            loadTerrainPNG(fis);
            fis.close();

            if(use_generate == false) { /* Not using generated - load water */
                /* Check for misc/water.png */
                f = new File(texturedir, tpname + "/" + WATER_PNG);
                if(!f.canRead()) {
                    f = new File(texturedir, STANDARDTP + "/" + WATER_PNG);              
                }
                fis = new FileInputStream(f);
                loadImage(fis, IMG_WATER);
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_STATIONARYWATER);
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_MOVINGWATER);
                fis.close();
            }
            else {
                patchTextureWithImage(IMG_WATER, BLOCKINDEX_STATIONARYWATER);
                patchTextureWithImage(IMG_WATERMOVING, BLOCKINDEX_MOVINGWATER);
                patchTextureWithImage(IMG_LAVA, BLOCKINDEX_STATIONARYLAVA);
                patchTextureWithImage(IMG_LAVAMOVING, BLOCKINDEX_MOVINGLAVA);
            }
            /* Patch in generated value */
            patchTextureWithImage(IMG_FIRE, BLOCKINDEX_FIRE);

            /* Check for misc/grasscolor.png */
            f = new File(texturedir, tpname + "/" + GRASSCOLOR_PNG);
            if(!f.canRead()) {
                f = new File(texturedir, STANDARDTP + "/" + GRASSCOLOR_PNG);            	
            }
            fis = new FileInputStream(f);
            loadBiomeShadingImage(fis, IMG_GRASSCOLOR);
            fis.close();
            /* Check for misc/foliagecolor.png */
            f = new File(texturedir, tpname + "/" + FOLIAGECOLOR_PNG);
            if(!f.canRead()) {
                f = new File(texturedir, STANDARDTP + "/" + FOLIAGECOLOR_PNG);            	
            }
            fis = new FileInputStream(f);
            loadBiomeShadingImage(fis, IMG_FOLIAGECOLOR);
            fis.close();
            /* Check for misc/watercolor.png */
            f = new File(texturedir, tpname + "/" + WATERCOLOR_PNG);
            if(!f.canRead()) {
                f = new File(texturedir, STANDARDTP + "/" + WATERCOLOR_PNG);                
            }
            fis = new FileInputStream(f);
            loadBiomeShadingImage(fis, IMG_WATERCOLOR);
            fis.close();
            
            /* Optional files - process if they exist */
            f = new File(texturedir, tpname + "/" + CUSTOMLAVAFLOWING_PNG);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadImage(fis, IMG_CUSTOMLAVAMOVING);
                patchTextureWithImage(IMG_CUSTOMLAVAMOVING, BLOCKINDEX_MOVINGLAVA);
                fis.close();
            }
            f = new File(texturedir, tpname + "/" + CUSTOMLAVASTILL_PNG);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadImage(fis, IMG_CUSTOMLAVASTILL);
                patchTextureWithImage(IMG_CUSTOMLAVASTILL, BLOCKINDEX_STATIONARYLAVA);
                fis.close();
            }
            f = new File(texturedir, tpname + "/" + IMG_CUSTOMWATERMOVING);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadImage(fis, IMG_CUSTOMWATERMOVING);
                patchTextureWithImage(IMG_CUSTOMWATERMOVING, BLOCKINDEX_MOVINGWATER);
                fis.close();
            }
            f = new File(texturedir, tpname + "/" + IMG_CUSTOMWATERSTILL);
            if(f.canRead()) {
                fis = new FileInputStream(f);
                loadImage(fis, IMG_CUSTOMWATERSTILL);
                patchTextureWithImage(IMG_CUSTOMWATERSTILL, BLOCKINDEX_STATIONARYWATER);
                fis.close();
            }
            /* Loop through dynamic files */
            for(int i = 0; i < addonfiles.size(); i++) {
                DynamicTileFile dtf = addonfiles.get(i);
                f = new File(texturedir, tpname + "/" + dtf.filename);
                if(!f.canRead()) {
                    f = new File(texturedir, STANDARDTP + "/" + dtf.filename);             
                }
                fis = new FileInputStream(f);
                loadDynamicImage(fis, i);
                fis.close();
            }
        } catch (IOException iox) {
            if(fis != null) {
                try { fis.close(); } catch (IOException io) {}
            }
            Log.info("Cannot process " + f.getPath() + " - " + iox);

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
        this.water_toned_op = tp.water_toned_op;

        this.imgs = tp.imgs;
    }
    
    /* Load terrain.png */
    private void loadTerrainPNG(InputStream is) throws IOException {
        int i, j;
        /* Load image */
        ImageIO.setUseCache(false);
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
        /* Fallbacks */
        terrain_argb[BLOCKINDEX_STATIONARYLAVA] = terrain_argb[255];
        terrain_argb[BLOCKINDEX_MOVINGLAVA] = terrain_argb[255];
        /* Now, build redstone textures with active wire color (since we're not messing with that) */
        Color tc = new Color();
        for(i = 0; i < native_scale*native_scale; i++) {
            if(terrain_argb[BLOCKINDEX_REDSTONE_NSEW_TONE][i] != 0) {
                /* Overlay NSEW redstone texture with toned wire color */
                tc.setARGB(terrain_argb[BLOCKINDEX_REDSTONE_NSEW_TONE][i]);
                tc.blendColor(0xFFC00000);  /* Blend in red */
                terrain_argb[BLOCKINDEX_REDSTONE_NSEW][i] = tc.getARGB();
            }
            if(terrain_argb[BLOCKINDEX_REDSTONE_EW_TONE][i] != 0) {
                /* Overlay NSEW redstone texture with toned wire color */
                tc.setARGB(terrain_argb[BLOCKINDEX_REDSTONE_EW_TONE][i]);
                tc.blendColor(0xFFC00000);  /* Blend in red */
                terrain_argb[BLOCKINDEX_REDSTONE_EW][i] = tc.getARGB();
            }
        }
        /* Build extended piston side texture - take top 1/4 of piston side, use to make piston extension */
        terrain_argb[BLOCKINDEX_PISTONEXTSIDE] = new int[native_scale*native_scale];
        System.arraycopy(terrain_argb[BLOCKINDEX_PISTONSIDE], 0, terrain_argb[BLOCKINDEX_PISTONEXTSIDE], 0,
                         native_scale * native_scale / 4);
        for(i = 0; i < native_scale/4; i++) {
            for(j = 0; j < (3*native_scale/4); j++) {
                terrain_argb[BLOCKINDEX_PISTONEXTSIDE][native_scale*(native_scale/4 + j) + (3*native_scale/8 + i)] =
                    terrain_argb[BLOCKINDEX_PISTONSIDE][native_scale*i + j];
            }
        }
        /* Build piston side while extended (cut off top 1/4, replace with rotated top for extension */
        terrain_argb[BLOCKINDEX_PISTONSIDE_EXT] = new int[native_scale*native_scale];
        System.arraycopy(terrain_argb[BLOCKINDEX_PISTONSIDE], native_scale*native_scale/4, 
                         terrain_argb[BLOCKINDEX_PISTONSIDE_EXT], native_scale*native_scale/4,
                         3 * native_scale * native_scale / 4);  /* Copy bottom 3/4 */
        for(i = 0; i < native_scale/4; i++) {
            for(j = 3*native_scale/4; j < native_scale; j++) {
                terrain_argb[BLOCKINDEX_PISTONSIDE_EXT][native_scale*(j - 3*native_scale/4) + (3*native_scale/8 + i)] =
                    terrain_argb[BLOCKINDEX_PISTONSIDE][native_scale*i + j];
            }
        }
        /* Build glass pane top in NSEW config (we use model to clip it) */
        terrain_argb[BLOCKINDEX_PANETOP_X] = new int[native_scale*native_scale];
        System.arraycopy(terrain_argb[BLOCKINDEX_GLASSPANETOP], 0, terrain_argb[BLOCKINDEX_PANETOP_X], 0, native_scale*native_scale);
        for(i = native_scale*7/16; i < native_scale*9/16; i++) {
            for(j = 0; j < native_scale; j++) {
                terrain_argb[BLOCKINDEX_PANETOP_X][native_scale*i + j] = terrain_argb[BLOCKINDEX_PANETOP_X][native_scale*j + i];
            }
        }
        /* Build air frame with eye overlay */
        terrain_argb[BLOCKINDEX_AIRFRAME_EYE] = new int[native_scale*native_scale];
        System.arraycopy(terrain_argb[BLOCKINDEX_AIRFRAME], 0, terrain_argb[BLOCKINDEX_AIRFRAME_EYE], 0, native_scale*native_scale);
        for(i = native_scale/4; i < native_scale*3/4; i++) {
            for(j = native_scale/4; j < native_scale*3/4; j++) {
                terrain_argb[BLOCKINDEX_AIRFRAME_EYE][native_scale*i + j] = terrain_argb[BLOCKINDEX_EYEOFENDER][native_scale*i + j];
            }
        }
        
        img.flush();
    }
    
    /* Load image into image array */
    private void loadImage(InputStream is, int idx) throws IOException {
        /* Load image */
    	ImageIO.setUseCache(false);
        BufferedImage img = ImageIO.read(is);
        if(img == null) { throw new FileNotFoundException(); }
        imgs[idx] = new LoadedImage();
        imgs[idx].width = img.getWidth();
        imgs[idx].height = img.getHeight();
        imgs[idx].argb = new int[imgs[idx].width * imgs[idx].height];
        img.getRGB(0, 0, imgs[idx].width, imgs[idx].height, imgs[idx].argb, 0, imgs[idx].width);
        img.flush();
    }

    /* Load dynamic texture files, and patch into terrain_argb */
    private void loadDynamicImage(InputStream is, int idx) throws IOException {
        loadImage(is, idx+IMG_CNT); /* Load image file */
        DynamicTileFile dtf = addonfiles.get(idx);  /* Get tile file definition */
        LoadedImage li = imgs[idx+IMG_CNT];
        int dim = li.width / dtf.tilecnt_x; /* Dimension of each tile */
        int old_argb[] = new int[dim*dim];
        for(int x = 0; x < dtf.tilecnt_x; x++) {
            for(int y = 0; y < dtf.tilecnt_y; y++) {
                if(dtf.tile_to_dyntile[y*dtf.tilecnt_x + x] > 0) {    /* dynamic ID? */
                    /* Copy source tile */
                    for(int j = 0; j < dim; j++) {
                        System.arraycopy(li.argb, (y*dim+j)*li.width + (x*dim), old_argb, j*dim, dim); 
                    }
                    /* Rescale to match rest of terrain PNG */
                    int new_argb[] = new int[native_scale*native_scale];
                    scaleTerrainPNGSubImage(dim, native_scale, old_argb, new_argb);
                    terrain_argb[dtf.tile_to_dyntile[y*dtf.tilecnt_x + x]] = new_argb;
                }
                
            }
        }
    }
    /* Load biome shading image into image array */
    private void loadBiomeShadingImage(InputStream is, int idx) throws IOException {
        loadImage(is, idx); /* Get image */
        
        LoadedImage li = imgs[idx];
        /* Get trivial color for biome-shading image */
        int clr = li.argb[li.height*li.width*3/4 + li.width/2];
        boolean same = true;
        for(int j = 0; same && (j < li.height); j++) {
            for(int i = 0; same && (i <= j); i++) {
                if(li.argb[li.width*j+i] != clr)
                    same = false;
            }
        }
        /* All the same - no biome lookup needed */
        if(same) {
            imgs[idx].argb = null;
            li.trivial_color = clr;
        }
        else {  /* Else, calculate color average for lower left quadrant */
            int[] clr_scale = new int[4];
            scaleTerrainPNGSubImage(li.width, 2, li.argb, clr_scale);
            li.trivial_color = clr_scale[2];
        }
    }
    
    /* Patch image into texture table */
    private void patchTextureWithImage(int image_idx, int block_idx) {
        /* Now, patch in to block table */
        int new_argb[] = new int[native_scale*native_scale];
        scaleTerrainPNGSubImage(imgs[image_idx].width, native_scale, imgs[image_idx].argb, new_argb);
        terrain_argb[block_idx] = new_argb;
        
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
        tp.terrain_argb = new int[terrain_argb.length][];
        /* Terrain.png is 16x16 array of images : process one at a time */
        for(int idx = 0; idx < terrain_argb.length; idx++) {
            tp.terrain_argb[idx] = new int[tp.native_scale*tp.native_scale];
            scaleTerrainPNGSubImage(native_scale, tp.native_scale, terrain_argb[idx],  tp.terrain_argb[idx]);
        }
        /* Special case - some textures are used as masks - need pure alpha (00 or FF) */
        makeAlphaPure(tp.terrain_argb[BLOCKINDEX_GRASSMASK]); /* Grass side mask */
    }
    private static void scaleTerrainPNGSubImage(int srcscale, int destscale, int[] src_argb, int[] dest_argb) {
        int nativeres = srcscale;
        int res = destscale;
        Color c = new Color();
        /* Same size, so just copy */
        if(res == nativeres) {
            System.arraycopy(src_argb, 0, dest_argb, 0, dest_argb.length);
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
                    double accum_red = 0;
                    double accum_green = 0;
                    double accum_blue = 0;
                    double accum_alpha = 0;
                    for(int xx = 0; xx < 2; xx++) {
                        int wx = (xx==0)?wgt_x:(nativeres-wgt_x);
                        if(wx == 0) continue;
                        for(int yy = 0; yy < 2; yy++) {
                            int wy = (yy==0)?wgt_y:(nativeres-wgt_y);
                            if(wy == 0) continue;
                            /* Accumulate */
                            c.setARGB(src_argb[(ind_y+yy)*nativeres + ind_x + xx]);
                            int w = wx * wy;
                            double a = (double)w * (double)c.getAlpha();
                            accum_red += c.getRed() * a;
                            accum_green += c.getGreen() * a;
                            accum_blue += c.getBlue() * a;
                            accum_alpha += a;
                        }
                    }
                    double newalpha = accum_alpha;
                    if(newalpha == 0.0) newalpha = 1.0;
                    /* Generate weighted compnents into color */
                    c.setRGBA((int)(accum_red / newalpha), (int)(accum_green / newalpha), 
                              (int)(accum_blue / newalpha), (int)(accum_alpha / (nativeres*nativeres)));
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
            double accum_red[] = new double[res*res];
            double accum_green[] = new double[res*res];
            double accum_blue[] = new double[res*res];
            double accum_alpha[] = new double[res*res];
            
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
                            double w = wx * wy;
                            double a = w * c.getAlpha();
                            accum_red[(ind_y+yy)*res + (ind_x+xx)] += c.getRed() * a;
                            accum_green[(ind_y+yy)*res + (ind_x+xx)] += c.getGreen() * a;
                            accum_blue[(ind_y+yy)*res + (ind_x+xx)] += c.getBlue() * a;
                            accum_alpha[(ind_y+yy)*res + (ind_x+xx)] += a;
                        }
                    }
                }
            }
            /* Produce normalized scaled values */
            for(int y = 0; y < res; y++) {
                for(int x = 0; x < res; x++) {
                    int off = (y*res) + x;
                    double aa = accum_alpha[off];
                    if(aa == 0.0) aa = 1.0;
                    c.setRGBA((int)(accum_red[off]/aa), (int)(accum_green[off]/aa),
                          (int)(accum_blue[off]/aa), (int)(accum_alpha[off] / (nativeres*nativeres)));
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
        BufferedImage img = DynmapBufferedImage.createBufferedImage(outbuf, terrain_width, terrain_height);
        ImageIO.setUseCache(false);
        ImageIO.write(img, "png", f);
    }

    /**
     * Load texture pack mappings
     */
    public static void loadTextureMapping(File datadir, ConfigurationNode config) {
        /* Start clean with texture packs - need to be loaded after mapping */
        packs.clear();
        /* Initialize map with blank map for all entries */
        HDTextureMap.initializeTable();
        /* Load block models */
        InputStream in = TexturePack.class.getResourceAsStream("/texture.txt");
        if(in != null) {
            loadTextureFile(in, "texture.txt", config);
            if(in != null) { try { in.close(); } catch (IOException x) {} in = null; }
        }
        else
            Log.severe("Error loading texture.txt");
        
        File renderdir = new File(datadir, "renderdata");
        String[] files = renderdir.list();
        if(files != null) {
            for(String fname : files) {
                if(fname.endsWith("-texture.txt")) {
                    File custom = new File(renderdir, fname);
                    if(custom.canRead()) {
                        try {
                            in = new FileInputStream(custom);
                            loadTextureFile(in, custom.getPath(), config);
                        } catch (IOException iox) {
                            Log.severe("Error loading " + custom.getPath() + " - " + iox);
                        } finally {
                            if(in != null) { try { in.close(); } catch (IOException x) {} in = null; }
                        }
                    }
                }
            }
        }
    }

    private static Integer getIntValue(Map<String,Integer> vars, String val) throws NumberFormatException {
        if(Character.isLetter(val.charAt(0))) {
            Integer v = vars.get(val);
            if(v == null)
                throw new NumberFormatException("invalid ID - " + val);
            return v;
        }
        else {
            return Integer.valueOf(val);
        }
    }

    /**
     * Translate face ID - in case we've got options to fix it
     */
    private static int translateFaceID(int id) {
        int f = (id / 1000);
        switch(f) {
            case COLORMOD_PINETONED:
            case COLORMOD_BIRCHTONED:
            case COLORMOD_LILYTONED:
                if(HDMapManager.biomeshadingfix == false) {
                    id = (COLORMOD_FOLIAGETONED * 1000) + (id % 1000);
                }
                break;
            case COLORMOD_WATERTONED:
                if(HDMapManager.biomeshadingfix == false) {
                    id = (COLORMOD_OLD_WATERSHADED * 1000) + (id % 1000);
                }
                break;
        }
        return id;
    }
    /**
     * Load texture pack mappings from texture.txt file
     */
    private static void loadTextureFile(InputStream txtfile, String txtname, ConfigurationNode config) {
        LineNumberReader rdr = null;
        int cnt = 0;
        HashMap<String,Integer> filetoidx = new HashMap<String,Integer>();
        HashMap<String,Integer> varvals = new HashMap<String,Integer>();

        try {
            String line;
            rdr = new LineNumberReader(new InputStreamReader(txtfile));
            while((line = rdr.readLine()) != null) {
                if(line.startsWith("block:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    int databits = -1;
                    int srctxtid = -1;
                    int faces[] = new int[] { -1, -1, -1, -1, -1, -1 };
                    line = line.substring(6);
                    BlockTransparency trans = BlockTransparency.OPAQUE;
                    String[] args = line.split(",");
                    boolean userenderdata = false;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            blkids.add(getIntValue(varvals, av[1]));
                        }
                        else if(av[0].equals("data")) {
                            if(databits < 0) databits = 0;
                            if(av[1].equals("*"))
                                databits = 0xFFFF;
                            else
                                databits |= (1 << getIntValue(varvals,av[1]));
                        }
                        else if(av[0].equals("top") || av[0].equals("y-")) {
                            faces[BlockStep.Y_MINUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("bottom") || av[0].equals("y+")) {
                            faces[BlockStep.Y_PLUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("north") || av[0].equals("x+")) {
                            faces[BlockStep.X_PLUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("south") || av[0].equals("x-")) {
                            faces[BlockStep.X_MINUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("west") || av[0].equals("z-")) {
                            faces[BlockStep.Z_MINUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("east") || av[0].equals("z+")) {
                            faces[BlockStep.Z_PLUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("allfaces")) {
                            int id = Integer.parseInt(av[1]);
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
                        else if(av[0].equals("topbottom")) {
                            faces[BlockStep.Y_MINUS.ordinal()] = 
                                faces[BlockStep.Y_PLUS.ordinal()] = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("transparency")) {
                            trans = BlockTransparency.valueOf(av[1]);
                            if(trans == null) {
                                trans = BlockTransparency.OPAQUE;
                                Log.severe("Texture mapping has invalid transparency setting - " + av[1] + " - line " + rdr.getLineNumber() + " of " + txtname);
                            }
                            /* If no water lighting fix */
                            if((blkids.contains(8) || blkids.contains(9)) && (HDMapManager.waterlightingfix == false)) {
                                trans = BlockTransparency.TRANSPARENT;  /* Treat water as transparent if no fix */
                            }
                        }
                        else if(av[0].equals("userenderdata")) {
                    		userenderdata = av[1].equals("true");
                        }
                        else if(av[0].equals("txtid")) {
                            if(filetoidx.containsKey(av[1]))
                                srctxtid = filetoidx.get(av[1]);
                            else
                                Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname);
                        }
                    }
                    /* If we have source texture, need to map values to dynamic ids */
                    if(srctxtid >= 0) {
                        for(int i = 0; i < faces.length; i++) {
                            if(faces[i] < 0) continue;  /* Leave invalid IDs alone */
                            
                            int relid = faces[i] % 1000;   /* Get relative ID */
                            /* Map to assigned ID in global tile table: preserve modifier */
                            faces[i] = (faces[i] - relid) + findOrAddDynamicTile(srctxtid, relid); 
                        }
                    }
                    /* If no data bits, assume all */
                    if(databits < 0) databits = 0xFFFF;
                    /* If we have everything, build block */
                    if(blkids.size() > 0) {
                        for(int i = 0; i < faces.length; i++)
                            faces[i] = translateFaceID(faces[i]);
                        HDTextureMap map = new HDTextureMap(blkids, databits, faces, trans, userenderdata);
                        map.addToTable();
                        cnt++;
                    }
                    else {
                        Log.severe("Texture mapping missing required parameters = line " + rdr.getLineNumber() + " of " + txtname);
                    }
                }
                else if(line.startsWith("texturefile:")) {
                    line = line.substring(line.indexOf(':')+1);
                    String[] args = line.split(",");
                    int xdim = 16, ydim = 16;
                    String fname = null;
                    String id = null;
                    for(String arg : args) {
                        String[] aval = arg.split("=");
                        if(aval.length < 2)
                            continue;
                        if(aval[0].equals("id"))
                            id = aval[1];
                        else if(aval[0].equals("filename"))
                            fname = aval[1];
                        else if(aval[0].equals("xcount"))
                            xdim = Integer.parseInt(aval[1]);
                        else if(aval[0].equals("ycount"))
                            ydim = Integer.parseInt(aval[1]);
                    }
                    if((fname != null) && (id != null)) {
                        /* Register the file */
                        int fid = findOrAddDynamicTileFile(fname, xdim, ydim);
                        filetoidx.put(id, fid); /* Save lookup */
                    }
                    else {
                        Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname);
                        return;
                    }
                }
                else if(line.startsWith("#") || line.startsWith(";")) {
                }
                else if(line.startsWith("enabled:")) {  /* Test if texture file is enabled */
                    line = line.substring(8).trim();
                    if(line.startsWith("true")) {   /* We're enabled? */
                        /* Nothing to do - keep processing */
                    }
                    else if(line.startsWith("false")) { /* Disabled */
                        return; /* Quit */
                    }
                    /* If setting is not defined or false, quit */
                    else if(config.getBoolean(line, false) == false) {
                        return;
                    }
                    else {
                        Log.info(line + " textures enabled");
                    }
                }
                else if(line.startsWith("var:")) {  /* Test if variable declaration */
                    line = line.substring(4).trim();
                    String args[] = line.split(",");
                    for(int i = 0; i < args.length; i++) {
                        String[] v = args[i].split("=");
                        if(v.length < 2) {
                            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname);
                            return;
                        }
                        try {
                            int val = Integer.valueOf(v[1]);    /* Parse default value */
                            int parmval = config.getInteger(v[0], val); /* Read value, with applied default */
                            varvals.put(v[0], parmval); /* And save value */
                        } catch (NumberFormatException nfx) {
                            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname);
                            return;
                        }
                    }
                }
            }
            Log.verboseinfo("Loaded " + cnt + " texture mappings from " + txtname);
        } catch (IOException iox) {
            Log.severe("Error reading " + txtname + " - " + iox.toString());
        } catch (NumberFormatException nfx) {
            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname);
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

    /* Process any ore hiding mappings */
    public static void handleHideOres() {
        /* Now, fix mapping if we're hiding any ores */
        if(MapManager.mapman.getHideOres()) {
            for(int i = 0; i < 256; i++) {
                int id = MapManager.mapman.getBlockIDAlias(i);
                if(id != i) {   /* New mapping? */
                    HDTextureMap.remapTexture(i, id);
                }
            }
        }
    }

    private static final int BLOCKID_GRASS = 2;
    private static final int BLOCKID_SNOW = 78;
    /**
     * Read color for given subblock coordinate, with given block id and data and face
     */
    public final void readColor(final HDPerspectiveState ps, final MapIterator mapiter, final Color rslt, final int blkid, final int lastblocktype,
            TexturePackHDShader.ShaderState ss) {
        int blkdata = ps.getBlockData();
        HDTextureMap map = HDTextureMap.getMap(blkid, blkdata, ps.getBlockRenderData());
        BlockStep laststep = ps.getLastBlockStep();
        int textid = map.faces[laststep.ordinal()]; /* Get index of texture source */
        if(textid < 0) {
            rslt.setTransparent();
            return;
        }
        else if(textid < 1000) {    /* If simple mapping */
            int[] texture = terrain_argb[textid];
            int[] xyz = ps.getSubblockCoord();
            /* Get texture coordinates (U=horizontal(left=0),V=vertical(top=0)) */
            int u = 0, v = 0;

            switch(laststep) {
                case X_MINUS: /* South face: U = East (Z-), V = Down (Y-) */
                    u = native_scale-xyz[2]-1; v = native_scale-xyz[1]-1; 
                    break;
                case X_PLUS:    /* North face: U = West (Z+), V = Down (Y-) */
                    u = xyz[2]; v = native_scale-xyz[1]-1; 
                    break;
                case Z_MINUS:   /* West face: U = South (X+), V = Down (Y-) */
                    u = xyz[0]; v = native_scale-xyz[1]-1;
                    break;
                case Z_PLUS:    /* East face: U = North (X-), V = Down (Y-) */
                    u = native_scale-xyz[0]-1; v = native_scale-xyz[1]-1;
                    break;
                case Y_MINUS:   /* U = East(Z-), V = South(X+) */
                case Y_PLUS:
                    u = native_scale-xyz[2]-1; v = xyz[0];
                    break;
            }
            /* Read color from texture */
            rslt.setARGB(texture[v*native_scale + u]);
            return;            
        }
        
        /* See if not basic block texture */
        int textop = textid / 1000;
        textid = textid % 1000;
        
        /* If clear-inside op, get out early */
        if(textop == COLORMOD_CLEARINSIDE) {
            /* Check if previous block is same block type as we are: surface is transparent if it is */
            if(blkid == lastblocktype) {
                rslt.setTransparent();
                return;
            }
            /* If water block, to watercolor tone op */
            if((blkid == 8) || (blkid == 9)) {
                textop = water_toned_op;
            }
        }

        int[] texture = terrain_argb[textid];
        int[] xyz = ps.getSubblockCoord();
        /* Get texture coordinates (U=horizontal(left=0),V=vertical(top=0)) */
        int u = 0, v = 0, tmp;

        switch(laststep) {
            case X_MINUS: /* South face: U = East (Z-), V = Down (Y-) */
                u = native_scale-xyz[2]-1; v = native_scale-xyz[1]-1; 
                break;
            case X_PLUS:    /* North face: U = West (Z+), V = Down (Y-) */
                u = xyz[2]; v = native_scale-xyz[1]-1; 
                break;
            case Z_MINUS:   /* West face: U = South (X+), V = Down (Y-) */
                u = xyz[0]; v = native_scale-xyz[1]-1;
                break;
            case Z_PLUS:    /* East face: U = North (X-), V = Down (Y-) */
                u = native_scale-xyz[0]-1; v = native_scale-xyz[1]-1;
                break;
            case Y_MINUS:   /* U = East(Z-), V = South(X+) */
            case Y_PLUS:
                u = native_scale-xyz[2]-1; v = xyz[0];
                break;
        }
        /* Handle U-V transorms before fetching color */
        switch(textop) {
            case COLORMOD_NONE:
            case COLORMOD_GRASSTONED:
            case COLORMOD_FOLIAGETONED:
            case COLORMOD_WATERTONED:
                break;
            case COLORMOD_ROT90:
                tmp = u; u = native_scale - v - 1; v = tmp;
                break;
            case COLORMOD_ROT180:
                u = native_scale - u - 1; v = native_scale - v - 1;
                break;
            case COLORMOD_ROT270:
                tmp = u; u = v; v = native_scale - tmp - 1;
                break;
            case COLORMOD_FLIPHORIZ:
                u = native_scale - u - 1;
                break;
            case COLORMOD_SHIFTDOWNHALF:
                if(v < native_scale/2) {
                    rslt.setTransparent();
                    return;
                }
                v -= native_scale/2;
                break;
            case COLORMOD_SHIFTDOWNHALFANDFLIPHORIZ:
                if(v < native_scale/2) {
                    rslt.setTransparent();
                    return;
                }
                v -= native_scale/2;
                u = native_scale - u - 1;
                break;
            case COLORMOD_INCLINEDTORCH:
                if(v >= (3*native_scale/4)) {
                    rslt.setTransparent();
                    return;
                }
                v += native_scale/4;
                if(u < native_scale/2) u = native_scale/2-1;
                if(u > native_scale/2) u = native_scale/2;
                break;
            case COLORMOD_GRASSSIDE:
                boolean do_grass_side = false;
                boolean do_snow_side = false;
                if(ss.do_better_grass) {
                    mapiter.unstepPosition(laststep);
                    if(mapiter.getBlockTypeID() == BLOCKID_SNOW)
                        do_snow_side = true;
                    if(mapiter.getBlockTypeIDAt(BlockStep.Y_MINUS) == BLOCKID_GRASS)
                        do_grass_side = true;
                    mapiter.stepPosition(laststep);
                }
                
                /* Check if snow above block */
                if(mapiter.getBlockTypeIDAt(BlockStep.Y_PLUS) == BLOCKID_SNOW) {
                    if(do_snow_side) {
                        texture = terrain_argb[BLOCKINDEX_SNOW]; /* Snow full side block */
                        textid = BLOCKINDEX_SNOW;
                    }
                    else {
                        texture = terrain_argb[BLOCKINDEX_SNOWSIDE]; /* Snow block */
                        textid = BLOCKINDEX_SNOWSIDE;
                    }
                    textop = 0;
                }
                else {  /* Else, check the grass color overlay */
                    if(do_grass_side) {
                        texture = terrain_argb[BLOCKINDEX_GRASS]; /* Grass block */
                        textid = BLOCKINDEX_GRASS;
                        textop = COLORMOD_GRASSTONED;   /* Force grass toning */
                    }
                    else {
                        int ovclr = terrain_argb[BLOCKINDEX_GRASSMASK][v*native_scale+u];
                        if((ovclr & 0xFF000000) != 0) { /* Hit? */
                            texture = terrain_argb[BLOCKINDEX_GRASSMASK]; /* Use it */
                            textop = COLORMOD_GRASSTONED;   /* Force grass toning */
                        }
                    }
                }
                break;
            case COLORMOD_CLEARINSIDE:
                break;
            case COLORMOD_LILYTONED:
                /* Rotate texture based on lily orientation function (from renderBlockLilyPad in RenderBlocks.jara in MCP) */
                long l1 = (long)(mapiter.getX() * 0x2fc20f) ^ (long)mapiter.getZ() * 0x6ebfff5L ^ (long)mapiter.getY();
                l1 = l1 * l1 * 0x285b825L + l1 * 11L;
                int orientation = (int)(l1 >> 16 & 3L);
                switch(orientation) {
                    case 0:
                        tmp = u; u = native_scale - v - 1; v = tmp;
                        break;
                    case 1:
                        u = native_scale - u - 1; v = native_scale - v - 1;
                        break;
                    case 2:
                        tmp = u; u = v; v = native_scale - tmp - 1;
                        break;
                    case 3:
                        break;
                }
                break;
            case COLORMOD_OLD_WATERSHADED:
                break;
        }
        /* Read color from texture */
        rslt.setARGB(texture[v*native_scale + u]);

        LoadedImage li = null;
        int clrmult = -1;
        /* Switch based on texture modifier */
        switch(textop) {
            case COLORMOD_GRASSTONED:
                li = imgs[IMG_GRASSCOLOR];
                break;
            case COLORMOD_FOLIAGETONED:
                li = imgs[IMG_FOLIAGECOLOR];
                break;
            case COLORMOD_WATERTONED:
                if(ss.do_swamp_shading && (mapiter.getBiome() == Biome.SWAMPLAND))
                    clrmult = 0xFFE0FF70;
                break;
            case COLORMOD_BIRCHTONED:
                clrmult = 0xFF80a755;    /* From ColorizerFoliage.java in MCP */
                break;
            case COLORMOD_PINETONED:
                clrmult = 0xFF619961;    /* From ColorizerFoliage.java in MCP */
                break;
            case COLORMOD_LILYTONED:
                clrmult =  0xFF208030; /* from BlockLilyPad.java in MCP */
                break;
            case COLORMOD_OLD_WATERSHADED:  /* Legacy water shading (wrong, but folks used it */
                if(ss.do_water_shading)
                    li = imgs[IMG_WATERCOLOR];                
                break;
        }
        if(li != null) {
            if((li.argb == null) || (!ss.do_biome_shading)) {
                clrmult = li.trivial_color;
            }
            else {
                clrmult = biomeLookup(li.argb, li.width, mapiter.getRawBiomeRainfall(), mapiter.getRawBiomeTemperature());
            }
            if(ss.do_swamp_shading && (mapiter.getBiome() == Biome.SWAMPLAND))
                clrmult = (clrmult & 0xFF000000) | (((clrmult & 0x00FEFEFE) + 0x4E0E4E) / 2);
        }
        if((clrmult != -1) && (clrmult != 0)) {
            rslt.blendColor(clrmult);
        }
    }
    
    private static final int biomeLookup(int[] argb, int width, double rainfall, double temp) {
        int w = width-1;
        int t = (int)((1.0-temp)*w);
        int h = (int)((1.0 - (temp*rainfall))*w);
        if(h > w) h = w;
        if(t > w) t = w;
        return argb[width*h + t];
    }
    
    private static final void makeAlphaPure(int[] argb) {
        for(int i = 0; i < argb.length; i++) {
            if((argb[i] & 0xFF000000) != 0)
                argb[i] |= 0xFF000000;
        }
    }

    /**
     * Add new dynmaic file definition, or return existing
     * 
     * @param fname
     * @param xdim
     * @param ydim
     * @return dynamic file index
     */
    private static int findOrAddDynamicTileFile(String fname, int xdim, int ydim) {
        DynamicTileFile f;
        /* Find existing, if already there */
        for(int i = 0; i < addonfiles.size(); i++) {
            f = addonfiles.get(i);
            if(f.filename.equals(fname))
                return i;
        }
        /* Add new tile file entry */
        f = new DynamicTileFile();
        f.filename = fname;
        f.tilecnt_x = xdim;
        f.tilecnt_y = ydim;
        f.tile_to_dyntile = new int[xdim*ydim];
        addonfiles.add(f);
        
        return addonfiles.size()-1;
    }
    /**
     * Add or find dynamic tile index of given dynamic tile
     * @param dynfile_idx - index of file
     * @param tile_id - ID of tile within file
     * @return global tile ID
     */
    private static int findOrAddDynamicTile(int dynfile_idx, int tile_id) {
        DynamicTileFile f = addonfiles.get(dynfile_idx);
        if(f == null) {
            Log.warning("Invalid add-on file index: " + dynfile_idx);
            return 0;
        }
        if(f.tile_to_dyntile[tile_id] == 0) {   /* Not assigned yet? */
            f.tile_to_dyntile[tile_id] = next_dynamic_tile;
            next_dynamic_tile++;    /* Allocate next ID */
        }
        return f.tile_to_dyntile[tile_id];
    }
    
    /* Based on TextureWaterFX.java in MCP */
    private void generateWater() {
        imgs[IMG_WATER] = new LoadedImage();
        imgs[IMG_WATER].width = 16;
        imgs[IMG_WATER].height = 16;
        imgs[IMG_WATER].argb = new int[256];

        float[] g = new float[256];
        float[] h = new float[256];
        float[] n = new float[256];
        float[] m = new float[256];
        Color c = new Color();
        Random r = new Random(1234);    /* Need to be deterministic */
        int kk;

        for(kk = 0; kk < 200; kk++) {
            for(int i = 0; i < 16; i++)
            {
                for(int k = 0; k < 16; k++)
                {
                    float f = 0.0F;
                    for(int j1 = i - 1; j1 <= i + 1; j1++)
                    {
                        int k1 = j1 & 0xf;
                        int i2 = k & 0xf;
                        f += g[k1 + i2 * 16];
                    }

                    h[i + k * 16] = f / 3.3F + n[i + k * 16] * 0.8F;
                }

            }

            for(int j = 0; j < 16; j++)
            {
                for(int l = 0; l < 16; l++)
                {
                    n[j + l * 16] += m[j + l * 16] * 0.05F;
                    if(n[j + l * 16] < 0.0F)
                    {
                        n[j + l * 16] = 0.0F;
                    }
                    m[j + l * 16] -= 0.1F;
                    if(r.nextDouble() < 0.050000000000000003D)
                    {
                        m[j + l * 16] = 0.5F;
                    }
                }

            }

            float af[] = h;
            h = g;
            g = af;
        }
        for(int i1 = 0; i1 < 256; i1++)
        {
            float f1 = g[i1];
            if(f1 > 1.0F)
            {
                f1 = 1.0F;
            }
            if(f1 < 0.0F)
            {
                f1 = 0.0F;
            }
            float f2 = f1 * f1;
            int l1 = (int)(32F + f2 * 32F);
            int j2 = (int)(50F + f2 * 64F);
            int k2 = 255;
            int l2 = (int)(146F + f2 * 50F);

            c.setRGBA(l1, j2, k2, l2);
            imgs[IMG_WATER].argb[i1] = c.getARGB();
        }
    }

    /* Based on TextureWaterFlowingFX.java in MCP */
    private void generateWaterFlowing() {
        
        imgs[IMG_WATERMOVING] = new LoadedImage();
        imgs[IMG_WATERMOVING].width = 16;
        imgs[IMG_WATERMOVING].height = 16;
        imgs[IMG_WATERMOVING].argb = new int[256];
        
        float[] g = new float[256];
        float[] h = new float[256];
        float[] n = new float[256];
        float[] m = new float[256];
        Color c = new Color();
        Random r = new Random(1234);    /* Need to be deterministic */
        
        int kk;
        for(kk = 0; kk < 200; kk++) {
            for(int i = 0; i < 16; i++)
            {
                for(int k = 0; k < 16; k++)
                {
                    float f = 0.0F;
                    for(int j1 = k - 2; j1 <= k; j1++)
                    {
                        int k1 = i & 0xf;
                        int i2 = j1 & 0xf;
                        f += g[k1 + i2 * 16];
                    }

                    h[i + k * 16] = f / 3.2F + n[i + k * 16] * 0.8F;
                }

            }

            for(int j = 0; j < 16; j++)
            {
                for(int l = 0; l < 16; l++)
                {
                    n[j + l * 16] += m[j + l * 16] * 0.05F;
                    if(n[j + l * 16] < 0.0F)
                    {
                        n[j + l * 16] = 0.0F;
                    }
                    m[j + l * 16] -= 0.3F;
                    if(r.nextDouble() < 0.20000000000000001D)
                    {
                        m[j + l * 16] = 0.5F;
                    }
                }

            }

            float af[] = h;
            h = g;
            g = af;
        }
        for(int i1 = 0; i1 < 256; i1++)
        {
            float f1 = g[i1 - kk * 16 & 0xff];
            if(f1 > 1.0F)
            {
                f1 = 1.0F;
            }
            if(f1 < 0.0F)
            {
                f1 = 0.0F;
            }
            float f2 = f1 * f1;
            int l1 = (int)(32F + f2 * 32F);
            int j2 = (int)(50F + f2 * 64F);
            int k2 = 255;
            int l2 = (int)(146F + f2 * 50F);

            c.setRGBA(l1, j2, k2, l2);
            imgs[IMG_WATERMOVING].argb[i1] = c.getARGB();
        }
    }
    
    /* Based on TextureLavaFX.java in MCP */
    private void generateLava() {
        imgs[IMG_LAVA] = new LoadedImage();
        imgs[IMG_LAVA].width = 16;
        imgs[IMG_LAVA].height = 16;
        imgs[IMG_LAVA].argb = new int[256];
        
        float[] g = new float[256];
        float[] h = new float[256];
        float[] n = new float[256];
        float[] m = new float[256];
        Color c = new Color();
        Random r = new Random(1234);    /* Need to be deterministic */
        
        int kk;
        for(kk = 0; kk < 200; kk++) {
            for(int i = 0; i < 16; i++)
            {
                for(int j = 0; j < 16; j++)
                {
                    float f = 0.0F;
                    int l = (int)(Math.sin(((float)j * 3.141593F * 2.0F) / 16F) * 1.2F);
                    int i1 = (int)(Math.sin(((float)i * 3.141593F * 2.0F) / 16F) * 1.2F);
                    for(int k1 = i - 1; k1 <= i + 1; k1++)
                    {
                        for(int i2 = j - 1; i2 <= j + 1; i2++)
                        {
                            int k2 = k1 + l & 0xf;
                            int i3 = i2 + i1 & 0xf;
                            f += g[k2 + i3 * 16];
                        }

                    }

                    h[i + j * 16] = f / 10F + ((n[(i + 0 & 0xf) + (j + 0 & 0xf) * 16] + n[(i + 1 & 0xf) + (j + 0 & 0xf) * 16] + n[(i + 1 & 0xf) + (j + 1 & 0xf) * 16] + n[(i + 0 & 0xf) + (j + 1 & 0xf) * 16]) / 4F) * 0.8F;
                    n[i + j * 16] += m[i + j * 16] * 0.01F;
                    if(n[i + j * 16] < 0.0F)
                    {
                        n[i + j * 16] = 0.0F;
                    }
                    m[i + j * 16] -= 0.06F;
                    if(r.nextDouble() < 0.005D)
                    {
                        m[i + j * 16] = 1.5F;
                    }
                }

            }

            float af[] = h;
            h = g;
            g = af;
        }
        for(int k = 0; k < 256; k++)
        {
            float f1 = g[k] * 2.0F;
            if(f1 > 1.0F)
            {
                f1 = 1.0F;
            }
            if(f1 < 0.0F)
            {
                f1 = 0.0F;
            }
            float f2 = f1;
            int j1 = (int)(f2 * 100F + 155F);
            int l1 = (int)(f2 * f2 * 255F);
            int j2 = (int)(f2 * f2 * f2 * f2 * 128F);

            c.setRGBA(j1, l1, j2, 255);
            imgs[IMG_LAVA].argb[k] = c.getARGB();
        }
    }

    /* Based on TextureLavaFlowFX.java in MCP */
    private void generateLavaFlow() {
        imgs[IMG_LAVAMOVING] = new LoadedImage();
        imgs[IMG_LAVAMOVING].width = 16;
        imgs[IMG_LAVAMOVING].height = 16;
        imgs[IMG_LAVAMOVING].argb = new int[256];
        
        float[] g = new float[256];
        float[] h = new float[256];
        float[] n = new float[256];
        float[] m = new float[256];
        Color c = new Color();
        Random r = new Random(1234);    /* Need to be deterministic */
        
        int kk;
        for(kk = 0; kk < 200; kk++) {
        for(int i = 0; i < 16; i++)
        {
            for(int j = 0; j < 16; j++)
            {
                float f = 0.0F;
                int l = (int)(Math.sin(((float)j * 3.141593F * 2.0F) / 16F) * 1.2F);
                int i1 = (int)(Math.sin(((float)i * 3.141593F * 2.0F) / 16F) * 1.2F);
                for(int k1 = i - 1; k1 <= i + 1; k1++)
                {
                    for(int i2 = j - 1; i2 <= j + 1; i2++)
                    {
                        int k2 = k1 + l & 0xf;
                        int i3 = i2 + i1 & 0xf;
                        f += g[k2 + i3 * 16];
                    }

                }

                h[i + j * 16] = f / 10F + ((n[(i + 0 & 0xf) + (j + 0 & 0xf) * 16] + n[(i + 1 & 0xf) + (j + 0 & 0xf) * 16] + n[(i + 1 & 0xf) + (j + 1 & 0xf) * 16] + n[(i + 0 & 0xf) + (j + 1 & 0xf) * 16]) / 4F) * 0.8F;
                n[i + j * 16] += m[i + j * 16] * 0.01F;
                if(n[i + j * 16] < 0.0F)
                {
                    n[i + j * 16] = 0.0F;
                }
                m[i + j * 16] -= 0.06F;
                if(r.nextDouble() < 0.005D)
                {
                    m[i + j * 16] = 1.5F;
                }
            }

        }

        float af[] = h;
        h = g;
        g = af;
        }
        for(int k = 0; k < 256; k++)
        {
            float f1 = g[k - (kk / 3) * 16 & 0xff] * 2.0F;
            if(f1 > 1.0F)
            {
                f1 = 1.0F;
            }
            if(f1 < 0.0F)
            {
                f1 = 0.0F;
            }
            float f2 = f1;
            int j1 = (int)(f2 * 100F + 155F);
            int l1 = (int)(f2 * f2 * 255F);
            int j2 = (int)(f2 * f2 * f2 * f2 * 128F);

            c.setRGBA(j1, l1, j2, 255);
            imgs[IMG_LAVAMOVING].argb[k] = c.getARGB();
        }

    }
    /* Adapted from TextureFlamesFX.java in MCP */
    private void generateFire() {
        imgs[IMG_FIRE] = new LoadedImage();
        imgs[IMG_FIRE].width = 16;
        imgs[IMG_FIRE].height = 16;
        imgs[IMG_FIRE].argb = new int[256];

        float[] g = new float[320];
        float[] h = new float[320];
        Random r = new Random(666);
        Color cc = new Color();

        for(int kk = 0; kk < 200; kk++) {
            for(int i = 0; i < 16; i++)
            {
                for(int j = 0; j < 20; j++)
                {
                    int l = 18;
                    float f1 = g[i + ((j + 1) % 20) * 16] * (float)l;
                    for(int i1 = i - 1; i1 <= i + 1; i1++)
                    {
                        for(int k1 = j; k1 <= j + 1; k1++)
                        {
                            int i2 = i1;
                            int k2 = k1;
                            if(i2 >= 0 && k2 >= 0 && i2 < 16 && k2 < 20)
                            {
                                f1 += g[i2 + k2 * 16];
                            }
                            l++;
                        }

                    }

                    h[i + j * 16] = f1 / ((float)l * 1.06F);
                    if(j >= 19)
                    {
                        h[i + j * 16] = (float)(r.nextDouble() * r.nextDouble() * r.nextDouble() * 4D + r.nextDouble() * 0.10000000149011612D + 0.20000000298023224D);
                    }
                }
            }

            float af[] = h;
            h = g;
            g = af;
        }
        for(int k = 0; k < 256; k++)
        {
            float f = g[k] * 1.8F;
            if(f > 1.0F)
            {
                f = 1.0F;
            }
            if(f < 0.0F)
            {
                f = 0.0F;
            }
            float f2 = f;
            int j1 = (int)(f2 * 155F + 100F);
            int l1 = (int)(f2 * f2 * 255F);
            int j2 = (int)(f2 * f2 * f2 * f2 * f2 * f2 * f2 * f2 * f2 * f2 * 255F);
            int c = 255;
            if(f2 < 0.5F)
            {
                c = 0;
            }
            f2 = (f2 - 0.5F) * 2.0F;
            cc.setRGBA(j1, l1, j2, c);
            imgs[IMG_FIRE].argb[k] = cc.getARGB();

        }

}

}
