package org.dynmap.hdmap;

import java.util.BitSet;
import java.util.HashMap;

import org.dynmap.renderer.DynmapBlockState;

public class HDBlockVolumetricModel extends HDBlockModel {
    /* Volumetric model specific attributes */
    private long blockflags[];
    private int nativeres;
    private HashMap<Integer, short[]> scaledblocks;
    /**
     * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
     * (for volumetric models)
     * @param bs - block state
     * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
     * @param nativeres - native subblocks per edge of cube (up to 64)
     * @param blockflags - array of native^2 long integers representing volume of block (bit X of element (nativeres*Y+Z) is set if that subblock is filled)
     *    if array is short, other elements area are assumed to be zero (fills from bottom of block up)
     * @param blockset - ID of set of blocks defining model
     */
    public HDBlockVolumetricModel(DynmapBlockState bs, BitSet databits, int nativeres, long[] blockflags, String blockset) {
        super(bs, databits, blockset);
        
        this.nativeres = nativeres;
        this.blockflags = new long[nativeres * nativeres];
        System.arraycopy(blockflags, 0, this.blockflags, 0, blockflags.length);
    }
    /**
     * Test if given native block is filled (for volumetric model)
     * 
     * @param x - X coordinate
     * @param y - Y coordinate
     * @param z - Z coordinate
     * @return true if set, false if not
     */
    public final boolean isSubblockSet(int x, int y, int z) {
        return ((blockflags[nativeres*y+z] & (1 << x)) != 0);
    }
    /**
     * Set subblock value (for volumetric model)
     * 
     * @param x - X coordinate
     * @param y - Y coordinate
     * @param z - Z coordinate
     * @param isset - true = set, false = clear
     */
    public final void setSubblock(int x, int y, int z, boolean isset) {
        if(isset)
            blockflags[nativeres*y+z] |= (1 << x);
        else
            blockflags[nativeres*y+z] &= ~(1 << x);            
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
                        weights[idx] = (offsets[idx]*res + res) - v;
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
                            if(map[off] > 255) map[off] = 255;
                            if(map[off] < 0) map[off] = 0;
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
                        weights[idx] = (offsets[idx]*nativeres + nativeres) - v;
                    }
                }
                /* Now, use weights and indices to fill in scaled map */
                long accum[] = new long[map.length];
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
                                            accum[(ind_y+yy)*res*res + (ind_z+zz)*res + (ind_x+xx)] +=
                                                wx*wy*wz;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                for(int i = 0; i < map.length; i++) {
                    map[i] = (short)(accum[i]*255/nativeres/nativeres/nativeres);
                    if(map[i] > 255) map[i] = 255;                            
                    if(map[i] < 0) map[i] = 0;
                }
            }
            scaledblocks.put(Integer.valueOf(res), map);
        }
        return map;
    }
    @Override
    public int getTextureCount() {
        return 6;
    }
}

