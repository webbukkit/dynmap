package org.dynmap;

import org.dynmap.markers.MarkerAPI;

/**
 * This is the interface representing the published API for the Dynmap plugin.  Public methods of the
 * DynmapPlugin class that are not defined in this interface are subject to change without notice, so
 * be careful with forming dependencies beyond these.  Plugins accessing dynmap 0.24 or later should
 * do so by casting the Plugin to this interface.
 * 
 */
public interface DynmapAPI {
    /**
     * This method can return null if the 'markers' component has not been configured - 
     * a warning message will be issued to the server.log in this event.
     * 
     * @return MarkerAPI, or null if not configured
     */
    public MarkerAPI getMarkerAPI();
    /**
     * Test if the marker API has been initialized yet
     *
     * @return true if it has been initialized
     */
    public boolean markerAPIInitialized();
    /**
     * Send generic message to all web users
     * @param sender - label for sender of message ("Message from <plugin>:") - if null, no from notice
     * @param msg - message to be sent
     */
    public boolean sendBroadcastToWeb(String sender, String msg);
}
