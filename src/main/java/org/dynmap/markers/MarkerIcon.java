package org.dynmap.markers;

/**
 * This defines the public interface to a marker icon, for use with the MarkerAPI
 */
public interface MarkerIcon {
    /** Default marker icon - always exists */
    public static final String DEFAULT = "default";
    
    /**
     * Get ID of the marker icon (unique among marker icons)
     * @return ID
     */
    public String getMarkerIconID();
    /**
     * Get label for marker icon (descriptive - for helping select icon, or for legend/key)
     * @return icon label
     */
    public String getMarkerIconLabel();
}
