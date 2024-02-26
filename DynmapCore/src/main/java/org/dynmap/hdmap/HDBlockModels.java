package org.dynmap.hdmap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.debug.Debug;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStateParser;
import org.dynmap.utils.ForgeConfigFile;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.dynmap.utils.Vector3D;

/**
 * Custom block models - used for non-cube blocks to represent the physical volume associated with the block
 * Used by perspectives to determine if rays have intersected a block that doesn't occupy its whole block
 */
public class HDBlockModels {
    private static int max_patches;
    static HashMap<Integer, HDBlockModel> models_by_id_data = new HashMap<Integer, HDBlockModel>();
    static PatchDefinitionFactory pdf = new PatchDefinitionFactory();
    static BitSet customModelsRequestingTileData = new BitSet(); // Index by globalStateIndex
    static BitSet changeIgnoredBlocks = new BitSet();   // Index by globalStateIndex
    private static HashSet<String> loadedmods = new HashSet<String>();
    private static HashMap<Integer, HDScaledBlockModels> scaled_models_by_scale = new HashMap<Integer, HDScaledBlockModels>();

    public static final int getMaxPatchCount() { return max_patches; }  
    public static final PatchDefinitionFactory getPatchDefinitionFactory() { return pdf; }
    
    /* Reset model if defined by different block set */
    public static boolean resetIfNotBlockSet(DynmapBlockState blk, String blockset) {
        HDBlockModel bm = models_by_id_data.get(blk.globalStateIndex);
        if((bm != null) && (bm.getBlockSet().equals(blockset) == false)) {
            Debug.debug("Reset block model for " + blk + " from " + bm.getBlockSet() + " due to new def from " + blockset);
            models_by_id_data.remove(blk.globalStateIndex);
            return true;
        }
        return false;
    }
    /* Get texture count needed for model */
    public static int getNeededTextureCount(DynmapBlockState blk) {
        HDBlockModel bm = models_by_id_data.get(blk.globalStateIndex);
        if(bm != null) {
            return bm.getTextureCount();
        }
        return 6;
    }
    
    public static final boolean isChangeIgnoredBlock(DynmapBlockState blk) {
        return changeIgnoredBlocks.get(blk.globalStateIndex);
    }
    

    /* Process any block aliases */
    public static void handleBlockAlias() {
        Set<String> aliasedblocks = MapManager.mapman.getAliasedBlocks();
        for (String bn : aliasedblocks) {
            String newid = MapManager.mapman.getBlockAlias(bn);
            if (newid.equals(bn) == false) {
                remapModel(bn, newid);
            }
        }
    }
    
    private static void remapModel(String bn, String newbn) {
        DynmapBlockState frombs = DynmapBlockState.getBaseStateByName(bn);
        DynmapBlockState tobs = DynmapBlockState.getBaseStateByName(bn);
        int fcnt = frombs.getStateCount();
        for (int bs = 0; bs < tobs.getStateCount(); bs++) {
            DynmapBlockState tb = tobs.getState(bs);
            DynmapBlockState fs = tobs.getState(bs % fcnt);
            HDBlockModel m = models_by_id_data.get(fs.globalStateIndex);
            if (m != null) {
                models_by_id_data.put(tb.globalStateIndex, m);
            }
            else {
                models_by_id_data.remove(tb.globalStateIndex);
            }
            customModelsRequestingTileData.set(tb.globalStateIndex, customModelsRequestingTileData.get(fs.globalStateIndex));
            changeIgnoredBlocks.set(tb.globalStateIndex, changeIgnoredBlocks.get(fs.globalStateIndex));
        }
    }        
    
    /**
     * Get list of tile entity fields needed for custom renderer at given ID and data value, if any
     * @param blk - block state
     * @return null if none needed, else list of fields needed
     */
    public static final String[] getTileEntityFieldsNeeded(DynmapBlockState blk) {
        int idx = blk.globalStateIndex;
        if(customModelsRequestingTileData.get(idx)) {
            HDBlockModel mod = models_by_id_data.get(idx);
            if(mod instanceof CustomBlockModel) {
                return ((CustomBlockModel)mod).render.getTileEntityFieldsNeeded();
            }
        }
        return null;
    }
    /**
     * Get scaled set of models for all modelled blocks 
     * @param scale - scale
     * @return scaled models
     */
    public static HDScaledBlockModels   getModelsForScale(int scale) {
        HDScaledBlockModels model = scaled_models_by_scale.get(Integer.valueOf(scale));
        if(model == null) {
            model = new HDScaledBlockModels(scale);
            scaled_models_by_scale.put(scale, model);
        }
        return model;
    }
    
    private static void addFiles(ArrayList<String> files, File dir, String path) {
        File[] listfiles = dir.listFiles();
        if(listfiles == null) return;
        for(File f : listfiles) {
            String fn = f.getName();
            if(fn.equals(".") || (fn.equals(".."))) continue;
            if(f.isFile()) {
                if(fn.endsWith("-models.txt")) {
                    files.add(path + fn);
                }
            }
            else if(f.isDirectory()) {
                addFiles(files, f, path + f.getName() + "/");
            }
        }
    }
    public static String getModIDFromFileName(String fn) {
        int off = fn.lastIndexOf('/');
        if (off > 0) fn = fn.substring(off+1);
        off = fn.lastIndexOf('-');
        if (off > 0) fn = fn.substring(0, off);
        return fn;
    }
    /**
     * Load models 
     * @param core - core object
     * @param config - model configuration data
     */
    public static void loadModels(DynmapCore core, ConfigurationNode config) {
        File datadir = core.getDataFolder();
        max_patches = 6;    /* Reset to default */
        /* Reset models-by-ID-Data cache */
        models_by_id_data.clear();
        /* Reset scaled models by scale cache */
        scaled_models_by_scale.clear();
        /* Reset change-ignored flags */
        changeIgnoredBlocks.clear();
        /* Reset model list */
        loadedmods.clear();
        
        /* Load block models */
        int i = 0;
        boolean done = false;
        InputStream in = null;
        ZipFile zf;
        while (!done) {
            in = TexturePack.class.getResourceAsStream("/models_" + i + ".txt");
            if(in != null) {
                loadModelFile(in, "models_" + i + ".txt", config, core, "core");
                try { in.close(); } catch (IOException iox) {} in = null;
            }
            else {
                done = true;
            }
            i++;
        }
        /* Check mods to see if model files defined there: do these first, as they trump other sources */
        for (String modid : core.getServer().getModList()) {
            File f = core.getServer().getModContainerFile(modid);   // Get mod file
            if ((f != null) && f.isFile()) {
                zf = null;
                in = null;
                try {
                    zf = new ZipFile(f);
                    String fn = "assets/" + modid.toLowerCase() + "/dynmap-models.txt";
                    ZipEntry ze = zf.getEntry(fn);
                    if (ze != null) {
                        in = zf.getInputStream(ze);
                        loadModelFile(in, fn, config, core, modid);
                        loadedmods.add(modid);  // Add to set: prevent others definitions for same mod
                    }
                } catch (ZipException e) {
                } catch (IOException e) {
                } finally {
                    if (in != null) {
                        try { in.close(); } catch (IOException e) { }
                        in = null;
                    }
                    if (zf != null) {
                        try { zf.close(); } catch (IOException e) { }
                        zf = null;
                    }
                }
            }
        }
        // Load external model files (these go before internal versions, to allow external overrides)
        ArrayList<String> files = new ArrayList<String>();
        File customdir = new File(datadir, "renderdata");
        addFiles(files, customdir, "");
        for(String fn : files) {
            File custom = new File(customdir, fn);
            if(custom.canRead()) {
                try {
                    in = new FileInputStream(custom);
                    loadModelFile(in, custom.getPath(), config, core, getModIDFromFileName(fn));
                } catch (IOException iox) {
                    Log.severe("Error loading " + custom.getPath());
                } finally {
                    if(in != null) { 
                        try { in.close(); } catch (IOException iox) {}
                        in = null;
                    }
                }
            }
        }
        // Load internal texture files (these go last, to allow other versions to replace them)
        zf = null;
        try {
            zf = new ZipFile(core.getPluginJarFile());
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                String n = ze.getName();
                if (!n.startsWith("renderdata/")) continue;
                if (!n.endsWith("-models.txt")) continue;
                in = zf.getInputStream(ze);
                if (in != null) {
                    loadModelFile(in, n, config, core, getModIDFromFileName(n));
                    try { in.close(); } catch (IOException x) { in = null; }
                }
            }
        } catch (IOException iox) {
            Log.severe("Error processing model files");
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException iox) {}
                in = null;
            }
            if (zf != null) {
                try { zf.close(); } catch (IOException iox) {}
                zf = null;
            }
        }
    }
    
    private static Integer getIntValue(Map<String,Integer> vars, String val) throws NumberFormatException {
        char c = val.charAt(0);
        if(Character.isLetter(c) || (c == '%') || (c == '&')) {
            int off = val.indexOf('+');
            int offset = 0;
            if (off > 0) {
                offset = Integer.valueOf(val.substring(off+1));
                val = val.substring(0,  off);
            }
            Integer v = vars.get(val);
            if(v == null) {
                if ((c == '%') || (c == '&')) { // block/item unique IDs
                    vars.put(val, 0);
                    v = 0;
                }
                else {
                    throw new NumberFormatException("invalid ID - " + val);
                }
            }
            if((offset != 0) && (v.intValue() > 0))
                v = v.intValue() + offset;
            return v;
        }
        else {
            return Integer.valueOf(val);
        }
    }

    // Patch index ordering, corresponding to BlockStep ordinal order
    public static final int boxPatchList[] = { 1, 4, 0, 3, 2, 5 };

    private static class BoxLimits {
        double xmin = 0.0, xmax = 1.0, ymin = 0.0, ymax = 1.0, zmin = 0.0, zmax = 1.0;
        int yrot = 0;
        int[] patches = new int[6]; // Default all to patch0
    }
    
    private static class ModelBoxSide {
    	BlockSide side;
    	int textureid;
    	double[] uv;
    	ModelBlockModel.SideRotation rot;
    };
    
    private static class ModelBox {
    	double[] from = new double[3];
    	double[] to = new double[3];
    	double xrot = 0, yrot = 0, zrot = 0;
    	double xrotorig = 8, yrotorig = 8, zrotorig = 8;
    	int modrotx = 0, modroty = 0, modrotz = 0;	// Model level rotation
    	boolean shade = true;
    	ArrayList<ModelBoxSide> sides = new ArrayList<ModelBoxSide>();
    };
    
    private static HashMap<String, BlockSide> toBlockSide = new HashMap<String, BlockSide>();
    static {
    	toBlockSide.put("u", BlockSide.TOP);
    	toBlockSide.put("d", BlockSide.BOTTOM);
    	toBlockSide.put("n", BlockSide.NORTH);
    	toBlockSide.put("s", BlockSide.SOUTH);
    	toBlockSide.put("w", BlockSide.WEST);
    	toBlockSide.put("e", BlockSide.EAST);
    };
    
    /**
     * Load models from file
     * @param core 
     */
    private static void loadModelFile(InputStream in, String fname, ConfigurationNode config, DynmapCore core, String blockset) {
        LineNumberReader rdr = null;
        int cnt = 0;
        boolean need_mod_cfg = false;
        boolean mod_cfg_loaded = false;
        String modname = "minecraft";
        String modversion = null;
        final String mcver = core.getDynmapPluginPlatformVersion();
        BlockStateParser bsp = new BlockStateParser();
        Map<DynmapBlockState, BitSet> bsprslt;
        try {
            String line;
            ArrayList<HDBlockVolumetricModel> modlist = new ArrayList<HDBlockVolumetricModel>();
            ArrayList<HDBlockPatchModel> pmodlist = new ArrayList<HDBlockPatchModel>();
            HashMap<String,Integer> varvals = new HashMap<String,Integer>();
            HashMap<String, PatchDefinition> patchdefs = new HashMap<String, PatchDefinition>();
            pdf.setPatchNameMape(patchdefs);
            int layerbits = 0;
            int rownum = 0;
            int scale = 0;
            rdr = new LineNumberReader(new BufferedReader(new InputStreamReader(in)));
            while ((line = rdr.readLine()) != null) {
                boolean skip = false;
                int lineNum = rdr.getLineNumber();
                if ((line.length() > 0) && (line.charAt(0) == '[')) {    // If version constrained like
                    int end = line.indexOf(']');    // Find end
                    if (end < 0) {
                        Log.severe("Format error - line " + lineNum + " of " + fname + ": bad version limit of file: " + fname);
                        return;
                    }
                    String vertst = line.substring(1, end);
                    String tver = mcver;
                    if (vertst.startsWith("mod:")) {    // If mod version ranged
                        tver = modversion;
                        vertst = vertst.substring(4);
                    }
                    if (!HDBlockModels.checkVersionRange(tver, vertst)) {
                        skip = true;
                    }
                    line = line.substring(end+1);
                }
                // Comment line
                if(line.startsWith("#") || line.startsWith(";")) {
                	skip = true;
                }
                // If we're skipping due to version restriction
                if (skip) continue;
                // Split off <type>:
                int typeend = line.indexOf(':');
                String typeid = "";
                if (typeend >= 0) {
                	typeid = line.substring(0, typeend);
                	line = line.substring(typeend+1).trim();
                }
                if (typeid.equals("block")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                    scale = 0;
                    String[] args = line.split(",");
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("scale")) {
                            scale = Integer.parseInt(av[1]);
                        }
                    }
                    bsprslt = bsp.getMatchingStates();
                    /* If we have everything, build block */
                    if ((bsprslt.size() > 0) && (scale > 0)) {
                        modlist.clear();
                        for (DynmapBlockState bblk : bsprslt.keySet()) {
                            if (bblk.isNotAir()) {
                                modlist.add(new HDBlockVolumetricModel(bblk, bsprslt.get(bblk), scale, new long[0], blockset));
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid model block name " + bblk.blockName + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Block model missing required parameters = line " + lineNum + " of file: " + fname);
                    }
                    layerbits = 0;
                }
                else if (typeid.equals("layer")) {
                    String args[] = line.split(",");
                    layerbits = 0;
                    rownum = 0;
                    for(String a: args) {
                        layerbits |= (1 << Integer.parseInt(a));
                    }
                }
                else if (typeid.equals("rotate")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);
                	
                    String args[] = line.split(",");
                    int rot = -1;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if (av.length < 2) continue;
                        if (av[0].equals("rot")) { rot = Integer.parseInt(av[1]); }
                    }
                    bsprslt = bsp.getMatchingStates();
                    if (bsprslt.size() != 1) {
                    	Log.severe("Missing rotate source on line " + lineNum + " of file: " + fname);
                    	continue;
                    }
                	DynmapBlockState basebs = bsprslt.keySet().iterator().next();
                	BitSet bits = bsprslt.get(basebs);
                	/* get old model to be rotated */
                	DynmapBlockState bs = basebs.getState(bits.nextSetBit(0));
                	if (bs.isAir()) {
                		Log.severe("Invalid rotate ID: " + bs + " on line " + lineNum + " of file: " + fname);
                		continue;
                	}
                	HDBlockModel mod = models_by_id_data.get(bs.globalStateIndex);
                    if (modlist.isEmpty()) {
                    }
                    else if ((mod != null) && ((rot%90) == 0) && (mod instanceof HDBlockVolumetricModel)) {
                        HDBlockVolumetricModel vmod = (HDBlockVolumetricModel)mod;
                        for(int x = 0; x < scale; x++) {
                            for(int y = 0; y < scale; y++) {
                                for(int z = 0; z < scale; z++) {
                                    if(vmod.isSubblockSet(x, y, z) == false) continue;
                                    switch(rot) {
                                        case 0:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(x, y, z, true);
                                            }
                                            break;
                                        case 90:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(scale-z-1, y, x, true);
                                            }
                                            break;
                                        case 180:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(scale-x-1, y, scale-z-1, true);
                                            }
                                            break;
                                        case 270:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(z, y, scale-x-1, true);
                                            }
                                            break;
                                    }
                                }
                            }
                        }
                    }
                    else {
                        Log.severe("Invalid rotate error - line " + lineNum + " of file:  " + fname);
                        continue;
                    }
                }
                else if (typeid.equals("patchrotate")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);
                	
                    String args[] = line.split(",");
                    int rotx = 0;
                    int roty = 0;
                    int rotz = 0;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("rot")) { roty = Integer.parseInt(av[1]); }
                        if(av[0].equals("roty")) { roty = Integer.parseInt(av[1]); }
                        if(av[0].equals("rotx")) { rotx = Integer.parseInt(av[1]); }
                        if(av[0].equals("rotz")) { rotz = Integer.parseInt(av[1]); }
                    }
                    bsprslt = bsp.getMatchingStates();
                    if (bsprslt.size() != 1) {
                    	Log.severe("Missing rotate source on line " + lineNum + " of file: " + fname);
                    	continue;
                    }
                	DynmapBlockState basebs = bsprslt.keySet().iterator().next();
                	BitSet bits = bsprslt.get(basebs);
                	/* get old model to be rotated */
                	DynmapBlockState bs = basebs.getState(bits.nextSetBit(0));
                	if (bs.isAir()) {
                		Log.severe("Invalid patchrotate ID: " + bs + " on line " + lineNum + "of file: " + fname);
                		continue;
                	}
                    HDBlockModel mod = models_by_id_data.get(bs.globalStateIndex);
                    if (pmodlist.isEmpty()) {
                    }
                    else if ((mod != null) && (mod instanceof HDBlockPatchModel)) {
                        HDBlockPatchModel pmod = (HDBlockPatchModel)mod;
                        PatchDefinition patches[] = pmod.getPatches();
                        PatchDefinition newpatches[] = new PatchDefinition[patches.length];
                        for (int i = 0; i < patches.length; i++) {
                            newpatches[i] = (PatchDefinition)pdf.getRotatedPatch(patches[i], rotx, roty, rotz, patches[i].textureindex);
                        }
                        if (patches.length > max_patches)
                            max_patches = patches.length;
                        for (HDBlockPatchModel patchmod : pmodlist) {
                            patchmod.setPatches(newpatches);
                        }
                    }
                    else {
                        Log.severe("Invalid rotate error - line " + lineNum + " of file: " + fname);
                        return;
                    }
                }
                else if (typeid.equals("ignore-updates")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                    bsprslt = bsp.getMatchingStates();

                    for (DynmapBlockState bbs : bsprslt.keySet()) {
                    	if (bbs.isNotAir()) {
                    		BitSet bits = bsprslt.get(bbs);
                    		for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i+1)) {
                    			DynmapBlockState bs = bbs.getState(i);
                				changeIgnoredBlocks.set(bs.globalStateIndex);
                    		}
                    	}
                    	else {
                        	Log.severe("Invalid update ignore block name " + bbs + " at line " + lineNum + " of file: " + fname);
                    	}
                    }
                }
                else if (typeid.equals("enabled")) {  /* Test if texture file is enabled */
                    if (line.startsWith("true")) {   /* We're enabled? */
                        /* Nothing to do - keep processing */
                    }
                    else if (line.startsWith("false")) { /* Disabled */
                        return; /* Quit */
                    }
                    /* If setting is not defined or false, quit */
                    else if (config.getBoolean(line, false) == false) {
                        return;
                    }
                    else {
                        Log.info(line + " models enabled");
                    }
                }
                else if (typeid.equals("var")) {  /* Test if variable declaration */
                    String args[] = line.split(",");
                    for(int i = 0; i < args.length; i++) {
                        String[] v = args[i].split("=");
                        if(v.length < 2) {
                            Log.severe("Format error - line " + lineNum + " of file: " + fname);
                            return;
                        }
                        try {
                            int val = Integer.valueOf(v[1]);    /* Parse default value */
                            int parmval = config.getInteger(v[0], val); /* Read value, with applied default */
                            varvals.put(v[0], parmval); /* And save value */
                        } catch (NumberFormatException nfx) {
                            Log.severe("Format error - line " + lineNum + " of file: " + fname);
                            return;
                        }
                    }
                }
                else if (typeid.equals("cfgfile")) { /* If config file */
                    File cfgfile = new File(line);
                    ForgeConfigFile cfg = new ForgeConfigFile(cfgfile);
                    if (!mod_cfg_loaded) {
                        need_mod_cfg = true;
                    }
                    if(cfg.load()) {
                        cfg.addBlockIDs(varvals);
                        need_mod_cfg = false;
                        mod_cfg_loaded = true;
                    }
                }
                else if (typeid.equals("patch")) {
                    String patchid = null;
                    String[] args = line.split(",");
                    double p_x0 = 0.0, p_y0 = 0.0, p_z0 = 0.0;
                    double p_xu = 0.0, p_yu = 1.0, p_zu = 0.0;
                    double p_xv = 1.0, p_yv = 0.0, p_zv = 0.0;
                    double p_umin = 0.0, p_umax = 1.0;
                    double p_vmin = 0.0, p_vmax = 1.0;
                    double p_vmaxatumax = -1.0;
                    double p_vminatumax = -1.0;
                    double p_uplusvmax = -1.0;
                    SideVisible p_sidevis = SideVisible.BOTH;
                    
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            patchid = av[1];
                        }
                        else if(av[0].equals("Ox")) {
                            p_x0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oy")) {
                            p_y0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oz")) {
                            p_z0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Ux")) {
                            p_xu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uy")) {
                            p_yu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uz")) {
                            p_zu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vx")) {
                            p_xv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vy")) {
                            p_yv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vz")) {
                            p_zv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umin")) {
                            p_umin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umax")) {
                            p_umax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmin")) {
                            p_vmin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmax")) {
                            p_vmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("UplusVmax")) {
                            Log.warning("UplusVmax deprecated - use VmaxAtUMax - line " + lineNum + " of file: " + fname);
                            p_uplusvmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("VmaxAtUMax")) {
                            p_vmaxatumax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("VminAtUMax")) {
                            p_vminatumax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("visibility")) {
                            if(av[1].equals("top"))
                                p_sidevis = SideVisible.TOP;
                            else if(av[1].equals("topflip"))
                                p_sidevis = SideVisible.TOPFLIP;
                            else if(av[1].equals("topflipv"))
                                p_sidevis = SideVisible.TOPFLIPV;
                            else if(av[1].equals("topfliphv"))
                                p_sidevis = SideVisible.TOPFLIPHV;
                            else if(av[1].equals("bottom"))
                                p_sidevis = SideVisible.BOTTOM;
                            else if(av[1].equals("flip"))
                                p_sidevis = SideVisible.FLIP;
                            else
                                p_sidevis = SideVisible.BOTH;
                        }
                    }
                    // Deprecated: If set, compute umax, vmax, and vmaxatumax
                    if (p_uplusvmax >= 0.0) {
                        p_umax = p_uplusvmax;
                        p_vmax = p_uplusvmax;
                        p_vmaxatumax = 0.0;
                    }
                    // If not set, match p_vmax by default
                    if (p_vmaxatumax < 0.0) {
                        p_vmaxatumax = p_vmax;
                    }
                    // If not set, match p_vmin by default
                    if (p_vminatumax < 0.0) {
                        p_vminatumax = p_vmin;
                    }
                    /* If completed, add to map */
                    if(patchid != null) {
                        PatchDefinition pd = pdf.getPatch(p_x0, p_y0, p_z0, p_xu, p_yu, p_zu, p_xv, p_yv, p_zv, p_umin, p_umax, p_vmin, p_vminatumax, p_vmax, p_vmaxatumax, p_sidevis, 0);
                        if(pd != null) {
                            patchdefs.put(patchid,  pd);
                        }
                    }
                }
                else if (typeid.equals("patchblock")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                    String[] args = line.split(",");
                    ArrayList<PatchDefinition> patches = new ArrayList<PatchDefinition>();
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].startsWith("patch")) {
                            int patchnum0, patchnum1;
                            String ids = av[0].substring(5);
                            String[] ids2 = ids.split("-");
                            if (ids2.length == 1) {
                                patchnum0 = patchnum1 = Integer.parseInt(ids2[0]);
                            }
                            else {
                                patchnum0 = Integer.parseInt(ids2[0]);
                                patchnum1 = Integer.parseInt(ids2[1]);
                            }
                            if (patchnum0 < 0) {
                                Log.severe("Invalid patch index " + patchnum0 + " - line " + lineNum + " of file: " + fname);
                                return;
                            }
                            if (patchnum1 < patchnum0) {
                                Log.severe("Invalid patch index " + patchnum1 + " - line " + lineNum + " of file: " + fname);
                                return;
                            }
                            String patchid = av[1];
                            /* Look up patch by name */
                            for (int i = patchnum0; i <= patchnum1; i++) {
                                PatchDefinition pd = pdf.getPatchByName(patchid, i);
                                if (pd == null) {
                                    Log.severe("Invalid patch ID " + patchid + " - line " + lineNum + " of file: " + fname);
                                    return;
                                }
                                patches.add(i,  pd);
                            }
                        }
                    }
                    /* If we have everything, build block */
                    bsprslt = bsp.getMatchingStates();
                    pmodlist.clear();
                    if (bsprslt.size() > 0) {
                        PatchDefinition[] patcharray = patches.toArray(new PatchDefinition[patches.size()]);
                        if(patcharray.length > max_patches)
                            max_patches = patcharray.length;
                        for (DynmapBlockState bs : bsprslt.keySet()) {
                            if (bs.isNotAir()) {
                                pmodlist.add(new HDBlockPatchModel(bs, bsprslt.get(bs), patcharray, blockset));
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid patchmodel block name " + bs + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Patch block model missing required parameters = line " + lineNum + " of file: " + fname);
                    }
                }
                // Shortcut for defining a patchblock that is a simple rectangular prism, with sidex corresponding to full block sides
                else if (typeid.equals("boxblock")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                	String[] args = line.split(",");
                    double xmin = 0.0, xmax = 1.0, ymin = 0.0, ymax = 1.0, zmin = 0.0, zmax = 1.0;
                    int[] patchlist = boxPatchList;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("xmin")) {
                            xmin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("xmax")) {
                            xmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("ymin")) {
                            ymin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("ymax")) {
                            ymax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("zmin")) {
                            zmin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("zmax")) {
                            zmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("patches")) {
                        	String[] v = av[1].split("/");
                        	patchlist = new int[6];
                        	for (int vidx = 0; (vidx < v.length) && (vidx < patchlist.length); vidx++) {
                        		patchlist[vidx] = getIntValue(varvals, v[vidx]);
                        	}
                        }
                    }
                    /* If we have everything, build block */
                    pmodlist.clear();
                    bsprslt = bsp.getMatchingStates();
                    if (bsprslt.size() > 0) {
                        ArrayList<RenderPatch> pd = new ArrayList<RenderPatch>();
                        CustomRenderer.addBox(pdf, pd, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
                        PatchDefinition[] patcharray = new PatchDefinition[pd.size()];
                        for (int i = 0; i < patcharray.length; i++) {
                            patcharray[i] = (PatchDefinition) pd.get(i);
                        }
                        if (patcharray.length > max_patches)
                            max_patches = patcharray.length;
                        for (DynmapBlockState bs : bsprslt.keySet()) {
                            if (bs.isNotAir()) {
                                pmodlist.add(new HDBlockPatchModel(bs, bsprslt.get(bs), patcharray, blockset));
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid boxmodel block name " + bs + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Box block model missing required parameters = line " + lineNum + " of file: " + fname);
                    }
                }
                // Shortcut for defining a patchblock that is a simple rectangular prism, with sidex corresponding to full block sides
                else if (typeid.equals("boxlist")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);
                	
                    String[] args = line.split(",");
                    ArrayList<BoxLimits> boxes = new ArrayList<BoxLimits>();
                    for (String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if (av[0].equals("box")) {
                        	String[] prms = av[1].split(":");
                        	BoxLimits box = new BoxLimits();
                        	if (prms.length > 0)
                        		box.xmin = Double.parseDouble(prms[0]);
                        	if (prms.length > 1)
                        		box.xmax = Double.parseDouble(prms[1]);
                        	if (prms.length > 2)
                        		box.ymin = Double.parseDouble(prms[2]);
                        	if (prms.length > 3)
                        		box.ymax = Double.parseDouble(prms[3]);
                        	if (prms.length > 4)
                        		box.zmin = Double.parseDouble(prms[4]);
                        	if (prms.length > 5)
                        		box.zmax = Double.parseDouble(prms[5]);
                        	if (prms.length > 6) {
                        		String[] pl = prms[6].split("/");
                        		for (int p = 0; (p < 6) && (p < pl.length); p++) {
                        			box.patches[p] = Integer.parseInt(pl[p]);
                        		}
                        	}
                        	if (prms.length > 7) {
                        		box.yrot = Integer.parseInt(prms[7]);
                        	}
                        	boxes.add(box);
                        }
                    }
                    /* If we have everything, build block */
                    bsprslt = bsp.getMatchingStates();
                    pmodlist.clear();
                    if (bsprslt.size() > 0) {
                        ArrayList<RenderPatch> pd = new ArrayList<RenderPatch>();
                        
                        for (BoxLimits bl : boxes) {
                    		CustomRenderer.addBox(pdf, pd, bl.xmin, bl.xmax, bl.ymin, bl.ymax, bl.zmin, bl.zmax, bl.patches, bl.yrot);
                        }
                        PatchDefinition[] patcharray = new PatchDefinition[pd.size()];
                        for (int i = 0; i < patcharray.length; i++) {
                            patcharray[i] = (PatchDefinition) pd.get(i);
                        }
                        if (patcharray.length > max_patches)
                            max_patches = patcharray.length;
                        for (DynmapBlockState bs : bsprslt.keySet()) {
                            if (bs.isNotAir()) {
                                pmodlist.add(new HDBlockPatchModel(bs, bsprslt.get(bs), patcharray, blockset));
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid boxlist block name " + bs + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Box list block model missing required parameters = line " + lineNum  + " of file: " + fname);
                    }
                }
                // Shortcur for building JSON model style 
                else if (typeid.equals("modellist")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                    String[] args = line.split(",");
                    ArrayList<ModelBox> boxes = new ArrayList<ModelBox>();
                    for (String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if (av[0].equals("box")) {
                        	// box=from-x/y/z:to-x/y/z/rotx/roty/rotz:<side - upnsew>/<txtidx>/umin/vmin/umax/vmax>:...
                        	String[] prms = av[1].split(":");
                        	
                        	ModelBox box = new ModelBox();
                        	if (prms.length > 0) {	// Handle from (from-x/y/z or from-x/y/z/shadow)
                        		String[] xyz = prms[0].split("/");
                        		if ((xyz.length == 3) || (xyz.length == 4)) {
                        			box.from[0] = Double.parseDouble(xyz[0]);
                        			box.from[1] = Double.parseDouble(xyz[1]);
                        			box.from[2] = Double.parseDouble(xyz[2]);
                            		if ((xyz.length >= 4) && (xyz[3].equals("false"))) {
                        				box.shade = false;
                            		}
                        		}
                        		else {
                                	Log.severe("Invalid modellist FROM value (" + prms[0] + " at line " + lineNum + " of file: " + fname);
                        		}
                        	}
                        	if (prms.length > 1) {	// Handle to (to-x/y/z or to-x/y/z/rotx/roty/rotz) or to-x/y/z/rotx/roty/rotz/rorigx/rorigy/rorigz
                        		String[] xyz = prms[1].split("/");
                        		if (xyz.length >= 3) {
                        			box.to[0] = Double.parseDouble(xyz[0]);
                        			box.to[1] = Double.parseDouble(xyz[1]);
                        			box.to[2] = Double.parseDouble(xyz[2]);
                        			if (xyz.length >= 6) {	// If 6, second set are rotations (xrot/yrot/zrot)
                            			box.xrot = Double.parseDouble(xyz[3]);
                            			box.yrot = Double.parseDouble(xyz[4]);
                            			box.zrot = Double.parseDouble(xyz[5]);
                        			}
                        			if (xyz.length >= 9) {	// If 9, third set is rotation origin (xrot/yrot/zrot)
                            			box.xrotorig = Double.parseDouble(xyz[6]);
                            			box.yrotorig = Double.parseDouble(xyz[7]);
                            			box.zrotorig = Double.parseDouble(xyz[8]);
                        			}
                        		}
                        		else {
                                	Log.severe("Invalid modellist TO value (" + prms[1] + " at line " + lineNum + " of file: " + fname);
                        		}
                        	}
                        	// Rest are faces (<side - upnsew>/<txtidx>/umin/vmin/umax/vmax> or <<side - upnsew>/<txtidx>)
                        	// OR R/mrx/mry/mrz for model rotation
                        	for (int faceidx = 2; faceidx < prms.length; faceidx++) {
                        		String v = prms[faceidx];	
                        		String[] flds = v.split("/");
                        		// If rotation
                        		if (flds[0].equals("R") && (flds.length == 4)) {
                        			box.modrotx = Integer.parseInt(flds[1]);
                        			box.modroty = Integer.parseInt(flds[2]);
                        			box.modrotz = Integer.parseInt(flds[3]);
                        			continue;
                        		}
                        		ModelBoxSide side = new ModelBoxSide();
                        		side.rot = null;
                        		if ((flds.length != 2) && (flds.length != 6)) {
                                	Log.severe("Invalid modellist face '" + v + "' at line " + lineNum + " of file: " + fname);
                                	continue;
                        		}
                        		if (flds.length > 0) {
                        			String face = flds[0];
                        			side.side = toBlockSide.get(face.substring(0, 1));
                        			if (side.side == null) {
                                    	Log.severe("Invalid modellist side value (" + face + ") in '" + v + "' at line " + lineNum + " of file: " + fname);
                                    	continue;
                        			}
                        			if (flds[0].length() > 1) {
                        				String r = flds[0].substring(1);
                        				switch (r) {
	                        				case "90":
	                        					side.rot = ModelBlockModel.SideRotation.DEG90;
	                        					break;
	                        				case "180":
	                        					side.rot = ModelBlockModel.SideRotation.DEG180;
	                        					break;
	                        				case "270":
	                        					side.rot = ModelBlockModel.SideRotation.DEG270;
	                        					break;
                        				}
                        			}
                        		}
                        		if (flds.length > 1) {
                        			side.textureid = getIntValue(varvals, flds[1]);
                        		}
                        		if (flds.length >= 6) {
                        			side.uv = new double[4];
                        			side.uv[0] = Double.parseDouble(flds[2]);
                        			side.uv[1] = Double.parseDouble(flds[3]);
                        			side.uv[2] = Double.parseDouble(flds[4]);
                        			side.uv[3] = Double.parseDouble(flds[5]);
                        		}
                        		box.sides.add(side);
                        	}
                        	boxes.add(box);
                        }
                    }
                    /* If we have everything, build block */
                    bsprslt = bsp.getMatchingStates();
                    pmodlist.clear();
                    if (bsprslt.size() > 0) {
                        ArrayList<PatchDefinition> pd = new ArrayList<PatchDefinition>();
                        
						for (ModelBox bl : boxes) {
							// Loop through faces
							for (ModelBoxSide side : bl.sides) {
								PatchDefinition patch = pdf.getModelFace(bl.from, bl.to, side.side, side.uv, side.rot, bl.shade, side.textureid);
								if (patch != null) {
									// If any rotations, apply them here
									if ((bl.xrot != 0) || (bl.yrot != 0) || (bl.zrot != 0)) {
										patch = pdf.getPatch(patch, -bl.xrot, -bl.yrot, -bl.zrot, 
											new Vector3D(bl.xrotorig / 16, bl.yrotorig / 16, bl.zrotorig / 16),
											patch.textureindex);
										if (patch == null) continue;
									}
									// If model rotation, apply too
		                            if ((bl.modrotx != 0) || (bl.modroty != 0) || (bl.modrotz != 0)) {
		                            	patch = pdf.getPatch(patch, bl.modrotx, bl.modroty, bl.modrotz, patch.textureindex);
										if (patch == null) continue;
		                            }									
									pd.add(patch);
								}
								else {
	                            	Log.severe(String.format("Invalid modellist patch for box %.02f/%.02f/%.02f:%.02f/%.02f/%.02f side %s at line %d of file: %s", bl.from[0], bl.from[1], bl.from[2], bl.to[0], bl.to[1], bl.to[2], side.side, lineNum, fname));
	                            	Log.verboseinfo(String.format("line = %s:%s", typeid, line));
								}
							}
                        }
                        PatchDefinition[] patcharray = new PatchDefinition[pd.size()];
                        for (int i = 0; i < patcharray.length; i++) {
                        	patcharray[i] = pd.get(i);                            	
                        }
                        if (patcharray.length > max_patches)
                            max_patches = patcharray.length;
                        for (DynmapBlockState bs : bsprslt.keySet()) {
                            if (bs.isNotAir()) {
                                pmodlist.add(new HDBlockPatchModel(bs, bsprslt.get(bs), patcharray, blockset));
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid modellist block name " + bs + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Model list block model missing required parameters = line " + lineNum + " of " + fname + " of file: " + fname);
                    }
                }
                else if (typeid.equals("customblock")) {
                	// Parse block states
                	bsp.processLine(modname, line, lineNum, varvals);

                	HashMap<String,String> custargs = new HashMap<String,String>();
                    String[] args = line.split(",");
                    String cls = null;
                    for (String a : args) {
                        String[] av = a.split("=");
                        if (av.length < 2) continue;
                        if (av[0].equals("id") || av[0].equals("data") || av[0].equals("state")) {
                        	// Skip block state args - should not be bassed to custom block handler
                        }
                        else if (av[0].equals("class")) {
                            cls = av[1];
                        }
                        else {
                            /* See if substitution value available */
                            Integer vv = varvals.get(av[1]);
                            if(vv == null)
                                custargs.put(av[0], av[1]);
                            else
                                custargs.put(av[0], vv.toString());
                        }
                    }
                    /* If we have everything, build block */
                    bsprslt = bsp.getMatchingStates();
                    if ((bsprslt.size() > 0) && (cls != null)) {
                        for (DynmapBlockState bs : bsprslt.keySet()) {
                            if (bs.isNotAir()) {
                                CustomBlockModel cbm = new CustomBlockModel(bs, bsprslt.get(bs), cls, custargs, blockset);
                                if(cbm.render == null) {
                                    Log.severe("Custom block model failed to initialize = line " + lineNum + " of file: " + fname);
                                }
                                else {
                                    /* Update maximum texture count */
                                    int texturecnt = cbm.getTextureCount();
                                    if(texturecnt > max_patches) {
                                        max_patches = texturecnt;
                                    }
                                }
                                cnt++;
                            }
                            else {
                            	Log.severe("Invalid custommodel block name " + bs + " at line " + lineNum + " of file: " + fname);
                            }
                        }
                    }
                    else {
                        Log.severe("Custom block model missing required parameters = line " + lineNum + " of file: " + fname);
                    }
                }
                else if (typeid.equals("modname")) {
                    String[] names = line.split(",");
                    boolean found = false;
                    for(String n : names) {
                        String[] ntok = n.split("[\\[\\]]");
                        String rng = null;
                        if (ntok.length > 1) {
                            n = ntok[0].trim();
                            rng = ntok[1].trim();
                        }
                        n = n.trim();
                        if (loadedmods.contains(n)) {   // Already supplied by mod itself?
                            return;
                        }
                        String modver = core.getServer().getModVersion(n);
                        if((modver != null) && ((rng == null) || checkVersionRange(modver, rng))) {
                            found = true;
                            Log.info(n + "[" + modver + "] models enabled");
                            modname = n;
                            modversion = modver;
                            loadedmods.add(n);  // Add to loaded mods
                            // Prime values from block and item unique IDs
                            core.addModBlockItemIDs(modname, varvals);
                            break;
                        }
                    }
                    if(!found) {
                        return;
                    }
                }
                else if (typeid.equals("version")) {
                    if (!checkVersionRange(mcver, line)) {
                        return;
                    }
                }
                else if(layerbits != 0) {   /* If we're working pattern lines */
                    /* Layerbits determine Y, rows count from North to South (X=0 to X=N-1), columns Z are West to East (N-1 to 0) */
                    for(int i = 0; (i < scale) && (i < line.length()); i++) {
                        if(line.charAt(i) == '*') { /* If an asterix, set flag */
                            for(int y = 0; y < scale; y++) {
                                if((layerbits & (1<<y)) != 0) {
                                    for(HDBlockVolumetricModel mod : modlist) {
                                        mod.setSubblock(rownum, y, scale-i-1, true);
                                    }
                                }
                            }
                        }
                    }
                    /* See if we're done with layer */
                    rownum++;
                    if(rownum >= scale) {
                        rownum = 0;
                        layerbits = 0;
                    }
                }
            }
            if (need_mod_cfg) {
                Log.severe("Error loading configuration file for " + modname);
            }
            Log.verboseinfo("Loaded " + cnt + " block models from " + fname + " of file: " + fname);
        } catch (IOException iox) {
            Log.severe("Error reading models.txt - " + iox.toString());
        } catch (NumberFormatException nfx) {
            Log.severe("Format error - line " + rdr.getLineNumber() + " of file: " + fname + ": " + nfx.getMessage());
        } finally {
            if(rdr != null) {
                try {
                    rdr.close();
                    rdr = null;
                } catch (IOException e) {
                }
            }
            pdf.setPatchNameMape(null);
        }
    }
    private static long vscale[] = { 10000000000L, 100000000, 1000000, 10000, 100, 1 };

    private static String normalizeVersion(String v) {
        StringBuilder v2 = new StringBuilder();
        boolean skip = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if ((c == '.') || ((c >= '0') && (c <= '9'))) {
                v2.append(c);
                skip = false;
            }
            else {
                if (!skip) {
                    skip = true;
                    v2.append('.');
                }
            }
        }
        return v2.toString();
    }
    
    private static long parseVersion(String v, boolean up) {
        v = normalizeVersion(v);
        String[] vv = v.split("\\.");
        long ver = 0;
        for (int i = 0; i < vscale.length; i++) {
            if (i < vv.length){ 
                try {
                    ver += vscale[i] * Integer.parseInt(vv[i]);
                } catch (NumberFormatException nfx) {
                }
            }
            else if (up) {
                ver += vscale[i] * 99;
            }
        }

        return ver;
    }
    public static boolean checkVersionRange(String ver, String range) {
        if (ver.equals(range))
            return true;
        String[] rng = range.split("-", -1);
        String low;
        String high;
        
        long v = parseVersion(ver, false);
        if (v == 0) return false;
        
        if (rng.length == 1) {
            low = rng[0];
            high = rng[0];
        }
        else {
            low = rng[0];
            high = rng[1];
        }
        if ((low.length() > 0) && (parseVersion(low, false) > v)) {
            return false;
        }
        if ((high.length() > 0) && (parseVersion(high, true) < v)) {
            return false;
        }
        return true;
    }
}
