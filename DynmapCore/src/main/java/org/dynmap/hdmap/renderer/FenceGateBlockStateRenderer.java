package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

// v1.13+ fence gate renderer
public class FenceGateBlockStateRenderer extends CustomRenderer {
        private static final int TEXTURE_SIDES = 0;
        private static final int TEXTURE_TOP = 1;
        private static final int TEXTURE_BOTTOM = 2;

        private static final int IDX_OPEN = 0;
        private static final int IDX_CLOSED = 1;
        private static final int IDX_INWALL = 0;
        private static final int IDX_NOTINWALL = 2;
        private static final int IDX_NORTH = 0;
        private static final int IDX_SOUTH = 4;
        private static final int IDX_WEST = 8;
        private static final int IDX_EAST = 12;
        // Meshes, indexed by idx%2=open/closed, (idx/2)%2=in-wall/not-in-wall, (idx/4)%4=n/s/w/e
        protected RenderPatch[][] meshes = new RenderPatch[16][];
        
        @Override
        public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
            if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
                return false;
            buildMeshes(rpf);
            return true;
        }

        @Override
        public int getMaximumTextureCount() {
            return 3;
        }
        
        private static final int[] patchlist = { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES };

        private void addBox(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax)  {
            addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
        }
        private void rotateModel(RenderPatchFactory rpf, int srcidx, int destidx, int rot) {
        	meshes[destidx] = new RenderPatch[meshes[srcidx].length];
        	for (int i = 0; i < meshes[destidx].length; i++) {
        		meshes[destidx][i] = rpf.getRotatedPatch(meshes[srcidx][i], 0, rot, 0, patchlist[i%6]);
        	}
        }
        private void buildMeshes(RenderPatchFactory rpf) {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            // Build model for closed fence gate facing south (default)
            addBox(rpf, list, 0.0, 0.125, 0.3125, 1.0, 0.4375, 0.5625);
            addBox(rpf, list, 0.875, 1.0, 0.3125, 1.0, 0.4375, 0.5625);
            addBox(rpf, list, 0.375, 0.5, 0.375, 0.9375, 0.4375, 0.5625);
            addBox(rpf, list, 0.5, 0.625, 0.375, 0.9375, 0.4375, 0.5625);
            addBox(rpf, list, 0.125, 0.375, 0.375, 0.5625, 0.4375, 0.5625);
            addBox(rpf, list, 0.125, 0.375, 0.75, 0.9375, 0.4375, 0.5625);
            addBox(rpf, list, 0.625, 0.875, 0.375, 0.5625, 0.4375, 0.5625);
            addBox(rpf, list, 0.625, 0.875, 0.75, 0.9375, 0.4375, 0.5625);
            meshes[IDX_SOUTH | IDX_CLOSED | IDX_NOTINWALL] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
            // Build rotated versions
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_NOTINWALL, IDX_NORTH | IDX_CLOSED | IDX_NOTINWALL, 180);
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_NOTINWALL, IDX_WEST | IDX_CLOSED | IDX_NOTINWALL, 90);
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_NOTINWALL, IDX_EAST | IDX_CLOSED | IDX_NOTINWALL, 270);
            // Build model for open fence gate facing south (default)
            addBox(rpf, list, 0.0, 0.125, 0.3125, 1.0, 0.4375, 0.5625);
            addBox(rpf, list, 0.875, 1.0, 0.3125, 1.0, 0.4375, 0.5625);
            addBox(rpf, list, 0.0, 0.125, 0.375, 0.9375, 0.8125, 0.9375);
            addBox(rpf, list, 0.875, 1.0, 0.375, 0.9375, 0.8125, 0.9375);
            addBox(rpf, list, 0.0, 0.125, 0.375, 0.5625, 0.5625, 0.8125);
            addBox(rpf, list, 0.0, 0.125, 0.75, 0.9375, 0.5625, 0.8125);
            addBox(rpf, list, 0.875, 1.0, 0.375, 0.5625, 0.5625, 0.8125);
            addBox(rpf, list, 0.875, 1.0, 0.75, 0.9375, 0.5625, 0.8125);
            meshes[IDX_SOUTH | IDX_OPEN | IDX_NOTINWALL] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
            // Build rotated versions
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_NOTINWALL, IDX_NORTH | IDX_OPEN | IDX_NOTINWALL, 180);
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_NOTINWALL, IDX_WEST | IDX_OPEN | IDX_NOTINWALL, 90);
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_NOTINWALL, IDX_EAST | IDX_OPEN | IDX_NOTINWALL, 270);
            // Build model for closed fence gate facing south in-wall (default)
            addBox(rpf, list, 0.0, 0.125, 0.125, 0.8125, 0.4375, 0.5625);
            addBox(rpf, list, 0.875, 1.0, 0.125, 0.8125, 0.4375, 0.5625);
            addBox(rpf, list, 0.375, 0.5, 0.1875, 0.75, 0.4375, 0.5625);
            addBox(rpf, list, 0.5, 0.625, 0.1875, 0.75, 0.4375, 0.5625);
            addBox(rpf, list, 0.125, 0.375, 0.1875, 0.375, 0.4375, 0.5625);
            addBox(rpf, list, 0.125, 0.375, 0.5625, 0.75, 0.4375, 0.5625);
            addBox(rpf, list, 0.625, 0.875, 0.1875, 0.375, 0.4375, 0.5625);
            addBox(rpf, list, 0.625, 0.875, 0.5625, 0.75, 0.4375, 0.5625);
            meshes[IDX_SOUTH | IDX_CLOSED | IDX_INWALL] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
            // Build rotated versions
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_INWALL, IDX_NORTH | IDX_CLOSED | IDX_INWALL, 180);
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_INWALL, IDX_WEST | IDX_CLOSED | IDX_INWALL, 90);
            rotateModel(rpf, IDX_SOUTH | IDX_CLOSED | IDX_INWALL, IDX_EAST | IDX_CLOSED | IDX_INWALL, 270);
            // Build model for open fence gate facing south in-wall (default)
            addBox(rpf, list, 0.0, 0.125, 0.125, 0.8125, 0.4375, 0.5625);            
            addBox(rpf, list, 0.875, 1.0, 0.125, 0.8125, 0.4375, 0.5625);
            addBox(rpf, list, 0.0, 0.125, 0.1875, 0.75, 0.8125, 0.9375);
            addBox(rpf, list, 0.875, 1.0, 0.1875, 0.75, 0.8125, 0.9375);
            addBox(rpf, list, 0.0, 0.125, 0.1875, 0.375, 0.5625, 0.8125);
            addBox(rpf, list, 0.0, 0.125, 0.5625, 0.75, 0.5625, 0.8125);
            addBox(rpf, list, 0.875, 1.0, 0.1875, 0.375, 0.5625, 0.8125);
            addBox(rpf, list, 0.875, 1.0, 0.5625, 0.75, 0.5625, 0.8125);
            meshes[IDX_SOUTH | IDX_OPEN | IDX_INWALL] = list.toArray(new RenderPatch[list.size()]);
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_INWALL, IDX_NORTH | IDX_OPEN | IDX_INWALL, 180);
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_INWALL, IDX_WEST | IDX_OPEN | IDX_INWALL, 90);
            rotateModel(rpf, IDX_SOUTH | IDX_OPEN | IDX_INWALL, IDX_EAST | IDX_OPEN | IDX_INWALL, 270);
            list.clear();
        }
        
        @Override
        public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
            int meta = ctx.getBlockType().stateIndex;
            // 32 states: meta%2=powered|unpowered, (meta/2)%2=open/closed, (meta/4)%2=in-wall/not-in-wall, (meta/8)%4=n/s/w/e
            return meshes[(meta >> 1) & 0xF];   // Don't care about powered: models are 0-15
        }    
        @Override
        public boolean isOnlyBlockStateSensitive() {
        	return true;
        }
    }

