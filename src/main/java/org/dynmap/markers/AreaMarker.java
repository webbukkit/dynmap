package org.dynmap.markers;

/**
 * This defines the public interface to an area marker object, for use with the MarkerAPI
 */
public interface AreaMarker extends GenericMarker {
    /**
     * Get the marker's label
     */
    public String getLabel();
    /**
     * Update the marker's label (plain text)
     */
    public void setLabel(String lbl);
    /**
     * Update the marker's label and markup flag
     * @param label - label string
     * @param markup - if true, label is processed as HTML (innerHTML for <span> used for label); false implies plaintext
     */
    public void setLabel(String lbl, boolean markup);
    /**
     * Test if marker label is processed as HTML
     */
    public boolean isLabelMarkup();
    /**
     * Set marker description (HTML markup shown in popup when clicked)
     * @param desc - HTML markup description
     */
    public void setDescription(String desc);
    /**
     * Get marker description
     * @return descrption
     */
    public String getDescription();
    /**
     * Get top Y coordinate
     * @return coordinate
     */
    public double getTopY();
    /**
     * Get bottom Y coordinate
     * @return coordinate
     */
    public double getBottomY();
    /**
     * Set Y coordinate range
     * @param ytop - y coordinate of top 
     * @param ybottom - y coordinate of bottom (=top for 2D)
     */
    public void setRangeY(double ytop, double ybottom);
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
     * Get Z coordinate of corner N
     * @param n - corner index
     * @return coordinate
     */
    public double getCornerZ(int n);
    /**
     * Set coordinates of corner N
     * @param n - index of corner: append new corner if >= corner count, else replace existing
     * @param x - x coordinate
     * @param z - z coordinate
     */
    public void setCornerLocation(int n, double x, double z);
    /**
     * Set/replace all corners
     * @param x - list of x coordinates
     * @param z - list of z coordinates
     */
    public void setCornerLocations(double[] x, double[] z);
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
}
