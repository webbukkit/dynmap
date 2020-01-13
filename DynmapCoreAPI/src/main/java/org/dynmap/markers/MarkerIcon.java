package org.dynmap.markers;

import java.io.InputStream;

/**
 * This defines the public interface to a marker icon, for use with the MarkerAPI
 */
public interface MarkerIcon {
    /** Default marker icon - always exists */
    public static final String DEFAULT = "default";
    /** Default sign marker icon - always exists */
    public static final String SIGN = "sign";
    /** Default world marker icon - always exists */
    public static final String WORLD = "world";

    public enum MarkerSize {
        MARKER_8x8("8x8"),
        MARKER_16x16("16x16"),
        MARKER_32x32("32x32");
        
        String sz;
        MarkerSize(String sz) {
            this.sz = sz;
        }
        public String getSize() {
            return sz;
        }
    }
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
    /**
     * Set label for marker icon
     */
    public void setMarkerIconLabel(String lbl);
    /**
     * Replace icon image for icon
     * @param in - input stream for PNG file
     */
    public void setMarkerIconImage(InputStream in);
    /**
     * Delete icon (not functional on builtin icons)
     */
    public void deleteIcon();
    /**
     * Is builtin marker
     * @return true
     */
    public boolean isBuiltIn();
    /**
     * Get marker size
     */
    public MarkerSize   getMarkerIconSize();
}
