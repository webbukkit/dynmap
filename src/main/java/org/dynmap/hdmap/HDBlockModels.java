package org.dynmap.hdmap;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;

/**
 * Custom block models - used for non-cube blocks to represent the physical volume associated with the block
 * Used by perspectives to determine if rays have intersected a block that doesn't occupy its whole block
 */
public class HDBlockModels {
    private int blockid;
    private int databits;
    private long blockflags[];
    private int nativeres;
    private HashMap<Integer, short[]> scaledblocks;
    
    private static HashMap<Integer, HDBlockModels> models_by_id_data = new HashMap<Integer, HDBlockModels>();
    
    public static class HDScaledBlockModels {
        private short[][][] modelvectors;
        
        public final short[] getScaledModel(int blocktype, int blockdata) {
            if((blocktype > modelvectors.length) || (modelvectors[blocktype] == null) || 
                    (modelvectors[blocktype][blockdata] == null)) {
                return null;
            }
            return modelvectors[blocktype][blockdata];
        }
    }
    
    private static HashMap<Integer, HDScaledBlockModels> scaled_models_by_scale = new HashMap<Integer, HDScaledBlockModels>();

    /* Block models */
    private static HDBlockModels WOOD_STAIR_UP_NORTH = new HDBlockModels(Material.WOOD_STAIRS, 1<<1, 2, new long[] { 
        0x03, 0x03, 
        0x01, 0x01 });
    private static HDBlockModels WOOD_STAIR_UP_SOUTH = new HDBlockModels(Material.WOOD_STAIRS, 1<<0, 2, new long[] {
        0x03, 0x03, 
        0x02, 0x02 });
    private static HDBlockModels WOOD_STAIR_UP_WEST = new HDBlockModels(Material.WOOD_STAIRS, 1<<2, 2, new long[] {
        0x03, 0x03,
        0x00, 0x03 });
    private static HDBlockModels WOOD_STAIR_UP_EAST = new HDBlockModels(Material.WOOD_STAIRS, 1<<3, 2, new long[] {
        0x03, 0x03, 
        0x03, 0x00 });
    private static HDBlockModels COBBLE_STAIR_UP_NORTH = new HDBlockModels(Material.COBBLESTONE_STAIRS, 1<<1, WOOD_STAIR_UP_NORTH);
    private static HDBlockModels COBBLE_STAIR_UP_SOUTH = new HDBlockModels(Material.COBBLESTONE_STAIRS, 1<<0, WOOD_STAIR_UP_SOUTH);
    private static HDBlockModels COBBLE_STAIR_UP_WEST = new HDBlockModels(Material.COBBLESTONE_STAIRS, 1<<2, WOOD_STAIR_UP_WEST);
    private static HDBlockModels COBBLE_STAIR_UP_EAST = new HDBlockModels(Material.COBBLESTONE_STAIRS, 1<<3, WOOD_STAIR_UP_EAST);
    private static HDBlockModels STEP = new HDBlockModels(Material.STEP, 0x0F, 2, new long[] { 
        0x03, 
        0x03 });
    private static HDBlockModels SNOW = new HDBlockModels(Material.SNOW, 0x0F, 4, new long[] {
        0x0F, 0x0F, 0x0F, 0x0F });
    private static HDBlockModels TORCH_UP = new HDBlockModels(Material.TORCH, (1<<5) | (1 << 0), 4, new long[] {
        0x00, 0x02, 0x00, 0x00, 
        0x00, 0x02, 0x00, 0x00 });
    private static HDBlockModels TORCH_SOUTH = new HDBlockModels(Material.TORCH, (1<<1), 4, new long[] {
        0x00, 0x00, 0x00, 0x00, 
        0x00, 0x01, 0x00, 0x00, 
        0x00, 0x02, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels TORCH_NORTH = new HDBlockModels(Material.TORCH, (1<<2), 4, new long[] {
        0x00, 0x00, 0x00, 0x00, 
        0x00, 0x00, 0x08, 0x00, 
        0x00, 0x00, 0x04, 0x00,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels TORCH_WEST = new HDBlockModels(Material.TORCH, (1<<3), 4, new long[] {
        0x00, 0x00, 0x00, 0x00, 
        0x02, 0x00, 0x00, 0x00, 
        0x00, 0x02, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels TORCH_EAST = new HDBlockModels(Material.TORCH, (1<<4), 4, new long[] {
        0x00, 0x00, 0x00, 0x00, 
        0x00, 0x00, 0x00, 0x02, 
        0x00, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels REDSTONETORCHON_UP = new HDBlockModels(Material.REDSTONE_TORCH_ON, (1<<5) | (1 << 0), TORCH_UP);    
    private static HDBlockModels REDSTONETORCHOFF_UP = new HDBlockModels(Material.REDSTONE_TORCH_OFF, (1<<5) | (1 << 0), TORCH_UP);    
    private static HDBlockModels REDSTONETORCHON_NORTH = new HDBlockModels(Material.REDSTONE_TORCH_ON, (1<<2), TORCH_NORTH);    
    private static HDBlockModels REDSTONETORCHOFF_NORTH = new HDBlockModels(Material.REDSTONE_TORCH_OFF, (1<<2), TORCH_NORTH);    
    private static HDBlockModels REDSTONETORCHON_SOUTH = new HDBlockModels(Material.REDSTONE_TORCH_ON, (1<<1), TORCH_SOUTH);    
    private static HDBlockModels REDSTONETORCHOFF_SOUTH = new HDBlockModels(Material.REDSTONE_TORCH_OFF, (1<<1), TORCH_SOUTH);    
    private static HDBlockModels REDSTONETORCHON_EAST = new HDBlockModels(Material.REDSTONE_TORCH_ON, (1<<4), TORCH_EAST);    
    private static HDBlockModels REDSTONETORCHOFF_EAST = new HDBlockModels(Material.REDSTONE_TORCH_OFF, (1<<4), TORCH_EAST);    
    private static HDBlockModels REDSTONETORCHON_WEST = new HDBlockModels(Material.REDSTONE_TORCH_ON, (1<<3), TORCH_WEST);    
    private static HDBlockModels REDSTONETORCHOFF_WEST = new HDBlockModels(Material.REDSTONE_TORCH_OFF, (1<<3), TORCH_WEST);    
    private static HDBlockModels FENCE = new HDBlockModels(Material.FENCE, 0xFFFF, 4, new long[] {
        0x00, 0x06, 0x06, 0x00, 
        0x00, 0x06, 0x06, 0x00, 
        0x00, 0x06, 0x06, 0x00,
        0x00, 0x06, 0x06, 0x00 });
    private static HDBlockModels TRAPDOOR = new HDBlockModels(Material.TRAP_DOOR, 0xFFFF, 4, new long[] {
        0x0F, 0x0F, 0x0F, 0x0F });
    private static HDBlockModels WOODPRESSPLATE = new HDBlockModels(Material.WOOD_PLATE, 0xFFFF, 4, new long[] {
        0x0F, 0x0F, 0x0F, 0x0F });
    private static HDBlockModels STONEPRESSPLATE = new HDBlockModels(Material.STONE_PLATE, 0xFFFF, 4, new long[] {
        0x0F, 0x0F, 0x0F, 0x0F });
    private static HDBlockModels WALLSIGN_NORTH = new HDBlockModels(Material.WALL_SIGN, (1<<4), 4, new long[] {
        0x00, 0x00, 0x00, 0x00,
        0x08, 0x08, 0x08, 0x08,
        0x08, 0x08, 0x08, 0x08,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels WALLSIGN_SOUTH = new HDBlockModels(Material.WALL_SIGN, (1<<5), 4, new long[] {
        0x00, 0x00, 0x00, 0x00,
        0x01, 0x01, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels WALLSIGN_EAST = new HDBlockModels(Material.WALL_SIGN, (1<<2), 4, new long[] {
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x0F,
        0x00, 0x00, 0x00, 0x0F,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels WALLSIGN_WEST = new HDBlockModels(Material.WALL_SIGN, (1<<3), 4, new long[] {
        0x00, 0x00, 0x00, 0x00,
        0x0F, 0x00, 0x00, 0x00,
        0x0F, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels REDSTONEWIRE = new HDBlockModels(Material.REDSTONE_WIRE, 0xFFFF, 8, new long[] {
        0x18, 0x18, 0x18, 0xFF, 0xFF, 0x18, 0x18, 0x18 });
    private static HDBlockModels SIGNPOST_EASTWEST = new HDBlockModels(Material.SIGN_POST, 0xC7C7, 8, new long[] {
        0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
    private static HDBlockModels SIGHPOST_NORTHSOUTH = new HDBlockModels(Material.SIGN_POST, 0x3838, 8, new long[] {
        0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00,
        0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
        0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
        0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
        0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
    
    /**
     * Block definition - copy from other
     */
    public HDBlockModels(Material blocktype, int databits, HDBlockModels m) {
        this.blockid = blocktype.getId();
        this.databits = databits;
        this.nativeres = m.nativeres;
        this.blockflags = m.blockflags;
        for(int i = 0; i < 16; i++) {
            if((databits & (1<<i)) != 0)
                models_by_id_data.put((blockid<<4)+i, this);
        }
    }
    /**
     * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
     * @param blockid - block ID
     * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
     * @param nativeres - native subblocks per edge of cube (up to 64)
     * @param blockflags - array of native^2 long integers representing volume of block (bit X of element (nativeres*Y+Z) is set if that subblock is filled)
     *    if array is short, other elements area are assumed to be zero (fills from bottom of block up)
     */
    public HDBlockModels(Material blocktype, int databits, int nativeres, long[] blockflags) {
        this.blockid = blocktype.getId();
        this.databits = databits;
        this.nativeres = nativeres;
        this.blockflags = new long[nativeres * nativeres];
        System.arraycopy(blockflags, 0, this.blockflags, 0, blockflags.length);
        for(int i = 0; i < 16; i++) {
            if((databits & (1<<i)) != 0)
                models_by_id_data.put((blockid<<4)+i, this);
        }
    }
    /**
     * Test if given native block is filled
     */
    public final boolean isSubblockSet(int x, int y, int z) {
        return ((blockflags[nativeres*y+z] & (1 << x)) != 0);
    }
    /**
     * Get scaled map of block: will return array of alpha levels, corresponding to how much of the
     * scaled subblocks are occupied by the original blocks (indexed by Y*res*res + Z*res + X)
     * @param res - requested scale (res subblocks per edge of block)
     * @return array of alpha values (0-255), corresponding to resXresXres subcubes of block
     */
    public short[] getScaledMap(int res) {
        if(scaledblocks == null) { scaledblocks = new HashMap<Integer, short[]>(); }
        short[] map = scaledblocks.get(Integer.valueOf(res));
        if(map == null) {
            map = new short[res*res*res];
            if(res == nativeres) {
                for(int i = 0; i < blockflags.length; i++) {
                    for(int j = 0; j < nativeres; j++) {
                        if((blockflags[i] & (1 << j)) != 0)
                            map[res*i+j] = 255;
                    }
                }
            }
            /* If scaling from smaller sub-blocks to larger, each subblock contributes to 1-2 blocks
             * on each axis:  need to calculate crossovers for each, and iterate through smaller
             * blocks to accumulate contributions
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
                        weights[idx] = (offsets[idx] + res) - v;
                    }
                }
                /* Now, use weights and indices to fill in scaled map */
                for(int y = 0, off = 0; y < res; y++) {
                    int ind_y = offsets[y];
                    int wgt_y = weights[y];
                    for(int z = 0; z < res; z++) {
                        int ind_z = offsets[z];
                        int wgt_z = weights[z];
                        for(int x = 0; x < res; x++, off++) {
                            int ind_x = offsets[x];
                            int wgt_x = weights[x];
                            int raw_w = 0;
                            for(int xx = 0; xx < 2; xx++) {
                                int wx = (xx==0)?wgt_x:(nativeres-wgt_x);
                                if(wx == 0) continue;
                                for(int yy = 0; yy < 2; yy++) {
                                    int wy = (yy==0)?wgt_y:(nativeres-wgt_y);
                                    if(wy == 0) continue;
                                    for(int zz = 0; zz < 2; zz++) {
                                        int wz = (zz==0)?wgt_z:(nativeres-wgt_z);
                                        if(wz == 0) continue;
                                        if(isSubblockSet(ind_x+xx, ind_y+yy, ind_z+zz)) {
                                            raw_w += wx*wy*wz;
                                        }
                                    }
                                }
                            }
                            map[off] = (short)((255*raw_w) / (nativeres*nativeres*nativeres));
                        }
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
                /* Now, use weights and indices to fill in scaled map */
                for(int y = 0; y < nativeres; y++) {
                    int ind_y = offsets[y];
                    int wgt_y = weights[y];
                    for(int z = 0; z < nativeres; z++) {
                        int ind_z = offsets[z];
                        int wgt_z = weights[z];
                        for(int x = 0; x < nativeres; x++) {
                            if(isSubblockSet(x, y, z)) {
                                int ind_x = offsets[x];
                                int wgt_x = weights[x];
                                for(int xx = 0; xx < 2; xx++) {
                                    int wx = (xx==0)?wgt_x:(res-wgt_x);
                                    if(wx == 0) continue;
                                    for(int yy = 0; yy < 2; yy++) {
                                        int wy = (yy==0)?wgt_y:(res-wgt_y);
                                        if(wy == 0) continue;
                                        for(int zz = 0; zz < 2; zz++) {
                                            int wz = (zz==0)?wgt_z:(res-wgt_z);
                                            if(wz == 0) continue;
                                            map[(ind_y+yy)*res*res + (ind_z+zz)*res + (ind_x+xx)] +=
                                                wx*wy*wz;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                for(int i = 0; i < map.length; i++) {
                    map[i] = (short)(255*map[i]/(nativeres*nativeres*nativeres));
                }
            }
            scaledblocks.put(Integer.valueOf(res), map);
        }
        return map;
    }
    
    /**
     * Get scaled set of models for all modelled blocks 
     * @param scale
     * @return
     */
    public static HDScaledBlockModels   getModelsForScale(int scale) {
        HDScaledBlockModels model = scaled_models_by_scale.get(Integer.valueOf(scale));
        if(model == null) {
            model = new HDScaledBlockModels();
            short[][][] blockmodels = new short[256][][];
            for(HDBlockModels m : models_by_id_data.values()) {
                short[][] row = blockmodels[m.blockid];
                if(row == null) {
                    row = new short[16][];
                    blockmodels[m.blockid] = row; 
                }
                short[] smod = null;
                for(int i = 0; i < 16; i++) {
                    if((m.databits & (1 << i)) != 0) {
                        if(smod == null) smod = m.getScaledMap(scale);
                        row[i] = smod;
                    }
                }
            }
            model.modelvectors = blockmodels;
            scaled_models_by_scale.put(scale, model);
        }
        return model;
    }
}
