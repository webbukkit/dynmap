package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/**
 * Simple renderer for handling single and double chests (1.13+)
 */
public class ChestStateRenderer extends ChestRenderer {
    protected enum ChestData {
        SINGLE_WEST, SINGLE_SOUTH, SINGLE_EAST, SINGLE_NORTH, LEFT_WEST, LEFT_SOUTH, LEFT_EAST, LEFT_NORTH, RIGHT_WEST, RIGHT_SOUTH, RIGHT_EAST, RIGHT_NORTH
    };

    private ChestData[] byIndex = { 
            ChestData.SINGLE_NORTH, ChestData.RIGHT_NORTH, ChestData.LEFT_NORTH, 
            ChestData.SINGLE_SOUTH, ChestData.RIGHT_SOUTH, ChestData.LEFT_SOUTH, 
            ChestData.SINGLE_WEST, ChestData.RIGHT_WEST, ChestData.LEFT_WEST, 
            ChestData.SINGLE_EAST, ChestData.RIGHT_EAST , ChestData.LEFT_EAST };
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = ctx.getBlockType().stateIndex / 2;    // Ignore waterlogged for model
        if (!double_chest) {    // If single only, skip to 3x index of state
            idx = idx * 3;
        }
        return models[byIndex[idx].ordinal()];
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
