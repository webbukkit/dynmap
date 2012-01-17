package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class AreaMarkerImpl implements AreaMarker {
    private String markerid;
    private String label;
    private boolean markup;
    private String desc;
    private MarkerSetImpl markerset;
    private String world;
    private boolean ispersistent;
    private ArrayList<Coord> corners;
    private int lineweight = 3;
    private double lineopacity = 0.8;
    private int linecolor = 0xFF0000;
    private double fillopacity = 0.35;
    private int fillcolor = 0xFF0000;
    private double ytop = 64.0;
    private double ybottom = 64.0;
    
    private static class Coord {
        double x, z;
        Coord(double x, double z) {
            this.x = x; this.z = z;
        }
    }
    
    /** 
     * Create area marker
     * @param id - marker ID
     * @param lbl - label
     * @param markup - if true, label is HTML markup
     * @param world - world id
     * @param x - x coord list
     * @param z - z coord list
     * @param persistent - true if persistent
     * @param set - marker set
     */
    AreaMarkerImpl(String id, String lbl, boolean markup, String world, double x[], double z[], boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.markup = markup;
        this.corners = new ArrayList<Coord>();
        for(int i = 0; i < x.length; i++) {
            this.corners.add(new Coord(x[i], z[i]));
        }
        this.world = world;
        this.desc = null;
        ispersistent = persistent;
        markerset = set;
    }
    /**
     * Make bare area marker - used for persistence load
     *  @param id - marker ID
     *  @param set - marker set
     */
    AreaMarkerImpl(String id, MarkerSetImpl set) {
        markerid = id;
        markerset = set;
        label = id;
        markup = false;
        desc = null;
        corners = new ArrayList<Coord>();
        world = "world";
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        markup = node.getBoolean("markup", false);
        ytop = node.getDouble("ytop", 64.0);
        ybottom = node.getDouble("ybottom", 64.0);
        List<Double> xx = node.getList("x");
        List<Double> zz = node.getList("z");
        corners.clear();
        if((xx != null) && (zz != null)) {
            for(int i = 0; (i < xx.size()) && (i < zz.size()); i++)
                corners.add(new Coord(xx.get(i), zz.get(i)));
        }
        world = node.getString("world", "world");
        desc = node.getString("desc", null);
        lineweight = node.getInteger("strokeWeight", 3);
        lineopacity = node.getDouble("strokeOpacity", 0.8);
        linecolor = node.getInteger("strokeColor", 0xFF0000);
        fillopacity = node.getDouble("fillOpacity", 0.35);
        fillcolor = node.getInteger("fillColor", 0xFF0000);
        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        corners.clear();
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
        markerset.removeAreaMarker(this);   /* Remove from our marker set (notified by set) */
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
        label = lbl;
        this.markup = markup;
        MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        List<Double> zz = new ArrayList<Double>();
        for(int i = 0; i < corners.size(); i++) {
            xx.add(corners.get(i).x);
            zz.add(corners.get(i).z);
        }
        node.put("x", xx);
        node.put("ytop", Double.valueOf(ytop));
        node.put("ybottom", Double.valueOf(ybottom));
        node.put("z", zz);
        node.put("world", world);
        if(desc != null)
            node.put("desc", desc);
        node.put("stokeWeight", lineweight);
        node.put("strokeOpacity", lineopacity);
        node.put("strokeColor", linecolor);
        node.put("fillOpacity", fillopacity);
        node.put("fillColor", fillcolor);

        return node;
    }
    @Override
    public String getWorld() {
        return world;
    }
    @Override
    public boolean isLabelMarkup() {
        return markup;
    }
    @Override
    public void setDescription(String desc) {
        if((this.desc == null) || (this.desc.equals(desc) == false)) {
            this.desc = desc;
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
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
    public double getTopY() {
        return ytop;
    }
    @Override
    public double getBottomY() {
        return ybottom;
    }
    @Override
    public void setRangeY(double ytop, double ybottom) {
        if((this.ytop != ytop) || (this.ybottom != ybottom)) {
            this.ytop = ytop;
            this.ybottom = ybottom;
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
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
    public double getCornerZ(int n) {
        Coord c = corners.get(n);
        if(c != null)
            return c.z;
        return 0;
    }
    @Override
    public void setCornerLocation(int n, double x, double z) {
        Coord c;
        if(n >= corners.size()) {
            corners.add(new Coord(x, z));
        }
        else {
            c = corners.get(n);
            if((c.x == x) && (c.z == z))
                return;
            c.x = x;
            c.z = z;
        }
        MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public void deleteCorner(int n) {
        if(n < corners.size()) {
            corners.remove(n);
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public void setCornerLocations(double[] x, double[] z) {
        /* Check if equals */
        if(x.length == corners.size()) {
            boolean match = true;
            for(int i = 0; i < x.length; i++) {
                Coord c = corners.get(i);
                if((c.x != x[i]) || (c.z != z[i])) {
                    match = false;
                    break;
                }
            }
            if(match)
                return;
        }
        corners.clear();
        for(int i = 0; (i < x.length) && (i < z.length); i++) {
            corners.add(new Coord(x[i], z[i]));
        }
        MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    @Override
    public void setLineStyle(int weight, double opacity, int color) {
        if((weight != lineweight) || (opacity != lineopacity) || (color != linecolor)) {
            lineweight = weight;
            lineopacity = opacity;
            linecolor = color;
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
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
    public void setFillStyle(double opacity, int color) {
        if((opacity != fillopacity) || (color != fillcolor)) {
            fillopacity = opacity;
            fillcolor = color;
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public double getFillOpacity() {
        return fillopacity;
    }
    @Override
    public int getFillColor() {
        return fillcolor;
    }
}
