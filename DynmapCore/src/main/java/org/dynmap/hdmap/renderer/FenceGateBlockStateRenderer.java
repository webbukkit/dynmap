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
        
        private void buildMeshes(RenderPatchFactory rpf) {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            for(int dat = 0; dat < 8; dat++) {
                // Add posts
                if ((dat & 1) == 0) {
                    addBox(rpf, list, 0.0, 0.125, 0.3125, 1.0, 0.4375, 0.5625);
                    addBox(rpf, list, 0.875, 1.0, 0.3125, 1.0, 0.4375, 0.5625);
                    if ((dat & 4) == 0) {   // If closed
                        addBox(rpf, list, 0.375, 0.625, 0.375, 0.9375, 0.4375, 0.5625);
                        addBox(rpf, list, 0.625, 0.875, 0.375, 0.5625, 0.4375, 0.5625);
                        addBox(rpf, list, 0.625, 0.875, 0.75, 0.9375, 0.4375, 0.5625);
                        addBox(rpf, list, 0.125, 0.375, 0.375f, 0.5625, 0.4375, 0.5625);
                        addBox(rpf, list, 0.125, 0.375, 0.75, 0.9375, 0.4375, 0.5625);
                    }
                    else if ((dat & 3) == 0) {
                        addBox(rpf, list, 0.0, 0.125, 0.375, 0.9375, 0.8125, 0.9375);
                        addBox(rpf, list, 0.875,  1.0, 0.375, 0.9375, 0.8125, 0.9375);
                        addBox(rpf, list, 0.0, 0.125, 0.375, 0.5625, 0.5625, 0.8125);
                        addBox(rpf, list, 0.875, 1.0, 0.375, 0.5625, 0.5625, 0.8125);
                        addBox(rpf, list, 0.0, 0.125, 0.75, 0.9375, 0.5625, 0.8125);
                        addBox(rpf, list, 0.875, 1.0, 0.75, 0.9375, 0.5625, 0.8125);
                    }
                    else {
                        addBox(rpf, list, 0.0, 0.125, 0.375, 0.9375, 0.0625, 0.1875);
                        addBox(rpf, list, 0.875, 1.0, 0.375, 0.9375, 0.0625, 0.1875);
                        addBox(rpf, list, 0.0, 0.125, 0.375, 0.5625, 0.1875, 0.4375);
                        addBox(rpf, list, 0.875, 1.0, 0.375, 0.5625, 0.1875, 0.4375);
                        addBox(rpf, list, 0.0, 0.125, 0.75, 0.9375, 0.1875, 0.4375);
                        addBox(rpf, list, 0.875, 1.0, 0.75, 0.9375, 0.1875, 0.4375);
                    }
                }
                else {
                    addBox(rpf, list, 0.4375, 0.5625, 0.3125, 1.0, 0.0, 0.125);
                    addBox(rpf, list, 0.4375, 0.5625, 0.3125, 1.0, 0.875, 1.0);
                    if ((dat & 4) == 0) {   // If closed
                        addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.9375, 0.375, 0.625);
                        addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.625, 0.875);
                        addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9375, 0.625, 0.875);
                        addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.125, 0.375);
                        addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9375, 0.125, 0.375);
                    }
                    else if ((dat & 3) == 3) {
                        addBox(rpf, list, 0.8125, 0.9375, 0.375, 0.9375, 0.0, 0.125);
                        addBox(rpf, list, 0.8125, 0.9375, 0.375, 0.9375, 0.875, 1.0);
                        addBox(rpf, list, 0.5625, 0.8125, 0.375, 0.5625, 0.0, 0.125);
                        addBox(rpf, list, 0.5625, 0.8125, 0.375, 0.5625, 0.875, 1.0);
                        addBox(rpf, list, 0.5625, 0.8125, 0.75, 0.9375, 0.0, 0.125);
                        addBox(rpf, list, 0.5625, 0.8125, 0.75, 0.9375, 0.875, 1.0);
                    }
                    else {
                        addBox(rpf, list, 0.0625, 0.1875, 0.375, 0.9375, 0.0, 0.125);
                        addBox(rpf, list, 0.0625, 0.1875, 0.375, 0.9375, 0.875, 1.0);
                        addBox(rpf, list, 0.1875, 0.4375, 0.375, 0.5625, 0.0, 0.125);
                        addBox(rpf, list, 0.1875, 0.4375, 0.375, 0.5625, 0.875, 1.0);
                        addBox(rpf, list, 0.1875, 0.4375, 0.75, 0.9375, 0.0, 0.125);
                        addBox(rpf, list, 0.1875, 0.4375, 0.75, 0.9375, 0.875, 1.0);
                    }
                }
                
                meshes[dat] = list.toArray(new RenderPatch[list.size()]);
                
                list.clear();
            }
        }
        
        @Override
        public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
            int meta = ctx.getBlockType().stateIndex;
            // 32 states: meta%2=powered|unpowered, (meta/2)%2=open/closed, (meta/4)%2=in-wall/not-in-wall, (meta/8)%4=n/s/w/e
            return meshes[(meta >> 1) & 0xF];   // Don't care about powered: models are 0-15
        }    
    }

