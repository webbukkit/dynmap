package org.dynmap.markers;

/**
 * This defines the public interface to a poly-line marker object, for use with the MarkerAPI
 */
public interface PolyLineMarker extends MarkerDescription {
    /**
     * Get corner location count
     */
    public int getCornerCount();
    /**
     * Get X coordinate of corner N
     * @param n - corner index
     * @return coordinate
     */
    public double getCornerX(int n);
    /**
     * Get Y coordinate of corner N
     * @param n - corner index
     * @return coordinate
     */
    public double getCornerY(int n);
    /**
     * Get Z coordinate of corner N
     * @param n - corner index
     * @return coordinate
     */
    public double getCornerZ(int n);
    /**
     * Set coordinates of corner N
     * @param n - index of corner: append new corner if &gt;= corner count, else replace existing
     * @param x - x coordinate
     * @param y - y coordinate
     * @param z - z coordinate
     */
    public void setCornerLocation(int n, double x, double y, double z);
    /**
     * Set/replace all corners
     * @param x - list of x coordinates
     * @param y - list of y coordinates
     * @param z - list of z coordinates
     */
    public void setCornerLocations(double[] x, double[] y, double[] z);
    /**
     * Delete corner N - shift corners after N forward
     * @param n - index of corner
     */
    public void deleteCorner(int n);
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
}
