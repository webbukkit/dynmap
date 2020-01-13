package org.dynmap.hdmap;

import java.util.BitSet;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

public class HDBlockPatchModel extends HDBlockModel {
    /* Patch model specific attributes */
    private PatchDefinition[] patches;
    private final int max_texture;
    /**
     * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
     * (for patch models)
     * @param bs - block state
     * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
     * @param patches - list of patches (surfaces composing model)
     * @param blockset - ID of set of blocks defining model
     */
    public HDBlockPatchModel(DynmapBlockState bs, BitSet databits, PatchDefinition[] patches, String blockset) {
        super(bs, databits, blockset);
        this.patches = patches;
        int max = 0;
        for(int i = 0; i < patches.length; i++) {
            if((patches[i] != null) && (patches[i].textureindex > max))
                max = patches[i].textureindex;
        }
        this.max_texture = max + 1;
    }
    /**
     * Get patches for block model (if patch model)
     * @return patches for model
     */
    public final PatchDefinition[] getPatches() {
        return patches;
    }
    /**
     * Set patches for block
     */
    public final void setPatches(PatchDefinition[] p) {
        patches = p;
    }
    
    @Override
    public int getTextureCount() {
        return max_texture;
    }
}

