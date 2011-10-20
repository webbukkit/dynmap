package org.dynmap.markers;

import org.bukkit.Location;

/**
 * This defines the public interface to a generic marker object, for use with the MarkerAPI
 */
public interface GenericMarker {
    /**
     * Get ID of the marker (unique string within the MarkerSet)
     * @return id of marker
     */
    public String getMarkerID();
    /**
     * Get the marker set for the marker
     * @return marker set
     */
    public MarkerSet getMarkerSet();
    /**
     * Delete the marker
     */
    public void deleteMarker();
    /**
     * Get marker's world ID
     * @return world id
     */
    public String getWorld();
    /**
     * Test if marker is persistent
     */
    public boolean isPersistentMarker();
}
