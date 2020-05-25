package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.EnterExitMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.EnterExitMarker.EnterExitText;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;
import org.dynmap.utils.Vector3D;

class CircleMarkerImpl implements CircleMarker, EnterExitMarker {
    private String markerid;
    private String label;
    private boolean markup;
    private String desc;
    private MarkerSetImpl markerset;
    private String world;
    private String normalized_world;
    private boolean ispersistent;
    private double x;
    private double y;
    private double z;
    private double xr;
    private double zr;
    private int lineweight = 3;
    private double lineopacity = 0.8;
    private int linecolor = 0xFF0000;
    private double fillopacity = 0.35;
    private int fillcolor = 0xFF0000;
    private boolean boostflag = false;
    private int minzoom = -1;
    private int maxzoom = -1;
    private EnterExitText greeting;
    private EnterExitText farewell;

    private static class BoundingBox {
        double xmin, xmax;
        double ymin, ymax;
        double xp[];
        double yp[];
    }
    private Map<String, BoundingBox> bb_cache = null;

    /** 
     * Create circle marker
     * @param id - marker ID
     * @param lbl - label
     * @param markup - if true, label is HTML markup
     * @param world - world id
     * @param x - x center
     * @param y - y center
     * @param z - z center
     * @param xr - radius on X axis
     * @param zr - radius on Z axis
     * @param persistent - true if persistent
     * @param set - marker set
     */
    CircleMarkerImpl(String id, String lbl, boolean markup, String world, double x, double y, double z, double xr, double zr, boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.markup = markup;
        this.x = x; this.y = y; this.z = z;
        this.xr = xr; this.zr = zr;
        this.world = world;
        this.normalized_world = DynmapWorld.normalizeWorldName(world);
        this.desc = null;
        this.minzoom = -1;
        this.maxzoom = -1;
        ispersistent = persistent;
        markerset = set;
    }
    /**
     * Make bare area marker - used for persistence load
     *  @param id - marker ID
     *  @param set - marker set
     */
    CircleMarkerImpl(String id, MarkerSetImpl set) {
        markerid = id;
        markerset = set;
        label = id;
        markup = false;
        desc = null;
        world = normalized_world = "world";
        this.minzoom = -1;
        this.maxzoom = -1;
        x = z = 0;
        y = 64;
        xr = zr = 0;
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        markup = node.getBoolean("markup", false);
        world = node.getString("world", "world");
        normalized_world = DynmapWorld.normalizeWorldName(world);
        x = node.getDouble("x", 0);
        y = node.getDouble("y", 64);
        z = node.getDouble("z", 0);
        xr = node.getDouble("xr", 0);
        zr = node.getDouble("zr", 0);
        desc = node.getString("desc", null);
        lineweight = node.getInteger("strokeWeight", -1);
        if(lineweight == -1) {	/* Handle typo-saved value */
        	 lineweight = node.getInteger("stokeWeight", 3);
        }
        lineopacity = node.getDouble("strokeOpacity", 0.8);
        linecolor = node.getInteger("strokeColor", 0xFF0000);
        fillopacity = node.getDouble("fillOpacity", 0.35);
        fillcolor = node.getInteger("fillColor", 0xFF0000);
        boostflag = node.getBoolean("boostFlag", false);
        minzoom = node.getInteger("minzoom", -1);
        maxzoom = node.getInteger("maxzoom", -1);
        String gt = node.getString("greeting", null);
        String gst = node.getString("greetingsub", null);
        if ((gt != null) || (gst != null)) {
        	greeting = new EnterExitText();
        	greeting.title = gt;
        	greeting.subtitle = gst;
        }
        String ft = node.getString("farewell", null);
        String fst = node.getString("farewellsub", null);
        if ((ft != null) || (fst != null)) {
        	farewell = new EnterExitText();
        	farewell.title = ft;
        	farewell.subtitle = fst;
        }

        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        markerset = null;
        bb_cache = null;
    }
    
    @Override
	public String getUniqueMarkerID() {
    	if (markerset != null) {
    		return markerset + ":circle:" + markerid;
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
        markerset.removeCircleMarker(this);   /* Remove from our marker set (notified by set) */
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
        MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        node.put("x", x);
        node.put("y", y);
        node.put("z", z);
        node.put("xr", xr);
        node.put("zr", zr);
        node.put("world", world);
        if(desc != null)
            node.put("desc", desc);
        node.put("strokeWeight", lineweight);
        node.put("strokeOpacity", lineopacity);
        node.put("strokeColor", linecolor);
        node.put("fillOpacity", fillopacity);
        node.put("fillColor", fillcolor);
        if(boostflag) {
            node.put("boostFlag", true);
        }
        if (minzoom >= 0) {
            node.put("minzoom", minzoom);
        }
        if (maxzoom >= 0) {
            node.put("maxzoom", maxzoom);
        }
        if (greeting != null) {
        	if (greeting.title != null) {
        		node.put("greeting", greeting.title);
        	}
        	if (greeting.subtitle != null) {
        		node.put("greetingsub", greeting.subtitle);
        	}        	
        }
        if (farewell != null) {
        	if (farewell.title != null) {
        		node.put("farewell", farewell.title);        		
        	}
        	if (farewell.subtitle != null) {
        		node.put("farewellsub", farewell.subtitle);        		
        	}
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
        if((this.desc == null) || (this.desc.equals(desc) == false)) {
            this.desc = desc;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
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
    public void setLineStyle(int weight, double opacity, int color) {
        if((weight != lineweight) || (opacity != lineopacity) || (color != linecolor)) {
            lineweight = weight;
            lineopacity = opacity;
            linecolor = color;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
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
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
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
    @Override
    public double getCenterX() {
        return x;
    }
    @Override
    public double getCenterY() {
        return y;
    }
    @Override
    public double getCenterZ() {
        return z;
    }
    @Override
    public void setCenter(String worldid, double x, double y, double z) {
        boolean updated = false;
        if(!worldid.equals(world)) {
            world = worldid;
            normalized_world = DynmapWorld.normalizeWorldName(world);
            updated = true;
        }
        if(this.x != x) {
            this.x = x;
            updated = true;
        }
        if(this.y != y) {
            this.y = y;
            updated = true;
        }
        if(this.z != z) {
            this.z = z;
            updated = true;
        }
        if(updated) {
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
            bb_cache = null;
        }
    }
    @Override
    public double getRadiusX() {
        return xr;
    }
    @Override
    public double getRadiusZ() {
        return zr;
    }
    @Override
    public void setRadius(double xr, double zr) {
        if((this.xr != xr) || (this.zr != zr)) {
            this.xr = xr;
            this.zr = zr;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
            bb_cache = null;
        }
    }
    @Override
    public void setMarkerSet(MarkerSet newset) {
        if(markerset != null) {
            markerset.removeCircleMarker(this);   /* Remove from our marker set (notified by set) */
        }
        markerset = (MarkerSetImpl)newset;
        markerset.insertCircleMarker(this);
    }
    @Override
    public void setBoostFlag(boolean bflag) {
        if (this.boostflag != bflag) {
            this.boostflag = bflag;
            if (markerset != null) {
                setMarkerSet(markerset);
            }
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public boolean getBoostFlag() {
        return boostflag;
    }

    final boolean testTileForBoostMarkers(DynmapWorld w, HDPerspective perspective, double tile_x, double tile_y, double tile_dim) {
        Map<String, BoundingBox> bbc = bb_cache;
        if(bbc == null) {
            bbc = new ConcurrentHashMap<String, BoundingBox>();
        }
        BoundingBox bb = bbc.get(perspective.getName());
        if (bb == null) { // No cached bounding box, so generate it
            bb = new BoundingBox();
            Vector3D v = new Vector3D();
            Vector3D v2 = new Vector3D();
            bb.xmin = Double.MAX_VALUE;
            bb.xmax = -Double.MAX_VALUE;
            bb.ymin = Double.MAX_VALUE;
            bb.ymax = -Double.MAX_VALUE;
            int cnt = 16; // Just do 16 points for now
            bb.xp = new double[cnt];
            bb.yp = new double[cnt];
            for(int i = 0; i < cnt; i++) {
                v.x = this.x + (this.xr * Math.cos(2.0*Math.PI*i/cnt));
                v.y = this.y;
                v.z = this.z + (this.zr * Math.sin(2.0*Math.PI*i/cnt));
                perspective.transformWorldToMapCoord(v,  v2);   // Transform to map coord
                if(v2.x < bb.xmin) bb.xmin = v2.x;
                if(v2.y < bb.ymin) bb.ymin = v2.y;
                if(v2.x > bb.xmax) bb.xmax = v2.x;
                if(v2.y > bb.ymax) bb.ymax = v2.y;
                bb.xp[i] = v2.x;
                bb.yp[i] = v2.y;
            }
            //System.out.println("x=" + bb.xmin + " - " + bb.xmax + ",  y=" + bb.ymin + " - " + bb.ymax);
            bbc.put(perspective.getName(), bb);
            bb_cache = bbc;
        }
        final double tile_x2 = tile_x + tile_dim;
        final double tile_y2 = tile_y + tile_dim;
        if ((bb.xmin > tile_x2) || (bb.xmax < tile_x) || (bb.ymin > tile_y2) || (bb.ymax < tile_y)) {
            //System.out.println("tile: " + tile_x + " / " + tile_y + " - miss");
            return false;
        }
        final int cnt = bb.xp.length;
        final double[] px = bb.xp;
        final double[] py = bb.yp;
        /* Now see if tile square intersects polygon - start with seeing if any point inside */
        if(MarkerImpl.testPointInPolygon(tile_x, tile_y, px, py)) { 
            return true; // If tile corner inside, we intersect
        }
        if(MarkerImpl.testPointInPolygon(tile_x2, tile_y, px, py)) { 
            return true; // If tile corner inside, we intersect
        }
        if(MarkerImpl.testPointInPolygon(tile_x, tile_y2, px, py)) { 
            return true; // If tile corner inside, we intersect
        }
        if(MarkerImpl.testPointInPolygon(tile_x2, tile_y2, px, py)) { 
            return true; // If tile corner inside, we intersect
        }
        /* Test if any polygon corners are inside square */
        for(int i = 0; i < cnt; i++) { 
            if((px[i] >= tile_x) && (px[i] <= tile_x2) && (py[i] >= tile_y) && (py[i] <= tile_y2)) {
                return true; // If poly corner inside tile, we intersect
            }
        }
        // Otherwise, only intersects if at least one edge crosses
        //for (int i = 0, j = cnt-1; i < cnt; j = i++) {
        //    // Test for X=tile_x side
        //    if ((px[i] < tile_x) && (px[j] >= tile_x) && ()
        // }
        //System.out.println("tile: " + tile_x + " / " + tile_y + " - hit");
        return false;
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
        MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
	@Override
	public EnterExitText getGreetingText() {
		return greeting;
	}
	@Override
	public EnterExitText getFarewellText() {
		return farewell;
	}
	@Override
	public void setGreetingText(String title, String subtitle) {
		if ((title != null) || (subtitle != null)) {
			greeting = new EnterExitText();
			greeting.title = title;
			greeting.subtitle = subtitle;
		}
		else {
			greeting = null;
		}
        if (markerset != null) {
            setMarkerSet(markerset);
        }
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
	}
	@Override
	public void setFarewellText(String title, String subtitle) {
		if ((title != null) || (subtitle != null)) {
			farewell = new EnterExitText();
			farewell.title = title;
			farewell.subtitle = subtitle;
		}
		else {
			farewell = null;
		}
        if (markerset != null) {
            setMarkerSet(markerset);
        }
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
	}
	@Override
	public boolean testIfPointWithinMarker(String worldid, double x, double y, double z) {
		// Wrong world
		if (!worldid.equals(this.world)) {
			return false;
		}
		// Test if inside ellipse
		double dx = ((x - this.x) * (x - this.x)) / (xr * xr);
		double dz = ((z - this.z) * (z - this.z)) / (zr * zr);
		return (dx + dz) <= 1.0;
	}
}
