package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.EnterExitMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;
import org.dynmap.utils.Vector3D;

class AreaMarkerImpl implements AreaMarker, EnterExitMarker {
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
    private double fillopacity = 0.35;
    private int fillcolor = 0xFF0000;
    private double ytop = 64.0;
    private double ybottom = 64.0;
    private boolean boostflag = false;
    private int minzoom;
    private int maxzoom;
    private EnterExitText greeting;
    private EnterExitText farewell;
    
    
    private static class Coord {
        double x, z;
        Coord(double x, double z) {
            this.x = x; this.z = z;
        }
        public String toString() {
        	return String.format("{%f,%f}",  x, z);
        }
    }
    private static class BoundingBox {
        double xmin, xmax;
        double ymin, ymax;
        double xp[];
        double yp[];
    }
    private Map<String, BoundingBox> bb_cache = null;
    
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
        this.normalized_world = DynmapWorld.normalizeWorldName(world);
        this.desc = null;
        ispersistent = persistent;
        markerset = set;
        if(MapManager.mapman != null) {
            DynmapWorld w = MapManager.mapman.getWorld(world);
            if(w != null) {
                ytop = ybottom = w.sealevel+1;    /* Default to world sealevel */
            }
        }
        this.minzoom = -1;
        this.maxzoom = -1;
        this.greeting = null;
        this.farewell = null;
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
        world = normalized_world = "world";
        this.minzoom = -1;
        this.maxzoom = -1;
        this.greeting = null;
        this.farewell = null;
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
        normalized_world = DynmapWorld.normalizeWorldName(world);
        desc = node.getString("desc", null);
        lineweight = node.getInteger("strokeWeight", -1);
        if(lineweight == -1) {	/* Handle typo-saved value */
        	 lineweight = node.getInteger("stokeWeight", 3);
        }
        lineopacity = node.getDouble("strokeOpacity", 0.8);
        linecolor = node.getInteger("strokeColor", 0xFF0000);
        fillopacity = node.getDouble("fillOpacity", 0.35);
        fillcolor = node.getInteger("fillColor", 0xFF0000);
        boostflag = node.getBoolean("boostFlag",  false);
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
        corners.clear();
        markerset = null;
        bb_cache = null;
    }
    
    @Override
	public String getUniqueMarkerID() {
    	if (markerset != null) {
    		return markerset + ":area:" + markerid;
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
        if(markerset == null) return;
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
        node.put("strokeWeight", lineweight);
        node.put("strokeOpacity", lineopacity);
        node.put("strokeColor", linecolor);
        node.put("fillOpacity", fillopacity);
        node.put("fillColor", fillcolor);
        if (boostflag) {
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
        if(markerset == null) return;
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
        if(markerset == null) return;
        if((this.ytop != ytop) || (this.ybottom != ybottom)) {
            this.ytop = ytop;
            this.ybottom = ybottom;
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
            bb_cache = null;
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
        if(markerset == null) return;
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
        bb_cache = null;
    }
    @Override
    public void deleteCorner(int n) {
        if(markerset == null) return;
        if(n < corners.size()) {
            corners.remove(n);
            MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
            bb_cache = null;
        }
    }
    @Override
    public void setCornerLocations(double[] x, double[] z) {
        if(markerset == null) return;
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
        bb_cache = null;
    }
    @Override
    public void setLineStyle(int weight, double opacity, int color) {
        if(markerset == null) return;
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
        if(markerset == null) return;
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
    @Override
    public void setMarkerSet(MarkerSet newset) {
        if(markerset != null) {
            markerset.removeAreaMarker(this);   /* Remove from our marker set (notified by set) */
        }
        markerset = (MarkerSetImpl)newset;
        markerset.insertAreaMarker(this);
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

    final boolean testTileForBoostMarkers(DynmapWorld w, HDPerspective perspective, final double tile_x, final double tile_y, final double tile_dim) {
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
            if (corners != null) {
                ArrayList<Coord> crn = corners;
                int cnt = crn.size();
                if (cnt == 2) { // Special case
                    cnt = 4;
                    crn = new ArrayList<Coord>();
                    Coord c0 = corners.get(0);
                    Coord c1 = corners.get(1);
                    crn.add(c0);
                    crn.add(new Coord(c0.x, c1.z));
                    crn.add(c1);
                    crn.add(new Coord(c1.x, c0.z));
                }
                double ymid = (this.ytop + this.ybottom) / 2.0; 
                bb.xp = new double[cnt];
                bb.yp = new double[cnt];
                for (int i = 0; i < cnt; i++) {
                    Coord c = crn.get(i);
                    v.x = c.x; v.y = ymid; v.z = c.z; // get coords of point, in world coord
                    perspective.transformWorldToMapCoord(v,  v2);   // Transform to map coord
                    if (v2.x < bb.xmin) bb.xmin = v2.x;
                    if (v2.y < bb.ymin) bb.ymin = v2.y;
                    if (v2.x > bb.xmax) bb.xmax = v2.x;
                    if (v2.y > bb.ymax) bb.ymax = v2.y;
                    bb.xp[i] = v2.x;
                    bb.yp[i] = v2.y;
                }
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
        MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
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
        MarkerAPIImpl.areaMarkerUpdated(this, MarkerUpdate.UPDATED);
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
		// If Y is in range (if there is a range)
		if ((ytop != ybottom) && ((y < ybottom) || (y > ytop))) {
			return false;
		}
		// Test if inside polygon
        int nvert = corners.size();
        Coord v0, v1;
        boolean c = false;
        if (nvert == 2) {	// Diagonal corners (simple rectangle
    		v0 = corners.get(0);
    		v1 = corners.get(1);
    		if (((v0.x > x) != (v1.x > x)) &&
    				((v0.z > z) != (v1.z > z))) {
    			c = true;
    		}
        }
        else {
        	for (int i = 0, j = nvert-1; i < nvert; j = i, i++) {
        		v0 = corners.get(i);
        		v1 = corners.get(j);
        		if (((v0.z > z) != (v1.z > z)) &&
        			(x < (v0.x + ((v1.x-v0.x)*(z-v0.z)/(v1.z-v0.z))))) {
        			c = !c;
        		}
        	}
        }
        return c;
	}
}
