package org.dynmap.exporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.hdmap.CustomBlockModel;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDBlockStateTextureMap;
import org.dynmap.hdmap.HDScaledBlockModels;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.IndexedVector3D;
import org.dynmap.utils.IndexedVector3DList;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

public class OBJExport {
    private final File destZipFile;     // Destination ZIP file
    private final HDShader shader;      // Shader to be used for textures
    private final DynmapWorld world;    // World to be rendered
    private final DynmapCore core;
    private final String basename;
    private int minX, minY, minZ;       // Minimum world coordinates to be rendered
    private int maxX, maxY, maxZ;       // Maximum world coordinates to be rendered
    private static Charset UTF8 = Charset.forName("UTF-8");
    private ZipOutputStream zos;        // Output stream ZIP for result
    private double originX, originY, originZ;   // Origin for exported model
    private double scale = 1.0;         // Scale for exported model
    private boolean centerOrigin = true;    // Center at origin
    private PatchDefinition[] defaultPathces;   // Default patches for solid block, indexed by BlockStep.ordinal()
    private HashSet<String> matIDs = new HashSet<String>();     // Set of defined material ids for RP
    
    private static class Face {
        String groupLine;
        String faceLine;
    }
    
    private HashMap<String, List<Face>> facesByTexture = new HashMap<String, List<Face>>();
    private static final int MODELSCALE = 16;
    private static final double BLKSIZE = 1.0 / (double) MODELSCALE;
    
    // Index of group settings
    public static final int GROUP_CHUNK = 0;
    public static final int GROUP_TEXTURE = 1;
    public static final int GROUP_BLOCKID = 2;
    public static final int GROUP_BLOCKIDMETA = 3;
    public static final int GROUP_COUNT = 4;
    private String[] group = new String[GROUP_COUNT];
    private boolean[] enabledGroups = new boolean[GROUP_COUNT];
    private String groupline = null;
    
    // Vertex set
    private IndexedVector3DList vertices;
    // UV set
    private IndexedVector3DList uvs;
    // Scaled models
    private HDScaledBlockModels models;
    
    public static final int ROT0 = 0;
    public static final int ROT90 = 1;
    public static final int ROT180 = 2;
    public static final int ROT270 = 3;
    public static final int HFLIP = 4;
    
    private static final double[][] pp = {
        { 0, 0, 0, 1, 0, 0, 0, 0, 1 },
        { 0, 1, 1, 1, 1, 1, 0, 1, 0 },
        { 1, 0, 0, 0, 0, 0, 1, 1, 0 },
        { 0, 0, 1, 1, 0, 1, 0, 1, 1 },
        { 0, 0, 0, 0, 0, 1, 0, 1, 0 },
        { 1, 0, 1, 1, 0, 0, 1, 1, 1 }
    };
    
    /**
     * Constructor for OBJ file export
     * @param dest - destination file (ZIP)
     * @param shader - shader to be used for coloring/texturing
     * @param world - world to be rendered
     * @param core - core object
     * @param basename - base file name
     */
    public OBJExport(File dest, HDShader shader, DynmapWorld world, DynmapCore core, String basename) {
        destZipFile = dest;
        this.shader = shader;
        this.world = world;
        this.core = core;
        this.basename = basename;
        this.defaultPathces = new PatchDefinition[6];
        PatchDefinitionFactory fact = HDBlockModels.getPatchDefinitionFactory();
        for (BlockStep s : BlockStep.values()) {
            double[] p = pp[s.getFaceEntered()];
            int ord = s.ordinal();
            defaultPathces[ord] = fact.getPatch(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], 0, 1, 0, 0, 1, 1, SideVisible.TOP, ord);
        }
        vertices = new IndexedVector3DList(new IndexedVector3DList.ListCallback() {
            @Override
            public void elementAdded(IndexedVector3DList list, IndexedVector3D newElement) {
                try {
                    /* Minecraft XYZ maps to OBJ YZX */
                    addStringToExportedFile(String.format(Locale.US, "v %.4f %.4f %.4f\n", 
                            (newElement.x - originX) * scale,
                            (newElement.y - originY) * scale,
                            (newElement.z - originZ) * scale
                            ));
                } catch (IOException iox) {
                }
            }
        });
        uvs = new IndexedVector3DList(new IndexedVector3DList.ListCallback() {
            @Override
            public void elementAdded(IndexedVector3DList list, IndexedVector3D newElement) {
                try {
                    addStringToExportedFile(String.format(Locale.US, "vt %.4f %.4f\n", newElement.x, newElement.y));
                } catch (IOException iox) {
                }
            }
        });
        // Get models
        models = HDBlockModels.getModelsForScale(MODELSCALE);
    }
    /**
     * Set render bounds
     * 
     * @param minx - minimum X coord
     * @param miny - minimum Y coord
     * @param minz - minimum Z coord
     * @param maxx - maximum X coord
     * @param maxy - maximum Y coord
     * @param maxz - maximum Z coord
     */
    public void setRenderBounds(int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        if (minx < maxx) {
            minX = minx; maxX = maxx;
        }
        else {
            minX = maxx; maxX = minx;
        }
        if (miny < maxy) {
            minY = miny; maxY = maxy;
        }
        else {
            minY = maxy; maxY = miny;
        }
        if (minz < maxz) {
            minZ = minz; maxZ = maxz;
        }
        else {
            minZ = maxz; maxZ = minz;
        }
        if (minY < world.minY) minY = world.minY;
        if (maxY >= world.worldheight) maxY = world.worldheight - 1;
        if (centerOrigin) {
            originX = (maxX + minX) / 2.0;
            originY = minY;
            originZ = (maxZ + minZ) / 2.0;
        }
    }
    /**
     * Set origin for exported model
     * @param ox - origin x
     * @param oy - origin y
     * @param oz - origin z
     */
    public void setOrigin(double ox, double oy, double oz) {
        originX = ox;
        originY = oy;
        originZ = oz;
        centerOrigin = false;
    }
    /**
     * Set scale for exported model
     * @param scale = scale
     */
    public void setScale(double scale) {
        this.scale = scale;
    }
    /**
     * Process export
     * 
     * @param sender - command sender: use for feedback messages
     * @return true if successful, false if not
     */
    public boolean processExport(DynmapCommandSender sender) {
        boolean good = false;
        try {
            // Open ZIP file destination
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZipFile)));
            
            List<DynmapChunk> requiredChunks = new ArrayList<DynmapChunk>();
            int mincx = (minX >> 4);
            int maxcx = (maxX + 15) >> 4;
            int mincz = (minZ >> 4);
            int maxcz = (maxZ + 15) >> 4;
            boolean[] edgebits = new boolean[6];

            startExportedFile(basename + ".obj");
            // Add material library
            addStringToExportedFile("mtllib " + basename + ".mtl\n");

            // Loop through - do 8x8 chunks at a time (plus 1 border each way)
            for (int cx = mincx; cx <= maxcx; cx += 4) {
                for (int cz = mincz; cz <= maxcz; cz += 4) {
                    // Build chunk cache for block of chunks
                    requiredChunks.clear();
                    for (int i = -1; i < 5; i++) {
                        for (int j = -1; j < 5; j++) {
                            if (((cx+i) <= maxcx) && ((cz+j) <= maxcz) && ((cx+i) >= mincx) && ((cz+j) >= mincz)) {
                                requiredChunks.add(new DynmapChunk(cx + i, cz + j));
                            }
                        }
                    }
                    // Get the chunk buffer
                    MapChunkCache cache = core.getServer().createMapChunkCache(world, requiredChunks, true, false, true, false);
                    if (cache == null) {
                        throw new IOException("Error loading chunk cache");
                    }
                    MapIterator iter = cache.getIterator(minX, minY, minZ);
                    for (int x = cx * 16; (x < (cx * 16 + 64)) && (x <= maxX); x++) {
                        if (x < minX) x = minX;
                        edgebits[BlockStep.X_PLUS.ordinal()] = (x == minX);
                        edgebits[BlockStep.X_MINUS.ordinal()] = (x == maxX);
                        for (int z = cz * 16; (z < (cz * 16 + 64)) && (z <= maxZ); z++) {
                            if (z < minZ) z = minZ;
                            edgebits[BlockStep.Z_PLUS.ordinal()] = (z == minZ);
                            edgebits[BlockStep.Z_MINUS.ordinal()] = (z == maxZ);
                            iter.initialize(x, minY, z);
                            updateGroup(GROUP_CHUNK, "chunk" + (x >> 4) + "_" + (z >> 4));
                            // Do first (bottom)
                            edgebits[BlockStep.Y_MINUS.ordinal()] = true;
                            edgebits[BlockStep.Y_PLUS.ordinal()] = false;
                            DynmapBlockState blk = iter.getBlockType();
                            if (blk.isNotAir()) {  // Not air
                                handleBlock(blk, iter, edgebits);
                            }
                            // Do middle
                            edgebits[BlockStep.Y_MINUS.ordinal()] = false;
                            for (int y = minY + 1; y < maxY; y++) {
                                iter.setY(y);
                                blk = iter.getBlockType();
                                if (blk.isNotAir()) {  // Not air
                                    handleBlock(blk, iter, edgebits);
                                }
                            }
                            // Do top
                            edgebits[BlockStep.Y_PLUS.ordinal()] = true;
                            iter.setY(maxY);
                            blk = iter.getBlockType();
                            if (blk.isNotAir()) {  // Not air
                                handleBlock(blk, iter, edgebits);
                            }
                        }
                    }
                    // Output faces by texture
                    String grp = "";
                    for (String material : facesByTexture.keySet()) {
                        List<Face> faces = facesByTexture.get(material);
                        matIDs.add(material);   // Record material use
                        addStringToExportedFile(String.format("usemtl %s\n", material)); 
                        for (Face face : faces) {
                            if ((face.groupLine != null) && (!face.groupLine.equals(grp))) {
                                grp = face.groupLine;
                                addStringToExportedFile(grp);
                            }
                            addStringToExportedFile(face.faceLine);
                        }
                    }
                    // Clear face table
                    facesByTexture.clear();
                    // Clean up vertices we've moved past
                    vertices.resetSet(minX, minY, minZ, cx * 16 + 64, maxY, cz * 16 + 64);
                }
            }
            finishExportedFile();
            // If shader provided, add shader content to ZIP
            if (shader != null) {
                sender.sendMessage("Adding textures from shader " + shader.getName());
                shader.exportAsMaterialLibrary(sender, this);
                sender.sendMessage("Texture export completed");
            }
            // And close the ZIP
            zos.finish();
            zos.close();
            zos = null;
            good = true;
            sender.sendMessage("Export completed - " + destZipFile.getPath());
        } catch (IOException iox) {
            sender.sendMessage("Export failed: " + iox.getMessage());
        } finally {
            if (zos != null) {
                try { zos.close(); } catch (IOException e) {}
                zos = null;
                destZipFile.delete();
            }
        }
        return good;
    }
    /**
     * Start adding file to export
     * @param fname - path/name of file in destination zip
     * @throws IOException if error starting file
     */
    public void startExportedFile(String fname) throws IOException {
        ZipEntry ze = new ZipEntry(fname);
        zos.putNextEntry(ze);
    }
    /**
     * Add bytes to current exported file
     * @param buf - buffer with bytes
     * @param off - offset of start
     * @param len - length to be added
     * @throws IOException if error adding to file
     */
    public void addBytesToExportedFile(byte[] buf, int off, int len) throws IOException {
        zos.write(buf, off, len);
    }
    /**
     * Add string to curent exported file (UTF-8)
     * @param str - string to be written
     * @throws IOException if error adding to file
     */
    public void addStringToExportedFile(String str) throws IOException {
        byte[] b = str.getBytes(UTF8);
        zos.write(b, 0, b.length);
    }
    /**
     * Finish adding file to export
     * @throws IOException if error completing file
     */
    public void finishExportedFile() throws IOException {
        zos.closeEntry();
    }
    /**
     * Handle block at current iterator coord
     * @param id - block ID
     * @param iter - iterator
     * @param edgebits - bit N corresponds to side N being an endge (forge render)
     */
    private void handleBlock(DynmapBlockState blk, MapIterator map, boolean[] edgebits) throws IOException {
        BlockStep[] steps = BlockStep.values();
        int[] txtidx = null;
        // See if the block has a patch model
        RenderPatch[] patches = models.getPatchModel(blk);
        /* If no patches, see if custom model */
        if(patches == null) {
            CustomBlockModel cbm = models.getCustomBlockModel(blk);
            if(cbm != null) {   /* If so, get our meshes */
                patches = cbm.getMeshForBlock(map);
            }
        }
        if (patches != null) {
            steps = new BlockStep[patches.length];
            txtidx = new int[patches.length];
            for (int i = 0; i < txtidx.length; i++) {
                txtidx[i] = ((PatchDefinition) patches[i]).getTextureIndex();
                steps[i] = ((PatchDefinition) patches[i]).step;
            }
        }
        else {  // See if volumetric
            short[] smod = models.getScaledModel(blk);
            if (smod != null) {
                patches = getScaledModelAsPatches(smod);
                steps = new BlockStep[patches.length];
                txtidx = new int[patches.length];
                for (int i = 0; i < patches.length; i++) {
                    PatchDefinition pd = (PatchDefinition) patches[i];
                    steps[i] = pd.step;
                    txtidx[i] = pd.getTextureIndex();
                }
            }
        }
        // Set block ID and ID+meta groups
        updateGroup(GROUP_BLOCKID, "blk" + blk.baseState.globalStateIndex);
        updateGroup(GROUP_BLOCKIDMETA, "blk" + blk.globalStateIndex);

        // Get materials for patches
        String[] mats = shader.getCurrentBlockMaterials(blk, map, txtidx, steps);
        
        if (patches != null) {  // Patch based model?
            for (int i = 0; i < patches.length; i++) {
                addPatch((PatchDefinition) patches[i], map.getX(), map.getY(), map.getZ(), mats[i]);
            }
        }
        else {
            boolean opaque = HDBlockStateTextureMap.getTransparency(blk) == BlockTransparency.OPAQUE;
            for (int face = 0; face < 6; face++) {
                DynmapBlockState blk2 = map.getBlockTypeAt(BlockStep.oppositeValues[face]);  // Get block in direction
                // If we're not solid, or adjacent block is not solid, draw side
                if ((!opaque) || blk2.isAir() || edgebits[face] || (HDBlockStateTextureMap.getTransparency(blk2) != BlockTransparency.OPAQUE)) {
                    addPatch(defaultPathces[face], map.getX(), map.getY(), map.getZ(), mats[face]);
                }
            }
        }
    }
    private int[] getTextureUVs(PatchDefinition pd, int rot) {
        int[] uv = new int[4];
        if (rot == ROT0) {
            uv[0] = uvs.getVectorIndex(pd.umin, pd.vmin, 0);
            uv[1] = uvs.getVectorIndex(pd.umax, pd.vmin, 0);
            uv[2] = uvs.getVectorIndex(pd.umax, pd.vmax, 0);
            uv[3] = uvs.getVectorIndex(pd.umin, pd.vmax, 0);
        }
        else if (rot == ROT90) {    // 90 degrees on texture
            uv[0] = uvs.getVectorIndex(1.0 - pd.vmin, pd.umin, 0);
            uv[1] = uvs.getVectorIndex(1.0 - pd.vmin, pd.umax, 0);
            uv[2] = uvs.getVectorIndex(1.0 - pd.vmax, pd.umax, 0);
            uv[3] = uvs.getVectorIndex(1.0 - pd.vmax, pd.umin, 0);
        }
        else if (rot == ROT180) {    // 180 degrees on texture
            uv[0] = uvs.getVectorIndex(1.0 - pd.umin, 1.0 - pd.vmin, 0);
            uv[1] = uvs.getVectorIndex(1.0 - pd.umax, 1.0 - pd.vmin, 0);
            uv[2] = uvs.getVectorIndex(1.0 - pd.umax, 1.0 - pd.vmax, 0);
            uv[3] = uvs.getVectorIndex(1.0 - pd.umin, 1.0 - pd.vmax, 0);
        }
        else if (rot == ROT270) {    // 270 degrees on texture
            uv[0] = uvs.getVectorIndex(pd.vmin, 1.0 - pd.umin, 0);
            uv[1] = uvs.getVectorIndex(pd.vmin, 1.0 - pd.umax, 0);
            uv[2] = uvs.getVectorIndex(pd.vmax, 1.0 - pd.umax, 0);
            uv[3] = uvs.getVectorIndex(pd.vmax, 1.0 - pd.umin, 0);
        }
        else if (rot == HFLIP) {
            uv[0] = uvs.getVectorIndex(1.0 - pd.umin, pd.vmin, 0);
            uv[1] = uvs.getVectorIndex(1.0 - pd.umax, pd.vmin, 0);
            uv[2] = uvs.getVectorIndex(1.0 - pd.umax, pd.vmax, 0);
            uv[3] = uvs.getVectorIndex(1.0 - pd.umin, pd.vmax, 0);
        }
        else {
            uv[0] = uvs.getVectorIndex(pd.umin, pd.vmin, 0);
            uv[1] = uvs.getVectorIndex(pd.umax, pd.vmin, 0);
            uv[2] = uvs.getVectorIndex(pd.umax, pd.vmax, 0);
            uv[3] = uvs.getVectorIndex(pd.umin, pd.vmax, 0);
        }
        return uv;
    }
    /**
     * Add patch as face to output
     */
    private void addPatch(PatchDefinition pd, double x, double y, double z, String material) throws IOException {
        // No material?  No face
        if (material == null) {
            return;
        }
        int rot = 0;
        int rotidx = material.indexOf('@'); // Check for rotation modifier
        if (rotidx >= 0) {
            rot = material.charAt(rotidx+1) - '0';  // 0-3
            material = material.substring(0, rotidx);
        }
        int[] v = new int[4];
        int[] uv = getTextureUVs(pd, rot);
        // Get offsets for U and V from origin
        double ux = pd.xu - pd.x0;
        double uy = pd.yu - pd.y0;
        double uz = pd.zu - pd.z0;
        double vx = pd.xv - pd.x0;
        double vy = pd.yv - pd.y0;
        double vz = pd.zv - pd.z0;
        // Offset to origin corner
        x = x + pd.x0;
        y = y + pd.y0;
        z = z + pd.z0;
        // Origin corner, offset by umin, vmin
        v[0] = vertices.getVectorIndex(x + ux*pd.umin + vx*pd.vmin, y + uy*pd.umin + vy*pd.vmin, z + uz*pd.umin + vz*pd.vmin);
        uv[0] = uvs.getVectorIndex(pd.umin, pd.vmin, 0);
        // Second is end of U (umax, vmin)
        v[1] = vertices.getVectorIndex(x + ux*pd.umax + vx*pd.vmin, y + uy*pd.umax + vy*pd.vmin, z + uz*pd.umax + vz*pd.vmin);
        uv[1] = uvs.getVectorIndex(pd.umax, pd.vmin, 0);
        // Third is end of U+V (umax, vmax)
        v[2] = vertices.getVectorIndex(x + ux*pd.umax + vx*pd.vmax, y + uy*pd.umax + vy*pd.vmax, z + uz*pd.umax + vz*pd.vmax);
        uv[2] = uvs.getVectorIndex(pd.umax, pd.vmax, 0);
        // Forth is end of V (umin, vmax)
        v[3] = vertices.getVectorIndex(x + ux*pd.umin + vx*pd.vmax, y + uy*pd.umin + vy*pd.vmax, z + uz*pd.umin + vz*pd.vmax);
        uv[3] = uvs.getVectorIndex(pd.umin, pd.vmax, 0);
        // Add patch to file
        addPatchToFile(v, uv, pd.sidevis, material, rot);
    }
    private void addPatchToFile(int[] v, int[] uv, SideVisible sv, String material, int rot) throws IOException {
        List<Face> faces = facesByTexture.get(material);
        if (faces == null) {
            faces = new ArrayList<Face>();
            facesByTexture.put(material, faces);
        }
        // If needed, rotate the UV sequence
        if (rot == HFLIP) { // Flip horizonntal
            int newuv[] = new int[uv.length];
            for (int i = 0; i < uv.length; i++) {
                newuv[i] = uv[i ^ 1];
            }
            uv = newuv;
        }
        else if (rot != ROT0) {
            int newuv[] = new int[uv.length];
            for (int i = 0; i < uv.length; i++) {
                newuv[i] = uv[(i+4-rot) % uv.length];
            }
            uv = newuv;
        }
        Face f = new Face();
        f.groupLine = updateGroup(GROUP_TEXTURE, material);
        switch (sv) {
            case TOP:
                f.faceLine = String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3]); 
                break;
            case BOTTOM:
                f.faceLine = String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[3], v[2], uv[2], v[1], uv[1], v[0], uv[0]); 
                break;
            case BOTH:
                f.faceLine = String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3]); 
                f.faceLine += String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[3], v[2], uv[2], v[1], uv[1], v[0], uv[0]); 
                break;
            case FLIP:
                f.faceLine = String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3]); 
                f.faceLine += String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[2], v[2], uv[3], v[1], uv[0], v[0], uv[1]); 
                break;
        }
        faces.add(f);
    }
    
    public Set<String> getMaterialIDs() {
        return matIDs;
    }
    
    private static final boolean getSubblock(short[] mod, int x, int y, int z) {
        if ((x >= 0) && (x < MODELSCALE) && (y >= 0) && (y < MODELSCALE) && (z >= 0) && (z < MODELSCALE)) {
            return mod[MODELSCALE*MODELSCALE*y + MODELSCALE*z + x] != 0;
        }
        return false;
    }
    // Scan along X axis
    private int scanX(short[] tmod, int x, int y, int z) {
        int xlen = 0;
        while (getSubblock(tmod, x+xlen, y, z)) { 
            xlen++;
        }
        return xlen;
    }
    // Scan along Z axis for rows matching given x length
    private int scanZ(short[] tmod, int x, int y, int z, int xlen) {
        int zlen = 0;
        while (scanX(tmod, x, y, z+zlen) >= xlen) {
            zlen++;
        }
        return zlen;
    }
    // Scan along Y axis for layers matching given X and Z lengths
    private int scanY(short[] tmod, int x, int y, int z, int xlen, int zlen) {
        int ylen = 0;
        while (scanZ(tmod, x, y+ylen, z, xlen) >= zlen) {
            ylen++;
        }
        return ylen;
    }
    private void addSubblock(short[] tmod, int x, int y, int z, List<RenderPatch> list) {
        // Find dimensions of cuboid
        int xlen = scanX(tmod, x, y, z);
        int zlen = scanZ(tmod, x, y, z, xlen);
        int ylen = scanY(tmod, x, y, z, xlen, zlen);
        // Add equivalent of boxblock
        CustomRenderer.addBox(HDBlockModels.getPatchDefinitionFactory(), list, 
                BLKSIZE * x, BLKSIZE * (x+xlen), 
                BLKSIZE * y, BLKSIZE * (y+ylen),
                BLKSIZE * z, BLKSIZE * (z+zlen), 
                HDBlockModels.boxPatchList);
        // And remove blocks from model (since we have them covered)
        for (int xx = 0; xx < xlen; xx++) {
            for (int yy = 0; yy < ylen; yy++) {
                for (int zz = 0; zz < zlen; zz++) {
                    tmod[MODELSCALE*MODELSCALE*(y+yy) + MODELSCALE*(z+zz) + (x+xx)] = 0;
                }
            }
        }
    }
    private PatchDefinition[] getScaledModelAsPatches(short[] mod) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        short[] tmod = Arrays.copyOf(mod, mod.length);  // Make copy
        for (int y = 0; y < MODELSCALE; y++) {
            for (int z = 0; z < MODELSCALE; z++) {
                for (int x = 0; x < MODELSCALE; x++) {
                    if (getSubblock(tmod, x, y, z)) {   // If occupied, try to add to list
                        addSubblock(tmod, x, y, z, list);
                    }
                }
            }
        }
        PatchDefinition[] pd = new PatchDefinition[list.size()];
        for (int i = 0; i < pd.length; i++) {
            pd[i] = (PatchDefinition) list.get(i);
        }
        return pd;
    }
    
    private String updateGroup(int grpIndex, String newgroup) {
        if (enabledGroups[grpIndex]) {
            if (!newgroup.equals(group[grpIndex])) {
                group[grpIndex] = newgroup;
                String newline = "g";
                for (int i = 0; i < GROUP_COUNT; i++) {
                    if (enabledGroups[i]) {
                        newline += " " + group[i];
                    }
                }
                newline += "\n";
                groupline = newline;
            }
        }
        return groupline;
    }
    
    public boolean getGroupEnabled(int grpIndex) {
        if (grpIndex < enabledGroups.length) {
            return enabledGroups[grpIndex];
        }
        else {
            return false;
        }
    }
    public void setGroupEnabled(int grpIndex, boolean set) {
        if (grpIndex < enabledGroups.length) {
            enabledGroups[grpIndex] = set;
        }
    }
    public String getBaseName() {
        return basename;
    }
}
