package org.dynmap.renderer;

public interface RenderPatchFactory {
    public enum SideVisible { TOP, BOTTOM, BOTH, FLIP };
    
    /**
     * Get/create patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range umin to umax (parallel to the U vecotr) and vmin to vmax
     * (parallel to the V vector).
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
     * The surface also needs to define the index of the texture to be used for shading the surface.
     * 
     * @param x0 - X coordinate of origin of patch
     * @param y0 - Y coordinate of origin of patch
     * @param z0 - Z coordinate of origin of patch
     * @param xu - X coordinate of end of U vector
     * @param yu - Y coordinate of end of U vector
     * @param zu - Z coordinate of end of U vector
     * @param xv - X coordinate of end of V vector
     * @param yv - Y coordinate of end of V vector
     * @param zv - Z coordinate of end of V vector
     * @param umin - lower bound for visibility along U vector (use 0.0 by default)
     * @param umax - upper bound for visibility along U vector (use 1.0 by default)
     * @param vmin - lower bound for visibility along V vector (use 0.0 by default)
     * @param vmax - upper bound for visibility along V vector (use 1.0 by default)
     * @param sidevis - Controls which sides of the surface are visible (U cross V defines normal - TOP is from that side, BOTTOM is opposite side)
     * @param textureidx - texture index to be used for patch
     */
    public RenderPatch getPatch(double x0, double y0, double z0, double xu, double yu, double zu, double xv, double yv, double zv, double umin, double umax, double vmin, double vmax, SideVisible sidevis, int textureidx);
    /**
     * Get/create patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range umin to umax (parallel to the U vector) and vmin to vmax
     * (parallel to the V vector).  
     * vmaxatumax allows for a triangle or trapezoid, specifying the max v value at umax (with vmax treated as the max v at u=umin)
     * vminatumax allows for a triangle or trapezoid, specifying the min v value at umax (with vmin treated as the min v at u=umin)
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
     * The surface also needs to define the index of the texture to be used for shading the surface.
     * 
     * @param x0 - X coordinate of origin of patch
     * @param y0 - Y coordinate of origin of patch
     * @param z0 - Z coordinate of origin of patch
     * @param xu - X coordinate of end of U vector
     * @param yu - Y coordinate of end of U vector
     * @param zu - Z coordinate of end of U vector
     * @param xv - X coordinate of end of V vector
     * @param yv - Y coordinate of end of V vector
     * @param zv - Z coordinate of end of V vector
     * @param umin - lower bound for visibility along U vector (use 0.0 by default)
     * @param umax - upper bound for visibility along U vector (use 1.0 by default)
     * @param vmin - lower bound for visibility along V vector at u=umin (use 0.0 by default)
     * @param vminatumax - lower bound for visibility along V vector at u=umax (use 0.0 by default)
     * @param vmax - upper bound for visibility along V vector at u=umin (use 1.0 by default)
     * @param vmaxatumax - upper bound for visibility along V vector at u=umax (use 1.0 by default)
     * @param sidevis - Controls which sides of the surface are visible (U cross V defines normal - TOP is from that side, BOTTOM is opposite side)
     * @param textureidx - texture index to be used for patch
     */
    public RenderPatch getPatch(double x0, double y0, double z0, double xu, double yu, double zu, double xv, double yv, double zv, double umin, double umax, double vmin, double vminatumax, double vmax, double vmaxatumax, SideVisible sidevis, int textureidx);
    /**
     * Get/create patch with given attributes.
     * 
     * Definition is a 2D triangular surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The visibility of
     * the patch is limited to the sum of the unit vector values for U and V via uplusvmax.
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
     * The surface also needs to define the index of the texture to be used for shading the surface.
     * 
     * @param x0 - X coordinate of origin of patch
     * @param y0 - Y coordinate of origin of patch
     * @param z0 - Z coordinate of origin of patch
     * @param xu - X coordinate of end of U vector
     * @param yu - Y coordinate of end of U vector
     * @param zu - Z coordinate of end of U vector
     * @param xv - X coordinate of end of V vector
     * @param yv - Y coordinate of end of V vector
     * @param zv - Z coordinate of end of V vector
     * @param uplusvmax - limit on sum of unit vectors for U and V (use 1.0 for triangle extending from origin to U and to V)
     * @param sidevis - Controls which sides of the surface are visible (U cross V defines normal - TOP is from that side, BOTTOM is opposite side)
     * @param textureidx - texture index to be used for patch
     */
    @Deprecated
    public RenderPatch getPatch(double x0, double y0, double z0, double xu, double yu, double zu, double xv, double yv, double zv, double uplusvmax, SideVisible sidevis, int textureidx);
    /**
     * Get/create patch with given attributes.
     * 
     * Generate from existing patch, after rotating xrot degrees around the X axis then yrot degrees around the Y axis, and then zrot degrees arond Z.
     * 
     * @param patch - original patch
     * @param xrot - degrees to rotate around X
     * @param yrot - degrees to rotate around Y
     * @param zrot - degrees to rotate around Z
     * @param textureidx - texture index to be used for rotated patch (-1 means same as original patch)
     * @return patch requested
     */
    public RenderPatch getRotatedPatch(RenderPatch patch, int xrot, int yrot, int zrot, int textureidx);
    /**
     * Get named patch with given attributes.  Name can encode rotation and patch index info
     * "name" - simple name
     * "name@rot" - name with rotation around Y
     * "name@x/y/z" - name with rotation around x, then y, then z axis
     * "name#patch" - name with explicit patch index
     * 
     * @param name - name of patch (must be defined in same config file as custom renderer): supports name@yrot, name@xrot/yrot/zrot
     * @param textureidx - texture index to be used for patch, if not provided in name encoding (#patchid suffix)
     * @return patch requested
     */
    public RenderPatch getNamedPatch(final String name, int textureidx);
    /**
     * Get index of texture from texture map, using given key value
     * @param id - texture map ID
     * @param key - key of requested texture
     * @return index of texture, or -1 if not found
     */
    public int getTextureIndexFromMap(String id, int key);
    /**
     * Get number of textures defined in given texture map
     * @param id - texture map ID
     * @return number of textures, or -1 if map not found
     */
    public int getTextureCountFromMap(String id);
}
