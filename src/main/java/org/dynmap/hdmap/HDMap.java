package org.dynmap.hdmap;

import org.dynmap.DynmapWorld;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
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
import org.dynmap.kzedmap.KzedMap.KzedBufferedImage;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.Vector3D;
import org.json.simple.JSONObject;

public class HDMap extends MapType {
    /* View angles */
    public double azimuth;  /* Angle in degrees from looking north (0), east (90), south (180), or west (270) */
    public double inclination;  /* Angle in degrees from horizontal (0) to vertical (90) */
    public double scale;    /* Scale - tile pixel widths per block */
    /* Represents last step of movement of the ray */
    public enum BlockStep {
        X_PLUS,
        Y_PLUS,
        Z_PLUS,
        X_MINUS,
        Y_MINUS,
        Z_MINUS
    };

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
    
    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /* Maximum and minimum inclinations */
    public static final double MAX_INCLINATION = 90.0;
    public static final double MIN_INCLINATION = 20.0;
    
    /* Maximum and minimum scale */
    public static final double MAX_SCALE = 64;
    public static final double MIN_SCALE = 1;
    
    private HDShader shaders[];
    private boolean need_skylightlevel = false;
    private boolean need_emittedlightlevel = false;
    private boolean need_biomedata = false;
    private boolean need_rawbiomedata = false;
    
    private class OurPerspectiveState implements HDPerspectiveState {
        int skylightlevel = 15;
        int emittedlightlevel = 0;
        int blocktypeid = 0;
        int blockdata = 0;
        Vector3D top, bottom;
        int px, py;
        BlockStep laststep = BlockStep.Y_MINUS;
        /**
         * Get sky light level - only available if shader requested it
         */
        public final int getSkyLightLevel() { return skylightlevel; }
        /**
         * Get emitted light level - only available if shader requested it
         */
        public final int getEmittedLightLevel() { return emittedlightlevel; }
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

    }
    
    public HDMap(ConfigurationNode configuration) {
        azimuth = configuration.getDouble("azimuth", 135.0);    /* Get azimuth (default to classic kzed POV */
        inclination = configuration.getDouble("inclination", 60.0);
        if(inclination > MAX_INCLINATION) inclination = MAX_INCLINATION;
        if(inclination < MIN_INCLINATION) inclination = MIN_INCLINATION;
        scale = configuration.getDouble("scale", MIN_SCALE);
        if(scale < MIN_SCALE) scale = MIN_SCALE;
        if(scale > MAX_SCALE) scale = MAX_SCALE;
        Log.info("azimuth=" + azimuth + ", inclination=" + inclination + ", scale=" + scale);
        
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
        
        Log.verboseinfo("Loading shaders for map '" + getClass().toString() + "'...");
        List<HDShader> shaders = configuration.<HDShader>createInstances("shaders", new Class<?>[0], new Object[0]);
        this.shaders = new HDShader[shaders.size()];
        shaders.toArray(this.shaders);
        Log.verboseinfo("Loaded " + shaders.size() + " shaders for map '" + getClass().toString() + "'.");
        for(HDShader shader : shaders) {
            if(shader.isBiomeDataNeeded())
                need_biomedata = true;
            if(shader.isEmittedLightLevelNeeded())
                need_emittedlightlevel = true;
            if(shader.isSkyLightLevelNeeded())
                need_skylightlevel = true;
            if(shader.isRawBiomeDataNeeded())
                need_rawbiomedata = true;
        }
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
        MapTile[] result = tiles.toArray(new MapTile[tiles.size()]);
        Log.info("processed update for " + loc);
        for(MapTile mt : result)
            Log.info("need to render " + mt);
        return result;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        HDMapTile t = (HDMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.tx;
        int y = t.ty;
        return new MapTile[] {
            new HDMapTile(w, this, x, y - 1),
            new HDMapTile(w, this, x + 1, y),
            new HDMapTile(w, this, x, y + 1),
            new HDMapTile(w, this, x - 1, y) };
    }

    public void addTile(HashSet<MapTile> tiles, DynmapWorld world, int tx, int ty) {
        tiles.add(new HDMapTile(world, this, tx, ty));
    }

    public void invalidateTile(MapTile tile) {
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
    public boolean render(MapChunkCache cache, MapTile tile, File bogus) {
        HDMapTile t = (HDMapTile) tile;
        World w = t.getWorld();
        Color rslt = new Color();
        MapIterator mapiter = cache.getIterator(0, 0, 0);
        /* Build shader state object for each shader */
        HDShaderState[] shaderstate = new HDShaderState[shaders.length];
        for(int i = 0; i < shaders.length; i++) {
            shaderstate[i] = shaders[i].getStateInstance(this, cache, mapiter);
            if(shaders[i].isEmittedLightLevelNeeded())
                need_emittedlightlevel = true;
            if(shaders[i].isSkyLightLevelNeeded())
                need_skylightlevel = true;
        }
        /* Create perspective state object */
        OurPerspectiveState ps = new OurPerspectiveState();
        
        /* Create buffered image for each */
        KzedBufferedImage im[] = new KzedBufferedImage[shaders.length];
        KzedBufferedImage dayim[] = new KzedBufferedImage[shaders.length];
        int[][] argb_buf = new int[shaders.length][];
        int[][] day_argb_buf = new int[shaders.length][];
        for(int i = 0; i < shaders.length; i++) {
            im[i] = KzedMap.allocateBufferedImage(tileWidth, tileHeight);
            argb_buf[i] = im[i].argb_buf;
            if(shaders[i].isNightAndDayEnabled()) {
                dayim[i] = KzedMap.allocateBufferedImage(tileWidth, tileHeight);
                day_argb_buf[i] = dayim[i].argb_buf;
            }
        }
        
        ps.top = new Vector3D();
        ps.bottom = new Vector3D();
        double xbase = t.tx * tileWidth;
        double ybase = t.ty * tileHeight;
        boolean shaderdone[] = new boolean[shaders.length];
        boolean rendered[] = new boolean[shaders.length];
        for(int x = 0; x < tileWidth; x++) {
            ps.px = x;
            for(int y = 0; y < tileHeight; y++) {
                ps.top.x = ps.bottom.x = xbase + x + 0.5;    /* Start at center of pixel at Y=127.5, bottom at Y=-0.5 */
                ps.top.y = ps.bottom.y = ybase + y + 0.5;
                ps.top.z = 127.5; ps.bottom.z = -0.5;
                map_to_world.transform(ps.top);            /* Transform to world coordinates */
                map_to_world.transform(ps.bottom);
                ps.py = y;
                for(int i = 0; i < shaders.length; i++) {
                    shaderstate[i].reset(ps);
                }
                raytrace(cache, mapiter, ps, shaderstate, shaderdone);
                for(int i = 0; i < shaders.length; i++) {
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
        for(int i = 0; i < shaders.length; i++) {
            long crc = hashman.calculateTileHash(argb_buf[i]);
            boolean tile_update = false;
            String shadername = shaders[i].getName();
            if(rendered[i]) {
                renderone = true;
                String fname = t.getFilename(shadername);
                File f = new File(t.getDynmapWorld().worldtilepath, fname);
                FileLockManager.getWriteLock(f);
                try {
                    if((!f.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), shadername, t.tx, t.ty))) {
                        /* Wrap buffer as buffered image */
                        Debug.debug("saving image " + f.getPath());
                        if(!f.getParentFile().exists())
                            f.getParentFile().mkdirs();
                        try {
                            FileLockManager.imageIOWrite(im[i].buf_img, "png", f);
                        } catch (IOException e) {
                            Debug.error("Failed to save image: " + f.getPath(), e);
                        } catch (java.lang.NullPointerException e) {
                            Debug.error("Failed to save image (NullPointerException): " + f.getPath(), e);
                        }
                        MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(fname));
                        hashman.updateHashCode(tile.getKey(), shadername, t.tx, t.ty, crc);
                        tile.getDynmapWorld().enqueueZoomOutUpdate(f);
                        tile_update = true;
                    }
                    else {
                        Debug.debug("skipping image " + f.getPath() + " - hash match");
                    }
                } finally {
                    FileLockManager.releaseWriteLock(f);
                    KzedMap.freeBufferedImage(im[i]);
                }
                MapManager.mapman.updateStatistics(tile, shadername, true, tile_update, !rendered[i]);
                /* Handle day image, if needed */
                if(dayim[i] != null) {
                    fname = t.getDayFilename(shadername);
                    f = new File(t.getDynmapWorld().worldtilepath, fname);
                    FileLockManager.getWriteLock(f);
                    shadername = shadername+"_day";
                    tile_update = false;
                    try {
                        if((!f.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), shadername, t.tx, t.ty))) {
                            /* Wrap buffer as buffered image */
                            Debug.debug("saving image " + f.getPath());
                            if(!f.getParentFile().exists())
                                f.getParentFile().mkdirs();
                            try {
                                FileLockManager.imageIOWrite(dayim[i].buf_img, "png", f);
                            } catch (IOException e) {
                                Debug.error("Failed to save image: " + f.getPath(), e);
                            } catch (java.lang.NullPointerException e) {
                                Debug.error("Failed to save image (NullPointerException): " + f.getPath(), e);
                            }
                            MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(fname));
                            hashman.updateHashCode(tile.getKey(), shadername, t.tx, t.ty, crc);
                            tile.getDynmapWorld().enqueueZoomOutUpdate(f);
                            tile_update = true;
                        }
                        else {
                            Debug.debug("skipping image " + f.getPath() + " - hash match");
                        }
                    } finally {
                        FileLockManager.releaseWriteLock(f);
                        KzedMap.freeBufferedImage(dayim[i]);
                    }
                    MapManager.mapman.updateStatistics(tile, shadername, true, tile_update, !rendered[i]);
                }
            }
        }
        return renderone;
    }
       
    /**
     * Trace ray, based on "Voxel Tranversal along a 3D line"
     */
    private void raytrace(MapChunkCache cache, MapIterator mapiter, OurPerspectiveState ps, 
            HDShaderState[] shaderstate, boolean[] shaderdone) {
        Vector3D top = ps.top;
        Vector3D bottom = ps.bottom;
        /* Compute total delta on each axis */
        double dx = Math.abs(bottom.x - top.x);
        double dy = Math.abs(bottom.y - top.y);
        double dz = Math.abs(bottom.z - top.z);
        /* Initial block coord */
        int x = (int) (Math.floor(top.x));
        int y = (int) (Math.floor(top.y));
        int z = (int) (Math.floor(top.z));
        /* Compute parametric step (dt) per step on each axis */
        double dt_dx = 1.0 / dx;
        double dt_dy = 1.0 / dy;
        double dt_dz = 1.0 / dz;
        /* Initialize parametric value to 0 (and we're stepping towards 1) */
        double t = 0;
        /* Compute number of steps and increments for each */
        int n = 1;
        int x_inc, y_inc, z_inc;
        
        double t_next_y, t_next_x, t_next_z;
        /* If perpendicular to X axis */
        if (dx == 0) {
            x_inc = 0;
            t_next_x = Double.MAX_VALUE;
        }
        /* If bottom is right of top */
        else if (bottom.x > top.x) {
            x_inc = 1;
            n += (int) (Math.floor(bottom.x)) - x;
            t_next_x = (Math.floor(top.x) + 1 - top.x) * dt_dx;
        }
        /* Top is right of bottom */
        else {
            x_inc = -1;
            n += x - (int) (Math.floor(bottom.x));
            t_next_x = (top.x - Math.floor(top.x)) * dt_dx;
        }
        /* If perpendicular to Y axis */
        if (dy == 0) {
            y_inc = 0;
            t_next_y = Double.MAX_VALUE;
        }
        /* If bottom is above top */
        else if (bottom.y > top.y) {
            y_inc = 1;
            n += (int) (Math.floor(bottom.y)) - y;
            t_next_y = (Math.floor(top.y) + 1 - top.y) * dt_dy;
        }
        /* If top is above bottom */
        else {
            y_inc = -1;
            n += y - (int) (Math.floor(bottom.y));
            t_next_y = (top.y - Math.floor(top.y)) * dt_dy;
        }
        /* If perpendicular to Z axis */
        if (dz == 0) {
            z_inc = 0;
            t_next_z = Double.MAX_VALUE;
        }
        /* If bottom right of top */
        else if (bottom.z > top.z) {
            z_inc = 1;
            n += (int) (Math.floor(bottom.z)) - z;
            t_next_z = (Math.floor(top.z) + 1 - top.z) * dt_dz;
        }
        /* If bottom left of top */
        else {
            z_inc = -1;
            n += z - (int) (Math.floor(bottom.z));
            t_next_z = (top.z - Math.floor(top.z)) * dt_dz;
        }
        /* Walk through scene */
        ps.laststep = BlockStep.Y_MINUS; /* Last step is down into map */
        mapiter.initialize(x, y, z);
        ps.skylightlevel = 15;
        ps.emittedlightlevel = 0;
        for (; n > 0; --n) {
            ps.blocktypeid = mapiter.getBlockTypeID();
            if(ps.blocktypeid != 0) {
                ps.blockdata = mapiter.getBlockData();
                boolean done = true;
                for(int i = 0; i < shaderstate.length; i++) {
                    if(!shaderdone[i])
                        shaderdone[i] = shaderstate[i].processBlock(ps);
                    done = done && shaderdone[i];
                }
                /* If all are done, we're out */
                if(done)
                    return;
            }
            if(need_skylightlevel)
                ps.skylightlevel = mapiter.getBlockSkyLight();
            if(need_emittedlightlevel)
                ps.emittedlightlevel = mapiter.getBlockEmittedLight();
            /* If X step is next best */
            if((t_next_x <= t_next_y) && (t_next_x <= t_next_z)) {
                x += x_inc;
                t = t_next_x;
                t_next_x += dt_dx;
                if(x_inc > 0) {
                    ps.laststep = BlockStep.X_PLUS;
                    mapiter.incrementX();
                }
                else {
                    ps.laststep = BlockStep.X_MINUS;
                    mapiter.decrementX();
                }
            }
            /* If Y step is next best */
            else if((t_next_y <= t_next_x) && (t_next_y <= t_next_z)) {
                y += y_inc;
                t = t_next_y;
                t_next_y += dt_dy;
                if(y_inc > 0) {
                    ps.laststep = BlockStep.Y_PLUS;
                    mapiter.incrementY();
                    if(mapiter.getY() > 127)
                        return;
                }
                else {
                    ps.laststep = BlockStep.Y_MINUS;
                    mapiter.decrementY();
                    if(mapiter.getY() < 0)
                        return;
                }
            }
            /* Else, Z step is next best */
            else {
                z += z_inc;
                t = t_next_z;
                t_next_z += dt_dz;
                if(z_inc > 0) {
                    ps.laststep = BlockStep.Z_PLUS;
                    mapiter.incrementZ();
                }
                else {
                    ps.laststep = BlockStep.Z_MINUS;
                    mapiter.decrementZ();
                }
            }
        }
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
    public List<String> baseZoomFilePrefixes() {
        ArrayList<String> s = new ArrayList<String>();
        for(HDShader r : shaders) {
            s.add(r.getName());
            if(r.isNightAndDayEnabled())
                s.add(r.getName() + "_day");
        }
        return s;
    }

    public int baseZoomFileStepSize() { return 1; }

    private static final int[] stepseq = { 3, 1, 2, 0 };
    
    public MapStep zoomFileMapStep() { return MapStep.X_PLUS_Y_MINUS; }

    public int[] zoomFileStepSequence() { return stepseq; }

    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 5; }

    @Override
    public String getName() {
        return "HDMap";
    }

    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        for(HDShader shader : shaders) {
            shader.buildClientConfiguration(worldObject);
        }
    }
}
