package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

// v1.13+ redstone wire renderer
public class RedstoneWireStateRenderer extends RedstoneWireRenderer {
	@Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ctx.getBlockType().stateIndex;
    	// Check each direction - value=up(0),side(1),none(2)
    	// Compute patch index
    	int pidx = 0;
    	switch (idx % 3) {	// west (XN)
    	case 0:	// up
    		pidx += 0x11;
    		break;
    	case 1: // side
    		pidx += 0x01;
    		break;
    	}
    	switch ((idx / 432) % 3) {	// east (XP)
    	case 0:	// up
    		pidx += 0x22;
    		break;
    	case 1: // side
    		pidx += 0x02;
    		break;
    	}
    	switch ((idx / 144) % 3) {	// north (ZN)
    	case 0:	// up
    		pidx += 0x44;
    		break;
    	case 1: // side
    		pidx += 0x04;
    		break;
    	}
    	switch ((idx / 3) % 3) {	// south (ZP)
    	case 0:	// up
    		pidx += 0x88;
    		break;
    	case 1: // side
    		pidx += 0x08;
    		break;
    	}
        return getMesh(pidx);
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
