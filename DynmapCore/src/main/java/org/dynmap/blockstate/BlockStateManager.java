package org.dynmap.blockstate;

import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;

// Handler for managing mapping of block states
public class BlockStateManager {
    private static IBlockStateHandler DEFAULT = new MetadataBlockStateHandler();
    
    private IBlockStateHandler[] blockHandlers = new IBlockStateHandler[DynmapCore.BLOCKTABLELEN];
    
    public BlockStateManager() {
        // Default to all meta for now
        for (int i = 0; i < blockHandlers.length; i++) {
            blockHandlers[i] = DEFAULT;
        }
    }
    /**
     * Get state count for given block ID
     * @param blkid - Block ID
     * @return state cnt
     */
    public int getBlockStateCount(int blkid) {
        if ((blkid >= 0) && (blkid < blockHandlers.length)) {
            return blockHandlers[blkid].getBlockStateCount();
        }
        return DEFAULT.getBlockStateCount();
    }
    /**
     * Get state for current block
     * @param blkctx = block context
     * @return state index
     */
    public int getBlockStateIndex(MapDataContext mdc) {
        DynmapBlockState blk = mdc.getBlockType();
        return (blk != null) ? blk.stateIndex : 0;
    }
}
