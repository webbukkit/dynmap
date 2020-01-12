package org.dynmap.markers;

/**
 * This defines the public interface to a marker object, for use with the MarkerAPI
 */
public interface Marker extends MarkerDescription {
    /**
     * Get marker's X coordinate
     *
     * @return x coordinate
     */
    double getX();

    /**
     * Get marker's Y coordinate
     *
     * @return y coordinate
     */
    double getY();

    /**
     * Get marker's Z coordinate
     *
     * @return z coordinate
     */
    double getZ();

    /**
     * Update the marker's location
     *
     * @param worldid - world ID
     * @param x       - x coord
     * @param y       - y coord
     * @param z       - z coord
     */
    void setLocation(String worldid, double x, double y, double z);

    /**
     * Get the marker's icon
     *
     * @return marker icon
     */
    MarkerIcon getMarkerIcon();

    /**
     * Set the marker's icon
     *
     * @param icon - new marker icon
     * @return true if new marker icon set, false if not allowed
     */
    boolean setMarkerIcon(MarkerIcon icon);
}
