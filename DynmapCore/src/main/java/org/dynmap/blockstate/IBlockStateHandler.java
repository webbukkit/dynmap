package org.dynmap.blockstate;

import org.dynmap.renderer.MapDataContext;

/**
 * Interface for block state handlers
 */
public interface IBlockStateHandler {
    /**
     * Return number of distinct blocks states
     * @return state count
     */
    public int getBlockStateCount();
    /**
     * Map current block to state
     * @param mdc - current map data context
     * @return state index
     */
    public int getBlockStateIndex(MapDataContext mdc);
}
