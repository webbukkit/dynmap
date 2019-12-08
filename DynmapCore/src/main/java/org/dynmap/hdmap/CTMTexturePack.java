package org.dynmap.hdmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.debug.Debug;
import org.dynmap.hdmap.TexturePack.TileFileFormat;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapIterator;

/**
 * Connected Texture Mod (CTM) handler
 */
public class CTMTexturePack {
    private String[] ctpfiles;
    private TexturePackLoader tpl;
    private CTMProps[][] bytilelist;
    private CTMProps[][] bybaseblockstatelist;
    private BitSet mappedtiles;
    private BitSet mappedblocks;
    private String[] biomenames;
    
    private String vanillatextures;
    
    static final int BOTTOM_FACE = 0; // 0, -1, 0
    static final int TOP_FACE = 1; // 0, 1, 0
    static final int NORTH_FACE = 2; // 0, 0, -1
    static final int SOUTH_FACE = 3; // 0, 0, 1
    static final int WEST_FACE = 4; // -1, 0, 0
    static final int EAST_FACE = 5; // 1, 0, 0

    private static final int META_MASK = 0xffff;
    private static final int ORIENTATION_U_D = 0;
    private static final int ORIENTATION_E_W = 1 << 16;
    private static final int ORIENTATION_N_S = 2 << 16;
    private static final int ORIENTATION_E_W_2 = 3 << 16;
    private static final int ORIENTATION_N_S_2 = 4 << 16;

    private static final int[][] ROTATE_UV_MAP = new int[][]{
        {WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, 2, -2, 0, 0},
        {NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, 2},
        {WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, -2, -2, 0, 0},
        {NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, -2},
    };

    private static final int[] GO_DOWN = new int[]{0, -1, 0};
    private static final int[] GO_UP = new int[]{0, 1, 0};
    private static final int[] GO_NORTH = new int[]{0, 0, -1};
    private static final int[] GO_SOUTH = new int[]{0, 0, 1};
    private static final int[] GO_WEST = new int[]{-1, 0, 0};
    private static final int[] GO_EAST = new int[]{1, 0, 0};

    // NEIGHBOR_OFFSETS[a][b][c] = offset from starting block
    // a: face 0-5
    // b: neighbor 0-7
    //    7   6   5
    //    0   *   4
    //    1   2   3
    // c: coordinate (x,y,z) 0-2
    protected static final int[][][] NEIGHBOR_OFFSET = new int[][][]{
        // BOTTOM_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_SOUTH),
            GO_SOUTH,
            add(GO_EAST, GO_SOUTH),
            GO_EAST,
            add(GO_EAST, GO_NORTH),
            GO_NORTH,
            add(GO_WEST, GO_NORTH),
        },
        // TOP_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_SOUTH),
            GO_SOUTH,
            add(GO_EAST, GO_SOUTH),
            GO_EAST,
            add(GO_EAST, GO_NORTH),
            GO_NORTH,
            add(GO_WEST, GO_NORTH),
        },
        // NORTH_FACE
        {
            GO_EAST,
            add(GO_EAST, GO_DOWN),
            GO_DOWN,
            add(GO_WEST, GO_DOWN),
            GO_WEST,
            add(GO_WEST, GO_UP),
            GO_UP,
            add(GO_EAST, GO_UP),
        },
        // SOUTH_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_DOWN),
            GO_DOWN,
            add(GO_EAST, GO_DOWN),
            GO_EAST,
            add(GO_EAST, GO_UP),
            GO_UP,
            add(GO_WEST, GO_UP),
        },
        // WEST_FACE
        {
            GO_NORTH,
            add(GO_NORTH, GO_DOWN),
            GO_DOWN,
            add(GO_SOUTH, GO_DOWN),
            GO_SOUTH,
            add(GO_SOUTH, GO_UP),
            GO_UP,
            add(GO_NORTH, GO_UP),
        },
        // EAST_FACE
        {
            GO_SOUTH,
            add(GO_SOUTH, GO_DOWN),
            GO_DOWN,
            add(GO_NORTH, GO_DOWN),
            GO_NORTH,
            add(GO_NORTH, GO_UP),
            GO_UP,
            add(GO_SOUTH, GO_UP),
        },
    };

    public enum CTMMethod {
        NONE, CTM, HORIZONTAL, TOP, RANDOM, REPEAT, VERTICAL, FIXED, HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL
    }
    public enum CTMConnect {
        NONE, BLOCK, TILE, MATERIAL, UNKNOWN
    }
    public static final int FACE_BOTTOM = (1 << 0);
    public static final int FACE_TOP = (1 << 1);
    public static final int FACE_NORTH = (1 << 2);
    public static final int FACE_SOUTH = (1 << 3);
    public static final int FACE_WEST = (1 << 4);
    public static final int FACE_EAST = (1 << 5);
    public static final int FACE_SIDES = FACE_EAST | FACE_WEST | FACE_NORTH | FACE_SOUTH;
    public static final int FACE_ALL = FACE_SIDES | FACE_TOP | FACE_BOTTOM;
    public static final int FACE_UNKNOWN = (1 << 7);

    public enum CTMSymmetry {
        NONE(1),
        OPPOSITE(2),
        ALL(6);
        
        public final int shift;
        
        CTMSymmetry(int sh) {
            shift = sh;
        }
        
    }

    public static class CTMProps {
        public String name = null;
        public String basePath = null;
        public int[] matchBlocks = null;
        public String[] matchTiles = null;
        public CTMMethod method = CTMMethod.NONE;
        public String[] tiles = null;
        public CTMConnect connect = CTMConnect.NONE;
        public int faces = FACE_ALL;
        public int metadata = -1;
        public int[] biomes = null;
        public int minY = 0;
        public int maxY = 1024;
        public int renderPass = 0;
        public boolean innerSeams = false;
        public int width = 0;
        public int height = 0;
        public int[] weights = null;
        public CTMSymmetry symmetry = CTMSymmetry.NONE;
        public int[] sumWeights = null;
        public int sumAllWeights = 0;
        public int[] matchTileIcons = null;
        public int[] tileIcons = null;
        
        private String[] tokenize(String v, String split)
        {
            StringTokenizer tok = new StringTokenizer(v, split);
            ArrayList<String> rslt = new ArrayList<String>();

            while (tok.hasMoreTokens()) {
                rslt.add(tok.nextToken());
            }
            return rslt.toArray(new String[rslt.size()]);
        }

        private void getFaces(Properties p) {
            String v = p.getProperty("faces", "all").trim().toLowerCase();
            this.faces = 0;
            String[] tok = v.split("\\s+");
            for(String t : tok) {
                if (t.equals("bottom")) {
                    this.faces |= FACE_BOTTOM;
                }
                else if (t.equals("top")) {
                    this.faces |= FACE_TOP;
                }
                else if (t.equals("north")) {
                    this.faces |= FACE_NORTH;
                }
                else if (t.equals("south")) {
                    this.faces |= FACE_SOUTH;
                }
                else if (t.equals("east")) {
                    this.faces |= FACE_EAST;
                }
                else if (t.equals("west")) {
                    this.faces |= FACE_WEST;
                }
                else if (t.equals("sides") || t.equals("side")) {
                    this.faces |= FACE_SIDES;
                }
                else if (t.equals("all")) {
                    this.faces |= FACE_ALL;
                }
                else {
                    Log.info("Unknown face in CTM file: " + t);
                    this.faces |= FACE_UNKNOWN;
                }
            }
        }
        private int parseInt(Properties p, String fld, int def) {
            String v = p.getProperty(fld);
            if (v == null) return def;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException nfx) {
                Log.info("Bad integer: " + v);
                return def;
            }
        }
        private int[] parseInts(Properties p, String fld) {
            String v = p.getProperty(fld);
            if(v == null) return null;
            String[] tok = tokenize(v, ", ");
            ArrayList<Integer> rslt = new ArrayList<Integer>();
            for(String t : tok) {
                t = t.trim();
                String[] vtok = tokenize(t, "-");
                if(vtok.length == 1) {  /* One value */
                    try {
                        rslt.add(Integer.parseInt(vtok[0]));
                    } catch (NumberFormatException nfx) {
                        Log.info("Bad integer in list: " + vtok[0]);
                    }
                }
                else if(vtok.length == 2) { /* Range */
                    try {
                        int low = Integer.parseInt(vtok[0]);
                        int high = Integer.parseInt(vtok[1]);
                        for (int i = low; i <= high; i++) {
                            rslt.add(i);
                        }
                    } catch (NumberFormatException nfx) {
                        Log.info("Bad integer in range: " + t);
                    }
                }
            }
            int[] out = new int[rslt.size()];
            for(int i = 0; i < out.length; i++) {
                out[i] = rslt.get(i);
            }
            return out;
        }
        private int parseRenderPass(Properties p, String fld, int def) {
            String v = p.getProperty(fld);
            if (v == null) return def;
            if (v.equalsIgnoreCase("overlay"))
                return 3;
            else if (v.equalsIgnoreCase("translucent"))
                return 1;
            else if (v.equalsIgnoreCase("backface"))
                return 2;
            else if (v.equalsIgnoreCase("solid"))
                return 0;
            else if (v.equalsIgnoreCase("cutout_mipped"))
                return 0;
            else if (v.equalsIgnoreCase("cutout"))
                return 0;
            else
                return parseInt(p, fld, def);
        }
        
        private void addBlockStateToIDSet(Set<Integer> list, DynmapBlockState bs) {
    		list.add(bs.globalStateIndex);
        }
        private void addBaseBlockStateToIDSet(Set<Integer> list, DynmapBlockState bs) {
        	bs = bs.baseState;
        	for (int i = 0; i < bs.getStateCount(); i++) {
        		list.add(bs.getStateByIndex(i).globalStateIndex);
        	}
        }
        private int[] getIDList(Properties properties, String key, String type) {
            Set<Integer> list = new HashSet<Integer>();
            String property = properties.getProperty(key, "");
            for (String token : property.split("\\s+")) {
                if (token.equals("")) {
                } else if (token.matches("\\d+")) {
                    try {
                        int id = Integer.parseInt(token);
                        DynmapBlockState bs = DynmapBlockState.getStateByLegacyBlockID(id);
                        if (bs == null) {
                        	Log.info("Unknown Legacy block ID in CTM: " + token);
                        }
                        else {
                        	addBaseBlockStateToIDSet(list, bs);
                        }
                    } catch (NumberFormatException e) {
                        Log.info("Bad ID token: " + token);
                    }
                } else { // String mapping - look for block name
                    if (token.indexOf(':') < 0) {   // No 'modid:'?
                        token = "minecraft:" + token;
                    }
                    String[] toks = token.split(":");
                    DynmapBlockState bs;
                    boolean addbase = false;
                    // If blockname:statename
                    if (toks.length > 2) {
                    	bs = DynmapBlockState.getStateByNameAndState(toks[0] + ":" + toks[1], toks[2]);
                    }
                    else {
                        bs = DynmapBlockState.getBaseStateByName(token);
                        addbase = true;
                    }
                	if (bs == DynmapBlockState.AIR) {
                		Log.info("Unknown block ID in CTM: " + token);
                    }
                    else if (addbase) {
                		addBaseBlockStateToIDSet(list, bs);
                    }
                    else {
                		addBlockStateToIDSet(list, bs);
                    }
                }
            }
            if (list.isEmpty()) {
                Matcher m = Pattern.compile(type + "(\\d+)").matcher(name);
                if (m.find()) {
                    try {
                        int id = Integer.parseInt(m.group(1));
                        DynmapBlockState bs = DynmapBlockState.getStateByLegacyBlockID(id);
                        if (bs == null) {
                        	Log.info("Unknown Legacy block ID from filename in CTM: " + name);
                        }
                        else {
                        	addBlockStateToIDSet(list, bs);
                        }
                    } catch (NumberFormatException e) {
                        Log.info("Bad block number: " + name);
                    }
                }
            }
            /* Make set into list */
            if (list.isEmpty())
                return null;
            
            int[] rslt = new int[list.size()];
            int i = 0;
            for(Integer v : list) {
                rslt[i] = v;
                i++;
            }
            
            return rslt;
        }

        private void getMethod(Properties p) {
            String v = p.getProperty("method", "default").trim().toLowerCase();
            if (v.equals("ctm") || v.equals("glass") || v.equals("default")) {
                method = CTMMethod.CTM;
            }
            else if (v.equals("horizontal") || v.equals("bookshelf")) {
                method = CTMMethod.HORIZONTAL;
            }
            else if (v.equals("vertical")) {
                method = CTMMethod.VERTICAL;
            }
            else if (v.equals("vertical+horizontal") || v.equals("v+h")) {
                method = CTMMethod.VERTICAL_HORIZONTAL;
            }
            else if (v.equals("horizontal+vertical") || v.equals("h+v")) {
                method = CTMMethod.HORIZONTAL_VERTICAL;
            }
            else if (v.equals("top") || v.equals("sandstone")) {
                method = CTMMethod.TOP;
            }
            else if (v.equals("random")) {
                method = CTMMethod.RANDOM;
            }
            else if (v.equals("repeat") || v.equals("pattern")) {
                method = CTMMethod.REPEAT;
            }
            else if (v.equals("fixed") || v.equals("static")) {
                method = CTMMethod.FIXED;
            }
            else {
                Log.info("Invalid CTM Method: " + v);
                method = CTMMethod.NONE;
            }
        }
        private void getConnect(Properties p) {
            String v = p.getProperty("connect", "none").toLowerCase();
            if (v.equals("none")) {
                this.connect = CTMConnect.NONE;
            }
            else if (v.equals("block")) {
                this.connect = CTMConnect.BLOCK;
            }
            else if (v.equals("tile")) {
                this.connect = CTMConnect.TILE;
            }
            else if (v.equals("material")) {
                this.connect = CTMConnect.MATERIAL;
            }
            else {
                Log.info("Invalid CTM Connect: " + v);
                this.connect = CTMConnect.UNKNOWN;
            }
        }
        private void getBiomes(Properties p, CTMTexturePack tp) {
            String v = p.getProperty("biomes", "").trim().toLowerCase();
            if (!v.equals("")) {
                ArrayList<Integer> ids = new ArrayList<Integer>();
                String[] biomenames = tp.biomenames;
                for(String s : v.split("\\s+")) {
                    for(int i = 0; i < biomenames.length; i++) {
                        if(s.equals(biomenames[i])) {
                            ids.add(i);
                            s = null;
                            break;
                        }
                    }
                    if(s != null) {
                        Debug.debug("CTM Biome not matched: " + s);
                    }
                }
                this.biomes = new int[ids.size()];
                for(int i = 0; i < this.biomes.length; i++) {
                    this.biomes[i] = ids.get(i);
                }
            }
            else {
                this.biomes = null;
            }
        }
        private void getSymmetry(Properties p) {
            String v = p.getProperty("symmetry", "none").trim().toLowerCase();
            if (v.equals("none")) {
                this.symmetry = CTMSymmetry.NONE;
            }
            else if (v.equals("opposite")) {
                this.symmetry = CTMSymmetry.OPPOSITE;
            }
            else if (v.equals("all")) {
                this.symmetry = CTMSymmetry.ALL;
            }
            else {
                Log.info("invalid CTM symmetry: " + v);
                this.symmetry = CTMSymmetry.NONE;
            }
        }
        private void getMatchTiles(Properties p) {
            String v = p.getProperty("matchTiles");
            if (v == null) {
                this.matchTiles = null;
            }
            else {
                String[] tok = tokenize(v.toLowerCase(), " ");
                for (int i = 0; i < tok.length; i++) {
                    String t = tok[i];
                    if (t.endsWith(".png")) {   /* Strip off PNG */
                        t = t.substring(0, t.length() - 4);
                    }
                    if (t.startsWith("/ctm/")) { /* If starts with / */
                        t = t.substring(1); // Strip off leading '/'
                    }
                    tok[i] = t;
                }
                this.matchTiles = tok;
            }
        }
        private String[] parseTileNames(String v) {
            if (v == null) {
                return null;
            }
            else {
                v = v.trim().toLowerCase();
                if (v.length() == 0) {
                    return null;
                }
                ArrayList<String> lst = new ArrayList<String>();
                String[] tok = tokenize(v, " ,");
                for (String t : tok) {
                    if (t.indexOf('-') >= 0) {
                        String[] vtok = tokenize(t, "-");
                        if (vtok.length == 2) {
                            try {
                                int low = Integer.parseInt(vtok[0]);
                                int high = Integer.parseInt(vtok[1]);
                                for (int i = low; i <= high; i++) {
                                    lst.add(String.valueOf(i));
                                }
                            } catch (NumberFormatException nfx) {
                                Log.info("Bad tile name range: " + t);
                            }
                        }
                    }
                    else {
                        lst.add(t);
                    }
                }
                String[] out = new String[lst.size()];
                for (int i = 0; i < out.length; i++) {
                    String vv = lst.get(i);
                    // If not absolute path
                    if ((vv.startsWith("/") == false) && (vv.startsWith("assets/") == false)) {
                        vv = this.basePath + "/" + vv;    // Build path
                    }
                    if (vv.endsWith(".png")) {   // If needed, strip of png
                        vv = vv.substring(0,  vv.length() - 4);
                    }
                    if (vv.startsWith("/ctm/")) {    // If base relative
                        vv = vv.substring(1); // Strip off '/'
                    }
                    out[i] = vv;
                }
                return out;
            }
        }
        // Compute match blocks, based on name
        private void getMatchBlocks() {
            this.matchBlocks = null;
            if (this.name.startsWith("block")) { 
                /* Parse number after "block" */
                int id = -1;
                for (int i = 5; i < this.name.length(); i++) {
                    char c = this.name.charAt(i);
                    if (Character.isDigit(c)) {
                        if (id < 0) 
                            id = (c - '0');
                        else
                            id = (10 * id) + (c - '0');
                    }
                }
                if (id >= 0) {
                	DynmapBlockState bs = DynmapBlockState.getStateByLegacyBlockID(id);
                	if (bs != null) {
                		this.matchBlocks = new int[] { bs.globalStateIndex };
                	}
                }
            }
        }
        
        public CTMProps(Properties p, String fname, CTMTexturePack tp) {
            String v;
            int last_sep = fname.lastIndexOf('/');
            this.name = fname;
            this.basePath = "";
            if(last_sep > 0) {
                this.name = fname.substring(last_sep+1);
                this.basePath = fname.substring(0,  last_sep);
            }
            int last_dot = this.name.lastIndexOf('.');
            if(last_dot > 0) {
                this.name = this.name.substring(0, last_dot);
            }
            this.matchBlocks = getIDList(p, "matchBlocks", "block"); 
            getMatchTiles(p);
            getMethod(p);
            this.tiles = parseTileNames(p.getProperty("tiles"));
            getConnect(p);
            getFaces(p);
            getSymmetry(p);
            getBiomes(p, tp);

            int[] md = parseInts(p, "metadata");
            if (md != null) {
                this.metadata = 0;
                for(int m : md) {
                    this.metadata |= (1 << m);
                }
            }
            this.minY = parseInt(p, "minHeight", -1);
            this.maxY = parseInt(p, "maxHeight", Integer.MAX_VALUE);
            this.renderPass = parseRenderPass(p, "renderPass", -1);
            this.width = parseInt(p, "width", -1);
            this.height = parseInt(p, "height", -1);
            this.weights = parseInts(p, "weights");
            /* Get innerSeams */
            v = p.getProperty("innerSeams");
            if(v != null) {
                this.innerSeams = v.equalsIgnoreCase("true");
            }
        }
        /**
         * Finish initialize
         * @param fname - CTM filename
         * @return true if good
         */
        public boolean isValid(String fname) {
            /* Must have name and base path */
            if ((this.name == null) || (this.name.length() == 0) || (this.basePath == null)) return false;
            // If no match blocks, detect value from name
            if (this.matchBlocks == null) {
                getMatchBlocks();
            }
            // If no match blocks nor tiles, assume name is tile
            if ((this.matchBlocks == null) && (this.matchTiles == null)) {
                this.matchTiles = new String[] { name };
            }
            if (this.method == CTMMethod.NONE) {
                Log.info("No matching method: " + fname);
                return false;
            }
            if (this.connect == CTMConnect.NONE) {
                if (this.matchBlocks != null) {
                    this.connect = CTMConnect.BLOCK;
                }
                else if (this.matchTiles != null) {
                    this.connect = CTMConnect.TILE;
                }
                else {
                    this.connect = CTMConnect.UNKNOWN;
                }
            }
            if (this.connect == CTMConnect.UNKNOWN) {
                Log.info("Bad connect: " + fname);
                return false;
            }
//            if (this.renderPass > 0) {
//                Log.info("Unsupported render pass: " + fname);
//                return false;
//            }
            if ((this.faces & FACE_UNKNOWN) > 0) {
                Log.info("Invalid face: " + fname);
                return false;
            }
            switch (this.method) {
                case CTM:
                    return isValidCtm(fname);
                    
                case HORIZONTAL:
                    return isValidHorizontal(fname);
                    
                case TOP:
                    return isValidTop(fname);
                    
                case RANDOM:
                    return isValidRandom(fname);
                    
                case REPEAT:
                    return isValidRepeat(fname);
                    
                case VERTICAL:
                    return isValidVertical(fname);

                case HORIZONTAL_VERTICAL:
                    return isValidHorizontalVertical(fname);

                case VERTICAL_HORIZONTAL:
                    return isValidVerticalHorizontal(fname);

                case FIXED:
                    return isValidFixed(fname);
                    
                default:
                    Log.info("Unknoen method: " + fname);
                    return false;
            }
        }
        private boolean isValidCtm(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-46");
            }
            if ((this.tiles == null) || (this.tiles.length < 47)) {   // Not enough for CTF
                Log.info("Not enough tiles for CTF method: " + fname);
                return false;
            }
            return true;
        }
        public final boolean exclude(DynmapBlockState block, int face, Context ctx) {
            if ((faces & (1 << ctx.reorient(face))) == 0) {
                return true;
            } else if (this.metadata != -1 && block.stateIndex >= 0 && block.stateIndex < 32) {
                int altMetadata = getOrientationFromMetadata(block) & META_MASK;
                if ((this.metadata & ((1 << block.stateIndex) | (1 << altMetadata))) == 0) {
                    return true;
                }
            }
            return false;
        }
        private boolean isValidHorizontal(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-3");
            }
            if ((this.tiles == null) || (this.tiles.length != 4)) {
                Log.info("Incorrect tile count for Horizonal method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidVertical(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-3");
            }
            if ((this.tiles == null) || (this.tiles.length != 4)) {
                Log.info("Incorrect tile count for Vertical method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidHorizontalVertical(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-6");
            }
            if ((this.tiles == null) || (this.tiles.length != 7)) {
                Log.info("Incorrect tile count for Horizontal+Vertical method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidVerticalHorizontal(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-6");
            }
            if ((this.tiles == null) || (this.tiles.length != 7)) {
                Log.info("Incorrect tile count for Vertical+Horizontal method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidRandom(String fname) {
            if ((this.tiles != null) && (this.tiles.length > 0)) {
                // Make sure same number of weights as tiles
                if ((this.weights != null) && (this.weights.length != this.tiles.length)) {
                    this.weights = null; // Ignore weights if not
                }
                // If weights, compute sums for faster lookup
                if (this.weights != null) {
                    this.sumWeights = new int[this.weights.length];
                    int sum = 0;
                    for (int i = 0; i < this.weights.length; i++) {
                        sum += this.weights[i];
                        this.sumWeights[i] = sum;
                    }
                    this.sumAllWeights = sum;
                }
                return true;
            }
            else {
                Log.info("Tiles required for Random method: " + fname);
                return false;
            }
        }
        private boolean isValidRepeat(String fname)
        {
            if (this.tiles == null) {
                Log.info("Tiles required for Repeat method: " + fname);
                return false;
            }
            // If valid width x height
            else if ((this.width > 0) && (this.width <= 16) && (this.height > 0) && (this.height <= 16)) {
                // If enough tiles for width x height
                if (this.tiles.length != this.width * this.height) {
                    Log.info("Number of tiles does not match repeat size: " + fname);
                    return false;
                }
                return true;
            }
            else {
                Log.info("Invalid dimensions for Repeat method: " + fname);
                return false;
            }
        }
        private boolean isValidFixed(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0");
            }
            if ((this.tiles == null) || (this.tiles.length != 1)) {
                Log.info("Required 1 tile for Fixed method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidTop(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0");
            }
            if ((this.tiles == null) || (this.tiles.length != 1)) {
                Log.info("Requires 1 tile for Top method: " + fname);
                return false;
            }
            return true;
        }
        private void registerTiles(String deftxtpath, String propname) {
            String proppath = propname.substring(0, propname.lastIndexOf('/'));
            if (this.matchTiles != null) {  // If any matching tiles, register them
                this.matchTileIcons = registerTiles(this.matchTiles, deftxtpath, proppath);
            }
            if (this.tiles != null) { // If any result tiles, register them (relative to prop location)
                this.tileIcons = registerTiles(this.tiles, proppath, proppath);
            }
        }
        private int[] registerTiles(String[] tilenames, String deftxtpath, String proppath) {
            if (tilenames == null) return null;
            int[] rslt = new int[tilenames.length];
            for (int i = 0; i < tilenames.length; i++) {
                String tn = tilenames[i];
                String ftn = tn;
                String modname = "minecraft";
                int colonindex = ftn.indexOf(':');
                if (colonindex > 0) {	// Modname:resource?
                	modname = ftn.substring(0, colonindex);
                	ftn = ftn.substring(colonindex+1);
                }
                if (ftn.startsWith("./")) {
                    ftn = proppath + "/" + ftn.substring(2);
                }
                else if (ftn.startsWith("assets/")) {   // Already full path?
                }
                else if (colonindex > 0) {  // modid:path?
                    ftn = String.format("assets/%s/textures/%s", modname, ftn);
                }
                else { // no path (base tile)
                    ftn = String.format(deftxtpath, modname, ftn);
                }
                if (!ftn.endsWith(".png")) {
                    ftn = ftn + ".png"; // Add .png if needed
                }
                if (modname.equals("minecraft")) {
                	modname = null;
                }
                //Log.info(tn + ":registerTiles(" + modname + ":" + ftn + ")");
                // Find file ID, add if needed
                int fid = TexturePack.findOrAddDynamicTileFile(ftn, modname, 1, 1, TileFileFormat.GRID, new String[0]);
                rslt[i] = TexturePack.findOrAddDynamicTile(fid, 0); 
            }
            return rslt;
        }
        
        final boolean shouldConnect(Context ctx, int[] offset) {
            DynmapBlockState neighbor = ctx.mapiter.getBlockTypeAt(offset[0], offset[1], offset[2]);
            if (neighbor.isAir()) {   // Always exclude air...
                return false;
            }
            if (exclude(neighbor, ctx.face, ctx)) {
                return false;
            }
            int neighborOrientation = getOrientationFromMetadata(neighbor);
            if ((ctx.orientation & ~META_MASK) != (neighborOrientation & ~META_MASK)) {
                return false;
            }
            if (this.metadata != -1) {
                if ((ctx.orientation & META_MASK) != (neighborOrientation & META_MASK)) {
                    return false;
                }
            }
            switch (connect) {
                case BLOCK:
                    return neighbor.baseState == ctx.blk.baseState;

                case TILE:
                    int txt = TexturePack.getTextureIDAt(ctx.mapiter, neighbor, ctx.laststep);
                    return (txt == ctx.textid);

                case MATERIAL:
                    return ctx.checkMaterialMatch(neighbor);

                default:
                    return false;
            }
        }
    }
    
    /**
     * Constructor for CTM support, using texture pack loader
     * @param tpl - texture pack loader
     * @param tp - texture pack
     * @param core - core object
     * @param is_rp - if true, resource pack; if false, texture pack
     */
    public CTMTexturePack(TexturePackLoader tpl, TexturePack tp, DynmapCore core, boolean is_rp) {
        ArrayList<String> files = new ArrayList<String>();
        this.tpl = tpl;
        biomenames = core.getBiomeNames();
        Set<String> ent = tpl.getEntries();
        String ctmpath;
        String ctmpath2;
        if (is_rp) {
            ctmpath = "assets/minecraft/mcpatcher/ctm/";
            ctmpath2 = "assets/minecraft/optifine/ctm/";
            vanillatextures = "assets/%1$s/textures/blocks/%2$s";
        }
        else {
            ctmpath = ctmpath2 = "ctm/";
            vanillatextures = "textures/blocks/%2$s";
        }
        for (String name : ent) {
            if((name.startsWith(ctmpath) || name.startsWith(ctmpath2)) && name.endsWith(".properties")) {
                files.add(name);
            }
        }
        ctpfiles = files.toArray(new String[files.size()]);
        Arrays.sort(ctpfiles);
        processFiles(core);
    }
    /**
     * Test if enabled properly
     * @return true if valid
     */
    public boolean isValid() {
        return (ctpfiles.length > 0);
    }
    
    private CTMProps[][] addToList(CTMProps[][] list, BitSet set, int[] keys, CTMProps p) {
        if (keys == null) return list;
        for (int k : keys) {
            if (k < 0) continue;
            if (k >= list.length) {
                list = Arrays.copyOf(list, k+1);
            }
            if (list[k] == null) {
                list[k] = new CTMProps[] { p };
            }
            else {
                int end = list[k].length;
                list[k] = Arrays.copyOf(list[k],  end + 1);
                list[k][end] = p;
            }
            set.set(k);
        }
        return list;
    }
    
    /**
     * Process property files
     */
    private void processFiles(DynmapCore core) {        
        bytilelist = new CTMProps[256][];
        bybaseblockstatelist = new CTMProps[256][];
        mappedtiles = new BitSet();
        mappedblocks = new BitSet();

        /* Fix biome names - all lower case, and strip spaces */
        String[] newbiomes = new String[biomenames.length];
        for(int i = 0; i < biomenames.length; i++) {
            if(biomenames[i] != null)
                newbiomes[i] = biomenames[i].toLowerCase().replace(" ", "");
            else
                biomenames[i] = "";
        }
        biomenames = newbiomes;
        
        for(String f : ctpfiles) {
            InputStream is = null;
            try {
                is = tpl.openTPResource(f);
                Properties p = new Properties();
                if(is != null) {
                    p.load(is);
                    
                    CTMProps ctmp = new CTMProps(p, f, this);
                    if(ctmp.isValid(f)) {
                        ctmp.registerTiles(this.vanillatextures, f);
                        bytilelist = addToList(bytilelist, mappedtiles, ctmp.matchTileIcons, ctmp);
                        bybaseblockstatelist = addToList(bybaseblockstatelist, mappedblocks, ctmp.matchBlocks, ctmp);
                    }
                }
            } catch (IOException iox) {
                Log.severe("Cannot process CTM file - " + f, iox);
            } finally {
                if(is != null) {
                    try { is.close(); } catch (IOException iox) {}
                }
            }
        }
//        for (int i = 0; i < bybaseblockstatelist.length; i++) {
//        	CTMProps[] p = bybaseblockstatelist[i];
//        	if (p != null) {
//        		DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(i);
//        		Log.info(bs.blockName + ":" + bs.stateName + "(" + i + "): legacyID=" + bs.legacyBlockID);
//        		for (CTMProps pp : p) {
//        			Log.info("  " + pp.name + ", faces=" + pp.faces + ",connect=" + pp.connect + ", meta=" + pp.metadata + ", method=" + pp.method);
//        		}
//        	}
//        }
//        for (int i = 0; i < mappedblocks.length(); i++) {
//        	if (mappedblocks.get(i)) {
//        		DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(i);
//        		Log.info("mapped:" + bs.blockName + ":" + bs.stateName + "(" + i + "): legacyID=" + bs.legacyBlockID);
//        	}
//        }
    }

    // Constants for rotateUV
    static final int REL_L = 0;
    static final int REL_DL = 1;
    static final int REL_D = 2;
    static final int REL_DR = 3;
    static final int REL_R = 4;
    static final int REL_UR = 5;
    static final int REL_U = 6;
    static final int REL_UL = 7;

    private class Context {
        final MapIterator mapiter;
        final DynmapBlockState blk;
        final BlockStep laststep;
        final int face;
        int textid;
        final int orientation;
        final int[] reorient;
        final boolean rotateTop;
        final int rotateUV;
        final int x, y, z;
        CTMProps prev1, prev2, prev3;
        Context(MapIterator mapiter, DynmapBlockState blk, BlockStep laststep, int textid) {
            this.mapiter = mapiter;
            this.blk = blk;
            this.laststep = laststep;
            this.face = laststep.getFaceEntered();
            this.textid = textid;
            this.orientation = getOrientationFromMetadata(blk);
            this.x = mapiter.getX();
            this.y = mapiter.getY();
            this.z = mapiter.getZ();
            switch (orientation & ~META_MASK) {
                case ORIENTATION_E_W:
                    this.reorient = ROTATE_UV_MAP[0];
                    this.rotateUV = ROTATE_UV_MAP[0][this.face + 6];
                    this.rotateTop = true;
                    break;
                case ORIENTATION_N_S:
                    this.reorient = ROTATE_UV_MAP[1];
                    this.rotateUV = ROTATE_UV_MAP[1][this.face + 6];
                    this.rotateTop = false;
                    break;
                case ORIENTATION_E_W_2:
                    this.reorient = ROTATE_UV_MAP[2];
                    this.rotateUV = ROTATE_UV_MAP[2][this.face + 6];
                    this.rotateTop = true;
                    break;
                case ORIENTATION_N_S_2:
                    this.reorient = ROTATE_UV_MAP[3];
                    this.rotateUV = ROTATE_UV_MAP[3][this.face + 6];
                    this.rotateTop = false;
                    break;
                default:
                    this.reorient = null;
                    this.rotateUV = 0;
                    this.rotateTop = false;
                    break;
            }
        }    
        final int reorient(int face) {
            if (face < 0 || face > 5 || reorient == null) {
                return face;
            } else {
                return reorient[face];
            }
        }
        final int rotateUV(int neighbor) {
            return (neighbor + rotateUV) & 7;
        }
        final boolean isPrevMatch(CTMProps p) {
            return (p == prev1) || (p == prev2) || (p == prev3);
        }
        final void setMatch(CTMProps p) {
            if (prev1 == null)
                prev1 = p;
            else if (prev2 == null)
                prev2 = p;
            else if (prev3 == null)
                prev3 = p;
        }
        final boolean checkMaterialMatch(DynmapBlockState neighbor) {
            if (blk == neighbor)
                return true;
            else 
            	return blk.material.equals(neighbor.material);
        }
    }
    
    public int mapTexture(MapIterator mapiter, DynmapBlockState blk, BlockStep laststep, int textid, HDShaderState ss) {
        int newtext = -1;
        int gidx = blk.globalStateIndex;
        if ((!this.mappedblocks.get(gidx)) && ((textid < 0) || (!this.mappedtiles.get(textid)))) {
            return textid;
        }
        // See if cached result
        DynLongHashMap cache = null;
        long idx = 0;
        if (ss != null) {
            cache = ss.getCTMTextureCache();
            idx = (mapiter.getBlockKey() << 8) | laststep.ordinal();
            Integer val = (Integer) cache.get(idx);
            if (val != null) {
                return val.intValue();
            }
        }
            
        Context ctx = new Context(mapiter, blk, laststep, textid);

        /* Check for first match */
        if ((textid >= 0) && (textid < bytilelist.length)) {
            newtext = mapTextureByList(bytilelist[textid], ctx);
        }
        if ((newtext < 0) && (gidx < bybaseblockstatelist.length)) {
            newtext = mapTextureByList(bybaseblockstatelist[gidx], ctx);
        }
        /* If matched, check for second match */
        if (newtext >= 0) {
            textid = newtext;   // Switch to new texture
            ctx.textid = newtext;
            // Only do tiles for recursive checks
            if ((textid >= 0) && (textid < bytilelist.length)) {
                newtext = mapTextureByList(bytilelist[textid], ctx);
            }
            /* If matched, check for third match */
            if (newtext >= 0) {
                textid = newtext;   // Switch to new texture
                ctx.textid = newtext;
                if ((textid >= 0) && (textid < bytilelist.length)) {
                    newtext = mapTextureByList(bytilelist[textid], ctx);
                }
                /* If matched, check for last match */
                if (newtext >= 0) {
                    textid = newtext;   // Switch to new texture
                    ctx.textid = newtext;
                    if ((textid >= 0) && (textid < bytilelist.length)) {
                        newtext = mapTextureByList(bytilelist[textid], ctx);
                    }
                    if (newtext >= 0) {
                        textid = newtext;   // Switch to new texture
                    }
                }
            }
        }
        // Add result to cache 
        if (cache != null) {
            cache.put(idx, textid);
        }
        return textid;
    }
    
    private int mapTextureByList(CTMProps[] lst, Context ctx) {
        if (lst == null) return -1;
        for (CTMProps p : lst) {
            if (p == null) continue;
            if (ctx.isPrevMatch(p)) continue;
            int newtxt = mapTextureByProp(p, ctx);
            if (newtxt >= 0) {
                ctx.setMatch(p);
                return newtxt;
            }
        }
        return -1;
    }

    private int mapTextureByProp(CTMProps p, Context ctx) {
        // Test if right face
        if ((ctx.laststep != null) && (p.faces != FACE_ALL)) {
            int face = ctx.laststep.getFaceEntered();
            // If not handled side
            if ((p.faces & (1 << face)) == 0) {
                return -1;
            }
        }
        // Test if right metadata
        if (p.metadata != -1) {
            int meta = ctx.blk.stateIndex;
            if ((p.metadata & (1 << meta)) == 0) {
                return -1;
            }
        }
        // Test if Y coordinate is valid
        int y = ctx.mapiter.getY();
        if ((y < p.minY) || (y > p.maxY)) {
            return -1;
        }
        // Test if excluded
        if (p.exclude(ctx.blk, ctx.laststep.getFaceEntered(), ctx)) {
            return -1;
        }

        // Test if biome is valid
        if (p.biomes != null) {
            int ord = -1; 
            BiomeMap bio = ctx.mapiter.getBiome();
            if (bio != null) {
                ord = bio.getBiomeID();
            }
            for(int i = 0; i < p.biomes.length; i++) {
                if (p.biomes[i] == ord) {
                    ord = -2;
                    break;
                }
            }
            if(ord != -2) {
                return -1;
            }
        }
        // Rest of it is based on method
        switch (p.method) {
            case CTM:
                return mapTextureCtm(p, ctx);

            case HORIZONTAL:
                return mapTextureHorizontal(p, ctx);

            case TOP:
                return mapTextureTop(p, ctx);

            case RANDOM:
                return mapTextureRandom(p, ctx);

            case REPEAT:
                return mapTextureRepeat(p, ctx);

            case VERTICAL:
                return mapTextureVertical(p, ctx);

            case HORIZONTAL_VERTICAL:
                return mapTextureHorizontalVertical(p, ctx);

            case VERTICAL_HORIZONTAL:
                return mapTextureVerticalHorizontal(p, ctx);
                
            case FIXED:
                return mapTextureFixed(p, ctx);

            default:
                return -1;
        }
    }
    // Map texture using CTM method
    private static final int[] neighborMapCtm = new int[]{
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26,
    };
    private int mapTextureCtm(CTMProps p, Context ctx) {
        int face = ctx.face;
        int[][] offsets = NEIGHBOR_OFFSET[face];
        int neighborBits = 0;
        for (int bit = 0; bit < 8; bit++) {
            if (p.shouldConnect(ctx, offsets[bit])) {
                neighborBits |= (1 << bit);
            }
        }
        return p.tileIcons[neighborMapCtm[neighborBits]];
    }
    // Map texture using horizontal method
    private static final int[] neighborMapHorizontal = { 3, 2, 0, 1 };
    private int mapTextureHorizontal(CTMProps p, Context ctx) {
        int face = ctx.face;
        if (face < 0) {
            face = NORTH_FACE;
        } else if (ctx.reorient(face) <= TOP_FACE) {
            return -1;
        }
        int[][] offsets = NEIGHBOR_OFFSET[face];
        int neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(0)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(4)])) {
            neighborBits |= 2;
        }
        return p.tileIcons[neighborMapHorizontal[neighborBits]];
    }
    // Map texture using top method
    private int mapTextureTop(CTMProps p, Context ctx) {
        int face = ctx.face;
        if (face < 0) {
            face = NORTH_FACE;
        } else if (ctx.reorient(face) <= TOP_FACE) {
            return -1;
        }
        int[][] offsets = NEIGHBOR_OFFSET[face];
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(6)])) {
            return p.tileIcons[0];
        }
        return -1;
    }
    // Map texture using random method
    private int mapTextureRandom(CTMProps p, Context ctx) {
        if (p.tileIcons.length == 1) { // Only one?
            return p.tileIcons[0];
        }
        // Apply symmetry
        int face = ctx.face;
        if (face < 0) {
            face = 0;
        }
        face = ctx.reorient(face) / p.symmetry.shift;
        int index = 0;
        // If no weights, consistent weight
        if (p.weights == null) {
            index = getRandom(ctx.x, ctx.y, ctx.z, face, p.tileIcons.length);
        }
        else {
            int rnd = getRandom(ctx.x, ctx.y, ctx.z, face, p.sumAllWeights);
            int[] w = p.sumWeights;
            // Find which range matches
            for (int i = 0; i < w.length; ++i) {
                if (rnd < w[i]) {
                    index = i;
                    break;
                }
            }
        }
        return p.tileIcons[index];
    }
    // Map texture using repeat method
    private int mapTextureRepeat(CTMProps p, Context ctx) {
        int face = ctx.face;
        
        if (face < 0) {
            face = 0;
        }
        //face = face / p.symmetry.shift; // MCPatcher version does nothing with this
        int x;
        int y;
        switch (face) {
            case TOP_FACE:
            case BOTTOM_FACE:
                if (ctx.rotateTop) {
                    x = ctx.z;
                    y = ctx.x;
                } else {
                    x = ctx.x;
                    y = ctx.z;
                }
                break;

            case NORTH_FACE:
                x = -ctx.x - 1;
                y = -ctx.y;
                break;

            case SOUTH_FACE:
                x = ctx.x;
                y = -ctx.y;
                break;

            case WEST_FACE:
                x = ctx.z;
                y = -ctx.y;
                break;

            case EAST_FACE:
                x = -ctx.z - 1;
                y = -ctx.y;
                break;

            default:
                return -1;
        }
        x %= p.width;
        if (x < 0) {
            x += p.width;
        }
        y %= p.height;
        if (y < 0) {
            y += p.height;
        }
        return p.tileIcons[p.width * y + x];
    }
    // Map texture using vertical method
    private static final int[] neighborMapVertical = { 3, 2, 0, 1 };
    private int mapTextureVertical(CTMProps p, Context ctx) {
        if (ctx.reorient(ctx.face) <= TOP_FACE) {
            return -1;
        }
        int[][] offsets = NEIGHBOR_OFFSET[ctx.face];
        int neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(2)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(6)])) {
            neighborBits |= 2;
        }
        return p.tileIcons[neighborMapVertical[neighborBits]];
    }

    // Map texture using h+v method
    // Index into this array is formed from these bit values:
    // 32  16  8
    //     *
    // 1   2   4
    private static final int[] neighborMapHorizontalVertical = new int[]{
        3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
        4, 4, 5, 4, 4, 4, 4, 4, 3, 3, 6, 3, 3, 3, 3, 3,
        3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
        3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
    };
    private int mapTextureHorizontalVertical(CTMProps p, Context ctx) {
        // Do horizontal first : only fall through if no match
        int face = ctx.face;
        if (face < 0) {
            face = NORTH_FACE;
        } else if (ctx.reorient(face) <= TOP_FACE) {
            return -1;
        }
        int[][] offsets = NEIGHBOR_OFFSET[face];
        int neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(0)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(4)])) {
            neighborBits |= 2;
        }
        int idx = neighborMapHorizontal[neighborBits];
        if (idx != 3) {
            return p.tileIcons[idx];
        }
        // Now check diagonals and up down
        neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_DL)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_D)])) {
            neighborBits |= 2;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_DR)])) {
            neighborBits |= 4;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_UR)])) {
            neighborBits |= 8;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_U)])) {
            neighborBits |= 16;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_UL)])) {
            neighborBits |= 32;
        }
        return p.tileIcons[neighborMapHorizontalVertical[neighborBits]];
    }
    // Index into this array is formed from these bit values:
    // 32     16
    // 1   *   8
    // 2       4
    private static final int[] neighborMapVerticalHorizontal = new int[]{
        3, 6, 3, 3, 3, 6, 3, 3, 4, 5, 4, 4, 3, 6, 3, 3,
        3, 6, 3, 3, 3, 6, 3, 3, 3, 6, 3, 3, 3, 6, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 3, 3, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
    };
    // Map texture using vertical+horizontal method
    private int mapTextureVerticalHorizontal(CTMProps p, Context ctx) {
        // Do vertical first : handle if any matches
        if (ctx.reorient(ctx.face) <= TOP_FACE) {
            return -1;
        }
        int[][] offsets = NEIGHBOR_OFFSET[ctx.face];
        int neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(2)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(6)])) {
            neighborBits |= 2;
        }
        int idx = neighborMapVertical[neighborBits];
        if (idx != 3) {
            return p.tileIcons[idx];
        }
        // Else, check horizontal and diagonals
        neighborBits = 0;
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_L)])) {
            neighborBits |= 1;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_DL)])) {
            neighborBits |= 2;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_DR)])) {
            neighborBits |= 4;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_R)])) {
            neighborBits |= 8;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_UR)])) {
            neighborBits |= 16;
        }
        if (p.shouldConnect(ctx, offsets[ctx.rotateUV(REL_UL)])) {
            neighborBits |= 32;
        }
        return p.tileIcons[neighborMapVerticalHorizontal[neighborBits]];
    }

    // Map texture using fixed method
    private int mapTextureFixed(CTMProps p, Context ctx) {
        return p.tileIcons[0];
    }

    private static final long P1 = 0x1c3764a30115L;
    private static final long P2 = 0x227c1adccd1dL;
    private static final long P3 = 0xe0d251c03ba5L;
    private static final long P4 = 0xa2fb1377aeb3L;
    private static final long MULTIPLIER = 0x5deece66dL;
    private static final long ADDEND = 0xbL;

    private static final int getRandom(int x, int y, int z, int face, int modulus)
    {
        long n = P1 * x * (x + ADDEND) + P2 * y * (y + ADDEND) + P3 * z * (z + ADDEND) + P4 * face * (face + ADDEND);
        n = MULTIPLIER * (n + x + y + z + face) + ADDEND;
        return (int) (((n >> 32) ^ n) & 0x7fffffff) % modulus;
    }
    
    private static int[] add(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new RuntimeException("arrays to add are not same length");
        }
        int[] c = new int[a.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
        return c;
    }

    //TODO: need to see if this is still valid or needed
    private static int getOrientationFromMetadata(DynmapBlockState block) {
        int orientation = ORIENTATION_U_D;
        int metadata = block.stateIndex;
        int newMeta = block.stateIndex;
        
        if (block.isLog()) {
            newMeta = metadata & ~0xc;
            switch (metadata & 0xc) {
            case 4:
                orientation = ORIENTATION_E_W;
                break;

            case 8:
                orientation = ORIENTATION_N_S;
                break;

            default:
                break;
            }
        }
        else if (block.blockName.equals(DynmapBlockState.QUARTZ_BLOCK)) {
            switch (metadata) {
            case 3:
                newMeta = 2;
                orientation = ORIENTATION_E_W_2;
                break;

            case 4:
                newMeta = 2;
                orientation = ORIENTATION_N_S_2;
                break;

            default:
                break;
            }
        }

        return orientation | newMeta;
    }
}
