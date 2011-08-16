package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import org.dynmap.DynmapWorld;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.TileHashManager;
import org.dynmap.debug.Debug;
import org.dynmap.utils.MapIterator.BlockStep;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.hdmap.TexturePack.HDTextureMap;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.Vector3D;
import org.json.simple.JSONObject;

public class IsoHDPerspective implements HDPerspective {
    private String name;
    /* View angles */
    public double azimuth;  /* Angle in degrees from looking north (0), east (90), south (180), or west (270) */
    public double inclination;  /* Angle in degrees from horizontal (0) to vertical (90) */
    public double scale;    /* Scale - tile pixel widths per block */
    public double maxheight;
    public double minheight;
    /* Coordinate space for tiles consists of a plane (X, Y), corresponding to the projection of each tile on to the
     * plane of the bottom of the world (X positive to the right, Y positive to the top), with Z+ corresponding to the
     * height above this plane on a vector towards the viewer).  Logically, this makes the parallelogram representing the
     * space contributing to the tile have consistent tile-space X,Y coordinate pairs for both the top and bottom faces
     * Note that this is a classic right-hand coordinate system, while minecraft's world coordinates are left handed 
     * (X+ is south, Y+ is up, Z+ is east). 
     */
    /* Transformation matrix for taking coordinate in world-space (x, y, z) and finding coordinate in tile space (x, y, z) */
    private Matrix3D world_to_map;
    private Matrix3D map_to_world;
    
    /* Scaled models for non-cube blocks */
    private HDBlockModels.HDScaledBlockModels scalemodels;
    private int modscale;
    
    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /* Maximum and minimum inclinations */
    public static final double MAX_INCLINATION = 90.0;
    public static final double MIN_INCLINATION = 20.0;
    
    /* Maximum and minimum scale */
    public static final double MAX_SCALE = 64;
    public static final double MIN_SCALE = 1;
    
    private boolean need_skylightlevel = false;
    private boolean need_emittedlightlevel = false;
    private boolean need_biomedata = false;
    private boolean need_rawbiomedata = false;

    private static final int CHEST_BLKTYPEID = 54;
    private static final int FENCE_BLKTYPEID = 85;
    private static final int REDSTONE_BLKTYPEID = 55;
    
    private enum ChestData {
        SINGLE_WEST, SINGLE_SOUTH, SINGLE_EAST, SINGLE_NORTH, LEFT_WEST, LEFT_SOUTH, LEFT_EAST, LEFT_NORTH, RIGHT_WEST, RIGHT_SOUTH, RIGHT_EAST, RIGHT_NORTH
    };
    
    /* Orientation lookup for single chest - index bits: occupied blocks NESW */
    private static final ChestData[] SINGLE_LOOKUP = { 
        ChestData.SINGLE_WEST, ChestData.SINGLE_EAST, ChestData.SINGLE_NORTH, ChestData.SINGLE_NORTH, 
        ChestData.SINGLE_WEST, ChestData.SINGLE_WEST, ChestData.SINGLE_NORTH, ChestData.SINGLE_NORTH,
        ChestData.SINGLE_SOUTH, ChestData.SINGLE_SOUTH, ChestData.SINGLE_WEST, ChestData.SINGLE_EAST,
        ChestData.SINGLE_SOUTH, ChestData.SINGLE_SOUTH, ChestData.SINGLE_WEST, ChestData.SINGLE_EAST
    };

    private class OurPerspectiveState implements HDPerspectiveState {
        int blocktypeid = 0;
        int blockdata = 0;
        int lastblocktypeid = 0;
        Vector3D top, bottom;
        int px, py;
        BlockStep laststep = BlockStep.Y_MINUS;
        
        BlockStep stepx, stepy, stepz;
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
        
        int[] subblock_xyz = new int[3];
        MapIterator mapiter;
        boolean isnether;
        boolean skiptoair;
        int skylevel = -1;
        int emitlevel = -1;

        public OurPerspectiveState(MapIterator mi, boolean isnether) {
            mapiter = mi;
            this.isnether = isnether;
        }
        /**
         * Update sky and emitted light 
         */
        private final void updateLightLevel() {
            /* Look up transparency for current block */
            BlockTransparency bt = HDTextureMap.getTransparency(blocktypeid);
            if(bt == BlockTransparency.TRANSPARENT) {
                skylevel = mapiter.getBlockSkyLight();
                emitlevel = mapiter.getBlockEmittedLight();
            }
            else if(HDTextureMap.getTransparency(lastblocktypeid) != BlockTransparency.SEMITRANSPARENT) {
                mapiter.unstepPosition(laststep);  /* Back up to block we entered on */
                if(mapiter.getY() < 128) {
                    emitlevel = mapiter.getBlockEmittedLight();
                    skylevel = mapiter.getBlockSkyLight();
                } else {
                    emitlevel = 0;
                    skylevel = 15;
                }
                mapiter.stepPosition(laststep);
            }
            else {
                mapiter.unstepPosition(laststep);  /* Back up to block we entered on */
                if(mapiter.getY() < 128) {
                    mapiter.stepPosition(BlockStep.Y_PLUS); /* Look above */
                    emitlevel = mapiter.getBlockEmittedLight();
                    skylevel = mapiter.getBlockSkyLight();
                    mapiter.stepPosition(BlockStep.Y_MINUS);
                } else {
                    emitlevel = 0;
                    skylevel = 15;
                }
                mapiter.stepPosition(laststep);
            }
        }
        /**
         * Get sky light level - only available if shader requested it
         */
        public final int getSkyLightLevel() {
            if(skylevel < 0) {
                updateLightLevel();
            }
            return skylevel;
        }
        /**
         * Get emitted light level - only available if shader requested it
         */
        public final int getEmittedLightLevel() {
            if(emitlevel < 0)
                updateLightLevel();
            return emitlevel;
        }
        /**
         * Get current block type ID
         */
        public final int getBlockTypeID() { return blocktypeid; }
        /**
         * Get current block data
         */
        public final int getBlockData() { return blockdata; }
        /**
         * Get direction of last block step
         */
        public final BlockStep getLastBlockStep() { return laststep; }
        /**
         * Get perspective scale
         */
        public final double getScale() { return scale; }
        /**
         * Get start of current ray, in world coordinates
         */
        public final Vector3D getRayStart() { return top; }
        /**
         * Get end of current ray, in world coordinates
         */
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
            dx = Math.abs(bottom.x - top.x);
            dy = Math.abs(bottom.y - top.y);
            dz = Math.abs(bottom.z - top.z);
            /* Initial block coord */
            x = (int) (Math.floor(top.x));
            y = (int) (Math.floor(top.y));
            z = (int) (Math.floor(top.z));
            /* Compute parametric step (dt) per step on each axis */
            dt_dx = 1.0 / dx;
            dt_dy = 1.0 / dy;
            dt_dz = 1.0 / dz;
            /* Initialize parametric value to 0 (and we're stepping towards 1) */
            t = 0;
            /* Compute number of steps and increments for each */
            n = 1;
            
            /* If perpendicular to X axis */
            if (dx == 0) {
                x_inc = 0;
                t_next_x = Double.MAX_VALUE;
                stepx = BlockStep.X_PLUS;
                mxout = modscale;
            }
            /* If bottom is right of top */
            else if (bottom.x > top.x) {
                x_inc = 1;
                n += (int) (Math.floor(bottom.x)) - x;
                t_next_x = (Math.floor(top.x) + 1 - top.x) * dt_dx;
                stepx = BlockStep.X_PLUS;
                mxout = modscale;
            }
            /* Top is right of bottom */
            else {
                x_inc = -1;
                n += x - (int) (Math.floor(bottom.x));
                t_next_x = (top.x - Math.floor(top.x)) * dt_dx;
                stepx = BlockStep.X_MINUS;
                mxout = -1;
            }
            /* If perpendicular to Y axis */
            if (dy == 0) {
                y_inc = 0;
                t_next_y = Double.MAX_VALUE;
                stepy = BlockStep.Y_PLUS;
                myout = modscale;
            }
            /* If bottom is above top */
            else if (bottom.y > top.y) {
                y_inc = 1;
                n += (int) (Math.floor(bottom.y)) - y;
                t_next_y = (Math.floor(top.y) + 1 - top.y) * dt_dy;
                stepy = BlockStep.Y_PLUS;
                myout = modscale;
            }
            /* If top is above bottom */
            else {
                y_inc = -1;
                n += y - (int) (Math.floor(bottom.y));
                t_next_y = (top.y - Math.floor(top.y)) * dt_dy;
                stepy = BlockStep.Y_MINUS;
                myout = -1;
            }
            /* If perpendicular to Z axis */
            if (dz == 0) {
                z_inc = 0;
                t_next_z = Double.MAX_VALUE;
                stepz = BlockStep.Z_PLUS;
                mzout = modscale;
            }
            /* If bottom right of top */
            else if (bottom.z > top.z) {
                z_inc = 1;
                n += (int) (Math.floor(bottom.z)) - z;
                t_next_z = (Math.floor(top.z) + 1 - top.z) * dt_dz;
                stepz = BlockStep.Z_PLUS;
                mzout = modscale;
            }
            /* If bottom left of top */
            else {
                z_inc = -1;
                n += z - (int) (Math.floor(bottom.z));
                t_next_z = (top.z - Math.floor(top.z)) * dt_dz;
                stepz = BlockStep.Z_MINUS;
                mzout = -1;
            }
            /* Walk through scene */
            laststep = BlockStep.Y_MINUS; /* Last step is down into map */
            nonairhit = false;
            skiptoair = isnether;
        }
        private int generateFenceBlockData(MapIterator mapiter) {
            int blockdata = 0;
            /* Check north */
            if(mapiter.getBlockTypeIDAt(BlockStep.X_MINUS) == FENCE_BLKTYPEID) {    /* Fence? */
                blockdata |= 1;
            }
            /* Look east */
            if(mapiter.getBlockTypeIDAt(BlockStep.Z_MINUS) == FENCE_BLKTYPEID) {    /* Fence? */
                blockdata |= 2;
            }
            /* Look south */
            if(mapiter.getBlockTypeIDAt(BlockStep.X_PLUS) == FENCE_BLKTYPEID) {    /* Fence? */
                blockdata |= 4;
            }
            /* Look west */
            if(mapiter.getBlockTypeIDAt(BlockStep.Z_PLUS) == FENCE_BLKTYPEID) {    /* Fence? */
                blockdata |= 8;
            }
            return blockdata;
        }
        /**
         * Generate chest block to drive model selection:
         *   0 = single facing west
         *   1 = single facing south
         *   2 = single facing east
         *   3 = single facing north
         *   4 = left side facing west
         *   5 = left side facing south
         *   6 = left side facing east
         *   7 = left side facing north
         *   8 = right side facing west
         *   9 = right side facing south
         *   10 = right side facing east
         *   11 = right side facing north
         * @param mapiter
         * @return
         */
        private int generateChestBlockData(MapIterator mapiter) {
            ChestData cd = ChestData.SINGLE_WEST;   /* Default to single facing west */
            /* Check adjacent block IDs */
            int ids[] = { mapiter.getBlockTypeIDAt(BlockStep.Z_PLUS),  /* To west */
                mapiter.getBlockTypeIDAt(BlockStep.X_PLUS),            /* To south */
                mapiter.getBlockTypeIDAt(BlockStep.Z_MINUS),           /* To east */
                mapiter.getBlockTypeIDAt(BlockStep.X_MINUS) };         /* To north */
            /* First, check if we're a double - see if any adjacent chests */
            if(ids[0] == CHEST_BLKTYPEID) { /* Another to west - assume we face south */
                cd = ChestData.RIGHT_SOUTH; /* We're right side */
            }
            else if(ids[1] == CHEST_BLKTYPEID) {    /* Another to south - assume west facing */
                cd = ChestData.LEFT_WEST;   /* We're left side */
            }
            else if(ids[2] == CHEST_BLKTYPEID) {    /* Another to east - assume south facing */
                cd = ChestData.LEFT_SOUTH;  /* We're left side */
            }
            else if(ids[3] == CHEST_BLKTYPEID) {    /* Another to north - assume west facing */
                cd = ChestData.RIGHT_WEST;  /* We're right side */
            }
            else {  /* Else, single - build index into lookup table */
                int idx = 0;
                for(int i = 0; i < ids.length; i++) {
                    if((ids[i] != 0) && (HDTextureMap.getTransparency(ids[i]) != BlockTransparency.TRANSPARENT)) {
                        idx |= (1<<i);
                    }
                }
                cd = SINGLE_LOOKUP[idx];
            }
            
            return cd.ordinal();
        }
        /**
         * Generate redstone wire model data:
         *   0 = NSEW wire
         *   1 = NS wire
         *   2 = EW wire
         *   3 = NE wire
         *   4 = NW wire
         *   5 = SE wire
         *   6 = SW wire
         *   7 = NSE wire
         *   8 = NSW wire
         *   9 = NEW wire
         *   10 = SEW wire
         * @param mapiter
         * @return
         */
        private int generateRedstoneWireBlockData(MapIterator mapiter) {
            /* Check adjacent block IDs */
            int ids[] = { mapiter.getBlockTypeIDAt(BlockStep.Z_PLUS),  /* To west */
                mapiter.getBlockTypeIDAt(BlockStep.X_PLUS),            /* To south */
                mapiter.getBlockTypeIDAt(BlockStep.Z_MINUS),           /* To east */
                mapiter.getBlockTypeIDAt(BlockStep.X_MINUS) };         /* To north */
            int flags = 0;
            for(int i = 0; i < 4; i++)
                if(ids[i] == REDSTONE_BLKTYPEID) flags |= (1<<i);
            switch(flags) {
                case 0: /* Nothing nearby */
                case 15: /* NSEW */
                    return 0;   /* NSEW graphic */
                case 2: /* S */
                case 8: /* N */
                case 10: /* NS */
                    return 1;   /* NS graphic */
                case 1: /* W */
                case 4: /* E */
                case 5: /* EW */
                    return 2;   /* EW graphic */
                case 12: /* NE */
                    return 3;
                case 9: /* NW */
                    return 4;
                case 6: /* SE */
                    return 5;
                case 3: /* SW */
                    return 6;
                case 14: /* NSE */
                    return 7;
                case 11: /* NSW */
                    return 8;
                case 13: /* NEW */
                    return 9;
                case 7: /* SEW */
                    return 10;
            }
            return 0;
        }
        private final boolean handleSubModel(short[] model, HDShaderState[] shaderstate, boolean[] shaderdone) {
            boolean firststep = true;
            
            while(!raytraceSubblock(model, firststep)) {
                boolean done = true;
                skylevel = emitlevel = -1;
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
            return false;
        }
        /**
         * Process visit of ray to block
         */
        private final boolean visit_block(MapIterator mapiter, HDShaderState[] shaderstate, boolean[] shaderdone) {
            lastblocktypeid = blocktypeid;
            blocktypeid = mapiter.getBlockTypeID();
            if(skiptoair) {	/* If skipping until we see air */
                if(blocktypeid == 0)	/* If air, we're done */
                	skiptoair = false;
            }
            else if(nonairhit || (blocktypeid != 0)) {
                switch(blocktypeid) {
                    case FENCE_BLKTYPEID:   /* Special case for fence - need to fake data so we can render properly */
                        blockdata = generateFenceBlockData(mapiter);
                        break;
                    case CHEST_BLKTYPEID:   /* Special case for chest - need to fake data so we can render */
                        blockdata = generateChestBlockData(mapiter);
                        break;
                    case REDSTONE_BLKTYPEID:    /* Special case for redstone - fake data for wire pattern */
                        blockdata = generateRedstoneWireBlockData(mapiter);
                        break;
                    default:
                        blockdata = mapiter.getBlockData();
                        break;
                }
                /* Look up to see if block is modelled */
                short[] model = scalemodels.getScaledModel(blocktypeid, blockdata);
                if(model != null) {
                    return handleSubModel(model, shaderstate, shaderdone);
                }
                else {
                    boolean done = true;
                    skylevel = emitlevel = -1;
                    subalpha = -1;
                    for(int i = 0; i < shaderstate.length; i++) {
                        if(!shaderdone[i])
                            shaderdone[i] = shaderstate[i].processBlock(this);
                        done = done && shaderdone[i];
                    }
                    /* If all are done, we're out */
                    if(done)
                        return true;
                    nonairhit = true;
                }
            }
            return false;
        }
        /**
         * Trace ray, based on "Voxel Tranversal along a 3D line"
         */
        private void raytrace(MapChunkCache cache, MapIterator mapiter, HDShaderState[] shaderstate, boolean[] shaderdone) {
            /* Initialize raytrace state variables */
            raytrace_init();

            mapiter.initialize(x, y, z);
            
            for (; n > 0; --n) {
        		if(visit_block(mapiter, shaderstate, shaderdone)) {
                    return;
                }
                /* If X step is next best */
                if((t_next_x <= t_next_y) && (t_next_x <= t_next_z)) {
                    x += x_inc;
                    t = t_next_x;
                    t_next_x += dt_dx;
                    laststep = stepx;
                    mapiter.stepPosition(laststep);
                }
                /* If Y step is next best */
                else if((t_next_y <= t_next_x) && (t_next_y <= t_next_z)) {
                    y += y_inc;
                    t = t_next_y;
                    t_next_y += dt_dy;
                    laststep = stepy;
                    mapiter.stepPosition(laststep);
                    /* If outside 0-127 range */
                    if((y & (~0x7F)) != 0) return;
                }
                /* Else, Z step is next best */
                else {
                    z += z_inc;
                    t = t_next_z;
                    t_next_z += dt_dz;
                    laststep = stepz;
                    mapiter.stepPosition(laststep);
                }
            }
        }
        
        private boolean logit = false;
        
        private boolean raytraceSubblock(short[] model, boolean firsttime) {
            if(firsttime) {
            	mt = t + 0.00000001;
            	xx = top.x + mt *(bottom.x - top.x);  
            	yy = top.y + mt *(bottom.y - top.y);  
            	zz = top.z + mt *(bottom.z - top.z);
            	mx = (int)((xx - Math.floor(xx)) * modscale);
            	my = (int)((yy - Math.floor(yy)) * modscale);
            	mz = (int)((zz - Math.floor(zz)) * modscale);
            	mdt_dx = dt_dx / modscale;
            	mdt_dy = dt_dy / modscale;
            	mdt_dz = dt_dz / modscale;
            	mt_next_x = t_next_x;
            	mt_next_y = t_next_y;
            	mt_next_z = t_next_z;
            	if(mt_next_x != Double.MAX_VALUE) {
            		togo = ((t_next_x - t) / mdt_dx);
            		mt_next_x = mt + (togo - Math.floor(togo)) * mdt_dx;
            	}
            	if(mt_next_y != Double.MAX_VALUE) {
            		togo = ((t_next_y - t) / mdt_dy);
            		mt_next_y = mt + (togo - Math.floor(togo)) * mdt_dy;
            	}
            	if(mt_next_z != Double.MAX_VALUE) {
            		togo = ((t_next_z - t) / mdt_dz);
            		mt_next_z = mt + (togo - Math.floor(togo)) * mdt_dz;
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
            if(subalpha < 0) {
                double tt = t + 0.0000001;
                double xx = top.x + tt * (bottom.x - top.x);  
                double yy = top.y + tt * (bottom.y - top.y);  
                double zz = top.z + tt * (bottom.z - top.z);
                subblock_xyz[0] = (int)((xx - Math.floor(xx)) * modscale);
                subblock_xyz[1] = (int)((yy - Math.floor(yy)) * modscale);
                subblock_xyz[2] = (int)((zz - Math.floor(zz)) * modscale);
            }
            else {
                subblock_xyz[0] = mx;
                subblock_xyz[1] = my;
                subblock_xyz[2] = mz;
            }
            return subblock_xyz;
        }
    }
    
    public IsoHDPerspective(ConfigurationNode configuration) {
        name = configuration.getString("name", null);
        if(name == null) {
            Log.severe("Perspective definition missing name - must be defined and unique");
            return;
        }
        azimuth = configuration.getDouble("azimuth", 135.0);    /* Get azimuth (default to classic kzed POV */
        inclination = configuration.getDouble("inclination", 60.0);
        if(inclination > MAX_INCLINATION) inclination = MAX_INCLINATION;
        if(inclination < MIN_INCLINATION) inclination = MIN_INCLINATION;
        scale = configuration.getDouble("scale", MIN_SCALE);
        if(scale < MIN_SCALE) scale = MIN_SCALE;
        if(scale > MAX_SCALE) scale = MAX_SCALE;
        /* Get max and min height */
        maxheight = configuration.getInteger("maximumheight", 127);
        if(maxheight > 127) maxheight = 127;
        minheight = configuration.getInteger("minimumheight", 0);
        if(minheight < 0) minheight = 0;

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
        transform.scale(scale, scale, Math.sin(Math.toRadians(inclination)));
        world_to_map = transform;
        /* Now, generate map to world tranform, by doing opposite actions in reverse order */
        transform = new Matrix3D();
        transform.scale(1.0/scale, 1.0/scale, 1/Math.sin(Math.toRadians(inclination)));
        transform.shearZ(0, -Math.tan(Math.toRadians(90.0-inclination)));
        transform.rotateYZ(-(90.0-inclination));
        transform.rotateXY(-180+azimuth);
        Matrix3D coordswap = new Matrix3D(0.0, -1.0, 0.0, 0.0, 0.0, 1.0, -1.0, 0.0, 0.0);
        transform.multiply(coordswap);
        map_to_world = transform;
        /* Scaled models for non-cube blocks */
        modscale = (int)Math.ceil(scale);
        scalemodels = HDBlockModels.getModelsForScale(modscale);;
    }   

    @Override
    public MapTile[] getTiles(Location loc) {
        DynmapWorld world = MapManager.mapman.getWorld(loc.getWorld().getName());
        HashSet<MapTile> tiles = new HashSet<MapTile>();
        Vector3D block = new Vector3D();
        block.setFromLocation(loc); /* Get coordinate for block */
        Vector3D corner = new Vector3D();
        /* Loop through corners of the cube */
        for(int i = 0; i < 2; i++) {
            double inity = block.y;
            for(int j = 0; j < 2; j++) {
                double initz = block.z;
                for(int k = 0; k < 2; k++) {
                    world_to_map.transform(block, corner);  /* Get map coordinate of corner */
                    addTile(tiles, world, (int)Math.floor(corner.x/tileWidth), (int)Math.floor(corner.y/tileHeight));
                    
                    block.z += 1;
                }
                block.z = initz;
                block.y += 1;
            }
            block.y = inity;
            block.x += 1;
        }
        return tiles.toArray(new MapTile[tiles.size()]);
    }

    @Override
    public MapTile[] getTiles(Location loc0, Location loc1) {
        DynmapWorld world = MapManager.mapman.getWorld(loc0.getWorld().getName());
        HashSet<MapTile> tiles = new HashSet<MapTile>();
        Vector3D blocks[] = new Vector3D[] { new Vector3D(), new Vector3D() };
        /* Get ordered point - 0=minX,Y,Z, 1=maxX,Y,Z */
        if(loc0.getBlockX() < loc1.getBlockX()) {
            blocks[0].x = loc0.getBlockX();
            blocks[1].x = loc1.getBlockX() + 1;
        }
        else {
            blocks[0].x = loc1.getBlockX();
            blocks[1].x = loc0.getBlockX() + 1;
        }
        if(loc0.getBlockY() < loc1.getBlockY()) {
            blocks[0].y = loc0.getBlockY();
            blocks[1].y = loc1.getBlockY() + 1;
        }
        else {
            blocks[0].y = loc1.getBlockY();
            blocks[1].y = loc0.getBlockY() + 1;
        }
        if(loc0.getBlockZ() < loc1.getBlockZ()) {
            blocks[0].z = loc0.getBlockZ();
            blocks[1].z = loc1.getBlockZ() + 1;
        }
        else {
            blocks[0].z = loc1.getBlockZ();
            blocks[1].z = loc0.getBlockZ() + 1;
        }        
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
                    int tx = (int)Math.floor(tcorner.x/tileWidth);
                    int ty = (int)Math.floor(tcorner.y/tileWidth);
                    if(mintilex > tx) mintilex = tx;
                    if(maxtilex < tx) maxtilex = tx;
                    if(mintiley > ty) mintiley = ty;
                    if(maxtiley < ty) maxtiley = ty;
                }
            }
        }
        /* Now, add the tiles for the ranges - not perfect, but it works (some extra tiles on corners possible) */
        for(int i = mintilex; i <= maxtilex; i++) {
            for(int j = mintiley; j < maxtiley; j++) {
                addTile(tiles, world, i, j);
            }
        }
        return tiles.toArray(new MapTile[tiles.size()]);
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        HDMapTile t = (HDMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tx;
        int y = t.ty;
        return new MapTile[] {
            new HDMapTile(w, this, x - 1, y - 1),
            new HDMapTile(w, this, x + 1, y - 1),
            new HDMapTile(w, this, x - 1, y + 1),
            new HDMapTile(w, this, x + 1, y + 1),
            new HDMapTile(w, this, x, y - 1),
            new HDMapTile(w, this, x + 1, y),
            new HDMapTile(w, this, x, y + 1),
            new HDMapTile(w, this, x - 1, y) };
    }

    public void addTile(HashSet<MapTile> tiles, DynmapWorld world, int tx, int ty) {
        tiles.add(new HDMapTile(world, this, tx, ty));
    }

    private static class Rectangle {
        double r0x, r0z;   /* Coord of corner of rectangle */
        double s1x, s1z;   /* Side vector for one edge */
        double s2x, s2z;    /* Side vector for other edge */
        public Rectangle(Vector3D v1, Vector3D v2, Vector3D v3) {
            r0x = v1.x;
            r0z = v1.z;
            s1x = v2.x - v1.x;
            s1z = v2.z - v1.z;
            s2x = v3.x - v1.x;
            s2z = v3.z - v1.z;
        }
        public Rectangle() {
        }
        public void setSquare(double rx, double rz, double s) {
            this.r0x = rx;
            this.r0z = rz;
            this.s1x = s;
            this.s1z = 0;
            this.s2x = 0;
            this.s2z = s;
        }
        double getX(int idx) {
            return r0x + (((idx & 1) == 0)?0:s1x) + (((idx & 2) != 0)?0:s2x);
        }
        double getZ(int idx) {
            return r0z + (((idx & 1) == 0)?0:s1z) + (((idx & 2) != 0)?0:s2z);
        }
        /**
         * Test for overlap of projection of one vector on to anoter
         */
        boolean testoverlap(double rx, double rz, double sx, double sz, Rectangle r) {
            double rmin_dot_s0 = Double.MAX_VALUE;
            double rmax_dot_s0 = Double.MIN_VALUE;
            /* Project each point from rectangle on to vector: find lowest and highest */
            for(int i = 0; i < 4; i++) {
                double r_x = r.getX(i) - rx;  /* Get relative positon of second vector start to origin */
                double r_z = r.getZ(i) - rz;
                double r_dot_s0 = r_x*sx + r_z*sz;   /* Projection of start of vector */
                if(r_dot_s0 < rmin_dot_s0) rmin_dot_s0 = r_dot_s0;
                if(r_dot_s0 > rmax_dot_s0) rmax_dot_s0 = r_dot_s0;
            }
            /* Compute dot products */
            double s0_dot_s0 = sx*sx + sz*sz; /* End of our side */
            if((rmax_dot_s0 < 0.0) || (rmin_dot_s0 > s0_dot_s0))
                return false;
            else
                return true;
        }
        /**
         * Test if two rectangles intersect
         * Based on separating axis theorem
         */
        boolean testRectangleIntesectsRectangle(Rectangle r) {
            /* Test if projection of each edge of one rectangle on to each edge of the other yields overlap */
            if(testoverlap(r0x, r0z, s1x, s1z, r) && testoverlap(r0x, r0z, s2x, s2z, r) && 
                    testoverlap(r0x+s1x, r0z+s1z, s2x, s2z, r) && testoverlap(r0x+s2x, r0z+s2z, s1x, s1z, r) && 
                    r.testoverlap(r.r0x, r.r0z, r.s1x, r.s1z, this) && r.testoverlap(r.r0x, r.r0z, r.s2x, r.s2z, this) &&
                    r.testoverlap(r.r0x+r.s1x, r.r0z+r.s1z, r.s2x, r.s2z, this) && r.testoverlap(r.r0x+r.s2x, r.r0z+r.s2z, r.s1x, r.s1z, this)) {
                return true;
            }
            else {
                return false;
            }
        }
        public String toString() {
            return "{ " + r0x + "," + r0z + "}x{" + (r0x+s1x) + ","+ + (r0z+s1z) + "}x{" + (r0x+s2x) + "," + (r0z+s2z) + "}";
        }
    }
    
    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        if (!(tile instanceof HDMapTile))
            return Collections.emptyList();
        
        HDMapTile t = (HDMapTile) tile;
        int min_chunk_x = Integer.MAX_VALUE;
        int max_chunk_x = Integer.MIN_VALUE;
        int min_chunk_z = Integer.MAX_VALUE;
        int max_chunk_z = Integer.MIN_VALUE;
        
        /* Make corners for volume: 0 = bottom-lower-left, 1 = top-lower-left, 2=bottom-upper-left, 3=top-upper-left
         * 4 = bottom-lower-right, 5 = top-lower-right, 6 = bottom-upper-right, 7 = top-upper-right */  
        Vector3D corners[] = new Vector3D[8];
        int[] chunk_x = new int[8];
        int[] chunk_z = new int[8];
        for(int x = t.tx, idx = 0; x <= (t.tx+1); x++) {
            for(int y = t.ty; y <= (t.ty+1); y++) {
                for(int z = 0; z <= 1; z++) {
                    corners[idx] = new Vector3D();
                    corners[idx].x = x*tileWidth; corners[idx].y = y*tileHeight; corners[idx].z = z*128;
                    map_to_world.transform(corners[idx]);
                    /* Compute chunk coordinates of corner */
                    chunk_x[idx] = (int)Math.floor(corners[idx].x / 16);
                    chunk_z[idx] = (int)Math.floor(corners[idx].z / 16);
                    /* Compute min/max of chunk coordinates */
                    if(min_chunk_x > chunk_x[idx]) min_chunk_x = chunk_x[idx];
                    if(max_chunk_x < chunk_x[idx]) max_chunk_x = chunk_x[idx];
                    if(min_chunk_z > chunk_z[idx]) min_chunk_z = chunk_z[idx];
                    if(max_chunk_z < chunk_z[idx]) max_chunk_z = chunk_z[idx];
                    idx++;
                }
            }
        }
        /* Make rectangles of X-Z projection of each side of the tile volume, 0 = top, 1 = bottom, 2 = left, 3 = right,
         * 4 = upper, 5 = lower */
        Rectangle rect[] = new Rectangle[6];
        rect[0] = new Rectangle(corners[1], corners[3], corners[5]);
        rect[1] = new Rectangle(corners[0], corners[2], corners[4]);
        rect[2] = new Rectangle(corners[0], corners[1], corners[2]);
        rect[3] = new Rectangle(corners[4], corners[5], corners[6]);
        rect[4] = new Rectangle(corners[2], corners[3], corners[6]);
        rect[5] = new Rectangle(corners[0], corners[1], corners[4]);
        
        /* Now, need to walk through the min/max range to see which chunks are actually needed */
        ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();
        Rectangle chunkrect = new Rectangle();
        int misscnt = 0;
        for(int x = min_chunk_x; x <= max_chunk_x; x++) {
            for(int z = min_chunk_z; z <= max_chunk_z; z++) {
                chunkrect.setSquare(x*16, z*16, 16);
                boolean hit = false;
                /* Check to see if square of chunk intersects any of our rectangle sides */
                for(int rctidx = 0; (!hit) && (rctidx < rect.length); rctidx++) {
                    if(chunkrect.testRectangleIntesectsRectangle(rect[rctidx])) {
                        hit = true;
                    }
                }
                if(hit) {
                    DynmapChunk chunk = new DynmapChunk(x, z);
                    chunks.add(chunk);
                }
                else {
                    misscnt++;
                }
            }
        }
        return chunks;
    }

    @Override
    public boolean render(MapChunkCache cache, HDMapTile tile, String mapname) {
        Color rslt = new Color();
        MapIterator mapiter = cache.getIterator(0, 0, 0);
        /* Build shader state object for each shader */
        HDShaderState[] shaderstate = MapManager.mapman.hdmapman.getShaderStateForTile(tile, cache, mapiter, mapname);
        int numshaders = shaderstate.length;
        if(numshaders == 0)
            return false;
        /* Check if nether world */
        boolean isnether = tile.getWorld().getEnvironment() == Environment.NETHER;
        /* Create buffered image for each */
        DynmapBufferedImage im[] = new DynmapBufferedImage[numshaders];
        DynmapBufferedImage dayim[] = new DynmapBufferedImage[numshaders];
        int[][] argb_buf = new int[numshaders][];
        int[][] day_argb_buf = new int[numshaders][];
        
        for(int i = 0; i < numshaders; i++) {
            HDShader shader = shaderstate[i].getShader();
            HDLighting lighting = shaderstate[i].getLighting();
            if(shader.isEmittedLightLevelNeeded() || lighting.isEmittedLightLevelNeeded())
                need_emittedlightlevel = true;
            if(shader.isSkyLightLevelNeeded() || lighting.isSkyLightLevelNeeded())
                need_skylightlevel = true;
            im[i] = DynmapBufferedImage.allocateBufferedImage(tileWidth, tileHeight);
            argb_buf[i] = im[i].argb_buf;
            if(lighting.isNightAndDayEnabled()) {
                dayim[i] = DynmapBufferedImage.allocateBufferedImage(tileWidth, tileHeight);
                day_argb_buf[i] = dayim[i].argb_buf;
            }
        }
        
        /* Create perspective state object */
        OurPerspectiveState ps = new OurPerspectiveState(mapiter, isnether);        
        
        ps.top = new Vector3D();
        ps.bottom = new Vector3D();
        double xbase = tile.tx * tileWidth;
        double ybase = tile.ty * tileHeight;
        boolean shaderdone[] = new boolean[numshaders];
        boolean rendered[] = new boolean[numshaders];
        for(int x = 0; x < tileWidth; x++) {
            ps.px = x;
            for(int y = 0; y < tileHeight; y++) {
                ps.top.x = ps.bottom.x = xbase + x + 0.5;    /* Start at center of pixel at Y=127.5, bottom at Y=-0.5 */
                ps.top.y = ps.bottom.y = ybase + y + 0.5;
                ps.top.z = maxheight + 0.5; ps.bottom.z = minheight - 0.5;
                map_to_world.transform(ps.top);            /* Transform to world coordinates */
                map_to_world.transform(ps.bottom);
                ps.py = y;
                for(int i = 0; i < numshaders; i++) {
                    shaderstate[i].reset(ps);
                }
                ps.raytrace(cache, mapiter, shaderstate, shaderdone);
                for(int i = 0; i < numshaders; i++) {
                    if(shaderdone[i] == false) {
                        shaderstate[i].rayFinished(ps);
                    }
                    else {
                        shaderdone[i] = false;
                        rendered[i] = true;
                    }
                    shaderstate[i].getRayColor(rslt, 0);
                    argb_buf[i][(tileHeight-y-1)*tileWidth + x] = rslt.getARGB();
                    if(day_argb_buf[i] != null) {
                        shaderstate[i].getRayColor(rslt, 1);
                        day_argb_buf[i][(tileHeight-y-1)*tileWidth + x] = rslt.getARGB();
                    }
                }
            }
        }

        boolean renderone = false;
        /* Test to see if we're unchanged from older tile */
        TileHashManager hashman = MapManager.mapman.hashman;
        for(int i = 0; i < numshaders; i++) {
            long crc = hashman.calculateTileHash(argb_buf[i]);
            boolean tile_update = false;
            String prefix = shaderstate[i].getMap().getPrefix();
            if(rendered[i]) {
                renderone = true;
                MapType.ImageFormat fmt = shaderstate[i].getMap().getImageFormat();
                String fname = tile.getFilename(prefix, fmt);
                File f = new File(tile.getDynmapWorld().worldtilepath, fname);
                FileLockManager.getWriteLock(f);
                try {
                    if((!f.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), prefix, tile.tx, tile.ty))) {
                        /* Wrap buffer as buffered image */
                        Debug.debug("saving image " + f.getPath());
                        if(!f.getParentFile().exists())
                            f.getParentFile().mkdirs();
                        try {
                            FileLockManager.imageIOWrite(im[i].buf_img, fmt, f);
                        } catch (IOException e) {
                            Debug.error("Failed to save image: " + f.getPath(), e);
                        } catch (java.lang.NullPointerException e) {
                            Debug.error("Failed to save image (NullPointerException): " + f.getPath(), e);
                        }
                        MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(fname));
                        hashman.updateHashCode(tile.getKey(), prefix, tile.tx, tile.ty, crc);
                        tile.getDynmapWorld().enqueueZoomOutUpdate(f);
                        tile_update = true;
                    }
                    else {
                        Debug.debug("skipping image " + f.getPath() + " - hash match");
                    }
                } finally {
                    FileLockManager.releaseWriteLock(f);
                    DynmapBufferedImage.freeBufferedImage(im[i]);
                }
                MapManager.mapman.updateStatistics(tile, prefix, true, tile_update, !rendered[i]);
                /* Handle day image, if needed */
                if(dayim[i] != null) {
                    fname = tile.getDayFilename(prefix, fmt);
                    f = new File(tile.getDynmapWorld().worldtilepath, fname);
                    FileLockManager.getWriteLock(f);
                    prefix = prefix+"_day";
                    tile_update = false;
                    try {
                        if((!f.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), prefix, tile.tx, tile.ty))) {
                            /* Wrap buffer as buffered image */
                            Debug.debug("saving image " + f.getPath());
                            if(!f.getParentFile().exists())
                                f.getParentFile().mkdirs();
                            try {
                                FileLockManager.imageIOWrite(dayim[i].buf_img, fmt, f);
                            } catch (IOException e) {
                                Debug.error("Failed to save image: " + f.getPath(), e);
                            } catch (java.lang.NullPointerException e) {
                                Debug.error("Failed to save image (NullPointerException): " + f.getPath(), e);
                            }
                            MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(fname));
                            hashman.updateHashCode(tile.getKey(), prefix, tile.tx, tile.ty, crc);
                            tile.getDynmapWorld().enqueueZoomOutUpdate(f);
                            tile_update = true;
                        }
                        else {
                            Debug.debug("skipping image " + f.getPath() + " - hash match");
                        }
                    } finally {
                        FileLockManager.releaseWriteLock(f);
                        DynmapBufferedImage.freeBufferedImage(dayim[i]);
                    }
                    MapManager.mapman.updateStatistics(tile, prefix, true, tile_update, !rendered[i]);
                }
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

    public boolean isHightestBlockYDataNeeded() {
        return false;
    }
    
    public boolean isBlockTypeDataNeeded() {
        return true;
    }
    
    public double getScale() {
        return scale;
    }

    public int getModelScale() {
        return modscale;
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
        s(mapObject, "scale", scale);
        s(mapObject, "worldtomap", world_to_map.toJSON());
        s(mapObject, "maptoworld", map_to_world.toJSON());
        int dir = ((360 + (int)(22.5+azimuth)) / 45) % 8;;
        s(mapObject, "compassview", directions[dir]);

    }
}
