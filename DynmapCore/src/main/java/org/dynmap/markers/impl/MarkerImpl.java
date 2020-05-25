package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class MarkerImpl implements Marker {
    private String markerid;
    private String label;
    private boolean markup;
    private String desc;
    private MarkerSetImpl markerset;
    private double x, y, z;
    private String world;
    private String normalized_world;
    private MarkerIconImpl icon;
    private boolean ispersistent;
    private int minzoom;
    private int maxzoom;
    
    /** 
     * Create marker
     * @param id - marker ID
     * @param lbl - label
     * @param markup - if true, label is HTML markup
     * @param world - world id
     * @param x - x coord
     * @param y - y coord
     * @param z - z coord
     * @param icon - marker icon
     * @param persistent - true if persistent
     */
    MarkerImpl(String id, String lbl, boolean markup, String world, double x, double y, double z, MarkerIconImpl icon, boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.markup = markup;
        this.x = x; this.y = y; this.z = z;
        this.world = world;
        this.normalized_world = DynmapWorld.normalizeWorldName(world);
        this.icon = icon;
        this.desc = null;
        ispersistent = persistent;
        markerset = set;
        this.minzoom = -1;
        this.maxzoom = -1;
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
        markup = false;
        desc = null;
        x = z = 0; y = 64; world = normalized_world = "world";
        icon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
        this.minzoom = -1;
        this.maxzoom = -1;
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        markup = node.getBoolean("markup", false);
        x = node.getDouble("x", 0);
        y = node.getDouble("y", 64);
        z = node.getDouble("z", 0);
        world = node.getString("world", "world");
        normalized_world = DynmapWorld.normalizeWorldName(world);
        desc = node.getString("desc", null);
        minzoom = node.getInteger("minzoom", -1);
        maxzoom = node.getInteger("maxzoom", -1);
        icon = MarkerAPIImpl.getMarkerIconImpl(node.getString("icon", MarkerIcon.DEFAULT)); 
        if(icon == null)
            icon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        icon = null;
        markerset = null;
    }
    
    @Override
	public String getUniqueMarkerID() {
    	if (markerset != null) {
    		return markerset + ":marker:" + markerid;
    	}
    	else {
    		return null;
    	}
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
        if(markerset == null) return;
        markerset.removeMarker(this);   /* Remove from our marker set (notified by set) */
        cleanup();
    }

    @Override
    public MarkerIcon getMarkerIcon() {
        return icon;
    }

    @Override
    public boolean setMarkerIcon(MarkerIcon icon) {
        if(markerset == null) return false;
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
        setLabel(lbl, false);
    }
    
    @Override
    public void setLabel(String lbl, boolean markup) {
        if(markerset == null) return;
        label = lbl;
        this.markup = markup;
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
        node.put("markup", markup);
        node.put("x", Double.valueOf(x));
        node.put("y", Double.valueOf(y));
        node.put("z", Double.valueOf(z));
        node.put("world", world);
        node.put("icon", icon.getMarkerIconID());
        if (this.minzoom >= 0) {
            node.put("minzoom", minzoom);
        }
        if (this.maxzoom >= 0) {
            node.put("maxzoom", maxzoom);
        }
        if(desc != null)
            node.put("desc", desc);

        return node;
    }
    @Override
    public String getWorld() {
        return world;
    }
    @Override
    public String getNormalizedWorld() {
        return normalized_world;
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
        if(markerset == null) return;
        this.world = worldid;
        this.x = x;
        this.y = y;
        this.z = z;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public boolean isLabelMarkup() {
        return markup;
    }
    @Override
    public void setDescription(String desc) {
        if(markerset == null) return;
        if((this.desc == null) || (this.desc.equals(desc) == false)) {
            this.desc = desc;
            MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    /**
     * Get marker description
     * @return descrption
     */
    public String getDescription() {
        return this.desc;
    }
    @Override
    public void setMarkerSet(MarkerSet newset) {
        if(markerset != null) {
            markerset.removeMarker(this);   /* Remove from our marker set (notified by set) */
        }
        markerset = (MarkerSetImpl)newset;
        markerset.insertMarker(this);
    }
    
    static boolean testPointInPolygon(double x, double y, double[] polyx, double[] polyy) {
        int nvert = polyx.length;
        int i, j;
        boolean c = false;
        for (i = 0, j = nvert-1; i < nvert; j = i++) {
            if ( ((polyy[i] > y) != (polyy[j] > y)) &&
                    (x < (polyx[j] - polyx[i]) * (y - polyy[i]) / (polyy[j] - polyy[i]) + polyx[i]) ) {
                c = !c;
            }
        }
        return c;
    }
    @Override
    public int getMinZoom() {
        return minzoom;
    }
    @Override
    public void setMinZoom(int zoom) {
        if (zoom < 0) zoom = -1;
        if (this.minzoom == zoom) return;
        this.minzoom = zoom;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public int getMaxZoom() {
        return maxzoom;
    }
    @Override
    public void setMaxZoom(int zoom) {
        if (zoom < 0) zoom = -1;
        if (this.maxzoom == zoom) return;
        this.maxzoom = zoom;
        MarkerAPIImpl.markerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
}
