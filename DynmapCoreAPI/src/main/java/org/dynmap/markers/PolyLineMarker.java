package org.dynmap.markers;

/**
 * This defines the public interface to a poly-line marker object, for use with the MarkerAPI
 */
public interface PolyLineMarker extends MarkerDescription {
    /**
     * Get corner location count
     */
    int getCornerCount();

    /**
     * Get X coordinate of corner N
     *
     * @param n - corner index
     * @return coordinate
     */
    double getCornerX(int n);

    /**
     * Get Y coordinate of corner N
     *
     * @param n - corner index
     * @return coordinate
     */
    double getCornerY(int n);

    /**
     * Get Z coordinate of corner N
     *
     * @param n - corner index
     * @return coordinate
     */
    double getCornerZ(int n);

    /**
     * Set coordinates of corner N
     *
     * @param n - index of corner: append new corner if &gt;= corner count, else replace existing
     * @param x - x coordinate
     * @param y - y coordinate
     * @param z - z coordinate
     */
    void setCornerLocation(int n, double x, double y, double z);

    /**
     * Set/replace all corners
     *
     * @param x - list of x coordinates
     * @param y - list of y coordinates
     * @param z - list of z coordinates
     */
    void setCornerLocations(double[] x, double[] y, double[] z);

    /**
     * Delete corner N - shift corners after N forward
     *
     * @param n - index of corner
     */
    void deleteCorner(int n);

    /**
     * Set line style
     *
     * @param weight  - stroke weight
     * @param opacity - stroke opacity
     * @param color   - stroke color (0xRRGGBB)
     */
    void setLineStyle(int weight, double opacity, int color);

    /**
     * Get line weight
     *
     * @return weight
     */
    int getLineWeight();

    /**
     * Get line opacity
     *
     * @return opacity (0.0-1.0)
     */
    double getLineOpacity();

    /**
     * Get line color
     *
     * @return color (0xRRGGBB)
     */
    int getLineColor();
}
