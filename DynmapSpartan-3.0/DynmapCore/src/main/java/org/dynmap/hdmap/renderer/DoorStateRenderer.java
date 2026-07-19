package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/**
 * Standard door renderer : two textures (top, bottom)
 */
public class DoorStateRenderer extends DoorRenderer {
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = ctx.getBlockType().stateIndex;  // Get our state index
        // Index: (idx%2)=powered/unpowered, (idx/2)%2=open/closed, (idx/4)%2=left/right, (idx/8)%2=upper/lower, (idx/16)%4=n/s/w/e
        int midx = 0;
        midx |= ((idx & 0x8) == 0) ? 8 : 0;	// If upper half
        midx |= ((idx & 0x2) == 0) ? 4 : 0;	// If open
        midx |= ((idx & 0x4) == 0) ? 0 : 16;	// If left hinge
        switch ((idx >> 4) & 0x3) {
        case 0:	// N
        	midx += 3;
        	break;
        case 1: // S
        	midx += 1;
        	break;
        case 2: // W
        	midx += 2;
        	break;
        case 3: // E
        	midx += 0;
        	break;
        }        
        return models[midx];
    }
}
