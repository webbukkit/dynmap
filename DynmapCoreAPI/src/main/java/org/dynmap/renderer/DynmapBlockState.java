package org.dynmap.renderer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// This represents a distinct block state value for a simple block from the world data.
// Each distinct persistent block state from the world data will map to exactly one instance of this class, such that
// for a given distinct world data value (blockID:meta for pre 1.13, blockstateid for 1.13+) will map to a specific instance
// of this class.  In this way, if block state X and block state Y are the same, they can be compared by (X = Y), versus
// needing the cost of an equals() or similar comparator.  Also, use in IdentityHashMaps and the like will be enabled.
public class DynmapBlockState {
    // Block state index (0-based offset of state vs first state for given block type)
    public final int stateIndex;
    // Base block state (state index = 0) - first state for block corresponding to the block for this state
    public final DynmapBlockState baseState;
    // Block name (minecraft:name for vanilla, modid:name for custom)
    public final String blockName;
    // Block state string (attrib=value, attrib=value, etc for 1.13+, meta=value for 1.12 or earlier)
    public final String stateName;
    public final String[] stateList;
    // Overall state index (uniquely assigned autoincrement number for state: packed, zero based)
    public final int globalStateIndex;
    // Legacy block ID (if defined - otherwise -1)
    public final int legacyBlockID;
    // Light attenuation level (levels dropped when light tries to pass through block)
    public int lightAttenuation;
    // List of block states (only defined on base block), indexed by stateIndex (null if single state base block)
    private DynmapBlockState[] states;
    private int stateLastIdx = 0;
    private ConcurrentHashMap<String, DynmapBlockState> lookup;
    // Full name for state (base name, or base name[state name])
    private final String fullName;
    // Material string
    public final String material;
    // Next global state index
    private static int nextGlobalStateIndex = 0;
    // Match flags
    private int matchflags;
    private static int MATCH_AIR = 1 << 0;
    private static int MATCH_WATER = 1 << 1;
    private static int MATCH_SNOW = 1 << 2;
    private static int MATCH_LOG = 1 << 3;
    private static int MATCH_GRASS = 1 << 4;
    private static int MATCH_WATERLOGGED = 1 << 5;
    private static int MATCH_LEAVES = 1 << 6;
    private static int MATCH_SOLID = 1 << 7;
    // Map of base blocks by name
    private static HashMap<String, DynmapBlockState> blocksByName = new HashMap<String, DynmapBlockState>();
    // Map of states by global state index
    private static HashMap<Integer, DynmapBlockState> blocksByIndex = new HashMap<Integer, DynmapBlockState>();
    // Map of base states by legacy ID
    private static HashMap<Integer, DynmapBlockState> blocksByLegacyID = new HashMap<Integer, DynmapBlockState>();
    // Array lookup by global state index (faster than map)
    private static DynmapBlockState[] blockArrayByIndex = null;
    private static DynmapBlockState[] blockArrayByLegacyID = null;
    
    // Well known block names (some versions might need to overwrite these)
    public static String AIR_BLOCK = "minecraft:air";
    public static String STONE_BLOCK = "minecraft:stone";
    public static String GRASS_BLOCK = "minecraft:grass";
    public static String GOLD_ORE_BLOCK = "minecraft:gold_ore";
    public static String IRON_ORE_BLOCK = "minecraft:iron_ore";
    public static String COAL_ORE_BLOCK = "minecraft:coal_ore";
    public static String LAPIS_ORE_BLOCK = "minecraft:lapis_ore";
    public static String DIAMOND_ORE_BLOCK = "minecraft:diamond_ore";
    public static String REDSTONE_ORE_BLOCK = "minecraft:redstone_ore";
    public static String LIT_REDSTONE_ORE_BLOCK = "minecraft:lit_redstone_ore";
    public static String EMERALD_ORE_BLOCK = "minecraft:emerald_ore";
    public static String QUARTZ_ORE_BLOCK = "minecraft:quartz_ore";
    public static String NETHER_GOLD_ORE_BLOCK = "minecraft:nether_gold_ore";
    public static String DEEPSLATE_GOLD_ORE_BLOCK = "minecraft:deepslate_gold_ore";
    public static String DEEPSLATE_IRON_ORE_BLOCK = "minecraft:deepslate_iron_ore";
    public static String DEEPSLATE_COAL_ORE_BLOCK = "minecraft:deepslate_coal_ore";
    public static String DEEPSLATE_LAPIS_ORE_BLOCK = "minecraft:deepslate_lapis_ore";
    public static String DEEPSLATE_DIAMOND_ORE_BLOCK = "minecraft:deepslate_diamond_ore";
    public static String DEEPSLATE_REDSTONE_ORE_BLOCK = "minecraft:deepslate_redstone_ore";
    public static String DEEPSLATE_EMERALD_ORE_BLOCK = "minecraft:deepslate_emerald_ore";
    public static String DEEPSLATE_COPPER_ORE_BLOCK = "minecraft:deepslate_copper_ore";
    public static String COPPER_ORE_BLOCK = "minecraft:copper_ore";
    public static String DEEPSLATE_BLOCK = "minecraft:deepslate";
    public static String NETHERRACK_BLOCK = "minecraft:netherrack";
    public static String LOG_BLOCK = "minecraft:log";
    public static String LOG2_BLOCK = "minecraft:log2";
    public static String LEAVES_BLOCK = "minecraft:leaves";
    public static String LEAVES2_BLOCK = "minecraft:leaves2";
    public static String GLASS_BLOCK = "minecraft:glass";
    public static String WOODEN_DOOR_BLOCK = "minecraft:wooden_door";
    public static String IRON_DOOR_BLOCK = "minecraft:iron_door";
    public static String SNOW_LAYER_BLOCK = "minecraft:snow_layer";
    public static String SNOW_BLOCK = "minecraft:snow";
    public static String ICE_BLOCK = "minecraft:ice";
    public static String QUARTZ_BLOCK = "minecraft:quartz_block";
    public static String WATER_BLOCK = "minecraft:water";
    public static String FLOWING_WATER_BLOCK = "minecraft:flowing_water";

    /** Names of log blocks: mod versions will need to add to this */
    private static HashSet<String> log_blocks = new HashSet<String>(Arrays.asList(LOG_BLOCK, LOG2_BLOCK));
    /** Names of water blocks: mod versions will need to add to this */
    private static HashSet<String> water_blocks = new HashSet<String>(Arrays.asList(WATER_BLOCK, FLOWING_WATER_BLOCK));

    // Well known base blocks - air
    public static final DynmapBlockState AIR = new DynmapBlockState(null, 0, AIR_BLOCK, "", "AIR", 0);

    private static DynmapBlockState still_water = null;

    public static class Builder {
    	private DynmapBlockState base;
    	private int stateidx;
    	private String blkname;
    	private String statename;
    	private String material;
    	private int legacyblkid;
    	private int matchflags;
    	private int lightblocked;
    	public Builder() {
    		reset();
    	}
    	public void reset() { base = null; blkname = null; statename = null; material = null; legacyblkid = -1; matchflags = 0; lightblocked = 0; }
    	public Builder setBaseState(DynmapBlockState blkbase) { this.base = blkbase; return this; }
    	public Builder setStateIndex(int sidx) { this.stateidx = sidx; return this; }
    	public Builder setBlockName(String blkname) { this.blkname = blkname; return this; }
    	public Builder setStateName(String stname) { this.statename = stname; return this; }
    	public Builder setMaterial(String mat) { this.material = mat; return this; }
    	public Builder setLegacyBlockID(int legacybid) { this.legacyblkid = legacybid; return this; }
    	public Builder setAir() { this.matchflags |= MATCH_AIR; return this; }
        public Builder setLog() { this.matchflags |= MATCH_LOG; return this; }
        public Builder setCustomWater() { this.matchflags |= MATCH_WATER; return this; }
        public Builder setWaterlogged() { this.matchflags |= MATCH_WATERLOGGED; return this; }
        public Builder setLeaves() { this.matchflags |= MATCH_LEAVES; return this; }
        public Builder setSolid() { this.matchflags |= MATCH_SOLID; return this; }
        public Builder setBlocksLight() { this.lightblocked = 15; return this; }
        public Builder setAttenuatesLight(int levels) { this.lightblocked = levels; return this; }
        public DynmapBlockState build() {
        	DynmapBlockState bs = new DynmapBlockState(base, stateidx, blkname, statename, material, legacyblkid, lightblocked);
        	if ((matchflags & MATCH_AIR) != 0) bs.setAir();
        	if ((matchflags & MATCH_LOG) != 0) bs.setLog();
           	if ((matchflags & MATCH_WATERLOGGED) != 0) bs.setWaterlogged();
        	if ((matchflags & MATCH_LEAVES) != 0) bs.setLeaves();
        	if ((matchflags & MATCH_SOLID) != 0) bs.setSolid();
           	if ((matchflags & MATCH_WATER) != 0) bs.addWaterBlock(blkname);
           	reset();	// Reset after build complete
           	return bs;
        }
    }

    /**
     * Constructor for block state
     * @param base - base block state (null if first/only state for block)
     * @param stateidx - index of state (0-based relative to the base block state)
     * @param blkname - block name, in modid:blockname format (minecraft:blockname for vanilla)
     * @param statename - block state name: null if single state block, "attrib=value,..." for 1.13+, "meta=value" for 1.12-
     * @param material - material name string
     */
    public DynmapBlockState(DynmapBlockState base, int stateidx, String blkname, String statename, String material) {
    	this(base, stateidx, blkname, statename, material, -1, -1);
    }
    /**
     * Constructor for block state
     * @param base - base block state (null if first/only state for block)
     * @param stateidx - index of state (0-based relative to the base block state)
     * @param blkname - block name, in modid:blockname format (minecraft:blockname for vanilla)
     * @param statename - block state name: null if single state block, "attrib=value,..." for 1.13+, "meta=value" for 1.12-
     * @param material - material name string
     * @param legacyblkid - legacy block ID (if defined), otherwise -1
     */
    public DynmapBlockState(DynmapBlockState base, int stateidx, String blkname, String statename, String material, int legacyblkid) {
    	this(base, stateidx, blkname, statename, material, legacyblkid, -1);
    }
    private DynmapBlockState(DynmapBlockState base, int stateidx, String blkname, String statename, String material, int legacyblkid, int lightAtten) {
    	
    	// If we generated lookup arrays, flush them and complain about it
    	if (blockArrayByIndex != null) {
    		blockArrayByIndex = null;
    		blockArrayByLegacyID = null;
    		System.err.println("Error: DynmapBlockState updated after arrays generated");
    		Thread.dumpStack();
    	}
        globalStateIndex = (nextGlobalStateIndex++);    // Assign index
        if (base == null) base = this;
        baseState = base;
        stateIndex = stateidx;
        legacyBlockID = legacyblkid;
        this.material = material;
        if (blkname.indexOf(':') == -1) {   // No mod:, assume minecraft:
            blkname = "minecraft:" + blkname;
        }
        blockName = blkname;
        stateName = (statename != null) ? statename : "";
        if (base != this) { // If we aren't base block state
            if (base.states == null) {  // If no state list yet
            	base.states = new DynmapBlockState[Math.max((stateidx+1)*3 / 2, 16)]; // Enough for us to fit
                Arrays.fill(base.states, AIR);
                base.states[0] = base;  // Add base state as index 0
               	base.lookup = new ConcurrentHashMap<String, DynmapBlockState>();	// Initialize lookup cache
            }
            else if (base.states.length <= stateidx) {  // Not enough room
                // Resize it
            	DynmapBlockState[] newstates = Arrays.copyOf(base.states, Math.max((stateidx+1)*3 / 2, 16));
                Arrays.fill(newstates, base.states.length, newstates.length, AIR);
                base.states = newstates;
            }
            base.states[stateidx] = this;
            base.stateLastIdx = Math.max(base.stateLastIdx, stateidx);
        }
        stateList = stateName.toLowerCase().split(",");
        // If base block state, add to map
        if (base == this) { 
            blocksByName.put(blkname, this);
            if (legacyBlockID >= 0) {
            	blocksByLegacyID.put(legacyBlockID, this);
            }
        }
        if (stateName.length() > 0) {
            fullName = blockName + "[" + stateName + "]";
        }
        else {
            fullName = blockName;
        }
        // Add to lookup by global ID
        blocksByIndex.put(globalStateIndex, this);
        // Precompute match flags
        matchflags |= blockName.equals(AIR_BLOCK) ? MATCH_AIR : 0;
        matchflags |= isWater(blockName) ? MATCH_WATER : 0;
        matchflags |= (blockName.equals(SNOW_BLOCK) || blockName.equals(SNOW_LAYER_BLOCK)) ? MATCH_SNOW : 0;
        matchflags |= blockName.equals(GRASS_BLOCK) ? MATCH_GRASS : 0;
        matchflags |= log_blocks.contains(blockName) ? MATCH_LOG : 0;
        // If water block, set singleton
        if (this.blockName.equals(WATER_BLOCK) && (this == this.baseState)) {
            still_water = this;
        }
    	lightAttenuation = lightAtten;
    }
    /**
     * Generate static lookup arrays once all BlockStates initialized
     */
    public static void finalizeBlockStates() {
    	// Build blockArrayByIndex
    	blockArrayByIndex = new DynmapBlockState[nextGlobalStateIndex];
    	Arrays.fill(blockArrayByIndex, AIR);
    	for (Map.Entry<Integer, DynmapBlockState> rec : blocksByIndex.entrySet()) {
    		blockArrayByIndex[rec.getKey().intValue()] = rec.getValue();    		
    	}
    	// Build blockArrayByLegacyID
    	int maxLegacyID = 0;
    	for (Map.Entry<Integer, DynmapBlockState> rec : blocksByLegacyID.entrySet()) {
    		maxLegacyID = Math.max(maxLegacyID, rec.getKey());
    	}
    	blockArrayByLegacyID = new DynmapBlockState[maxLegacyID+1];
    	for (Map.Entry<Integer, DynmapBlockState> rec : blocksByLegacyID.entrySet()) {
    		blockArrayByLegacyID[rec.getKey().intValue()] = rec.getValue();    		
    	}    	
    }
    /**
     * Get state for same base block with given index
     * @param idx - index number
     * @return new state, or AIR if invalid index
     */
    public final DynmapBlockState getStateByIndex(int idx) {
        if (baseState.states == null) {
            return (idx == 0) ? this : null;
        }
        return ((idx >= 0) && (idx <= baseState.stateLastIdx)) ? baseState.states[idx] : DynmapBlockState.AIR;
    }
    /**
     * Find base block state by block name
     * @param name - block name (modid:name)
     * @return base block state, or AIR if not found
     */
    public static final DynmapBlockState getBaseStateByName(String name) {
        DynmapBlockState blk = blocksByName.get(name);
        if ((blk == null) && (name.indexOf(':') == -1)) {
            blk = blocksByName.get("minecraft:" + name);
            if (blk == null) {	// If still null, see if legacy ID number
            	try {
            		int v = Integer.parseInt(name);
            		if (v >= 0) {
            			blk = blocksByLegacyID.get(v);
            		}
            	} catch (NumberFormatException nfx) {
            	}
            }
        }
        return (blk != null) ? blk : AIR;
    }
    /**
     * Find block state by block name and state index
     * @param name - block name (modid:name)
     * @param idx - state index
     * @return base block state, or AIR if not found
     */
    public static final DynmapBlockState getStateByNameAndIndex(String name, int idx) {
        DynmapBlockState blk = getBaseStateByName(name);
        if (blk != null) {
            blk = blk.getState(idx);
        }
        return (blk != null) ? blk : AIR;
    }
    /**
     * Find block state by global index
     * @param gidx - global index
     * @return block state, or AIR if not found
     */
    public static final DynmapBlockState getStateByGlobalIndex(int gidx) {
    	if (blockArrayByIndex != null) {
    		try {
    			return blockArrayByIndex[gidx];
    		} catch (ArrayIndexOutOfBoundsException aioob) {
    			return AIR;
    		}
    	}
        DynmapBlockState bs = blocksByIndex.get(gidx);
        return (bs != null) ? bs : AIR;
    }
    /**
     * Find block state by legacy ID
     * @param legacyid - legacy ID
     * @return block base state, or null if not found
     */
    public static final DynmapBlockState getStateByLegacyBlockID(int legacyid) {
    	if (blockArrayByLegacyID != null) {
    		try {
    			return blockArrayByLegacyID[legacyid];
    		} catch (ArrayIndexOutOfBoundsException aioob) {
    			return null;
    		}
    	}    	
    	return blocksByLegacyID.get(legacyid);
    }
    /**
     * Find block state by name and state name
     * @param name - block name
     * @param statename - state name
     * @return base block state, or AIR if not found
     */
    public static final DynmapBlockState getStateByNameAndState(String name, String statename) {
        DynmapBlockState blk = getBaseStateByName(name);
        DynmapBlockState rslt = AIR;
        if (blk != null) {
        	rslt = blk;
        	if (blk.states != null) {
        		// See if we have this in cache
        		rslt = blk.lookup.get(statename);
        		if (rslt != null) return rslt;
        		
            	rslt = AIR;	// Assume miss
        	    String[] statelist = statename.toLowerCase().split(",");
        		for (DynmapBlockState bb : blk.states) {
        		    boolean match = true;
        		    for (int i = 0; i < statelist.length; i++) {
        		        boolean valmatch = false;
        		        for (int j = 0; j < bb.stateList.length; j++) {
        		            if (statelist[i].equals(bb.stateList[j])) {
        		                valmatch = true;
        		                break;
        		            }
        		        }
        		        if (!valmatch) {
        		            match = false;
        		            break;
        		        }
        		    }
        			if (match) {
        				rslt = bb;
        				break;
        			}
        		}        	
        		blk.lookup.put(statename, rslt);	// Cache the lookup
        	}
        }
        return rslt;
    }
    /**
     * Get current top of range of block state global indexes, plus 1
     * @return length of global block state index range (N, for 0-(N-1))
     */
    public static final int getGlobalIndexMax() {
        return nextGlobalStateIndex;
    }
    /**
     * Return true if block is not air
     * @return true if not air, false if air
     */
    public final boolean isNotAir() {
        return (matchflags & MATCH_AIR) == 0;
    }
    /**
     * Return true if block is air
     * @return true if air, false if air
     */
    public final boolean isAir() {
        return (matchflags & MATCH_AIR) != 0;
    }
    /**
     * Set to air
     */
    public final void setAir() {
    	matchflags |= MATCH_AIR;
    }
    /**
     * Return number of states under base state
     * @return state count
     */
    public final int getStateCount() {
        return baseState.stateLastIdx + 1;
    }
    /**
     * Get nth state index within base block state
     * @param idx - state index
     * @return state, or null if not defined
     */
    public final DynmapBlockState getState(int idx) {
        if (baseState.states == null) {
            return (idx == 0) ? this : AIR;
        }
        else {
            return (idx <= baseState.stateLastIdx) ? baseState.states[idx] : AIR; 
        }
    }
    /**
     * Test if block is log block
     */
    public final boolean isLog() {
        return (matchflags & MATCH_LOG) != 0;
    }
    /**
     * Set to log block
     */
    public final void setLog() {
        matchflags |= MATCH_LOG;
    }
    /**
     * Test if block is water block
     */
    public final boolean isWater() {
        return (matchflags & MATCH_WATER) != 0;
    }
    /**
     * Test if block name is water block
     */
    public static boolean isWater(String blockname) {
        return water_blocks.contains(blockname);
    }
    /**
     * Add water block name
     * @param name - name of custom water block
     */
    public void addWaterBlock(String name) {
        water_blocks.add(name);
        // Apply to existing blocks
        DynmapBlockState bbs = DynmapBlockState.getBaseStateByName(name);
        if (bbs.isNotAir()) {
            for (int i = 0; i < bbs.getStateCount(); i++) {
                bbs.states[i].matchflags |= MATCH_WATER;
            }
        }
    }
    /**
     * Test if block is snow block
     */
    public final boolean isSnow() {
        return (matchflags & MATCH_SNOW) != 0;
    }
    /**
     * Test if block is grass block
     */
    public final boolean isGrass() {
        return (matchflags & MATCH_GRASS) != 0;
    }
    /**
     * Test if block is waterlogged (in block filled with water)
     */
    public final boolean isWaterlogged() {
        return (matchflags & MATCH_WATERLOGGED) != 0;
    }
    /**
     * Set state to be waterlogged (block filled with water)
     */
    public final void setWaterlogged() {
    	matchflags |= MATCH_WATERLOGGED;
    }
    /**
     * Test if block is water OR waterlogged (block filled with water)
     */
    public final boolean isWaterFilled() {
        return (matchflags & (MATCH_WATERLOGGED | MATCH_WATER)) != 0;
    }
    /**
     * Test if block is leaves
     */
    public final boolean isLeaves() {
        return (matchflags & MATCH_LEAVES) != 0;
    }
    /**
     * Set state to be leaves
     */
    public final void setLeaves() {
    	matchflags |= MATCH_LEAVES;
    }
    /**
     * Test for matching blockname
     */
    public boolean is(String blkname) {
        return blockName.equals(blkname);
    }
    /**
     * Test for matching base state
     */
    public boolean matchingBaseState(DynmapBlockState blk) {
        return this.baseState == blk.baseState;
    }
    /**
     * Get liquid state (null if not waterlogged or otherwise immmersed)
     */
    public DynmapBlockState getLiquidState() {
        if (isWaterlogged()) {
            return still_water;
        }
        return null;
    }
    /**
     * Test if block is solid
     */
    public boolean isSolid() {
    	return (matchflags & MATCH_SOLID) != 0;
    }
    /**
     * Set to solid
     */
    public void setSolid() {
    	matchflags |= MATCH_SOLID;
    }
    /**
     * Get light attenuation
     */
    public final int getLightAttenuation() {
    	if (lightAttenuation < 0) {
    		lightAttenuation = (isWater() || isWaterlogged() || isLeaves()) ? 1 : 0;
    	}
    	return lightAttenuation;    	
    }
    /**
     * To printable string
     */
    @Override
    public String toString() {
        return fullName;
    }
}
