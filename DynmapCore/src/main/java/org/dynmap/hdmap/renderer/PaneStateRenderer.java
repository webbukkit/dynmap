package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/*
 * Glass pane / iron fence renderer for 1.13+
 */
public class PaneStateRenderer extends PaneRenderer {
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        // State map: 32 states, bit 4=east, bit 3=north, bit 2=south, bit 1=waterlogged, bit 0=west
        int idx = ctx.getBlockType().stateIndex;
        int meshidx = (((idx & 0x10) == 0) ? SIDE_XP : 0) | (((idx & 0x08) == 0) ? SIDE_ZN : 0) | (((idx & 0x04) == 0) ? SIDE_ZP : 0) | (((idx & 0x01) == 0) ? SIDE_XN : 0);
        return meshes[meshidx];
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
