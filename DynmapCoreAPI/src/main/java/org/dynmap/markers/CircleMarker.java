package org.dynmap.markers;

/**
 * This defines the public interface to a circle marker object, for use with the MarkerAPI
 */
public interface CircleMarker extends MarkerDescription, EnterExitMarker {
    /**
     * Get center X coordinate
     * @return x coordinate
     */
    public double getCenterX();
    /**
     * Get center Y coordinate
     * @return y coordinate
     */
    public double getCenterY();
    /**
     * Get center Z coordinate
     * @return z coordinate
     */
    public double getCenterZ();
    /**
     * Update the cenerlocation
     * @param worldid - world ID
     * @param x - x coord
     * @param y - y coord
     * @param z - z coord
     */
    public void setCenter(String worldid, double x, double y, double z);
    /**
     * Get radius - X axis
     * 
     * @return radius, in blocks, of X axis
     */
    public double getRadiusX();
    /**
     * Get radius - Z axis
     * 
     * @return radius, in blocks, of Z axis
     */
    public double getRadiusZ();
    /**
     * Set X and Z radii
     * 
     * @param xr - radius on X axis
     * @param zr - radius on Z axis
     */
    public void setRadius(double xr, double zr);
    /**
     * Set line style
     * @param weight - stroke weight
     * @param opacity - stroke opacity
     * @param color - stroke color (0xRRGGBB)
     */
    public void setLineStyle(int weight, double opacity, int color);
    /**
     * Get line weight
     * @return weight
     */
    public int getLineWeight();
    /**
     * Get line opacity
     * @return opacity (0.0-1.0)
     */
    public double getLineOpacity();
    /**
     * Get line color
     * @return color (0xRRGGBB)
     */
    public int getLineColor();
    /**
     * Set fill style
     * @param opacity - fill color opacity
     * @param color - fill color (0xRRGGBB)
     */
    public void setFillStyle(double opacity, int color);
    /**
     * Get fill opacity
     * @return opacity (0.0-1.0)
     */
    public double getFillOpacity();
    /**
     * Get fill color
     * @return color (0xRRGGBB)
     */
    public int getFillColor();
    /**
     * Set resolution boost flag
     * @param bflag - boost flag
     */
    public void setBoostFlag(boolean bflag);
    /**
     * Get resolution boost flag
     * @return boost flag
     */
    public boolean getBoostFlag();
}
