package org.dynmap.modsupport;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Patch block model
 */
public interface PatchBlockModel extends BlockModel {
    /**
     * Add patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range umin to umax (parallel to the U vecotr) and vmin to vmax
     * (parallel to the V vector).
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
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
     * @param uplusvmax - upper bound for visibility for U+V (use 100.0 by default: &lt;=1.0 for triangle)
     * @param sidevis - Controls which sides of the surface are visible (U cross V defines normal - TOP is from that side, BOTTOM is opposite side)
     * @return patch ID
     */
    @Deprecated
    public String addPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax, SideVisible sidevis);
    /**
     * Add patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range umin to umax (parallel to the U vecotr) and vmin to vmax
     * (parallel to the V vector).
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
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
     * @return patch ID
     */
    public String addPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vminatumax, double vmax, double vmaxatumax, SideVisible sidevis);
    /**
     * Add patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range 0.0 to 1.0 (parallel to the U vecotr) and 0.0 to 1.0
     * (parallel to the V vector).
     * The surface can be visible via one side (SideVisible.TOP, SideVisible.BOTTOM) or both sides (SideVisible.BOTH).
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
     * @param sidevis - Controls which sides of the surface are visible (U cross V defines normal - TOP is from that side, BOTTOM is opposite side)
     * @return patch ID
     */
    public String addPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, SideVisible sidevis);
    /**
     * Add patch with given attributes.
     * 
     * Definition is a 2D parallelogram surface, with origin &lt;x0,y0,z0&gt; within the block, and defined by two edge vectors -
     * one with and end point of &lt;xu,yu,zu&gt;, and a second with an end point of &lt;xv,yv,zv&gt;.  The patch is
     * defined within the unit vector range 0.0 to 1.0 (parallel to the U vecotr) and 0.0 to 1.0
     * (parallel to the V vector).
     * The surface is visible on both sides
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
     * @return patch ID
     */
    public String addPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv);
    /**
     * Add rotated patch
     * @param patchid - existing patch ID
     * @param xrot - x axis rotation (0, 90, 180, 270)
     * @param yrot - y axis rotation (0, 90, 180, 270)
     * @param zrot - z axis rotation (0, 90, 180, 270)
     * @return patch ID
     */
    public String addRotatedPatch(String patchid, int xrot, int yrot, int zrot);
}
