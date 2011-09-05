package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class MarkerImpl implements Marker {
    private String markerid;
    private String label;
    private MarkerSetImpl markerset;
    private double x, y, z;
    private String world;
    private MarkerIconImpl icon;
    private boolean ispersistent;
    
    /** 
     * Create marker
     * @param id - marker ID
     * @param lbl - label
     * @param world - world id
     * @param x - x coord
     * @param y - y coord
     * @param z - z coord
     * @param icon - marker icon
     * @param persistent - true if persistent
     */
    MarkerImpl(String id, String lbl, String world, double x, double y, double z, MarkerIconImpl icon, boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.x = x; this.y = y; this.z = z;
        this.world = world;
        this.icon = icon;
        ispersistent = persistent;
        markerset = set;
    }
    /**
     * Make bare marker - used for persistence load
     *  @param id - marker ID
     *  @param set - marker set
     */
    MarkerImpl(String id, MarkerSetImpl set) {
        markerid = id;
        markerset = set;
        label = id;
        x = z = 0; y = 64; world = "world";
        icon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        x = node.getDouble("x", 0);
        y = node.getDouble("y", 64);
        z = node.getDouble("z", 0);
        world = node.getString("world", "world");
        icon = MarkerAPIImpl.getMarkerIconImpl(node.getString("icon", MarkerIcon.DEFAULT)); 
        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        icon = null;
        markerset = null;
    }
    
    @Override
    public String getMarkerID() {
        return markerid;
    }

    @Override
    public MarkerSet getMarkerSet() {
        return markerset;
    }

    @Override
    public void deleteMarker() {
        markerset.removeMarker(this);   /* Remove from our marker set (notified by set) */
        cleanup();
    }

    @Override
    public MarkerIcon getMarkerIcon() {
        return icon;
    }

    @Override
    public boolean setMarkerIcon(MarkerIcon icon) {
        if(!(icon instanceof MarkerIconImpl)) {
            return false;
        }
        /* Check if icons restricted for this set */
        Set<MarkerIcon> icns = markerset.getAllowedMarkerIcons();
        if((icns != null) && (icns.contains(icon) == false)) {
            return false;
        }
        this.icon = (MarkerIconImpl)icon;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();

        return true;
    }

    @Override
    public boolean isPersistentMarker() {
        return ispersistent;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setLabel(String lbl) {
        label = lbl;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        if(!ispersistent)   /* Nothing if not persistent */
            return null;
        HashMap<String, Object> node = new HashMap<String, Object>();
        node.put("label", label);
        node.put("x", Double.valueOf(x));
        node.put("y", Double.valueOf(y));
        node.put("z", Double.valueOf(z));
        node.put("world", world);
        node.put("icon", icon.getMarkerIconID());

        return node;
    }
    @Override
    public String getWorld() {
        return world;
    }
    @Override
    public double getX() {
        return x;
    }
    @Override
    public double getY() {
        return y;
    }
    @Override
    public double getZ() {
        return z;
    }
    @Override
    public void setLocation(String worldid, double x, double y, double z) {
        this.world = worldid;
        this.x = x;
        this.y = y;
        this.z = z;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
}
