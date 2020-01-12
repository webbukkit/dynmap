package org.dynmap.markers;

import java.io.InputStream;

/**
 * This defines the public interface to a marker icon, for use with the MarkerAPI
 */
public interface MarkerIcon {
    /**
     * Default marker icon - always exists
     */
    String DEFAULT = "default";
    /**
     * Default sign marker icon - always exists
     */
    String SIGN = "sign";
    /**
     * Default world marker icon - always exists
     */
    String WORLD = "world";

    enum MarkerSize {
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
     *
     * @return ID
     */
    String getMarkerIconID();

    /**
     * Get label for marker icon (descriptive - for helping select icon, or for legend/key)
     *
     * @return icon label
     */
    String getMarkerIconLabel();

    /**
     * Set label for marker icon
     */
    void setMarkerIconLabel(String lbl);

    /**
     * Replace icon image for icon
     *
     * @param in - input stream for PNG file
     */
    void setMarkerIconImage(InputStream in);

    /**
     * Delete icon (not functional on builtin icons)
     */
    void deleteIcon();

    /**
     * Is builtin marker
     *
     * @return true
     */
    boolean isBuiltIn();

    /**
     * Get marker size
     */
    MarkerSize getMarkerIconSize();
}
