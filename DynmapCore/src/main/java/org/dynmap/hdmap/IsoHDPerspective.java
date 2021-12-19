package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import org.dynmap.DynmapWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.MapTypeState;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.BlockStep;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.Polygon;
import org.dynmap.utils.TileFlags;
import org.dynmap.utils.Vector3D;
import org.json.simple.JSONObject;

public class IsoHDPerspective implements HDPerspective {
    private final String name;
    private final int hashcode;
    /* View angles */
    public final double azimuth;  /* Angle in degrees from looking north (0), east (90), south (180), or west (270) */
    public final double inclination;  /* Angle in degrees from horizontal (0) to vertical (90) */
    public final double maxheight;
    public final double minheight;
    /* Coordinate space for tiles consists of a plane (X, Y), corresponding to the projection of each tile on to the
     * plane of the bottom of the world (X positive to the right, Y positive to the top), with Z+ corresponding to the
     * height above this plane on a vector towards the viewer).  Logically, this makes the parallelogram representing the
     * space contributing to the tile have consistent tile-space X,Y coordinate pairs for both the top and bottom faces
     * Note that this is a classic right-hand coordinate system, while minecraft's world coordinates are left handed 
     * (X+ is south, Y+ is up, Z+ is east). 
     */
    /* Transformation matrix for taking coordinate in world-space (x, y, z) and finding coordinate in tile space (x, y, z) */
    private final Matrix3D world_to_map;
    private final Matrix3D map_to_world;
    
    /* Scale for default tiles */
    private final int basemodscale;
    
    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /* Maximum and minimum inclinations */
    public static final double MAX_INCLINATION = 90.0;
    public static final double MIN_INCLINATION = 20.0;
    
    /* Maximum and minimum scale */
    public static final int MAX_SCALE = 64;
    public static final int MIN_SCALE = 1;
    
    private boolean need_biomedata = false;
    private boolean need_rawbiomedata = false;

    private static final BlockStep [] semi_steps = { BlockStep.Y_PLUS, BlockStep.X_MINUS, BlockStep.X_PLUS, BlockStep.Z_MINUS, BlockStep.Z_PLUS };
    
    private class OurPerspectiveState implements HDPerspectiveState {
        DynmapBlockState blocktype = DynmapBlockState.AIR;
        DynmapBlockState lastblocktype = DynmapBlockState.AIR;
        Vector3D top, bottom, direction;
        int px, py;
        BlockStep laststep = BlockStep.Y_MINUS;
        
        BlockStep stepx, stepy, stepz;

        /* Scaled models for non-cube blocks */
        private final HDScaledBlockModels scalemodels;
        private final int modscale;

        /* Section-level raytrace variables */
        int sx, sy, sz;
        double sdt_dx, sdt_dy, sdt_dz;
        double st_next_x, st_next_y, st_next_z;
        /* Raytrace state variables */
        double dx, dy, dz;
        int x, y, z;
        double dt_dx, dt_dy, dt_dz, t;
        int n;
        int x_inc, y_inc, z_inc;        
        double t_next_y, t_next_x, t_next_z;
        boolean nonairhit;
        /* Subblock tracer state */
        int mx, my, mz;
        double xx, yy, zz;
        double mdt_dx;
        double mdt_dy;
        double mdt_dz;
        double togo;
        double mt_next_x, mt_next_y, mt_next_z;
        int subalpha;
        double mt;
        double mtend;
        int mxout, myout, mzout;
        /* Patch state and work variables */
        Vector3D v0 = new Vector3D();
        Vector3D vS = new Vector3D();
        Vector3D d_cross_uv = new Vector3D();
        // Double max patch count to be safe for patches + water patches
        double patch_t[] = new double[2*HDBlockModels.getMaxPatchCount()];
        double patch_u[] = new double[2*HDBlockModels.getMaxPatchCount()];
        double patch_v[] = new double[2*HDBlockModels.getMaxPatchCount()];
        BlockStep patch_step[] = new BlockStep[2*HDBlockModels.getMaxPatchCount()];
        int patch_id[] = new int[2*HDBlockModels.getMaxPatchCount()];
        int cur_patch = -1;
        double cur_patch_u;
        double cur_patch_v;
        double cur_patch_t;
        
        int[] subblock_xyz = new int[3];
        final MapIterator mapiter;
        final boolean isnether;
        boolean skiptoair;
        final int worldheight;
        final LightLevels llcache[];
        
        /* Cache for custom model patch lists */
        private final DynLongHashMap custom_meshes;
        private final DynLongHashMap custom_fluid_meshes;

        public OurPerspectiveState(MapIterator mi, boolean isnether, int scaled) {
            mapiter = mi;
            this.isnether = isnether;
            worldheight = mapiter.getWorldHeight();
            llcache = new LightLevels[4];
            for(int i = 0; i < llcache.length; i++)
                llcache[i] = new LightLevels();
            custom_meshes = new DynLongHashMap();
            custom_fluid_meshes = new DynLongHashMap();
            modscale = basemodscale << scaled;
            scalemodels = HDBlockModels.getModelsForScale(basemodscale << scaled);
        }
        
        private final void updateSemitransparentLight(LightLevels ll) {
        	int emitted = 0, sky = 0;
        	for(int i = 0; i < semi_steps.length; i++) {
        	    BlockStep s = semi_steps[i];
        		mapiter.stepPosition(s);
        		int v = mapiter.getBlockEmittedLight();
        		if(v > emitted) emitted = v;
        		v = mapiter.getBlockSkyLight();
        		if(v > sky) sky = v;
        		mapiter.unstepPosition(s);
        	}
        	ll.sky = sky;
        	ll.emitted = emitted;
        }
        /**
         * Update sky and emitted light 
         */
        private final void updateLightLevel(DynmapBlockState blk, LightLevels ll) {
            /* Look up transparency for current block */
            BlockTransparency bt = HDBlockStateTextureMap.getTransparency(blk);
            switch(bt) {
            	case TRANSPARENT:
            		ll.sky = mapiter.getBlockSkyLight();
            		ll.emitted = mapiter.getBlockEmittedLight();
            		break;
            	case OPAQUE:
        			if(HDBlockStateTextureMap.getTransparency(lastblocktype) != BlockTransparency.SEMITRANSPARENT) {
                		mapiter.unstepPosition(laststep);  /* Back up to block we entered on */
                		if(mapiter.getY() < worldheight) {
                		    ll.sky = mapiter.getBlockSkyLight();
                		    ll.emitted = mapiter.getBlockEmittedLight();
                		} else {
                		    ll.sky = 15;
                		    ll.emitted = 0;
                		}
                		mapiter.stepPosition(laststep);
        			}
        			else {
                		mapiter.unstepPosition(laststep);  /* Back up to block we entered on */
                		updateSemitransparentLight(ll);
                		mapiter.stepPosition(laststep);
        			}
        			break;
            	case SEMITRANSPARENT:
            		updateSemitransparentLight(ll);
            		break;
        		default:
                    ll.sky = mapiter.getBlockSkyLight();
                    ll.emitted = mapiter.getBlockEmittedLight();
                    break;
            }
        }
        /**
         * Get light level - only available if shader requested it
         */
        @Override
        public final void getLightLevels(LightLevels ll) {
            updateLightLevel(blocktype, ll);
        }
        /**
         * Get sky light level - only available if shader requested it
         */
        @Override
        public final void getLightLevelsAtStep(BlockStep step, LightLevels ll) {
            if(((step == BlockStep.Y_MINUS) && (y == 0)) ||
                    ((step == BlockStep.Y_PLUS) && (y == worldheight))) {
                getLightLevels(ll);
                return;
            }
            BlockStep blast = laststep;
            mapiter.stepPosition(step);
            laststep = blast;
            updateLightLevel(mapiter.getBlockType(), ll);
            mapiter.unstepPosition(step);
            laststep = blast;
        }
        /**
         * Get current block type ID
         */
        @Override
        public final DynmapBlockState getBlockState() { return blocktype; }
        /**
         * Get direction of last block step
         */
        @Override
        public final BlockStep getLastBlockStep() { return laststep; }
        /**
         * Get perspective scale
         */
        @Override
        public final double getScale() { return modscale; }
        /**
         * Get start of current ray, in world coordinates
         */
        @Override
        public final Vector3D getRayStart() { return top; }
        /**
         * Get end of current ray, in world coordinates
         */
        @Override
        public final Vector3D getRayEnd() { return bottom; }
        /**
         * Get pixel X coordinate
         */
        public final int getPixelX() { return px; }
        /**
         * Get pixel Y coordinate
         */
        public final int getPixelY() { return py; }
        /**
         * Get map iterator
         */
        public final MapIterator getMapIterator() { return mapiter; }
        /**
         * Return submodel alpha value (-1 if no submodel rendered)
         */
        public int getSubmodelAlpha() {
            return subalpha;
        }
        /**
         * Initialize raytrace state variables
         */
        private void raytrace_init() {
            /* Compute total delta on each axis */
            dx = Math.abs(direction.x);
            dy = Math.abs(direction.y);
            dz = Math.abs(direction.z);
            /* Compute parametric step (dt) per step on each axis */
            dt_dx = 1.0 / dx;
            dt_dy = 1.0 / dy;
            dt_dz = 1.0 / dz;
            /* Initialize parametric value to 0 (and we're stepping towards 1) */
            t = 0;
            /* Compute number of steps and increments for each */
            n = 1;

            /* Initial section coord */
            sx = fastFloor(top.x/16.0);
            sy = fastFloor(top.y/16.0);
            sz = fastFloor(top.z/16.0);
            /* Compute parametric step (dt) per step on each axis */
            sdt_dx = 16.0 / dx;
            sdt_dy = 16.0 / dy;
            sdt_dz = 16.0 / dz;
            
            /* If perpendicular to X axis */
            if (dx == 0) {
                x_inc = 0;
                st_next_x = Double.MAX_VALUE;
                stepx = BlockStep.X_PLUS;
                mxout = modscale;
            }
            /* If bottom is right of top */
            else if (bottom.x > top.x) {
                x_inc = 1;
                n += fastFloor(bottom.x) - x;
                st_next_x = (fastFloor(top.x/16.0) + 1 - (top.x/16.0)) * sdt_dx;
                stepx = BlockStep.X_PLUS;
                mxout = modscale;
            }
            /* Top is right of bottom */
            else {
                x_inc = -1;
                n += x - fastFloor(bottom.x);
                st_next_x = ((top.x/16.0) - fastFloor(top.x/16.0)) * sdt_dx;
                stepx = BlockStep.X_MINUS;
                mxout = -1;
            }
            /* If perpendicular to Y axis */
            if (dy == 0) {
                y_inc = 0;
                st_next_y = Double.MAX_VALUE;
                stepy = BlockStep.Y_PLUS;
                myout = modscale;
            }
            /* If bottom is above top */
            else if (bottom.y > top.y) {
                y_inc = 1;
                n += fastFloor(bottom.y) - y;
                st_next_y = (fastFloor(top.y/16.0) + 1 - (top.y/16.0)) * sdt_dy;
                stepy = BlockStep.Y_PLUS;
                myout = modscale;
            }
            /* If top is above bottom */
            else {
                y_inc = -1;
                n += y - fastFloor(bottom.y);
                st_next_y = ((top.y/16.0) - fastFloor(top.y/16.0)) * sdt_dy;
                stepy = BlockStep.Y_MINUS;
                myout = -1;
            }
            /* If perpendicular to Z axis */
            if (dz == 0) {
                z_inc = 0;
                st_next_z = Double.MAX_VALUE;
                stepz = BlockStep.Z_PLUS;
                mzout = modscale;
            }
            /* If bottom right of top */
            else if (bottom.z > top.z) {
                z_inc = 1;
                n += fastFloor(bottom.z) - z;
                st_next_z = (fastFloor(top.z/16.0) + 1 - (top.z/16.0)) * sdt_dz;
                stepz = BlockStep.Z_PLUS;
                mzout = modscale;
            }
            /* If bottom left of top */
            else {
                z_inc = -1;
                n += z - fastFloor(bottom.z);
                st_next_z = ((top.z/16.0) - fastFloor(top.z/16.0)) * sdt_dz;
                stepz = BlockStep.Z_MINUS;
                mzout = -1;
            }
            /* Walk through scene */
            laststep = BlockStep.Y_MINUS; /* Last step is down into map */
            nonairhit = false;
            skiptoair = isnether;
        }

        private final boolean handleSubModel(short[] model, HDShaderState[] shaderstate, boolean[] shaderdone) {
            boolean firststep = true;
            
            while(!raytraceSubblock(model, firststep)) {
                boolean done = true;
                for(int i = 0; i < shaderstate.length; i++) {
                    if(!shaderdone[i])
                        shaderdone[i] = shaderstate[i].processBlock(this);
                    done = done && shaderdone[i];
                }
                /* If all are done, we're out */
                if(done)
                    return true;
                nonairhit = true;
                firststep = false;
            }
            // Set current block as last block for any incomplete shaders
            for(int i = 0; i < shaderstate.length; i++) {
                if(!shaderdone[i])
                    // Update with current block as last block
                    shaderstate[i].setLastBlockState(blocktype);
            }
            return false;
        }
        
        private final int handlePatch(PatchDefinition pd, int hitcnt) {
            /* Compute origin of patch */
            v0.x = (double)x + pd.x0;
            v0.y = (double)y + pd.y0;
            v0.z = (double)z + pd.z0;
            /* Compute cross product of direction and V vector */
            d_cross_uv.set(direction);
            d_cross_uv.crossProduct(pd.v);
            /* Compute determinant - inner product of this with U */
            double det = pd.u.innerProduct(d_cross_uv);
            /* If parallel to surface, no intercept */
            switch(pd.sidevis) {
                case TOP:
                    if (det < 0.000001) {
                        return hitcnt;
                    }
                    break;
                case BOTTOM:
                    if (det > -0.000001) {
                        return hitcnt;
                    }
                    break;
                case BOTH:
                case FLIP:
                    if((det > -0.000001) && (det < 0.000001)) {
                        return hitcnt;
                    }
                    break;
            }
            double inv_det = 1.0 / det; /* Calculate inverse determinant */
            /* Compute distance from patch to ray origin */
            vS.set(top);
            vS.subtract(v0);
            /* Compute u - slope times inner product of offset and cross product */
            double u = inv_det * vS.innerProduct(d_cross_uv);
            if ((u <= pd.umin) || (u >= pd.umax)) {
                return hitcnt;
            }
            /* Compute cross product of offset and U */
            vS.crossProduct(pd.u);
            /* Compute V using slope times inner product of direction and cross product */
            double v = inv_det * direction.innerProduct(vS);
            // Check constrains: v must be below line from (umin, vmax) to (umax, vmaxatumax)
            double urel = (u > pd.umin) ? ((u - pd.umin) / (pd.umax - pd.umin)) : 0.0; 
            double vmaxatu = pd.vmax + (pd.vmaxatumax - pd.vmax) * urel;
            // Check constrains: v must be above line from (umin, vmin) to (umax, vminatumax)
            double vminatu = pd.vmin + (pd.vminatumax - pd.vmin) * urel;
            if ((v <= vminatu) || (v >= vmaxatu)) {
                return hitcnt;
            }
            /* Compute parametric value of intercept */
            double t = inv_det * pd.v.innerProduct(vS);
            if (t > 0.000001) { /* We've got a hit */
                patch_t[hitcnt] = t;
                patch_u[hitcnt] = u;
                patch_v[hitcnt] = v;
                patch_id[hitcnt] = pd.textureindex;
                if(det > 0) {
                    patch_step[hitcnt] = pd.step.opposite();
                }
                else {
                    if (pd.sidevis == SideVisible.FLIP) {
                        patch_u[hitcnt] = 1 - u;
                    }
                    patch_step[hitcnt] = pd.step;
                }
                hitcnt++;
            }
            return hitcnt;
        }
        
        private final boolean handlePatches(RenderPatch[] patches, HDShaderState[] shaderstate, boolean[] shaderdone, DynmapBlockState fluidstate, RenderPatch[] fluidpatches) {
            int hitcnt = 0;
            int water_hit = Integer.MAX_VALUE; // hit index of first water hit
            /* Loop through patches : compute intercept values for each */
            for(int i = 0; i < patches.length; i++) {
                hitcnt = handlePatch((PatchDefinition)patches[i], hitcnt);
            }
            if ((fluidpatches != null) && (fluidpatches.length > 0)) {
                int prev_hitcnt = hitcnt;
                for(int i = 0; i < fluidpatches.length; i++) {
                    hitcnt = handlePatch((PatchDefinition)fluidpatches[i], hitcnt);
                }
                if (prev_hitcnt < hitcnt) { // At least one water hit?
                    water_hit = prev_hitcnt;    // Remember index
                }
            }
            /* If no hits, we're done */
            if(hitcnt == 0) {
                // Set current block as last block for any incomplete shaders
                for(int i = 0; i < shaderstate.length; i++) {
                    if(!shaderdone[i])
                        // Update with current block as last block
                        shaderstate[i].setLastBlockState(blocktype);
                }
                return false;
            }
            BlockStep old_laststep = laststep;  /* Save last step */
            DynmapBlockState cur_bt = blocktype;
            for(int i = 0; i < hitcnt; i++) {
                /* Find closest hit (lowest parametric value) */
                double best_t = Double.MAX_VALUE;
                int best_patch = 0;
                for(int j = 0; j < hitcnt; j++) {
                    if(patch_t[j] < best_t) {
                        best_patch = j;
                        best_t = patch_t[j];
                    }
                }
                cur_patch = patch_id[best_patch]; /* Mark this as current patch */
                cur_patch_u = patch_u[best_patch];
                cur_patch_v = patch_v[best_patch];
                laststep = patch_step[best_patch];
                cur_patch_t = best_t;
                // If the water patch, switch to water state and patch index
                if (best_patch >= water_hit) {
                    blocktype = fluidstate;
                }
                /* Process the shaders */
                boolean done = true;
                for(int j = 0; j < shaderstate.length; j++) {
                    if(!shaderdone[j])
                        shaderdone[j] = shaderstate[j].processBlock(this);
                    done = done && shaderdone[j];
                }
                // If water, restore block type
                if (best_patch >= water_hit) {
                    blocktype = cur_bt;
                }
                cur_patch = -1;
                /* If all are done, we're out */
                if(done) {
                    laststep = old_laststep;
                    return true;
                }
                nonairhit = true;
                /* Now remove patch and repeat */
                patch_t[best_patch] = Double.MAX_VALUE;
            }
            laststep = old_laststep;

            // Set current block as last block for any incomplete shaders
            for(int i = 0; i < shaderstate.length; i++) {
                if(!shaderdone[i])
                    // Update with current block as last block
                    shaderstate[i].setLastBlockState(blocktype);
            }

            return false;
        }

        private RenderPatch[] getPatches(DynmapBlockState bt, boolean isFluid) {
            RenderPatch[] patches = scalemodels.getPatchModel(bt);
            /* If no patches, see if custom model */
            if (patches == null) {
                CustomBlockModel cbm = scalemodels.getCustomBlockModel(bt);
                if (cbm != null) {   /* If found, see if cached already */
                    if (isFluid) {
                        patches = this.getCustomFluidMesh();
                        if (patches == null) {
                            patches = cbm.getMeshForBlock(mapiter);
                            this.setCustomFluidMesh(patches);
                        }
                    }
                    else {
                        patches = this.getCustomMesh();
                        if (patches == null) {
                            patches = cbm.getMeshForBlock(mapiter);
                            this.setCustomMesh(patches);
                        }
                    }
                }
            }
            return patches;
        }
        /**
         * Process visit of ray to block
         */
        private final boolean visit_block(HDShaderState[] shaderstate, boolean[] shaderdone) {
            lastblocktype = blocktype;
            blocktype = mapiter.getBlockType();
            if (skiptoair) {	/* If skipping until we see air */
                if (blocktype.isAir()) {	/* If air, we're done */
                	skiptoair = false;
                }
            }
            else if(nonairhit || blocktype.isNotAir()) {
                short[] model;
                RenderPatch[] patches = getPatches(blocktype, false);
                /* Look up to see if block is modelled */
                if(patches != null) {
                    RenderPatch[] fluidpatches = null;
                    // If so, check for waterlogged
                    DynmapBlockState fluidstate = blocktype.getLiquidState();
                    if (fluidstate != null) {
                        fluidpatches = getPatches(fluidstate, true);
                    }
                    return handlePatches(patches, shaderstate, shaderdone, fluidstate, fluidpatches);
                }
                else if ((model = scalemodels.getScaledModel(blocktype)) != null) {
                    return handleSubModel(model, shaderstate, shaderdone);
                }
                else {
                	boolean done = true;
                    subalpha = -1;
                    for(int i = 0; i < shaderstate.length; i++) {
                        if(!shaderdone[i]) {
                            shaderdone[i] = shaderstate[i].processBlock(this);
                            // Update with current block as last block
                            shaderstate[i].setLastBlockState(blocktype);
                        }
                        done = done && shaderdone[i];
                    }
                    if (done)
                    	return true;
                    nonairhit = true;
                }
            }
            return false;
        }
        
        /* Skip empty : return false if exited */
        private final boolean raytraceSkipEmpty(MapChunkCache cache) {
        	int minsy = cache.getWorld().minY >> 4;
            while(cache.isEmptySection(sx, sy, sz)) {
                /* If Y step is next best */
                if((st_next_y <= st_next_x) && (st_next_y <= st_next_z)) {
                    sy += y_inc;
                    t = st_next_y;
                    st_next_y += sdt_dy;
                    laststep = stepy;
                    if (sy < minsy)
                        return false;
                }
                /* If X step is next best */
                else if((st_next_x <= st_next_y) && (st_next_x <= st_next_z)) {
                    sx += x_inc;
                    t = st_next_x;
                    st_next_x += sdt_dx;
                    laststep = stepx;
                }
                /* Else, Z step is next best */
                else {
                    sz += z_inc;
                    t = st_next_z;
                    st_next_z += sdt_dz;
                    laststep = stepz;
                }
            }
            return true;
        }
        
        /**
         * Step block iterator: false if done
         */
        private final boolean raytraceStepIterator(int miny, int maxy) {
            /* If Y step is next best */
            if ((t_next_y <= t_next_x) && (t_next_y <= t_next_z)) {
                y += y_inc;
                t = t_next_y;
                t_next_y += dt_dy;
                laststep = stepy;
                mapiter.stepPosition(laststep);
                /* If outside 0-(height-1) range */
                if ((y < miny) || (y > maxy)) {
                    return false;
                }
            }
            /* If X step is next best */
            else if ((t_next_x <= t_next_y) && (t_next_x <= t_next_z)) {
                x += x_inc;
                t = t_next_x;
                t_next_x += dt_dx;
                laststep = stepx;
                mapiter.stepPosition(laststep);
            }
            /* Else, Z step is next best */
            else {
                z += z_inc;
                t = t_next_z;
                t_next_z += dt_dz;
                laststep = stepz;
                mapiter.stepPosition(laststep);
            }
            return true;
        }
        
        /**
         * Trace ray, based on "Voxel Tranversal along a 3D line"
         */
        private final void raytrace(MapChunkCache cache, HDShaderState[] shaderstate, boolean[] shaderdone) {
        	int minY = cache.getWorld().minY;
        	int height = cache.getWorld().worldheight;
        	
            /* Initialize raytrace state variables */
            raytrace_init();

            /* Skip sections until we hit a non-empty one */
            if (!raytraceSkipEmpty(cache))
                return;
            
            raytrace_section_init();
            
            if (y < minY)
                return;
            
            mapiter.initialize(x, y, z);
            
            for (; n > 0; --n) {
        		if (visit_block(shaderstate, shaderdone)) {
                    return;
                }
        		if (!raytraceStepIterator(minY, height)) {
        		    return;
        		}
            }
        }

        private final void raytrace_section_init() {
            t = t - 0.000001;
            double xx = top.x + t * direction.x;
            double yy = top.y + t * direction.y;
            double zz = top.z + t * direction.z;
            x = fastFloor(xx);  
            y = fastFloor(yy);  
            z = fastFloor(zz);
            t_next_x = st_next_x;
            t_next_y = st_next_y;
            t_next_z = st_next_z;
            n = 1;
            if(t_next_x != Double.MAX_VALUE) {
                if(stepx == BlockStep.X_PLUS) {
                    t_next_x = t + (x + 1 - xx) * dt_dx;
                    n += fastFloor(bottom.x) - x;
                }
                else {
                    t_next_x = t + (xx - x) * dt_dx;
                    n += x - fastFloor(bottom.x);
                }
            }
            if(t_next_y != Double.MAX_VALUE) {
                if(stepy == BlockStep.Y_PLUS) {
                    t_next_y = t + (y + 1 - yy) * dt_dy;
                    n += fastFloor(bottom.y) - y;
                }
                else {
                    t_next_y = t + (yy - y) * dt_dy;
                    n += y - fastFloor(bottom.y);
                }
            }
            if(t_next_z != Double.MAX_VALUE) {
                if(stepz == BlockStep.Z_PLUS) {
                    t_next_z = t + (z + 1 - zz) * dt_dz;
                    n += fastFloor(bottom.z) - z;
                }
                else {
                    t_next_z = t + (zz - z) * dt_dz;
                    n += z - fastFloor(bottom.z);
                }
            }
        }

        private final boolean raytraceSubblock(short[] model, boolean firsttime) {
            if(firsttime) {
            	mt = t + 0.00000001;
            	xx = top.x + mt * direction.x;  
            	yy = top.y + mt * direction.y;  
            	zz = top.z + mt * direction.z;
            	mx = (int)((xx - fastFloor(xx)) * modscale);
            	my = (int)((yy - fastFloor(yy)) * modscale);
            	mz = (int)((zz - fastFloor(zz)) * modscale);
            	mdt_dx = dt_dx / modscale;
            	mdt_dy = dt_dy / modscale;
            	mdt_dz = dt_dz / modscale;
            	mt_next_x = t_next_x;
            	mt_next_y = t_next_y;
            	mt_next_z = t_next_z;
            	if(mt_next_x != Double.MAX_VALUE) {
            		togo = ((t_next_x - t) / mdt_dx);
            		mt_next_x = mt + (togo - fastFloor(togo)) * mdt_dx;
            	}
            	if(mt_next_y != Double.MAX_VALUE) {
            		togo = ((t_next_y - t) / mdt_dy);
            		mt_next_y = mt + (togo - fastFloor(togo)) * mdt_dy;
            	}
            	if(mt_next_z != Double.MAX_VALUE) {
            		togo = ((t_next_z - t) / mdt_dz);
            		mt_next_z = mt + (togo - fastFloor(togo)) * mdt_dz;
            	}
            	mtend = Math.min(t_next_x, Math.min(t_next_y, t_next_z));
            }
            subalpha = -1;
            boolean skip = !firsttime;	/* Skip first block on continue */
            while(mt <= mtend) {
            	if(!skip) {
            		try {
            			int blkalpha = model[modscale*modscale*my + modscale*mz + mx];
            			if(blkalpha > 0) {
            				subalpha = blkalpha;
            				return false;
            			}
            		} catch (ArrayIndexOutOfBoundsException aioobx) {	/* We're outside the model, so miss */
            			return true;
            		}
            	}
            	else {
            		skip = false;
            	}
        		
                /* If X step is next best */
                if((mt_next_x <= mt_next_y) && (mt_next_x <= mt_next_z)) {
                    mx += x_inc;
                    mt = mt_next_x;
                    mt_next_x += mdt_dx;
                    laststep = stepx;
                    if(mx == mxout) {
                        return true;
                    }
                }
                /* If Y step is next best */
                else if((mt_next_y <= mt_next_x) && (mt_next_y <= mt_next_z)) {
                    my += y_inc;
                    mt = mt_next_y;
                    mt_next_y += mdt_dy;
                    laststep = stepy;
                    if(my == myout) {
                        return true;
                    }
                }
                /* Else, Z step is next best */
                else {
                    mz += z_inc;
                    mt = mt_next_z;
                    mt_next_z += mdt_dz;
                    laststep = stepz;
                    if(mz == mzout) {
                        return true;
                    }
                }
            }
            return true;
        }
        
        public final int[] getSubblockCoord() {
            if(cur_patch >= 0) {    /* If patch hit */
                double tt = cur_patch_t;
                double xx = top.x + tt * direction.x;  
                double yy = top.y + tt * direction.y;  
                double zz = top.z + tt * direction.z;
                subblock_xyz[0] = (int)((xx - fastFloor(xx)) * modscale);
                subblock_xyz[1] = (int)((yy - fastFloor(yy)) * modscale);
                subblock_xyz[2] = (int)((zz - fastFloor(zz)) * modscale);
            }
            else if(subalpha < 0) {
                double tt = t + 0.0000001;
                double xx = top.x + tt * direction.x;  
                double yy = top.y + tt * direction.y;  
                double zz = top.z + tt * direction.z;
                subblock_xyz[0] = (int)((xx - fastFloor(xx)) * modscale);
                subblock_xyz[1] = (int)((yy - fastFloor(yy)) * modscale);
                subblock_xyz[2] = (int)((zz - fastFloor(zz)) * modscale);
            }
            else {
                subblock_xyz[0] = mx;
                subblock_xyz[1] = my;
                subblock_xyz[2] = mz;
            }
            return subblock_xyz;
        }
        
        // Is the hit on a cullable face?
        public final boolean isOnFace() {
            double tt;
            if(cur_patch >= 0) {    /* If patch hit */
                tt = cur_patch_t;
            }
            else if(subalpha < 0) {
                tt = t + 0.0000001;
            }
            else {  // Full blocks always on face
                return true;
            }
            double xx = top.x + tt * direction.x;  
            double yy = top.y + tt * direction.y;  
            double zz = top.z + tt * direction.z;
            double xoff = xx - fastFloor(xx);
            double yoff = yy - fastFloor(yy);
            double zoff = zz - fastFloor(zz);
            return ((xoff < 0.00001) || (xoff > 0.99999) || (yoff < 0.00001) || (yoff > 0.99999) || (zoff < 0.00001) || (zoff > 0.99999));
        }
        
        /**
         * Get current texture index
         */
        @Override
        public int getTextureIndex() {
            return cur_patch;
        }

        /**
         * Get current U of patch intercept
         */
        @Override
        public double getPatchU() {
            return cur_patch_u;
        }

        /**
         * Get current V of patch intercept
         */
        @Override
        public double getPatchV() {
            return cur_patch_v;
        }
        /**
         * Light level cache
         * @param index of light level (0-3)
         */
        @Override
        public final LightLevels getCachedLightLevels(int idx) {
            return llcache[idx];
        }
        /**
         * Get custom mesh for block, if defined (null if not)
         */
        public final RenderPatch[] getCustomMesh() {
            long key = this.mapiter.getBlockKey();  /* Get key for current block */
            return (RenderPatch[])custom_meshes.get(key);
        }
        /**
         * Save custom mesh for block
         */
        public final void setCustomMesh(RenderPatch[] mesh) {
            long key = this.mapiter.getBlockKey();  /* Get key for current block */
            custom_meshes.put(key,  mesh);
        }
        /**
         * Get custom fluid mesh for block, if defined (null if not)
         */
        public final RenderPatch[] getCustomFluidMesh() {
            long key = this.mapiter.getBlockKey();  /* Get key for current block */
            return (RenderPatch[])custom_fluid_meshes.get(key);
        }
        /**
         * Save custom mesh for block
         */
        public final void setCustomFluidMesh(RenderPatch[] mesh) {
            long key = this.mapiter.getBlockKey();  /* Get key for current block */
            custom_fluid_meshes.put(key,  mesh);
        }
    }
    
    public IsoHDPerspective(DynmapCore core, ConfigurationNode configuration) {
        name = configuration.getString("name", null);
        if(name == null) {
            Log.severe("Perspective definition missing name - must be defined and unique");
            hashcode = 0;
        }
        else {
            hashcode = name.hashCode();
        }
        double az = 90.0 + configuration.getDouble("azimuth", 135.0);    /* Get azimuth (default to classic kzed POV) */
        if(az >= 360.0) {
            az = az - 360.0;
        }
        azimuth = az;
        double inc;
        inc = configuration.getDouble("inclination", 60.0);
        if(inc > MAX_INCLINATION) inc = MAX_INCLINATION;
        if(inc < MIN_INCLINATION) inc = MIN_INCLINATION;
        inclination = inc;
        int mscale = (int)Math.ceil(configuration.getDouble("scale", MIN_SCALE));
        if(mscale < MIN_SCALE) mscale = MIN_SCALE;
        if(mscale > MAX_SCALE) mscale = MAX_SCALE;
        basemodscale = mscale;
        /* Get max and min height */
        maxheight = configuration.getInteger("maximumheight", Integer.MIN_VALUE);
        
        minheight = configuration.getInteger("minimumheight", Integer.MIN_VALUE);
        /* Generate transform matrix for world-to-tile coordinate mapping */
        /* First, need to fix basic coordinate mismatches before rotation - we want zero azimuth to have north to top
         * (world -X -> tile +Y) and east to right (world -Z to tile +X), with height being up (world +Y -> tile +Z)
         */
        Matrix3D transform = new Matrix3D(0.0, 0.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        /* Next, rotate world counterclockwise around Z axis by azumuth angle */
        transform.rotateXY(180-azimuth);
        /* Next, rotate world by (90-inclination) degrees clockwise around +X axis */
        transform.rotateYZ(90.0-inclination);
        /* Finally, shear along Z axis to normalize Z to be height above map plane */
        transform.shearZ(0, Math.tan(Math.toRadians(90.0-inclination)));
        /* And scale Z to be same scale as world coordinates, and scale X and Y based on setting */
        transform.scale(basemodscale, basemodscale, Math.sin(Math.toRadians(inclination)));
        world_to_map = transform;
        /* Now, generate map to world tranform, by doing opposite actions in reverse order */
        transform = new Matrix3D();
        transform.scale(1.0/basemodscale, 1.0/basemodscale, 1/Math.sin(Math.toRadians(inclination)));
        transform.shearZ(0, -Math.tan(Math.toRadians(90.0-inclination)));
        transform.rotateYZ(-(90.0-inclination));
        transform.rotateXY(-180+azimuth);
        Matrix3D coordswap = new Matrix3D(0.0, -1.0, 0.0, 0.0, 0.0, 1.0, -1.0, 0.0, 0.0);
        transform.multiply(coordswap);
        map_to_world = transform;
    }   

    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
        HashSet<TileFlags.TileCoord> tiles = new HashSet<TileFlags.TileCoord>();
        Vector3D block = new Vector3D();
        block.x = x;
        block.y = y;
        block.z = z;
        Vector3D corner = new Vector3D();
        /* Loop through corners of the cube */
        for(int i = 0; i < 2; i++) {
            double inity = block.y;
            for(int j = 0; j < 2; j++) {
                double initz = block.z;
                for(int k = 0; k < 2; k++) {
                    world_to_map.transform(block, corner);  /* Get map coordinate of corner */
                    tiles.add(new TileFlags.TileCoord(fastFloor(corner.x/tileWidth), fastFloor(corner.y/tileHeight)));
                    block.z += 1;
                }
                block.z = initz;
                block.y += 1;
            }
            block.y = inity;
            block.x += 1;
        }
        return new ArrayList<TileFlags.TileCoord>(tiles);
    }

    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        ArrayList<TileFlags.TileCoord> tiles = new ArrayList<TileFlags.TileCoord>();
        Vector3D blocks[] = new Vector3D[] { new Vector3D(), new Vector3D() };
        blocks[0].x = minx - 1;
        blocks[0].y = miny - 1;
        blocks[0].z = minz - 1;
        blocks[1].x = maxx + 1;
        blocks[1].y = maxy + 1;
        blocks[1].z = maxz + 1;
        
        Vector3D corner = new Vector3D();
        Vector3D tcorner = new Vector3D();
        int mintilex = Integer.MAX_VALUE;
        int maxtilex = Integer.MIN_VALUE;
        int mintiley = Integer.MAX_VALUE;
        int maxtiley = Integer.MIN_VALUE;
        /* Loop through corners of the prism */
        for(int i = 0; i < 2; i++) {
            corner.x = blocks[i].x;
            for(int j = 0; j < 2; j++) {
                corner.y = blocks[j].y;
                for(int k = 0; k < 2; k++) {
                    corner.z = blocks[k].z;
                    world_to_map.transform(corner, tcorner);  /* Get map coordinate of corner */
                    int tx = fastFloor(tcorner.x/tileWidth);
                    int ty = fastFloor(tcorner.y/tileWidth);
                    if(mintilex > tx) mintilex = tx;
                    if(maxtilex < tx) maxtilex = tx;
                    if(mintiley > ty) mintiley = ty;
                    if(maxtiley < ty) maxtiley = ty;
                }
            }
        }
        /* Now, add the tiles for the ranges - not perfect, but it works (some extra tiles on corners possible) */
        for(int i = mintilex; i <= maxtilex; i++) {
            for(int j = mintiley-1; j <= maxtiley; j++) {
                tiles.add(new TileFlags.TileCoord(i, j));
            }
        }
        return tiles;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        HDMapTile t = (HDMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tx;
        int y = t.ty;
        return new MapTile[] {
            new HDMapTile(w, this, x - 1, y - 1, t.boostzoom),
            new HDMapTile(w, this, x + 1, y - 1, t.boostzoom),
            new HDMapTile(w, this, x - 1, y + 1, t.boostzoom),
            new HDMapTile(w, this, x + 1, y + 1, t.boostzoom),
            new HDMapTile(w, this, x, y - 1, t.boostzoom),
            new HDMapTile(w, this, x + 1, y, t.boostzoom),
            new HDMapTile(w, this, x, y + 1, t.boostzoom),
            new HDMapTile(w, this, x - 1, y, t.boostzoom) };
    }

    private static final int corners_by_side[][] = {
        { 1, 3, 7, 5 }, // Top
        { 0, 2, 6, 4 }, // Bottom
        { 0, 1, 3, 2 }, // Left
        { 4, 5, 7, 6 }, // Right
        { 2, 3, 7, 6 }, // Upper
        { 0, 1, 5, 4 }  // Lower
    };

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        if (!(tile instanceof HDMapTile))
            return Collections.emptyList();
        
        HDMapTile t = (HDMapTile) tile;
        int min_chunk_x = Integer.MAX_VALUE;
        int max_chunk_x = Integer.MIN_VALUE;
        int min_chunk_z = Integer.MAX_VALUE;
        int max_chunk_z = Integer.MIN_VALUE;
        
        /* Make corners for volume: 
         * 0 = bottom-lower-left (xyz), 
         * 1 = top-lower-left (xyZ), 
         * 2 = bottom-upper-left (xYz), 
         * 3 = top-upper-left (xYZ),
         * 4 = bottom-lower-right (Xyz), 
         * 5 = top-lower-right (XyZ), 
         * 6 = bottom-upper-right (XYz), 
         * 7 = top-upper-right (XYZ) */  
        Vector3D corners[] = new Vector3D[8];
        double dx = -basemodscale, dy = -basemodscale;    /* Add 1 block on each axis */
        for(int x = t.tx, idx = 0; x <= (t.tx+1); x++) {
            dy = -basemodscale;
            for(int y = t.ty; y <= (t.ty+1); y++) {
                for(int z = 0; z <= 1; z++) {
                    corners[idx] = new Vector3D();
                    corners[idx].x = x*tileWidth + dx;
                    corners[idx].y = y*tileHeight + dy;
                    corners[idx].z = (z == 1) ? t.getDynmapWorld().worldheight : t.getDynmapWorld().minY;
                    map_to_world.transform(corners[idx]);
                    /* Compute chunk coordinates of corner */
                    int cx = fastFloor(corners[idx].x / 16);
                    int cz = fastFloor(corners[idx].z / 16);
                    /* Compute min/max of chunk coordinates */
                    if(min_chunk_x > cx) min_chunk_x = cx;
                    if(max_chunk_x < cx) max_chunk_x = cx;
                    if(min_chunk_z > cz) min_chunk_z = cz;
                    if(max_chunk_z < cz) max_chunk_z = cz;
                    idx++;
                }
                dy = basemodscale;
            }
            dx = basemodscale;
        }
        /* Make rectangles of X-Z projection of each side of the tile volume, 0 = top, 1 = bottom, 2 = left, 3 = right,
         * 4 = upper, 5 = lower */
        Polygon[] side = new Polygon[6];
        for (int sidenum = 0; sidenum < side.length; sidenum++) {
            side[sidenum] = new Polygon();
            for (int corner = 0; corner < corners_by_side[sidenum].length; corner++) {
                int cid = corners_by_side[sidenum][corner];
                side[sidenum].addVertex(corners[cid].x, corners[cid].z);
            }
        }
        /* Now, need to walk through the min/max range to see which chunks are actually needed */
        ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();
        
        for(int x = min_chunk_x; x <= max_chunk_x; x++) {
            for(int z = min_chunk_z; z <= max_chunk_z; z++) {
                boolean hit = false;
                for (int sidenum = 0; (!hit) && (sidenum < side.length); sidenum++) {
                    if (side[sidenum].clip(16.0*x, 16.0*z, 16.0*(x+1), 16.0*(z+1)) != null) {
                        hit = true;
                    }
                }
                //xs += c;
                if(hit) {
                    DynmapChunk chunk = new DynmapChunk(x, z);
                    chunks.add(chunk);
                }
            }
        }
        return chunks;
    }

    @Override
    public boolean render(MapChunkCache cache, HDMapTile tile, String mapname) {
        final long startTimestamp = System.currentTimeMillis();
        Color rslt = new Color();
        MapIterator mapiter = cache.getIterator(0, 0, 0);
        DynmapWorld world = tile.getDynmapWorld();
        int scaled = 0;
        if ((tile.boostzoom > 0) && MarkerAPIImpl.testTileForBoostMarkers(cache.getWorld(), this, tile.tx * tileWidth, tile.ty * tileHeight, tileWidth)) {
            scaled = tile.boostzoom;
        }
        int sizescale = 1 << scaled;

        /* Build shader state object for each shader */
        HDShaderState[] shaderstate = MapManager.mapman.hdmapman.getShaderStateForTile(tile, cache, mapiter, mapname, sizescale * this.basemodscale);
        int numshaders = shaderstate.length;
        if(numshaders == 0)
            return false;
        /* Check if nether world */
        boolean isnether = world.isNether();
        /* Create buffered image for each */
        DynmapBufferedImage im[] = new DynmapBufferedImage[numshaders];
        DynmapBufferedImage dayim[] = new DynmapBufferedImage[numshaders];
        int[][] argb_buf = new int[numshaders][];
        int[][] day_argb_buf = new int[numshaders][];
        boolean isjpg[] = new boolean[numshaders];
        int bgday[] = new int[numshaders];
        int bgnight[] = new int[numshaders];
        
        for(int i = 0; i < numshaders; i++) {
            HDLighting lighting = shaderstate[i].getLighting();
            im[i] = DynmapBufferedImage.allocateBufferedImage(tileWidth * sizescale, tileHeight * sizescale);
            argb_buf[i] = im[i].argb_buf;
            if(lighting.isNightAndDayEnabled()) {
                dayim[i] = DynmapBufferedImage.allocateBufferedImage(tileWidth * sizescale, tileHeight * sizescale);
                day_argb_buf[i] = dayim[i].argb_buf;
            }
            isjpg[i] = shaderstate[i].getMap().getImageFormat() != ImageFormat.FORMAT_PNG;
            bgday[i] = shaderstate[i].getMap().getBackgroundARGBDay();
            bgnight[i] = shaderstate[i].getMap().getBackgroundARGBNight();
        }
        // Mark the tiles we're going to render as validated
        for (int i = 0; i < numshaders; i++) {
            MapTypeState mts = world.getMapState(shaderstate[i].getMap());
            if (mts != null) {
                mts.validateTile(tile.tx, tile.ty);
            }
        }
        /* Create perspective state object */
        OurPerspectiveState ps = new OurPerspectiveState(mapiter, isnether, scaled);        
        
        ps.top = new Vector3D();
        ps.bottom = new Vector3D();
        ps.direction = new Vector3D();
        double xbase = tile.tx * tileWidth;
        double ybase = tile.ty * tileHeight;
        boolean shaderdone[] = new boolean[numshaders];
        boolean rendered[] = new boolean[numshaders];
        double height = maxheight;
        if (height == Integer.MIN_VALUE) {    /* Not set - assume world height - 1 */
            if (isnether)
                height = 127;
            else
                height = tile.getDynmapWorld().worldheight - 1;
        }
        double miny = minheight;
        if (miny == Integer.MIN_VALUE) {    /* Not set - assume world height - 1 */
        	miny = tile.getDynmapWorld().minY;
        }
        
        for(int x = 0; x < tileWidth * sizescale; x++) {
            ps.px = x;
            for(int y = 0; y < tileHeight * sizescale; y++) {
                ps.top.x = ps.bottom.x = xbase + (x + 0.5) / sizescale;    /* Start at center of pixel at Y=height+0.5, bottom at Y=-0.5 */
                ps.top.y = ps.bottom.y = ybase + (y + 0.5) / sizescale;
                ps.top.z = height + 0.5; ps.bottom.z = miny - 0.5;
                map_to_world.transform(ps.top);            /* Transform to world coordinates */
                map_to_world.transform(ps.bottom);
                ps.direction.set(ps.bottom);
                ps.direction.subtract(ps.top);
                ps.py = y / sizescale;
                for(int i = 0; i < numshaders; i++) {
                    shaderstate[i].reset(ps);
                }
                try {
                    ps.raytrace(cache, shaderstate, shaderdone);
                } catch (Exception ex) {
                    Log.severe("Error while raytracing tile: perspective=" + this.name + ", coord=" + mapiter.getX() + "," + mapiter.getY() + "," + mapiter.getZ() + ", blockid=" + mapiter.getBlockType() + ", lighting=" + mapiter.getBlockSkyLight() + ":" + mapiter.getBlockEmittedLight() + ", biome=" + mapiter.getBiome().toString(), ex);
                }
                for(int i = 0; i < numshaders; i++) {
                    if(shaderdone[i] == false) {
                        shaderstate[i].rayFinished(ps);
                    }
                    else {
                        shaderdone[i] = false;
                        rendered[i] = true;
                    }
                    shaderstate[i].getRayColor(rslt, 0);
                    int c_argb = rslt.getARGB();
                    if(c_argb != 0) rendered[i] = true;
                    if(isjpg[i] && (c_argb == 0)) {
                        argb_buf[i][(tileHeight*sizescale-y-1)*tileWidth*sizescale + x] = bgnight[i];
                    }
                    else {
                        argb_buf[i][(tileHeight*sizescale-y-1)*tileWidth*sizescale + x] = c_argb;
                    }
                    if(day_argb_buf[i] != null) {
                        shaderstate[i].getRayColor(rslt, 1);
                        c_argb = rslt.getARGB();
                        if(isjpg[i] && (c_argb == 0)) {
                            day_argb_buf[i][(tileHeight*sizescale-y-1)*tileWidth*sizescale + x] = bgday[i];
                        }
                        else {
                            day_argb_buf[i][(tileHeight*sizescale-y-1)*tileWidth*sizescale + x] = c_argb;
                        }
                    }
                }
            }
        }

        boolean renderone = false;
        /* Test to see if we're unchanged from older tile */
        MapStorage storage = world.getMapStorage();
        for(int i = 0; i < numshaders; i++) {
            long crc = MapStorage.calculateImageHashCode(argb_buf[i], 0, argb_buf[i].length);
            boolean tile_update = false;
            String prefix = shaderstate[i].getMap().getPrefix();

            MapStorageTile mtile = storage.getTile(world, shaderstate[i].getMap(), tile.tx, tile.ty, 0, MapType.ImageVariant.STANDARD);

            mtile.getWriteLock();
            try {
                if(mtile.matchesHashCode(crc) == false) {
                    /* Wrap buffer as buffered image */
                    if(rendered[i]) {   
                        mtile.write(crc, im[i].buf_img, startTimestamp);
                    }
                    else {
                        mtile.delete();
                    }
                    MapManager.mapman.pushUpdate(tile.getDynmapWorld(), new Client.Tile(mtile.getURI()));
                    tile_update = true;
                    renderone = true;
                }
                else {
                    if(!rendered[i]) {   
                        mtile.delete();
                    }
                }
            } finally {
                mtile.releaseWriteLock();
                DynmapBufferedImage.freeBufferedImage(im[i]);
            }
            MapManager.mapman.updateStatistics(tile, prefix, true, tile_update, !rendered[i]);
            /* Handle day image, if needed */
            if(dayim[i] != null) {
                crc = MapStorage.calculateImageHashCode(day_argb_buf[i], 0, day_argb_buf[i].length);

                mtile = storage.getTile(world, shaderstate[i].getMap(), tile.tx, tile.ty, 0, MapType.ImageVariant.DAY);

                mtile.getWriteLock();
                tile_update = false;
                try {
                    if(mtile.matchesHashCode(crc) == false) {
                        /* Wrap buffer as buffered image */
                        if(rendered[i]) {
                            mtile.write(crc, dayim[i].buf_img, startTimestamp);
                        }
                        else {
                            mtile.delete();
                        }
                        MapManager.mapman.pushUpdate(tile.getDynmapWorld(), new Client.Tile(mtile.getURI()));
                        tile_update = true;
                        renderone = true;
                    }
                    else {
                        if(!rendered[i]) {   
                            mtile.delete();
                        }
                    }
                } finally {
                    mtile.releaseWriteLock();
                    DynmapBufferedImage.freeBufferedImage(dayim[i]);
                }
                MapManager.mapman.updateStatistics(tile, prefix+"_day", true, tile_update, !rendered[i]);
            }
        }
        return renderone;
    }

    @Override
    public boolean isBiomeDataNeeded() {
        return need_biomedata;
    }

    @Override
    public boolean isRawBiomeDataNeeded() { 
         return need_rawbiomedata;
     }

    @Override
    public boolean isHightestBlockYDataNeeded() {
        return false;
    }
    
    @Override
    public boolean isBlockTypeDataNeeded() {
        return true;
    }
    
    @Override
    public double getScale() {
        return basemodscale;
    }

    @Override
    public int getModelScale() {
        return basemodscale;
    }

    @Override
    public String getName() {
        return name;
    }

    private static String[] directions = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
    @Override
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "perspective", name);
        s(mapObject, "azimuth", azimuth);
        s(mapObject, "inclination", inclination);
        s(mapObject, "scale", basemodscale);
        s(mapObject, "worldtomap", world_to_map.toJSON());
        s(mapObject, "maptoworld", map_to_world.toJSON());
        int dir = (((360 + (int)(22.5+azimuth)) / 45) + 6) % 8;
        s(mapObject, "compassview", directions[dir]);
    }
    
    private static final int fastFloor(double f) {
        return ((int)(f + 1000000000.0)) - 1000000000;
    }
    
    @Override
    public void transformWorldToMapCoord(Vector3D input, Vector3D rslt) {
        world_to_map.transform(input,  rslt);
    }

    @Override
    public int hashCode() {
        return hashcode;
    }
}
