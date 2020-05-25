package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.markers.PolyLineMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class PolyLineMarkerImpl implements PolyLineMarker {
    private String markerid;
    private String label;
    private boolean markup;
    private String desc;
    private MarkerSetImpl markerset;
    private String world;
    private String normalized_world;
    private boolean ispersistent;
    private ArrayList<Coord> corners;
    private int lineweight = 3;
    private double lineopacity = 0.8;
    private int linecolor = 0xFF0000;
    private int minzoom;
    private int maxzoom;
    
    private static class Coord {
        double x, y, z;
        Coord(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }
    }
    
    /** 
     * Create poly-line marker
     * @param id - marker ID
     * @param lbl - label
     * @param markup - if true, label is HTML markup
     * @param world - world id
     * @param x - x coord list
     * @param y - y coord list
     * @param z - z coord list
     * @param persistent - true if persistent
     * @param set - marker set
     */
    PolyLineMarkerImpl(String id, String lbl, boolean markup, String world, double x[], double[] y, double z[], boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.markup = markup;
        this.corners = new ArrayList<Coord>();
        for(int i = 0; i < x.length; i++) {
            this.corners.add(new Coord(x[i], y[i], z[i]));
        }
        this.world = world;
        this.normalized_world = DynmapWorld.normalizeWorldName(world);
        this.desc = null;
        this.minzoom = -1;
        this.maxzoom = -1;
        ispersistent = persistent;
        markerset = set;
    }
    /**
     * Make bare poly-line marker - used for persistence load
     *  @param id - marker ID
     *  @param set - marker set
     */
    PolyLineMarkerImpl(String id, MarkerSetImpl set) {
        markerid = id;
        markerset = set;
        label = id;
        markup = false;
        desc = null;
        corners = new ArrayList<Coord>();
        this.minzoom = -1;
        this.maxzoom = -1;
        world = normalized_world = "world";
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        markup = node.getBoolean("markup", false);
        List<Double> xx = node.getList("x");
        List<Double> yy = node.getList("y");
        List<Double> zz = node.getList("z");
        corners.clear();
        if((xx != null) && (yy != null) && (zz != null)) {
            int sz = Math.min(xx.size(), Math.min(yy.size(), zz.size()));
            for(int i = 0; i < sz; i++)
                corners.add(new Coord(xx.get(i), yy.get(i), zz.get(i)));
        }
        world = node.getString("world", "world");
        normalized_world = DynmapWorld.normalizeWorldName(world);
        desc = node.getString("desc", null);
        lineweight = node.getInteger("strokeWeight", -1);
        if(lineweight == -1) {	/* Handle typo-saved value */
        	 lineweight = node.getInteger("stokeWeight", 3);
        }
        lineopacity = node.getDouble("strokeOpacity", 0.8);
        linecolor = node.getInteger("strokeColor", 0xFF0000);
        this.minzoom = node.getInteger("minzoom", -1);
        this.maxzoom = node.getInteger("maxzoom", -1);
        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        corners.clear();
        markerset = null;
    }
    
    @Override
	public String getUniqueMarkerID() {
    	if (markerset != null) {
    		return markerset + ":poly:" + markerid;
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
        markerset.removePolyLineMarker(this);   /* Remove from our marker set (notified by set) */
        cleanup();
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
        MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        List<Double> xx = new ArrayList<Double>();
        List<Double> yy = new ArrayList<Double>();
        List<Double> zz = new ArrayList<Double>();
        for(int i = 0; i < corners.size(); i++) {
            Coord c = corners.get(i);
            xx.add(c.x);
            yy.add(c.y);
            zz.add(c.z);
        }
        node.put("x", xx);
        node.put("y", yy);
        node.put("z", zz);
        node.put("world", world);
        if(desc != null)
            node.put("desc", desc);
        node.put("strokeWeight", lineweight);
        node.put("strokeOpacity", lineopacity);
        node.put("strokeColor", linecolor);
        if (minzoom >= 0) {
            node.put("minzoom", minzoom);
        }
        if (maxzoom >= 0) {
            node.put("maxzoom", maxzoom);
        }

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
    public boolean isLabelMarkup() {
        return markup;
    }
    @Override
    public void setDescription(String desc) {
        if(markerset == null) return;
        if((this.desc == null) || (this.desc.equals(desc) == false)) {
            this.desc = desc;
            MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
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
    public int getCornerCount() {
        return corners.size();
    }
    @Override
    public double getCornerX(int n) {
        Coord c = corners.get(n);
        if(c != null)
            return c.x;
        return 0;
    }
    @Override
    public double getCornerY(int n) {
        Coord c = corners.get(n);
        if(c != null)
            return c.y;
        return 0;
    }
    @Override
    public double getCornerZ(int n) {
        Coord c = corners.get(n);
        if(c != null)
            return c.z;
        return 0;
    }
    @Override
    public void setCornerLocation(int n, double x, double y, double z) {
        if(markerset == null) return;
        Coord c;
        if(n >= corners.size()) {
            corners.add(new Coord(x, y, z));
        }
        else {
            c = corners.get(n);
            if((c.x == x) && (c.y == y) && (c.z == z))
                return;
            c.x = x;
            c.y = y;
            c.z = z;
        }
        MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public void deleteCorner(int n) {
        if(markerset == null) return;
        if(n < corners.size()) {
            corners.remove(n);
            MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public void setCornerLocations(double[] x, double[] y, double[] z) {
        if(markerset == null) return;
        /* Check if equals */
        int sz = Math.min(x.length, Math.min(y.length, z.length));
        if(sz == corners.size()) {
            boolean match = true;
            for(int i = 0; i < sz; i++) {
                Coord c = corners.get(i);
                if((c.x != x[i]) || (c.y != y[i]) || (c.z != z[i])) {
                    match = false;
                    break;
                }
            }
            if(match)
                return;
        }
        corners.clear();
        for(int i = 0; i < sz; i++) {
            corners.add(new Coord(x[i], y[i], z[i]));
        }
        MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public void setLineStyle(int weight, double opacity, int color) {
        if(markerset == null) return;
        if((weight != lineweight) || (opacity != lineopacity) || (color != linecolor)) {
            lineweight = weight;
            lineopacity = opacity;
            linecolor = color;
            MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public int getLineWeight() {
        return lineweight;
    }
    @Override
    public double getLineOpacity() {
        return lineopacity;
    }
    @Override
    public int getLineColor() {
        return linecolor;
    }
    @Override
    public void setMarkerSet(MarkerSet newset) {
        if(markerset != null) {
            markerset.removePolyLineMarker(this);   /* Remove from our marker set (notified by set) */
        }
        markerset = (MarkerSetImpl)newset;
        markerset.insertPolyLineMarker(this);
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
        MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        MarkerAPIImpl.polyLineMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
}
