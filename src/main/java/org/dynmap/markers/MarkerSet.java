package org.dynmap.markers;

import java.util.Set;

/**
 * This defines the public interface to a marker set object, for use with the MarkerAPI.
 * This represents a logical set of markers, which are presented as a labelled layer on the web UI.
 * Marker sets can be created as persistent or non-persistent, but only persistent marker sets can contain persistent markers.
 */
public interface MarkerSet {
    public static final String DEFAULT = "markers"; /* Default set - always exists */
    
    /**
     * Get set of all markers currently in the set
     * @return set of markers (set is copy - safe to iterate)
     */
    public Set<Marker> getMarkers();
    /**
     * Create a new marker in the marker set
     * 
     * @param id - ID of the marker - must be unique within the set: if null, unique ID is generated
     * @param label - Label for the marker
     * @param world - world ID
     * @param x - x coord
     * @param y - y coord
     * @param z - z coord
     * @param icon - Icon for the marker
     * @param is_persistent - if true, marker is persistent (saved and reloaded on restart).  If set is not persistent, this must be false.
     * @return created marker, or null if cannot be created.
     */
    public Marker createMarker(String id, String label, String world, double x, double y, double z, MarkerIcon icon, boolean is_persistent);
    /**
     * Get marker by ID
     * @param id - ID of the marker
     * @return marker, or null if cannot be found
     */
    public Marker   findMarker(String id);
    /**
     * Get ID of marker set - unique among marker sets
     * @return ID
     */
    public String getMarkerSetID();
    /**
     * Get label for marker set
     * @return label
     */
    public String getMarkerSetLabel();
    /**
     * Update label for marker set
     * @param lbl - label for marker set
     */
    public void setMarketSetLabel(String lbl);
    /**
     * Test if marker set is persistent
     * @return true if the set is persistent
     */
    public boolean isMarkerSetPersistent();
    /**
     * Get marker icons allowed in set (if restricted)
     * @return set of allowed marker icons
     */
    public Set<MarkerIcon> getAllowedMarkerIcons();
    /**
     * Add marker icon to allowed set (must have been created restricted)
     * @param icon - icon to be added
     */
    public void addAllowedMarkerIcon(MarkerIcon icon);
    /**
     * Test if marker icon is allowed
     * @param icon - marker icon
     * @return true if allowed, false if not
     */
    public boolean isAllowedMarkerIcon(MarkerIcon icon);
    /**
     * Get distinct set of marker icons used by set (based on markers currently in set)
     * @return set of marker icons
     */
    public Set<MarkerIcon> getMarkerIconsInUse();
    /**
     * Delete marker set
     */
    public void deleteMarkerSet();
}
