package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.EnterExitMarker;
import org.dynmap.markers.PolyLineMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class MarkerSetImpl implements MarkerSet {
    private ConcurrentHashMap<String, MarkerImpl> markers = new ConcurrentHashMap<String, MarkerImpl>();
    private ConcurrentHashMap<String, AreaMarkerImpl> areamarkers = new ConcurrentHashMap<String, AreaMarkerImpl>();
    private ConcurrentHashMap<String, PolyLineMarkerImpl> linemarkers = new ConcurrentHashMap<String, PolyLineMarkerImpl>();
    private ConcurrentHashMap<String, CircleMarkerImpl> circlemarkers = new ConcurrentHashMap<String, CircleMarkerImpl>();
    private ConcurrentHashMap<String, AreaMarkerImpl> boostingareamarkers = null;
    private ConcurrentHashMap<String, CircleMarkerImpl> boostingcirclemarkers = null;
    private ConcurrentHashMap<String, EnterExitMarker> enterexitmarkers = null;
    private String setid;
    private String label;
    private ConcurrentHashMap<String, MarkerIconImpl> allowedicons = null;
    private boolean hide_by_def;
    private boolean ispersistent;
    private int prio = 0;
    private int minzoom = -1;
    private int maxzoom = -1;
    private Boolean showlabels = null;
    private MarkerIcon deficon;
    
    MarkerSetImpl(String id) {
        setid = id;
        label = id;
        deficon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
    }
    
    MarkerSetImpl(String id, String lbl, Set<MarkerIcon> iconlimit, boolean persistent) {
        setid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        if(iconlimit != null) {
            allowedicons = new ConcurrentHashMap<String, MarkerIconImpl>();
            for(MarkerIcon ico : iconlimit) {
                if(ico instanceof MarkerIconImpl) {
                    allowedicons.put(ico.getMarkerIconID(), (MarkerIconImpl)ico);
                }
            }
        }
        ispersistent = persistent;
        deficon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
    }
    
    void cleanup() {
        for(MarkerImpl m : markers.values())
            m.cleanup();
        for(AreaMarkerImpl m : areamarkers.values())
            m.cleanup();
        for(PolyLineMarkerImpl m : linemarkers.values())
            m.cleanup();
        for(CircleMarkerImpl m : circlemarkers.values())
            m.cleanup();
        markers.clear();
        if (boostingareamarkers != null) {
            boostingareamarkers.clear();
            boostingareamarkers = null;
        }
        if (boostingcirclemarkers != null) {
            boostingcirclemarkers.clear();
            boostingcirclemarkers = null;
        }
        if (enterexitmarkers != null) {
        	enterexitmarkers.clear();
        	enterexitmarkers = null;
        }
        deficon = null;
    }
    
    @Override
    public Set<Marker> getMarkers() {
        return new HashSet<Marker>(markers.values());
    }

    @Override
    public Set<AreaMarker> getAreaMarkers() {
        return new HashSet<AreaMarker>(areamarkers.values());
    }

    @Override
    public Set<PolyLineMarker> getPolyLineMarkers() {
        return new HashSet<PolyLineMarker>(linemarkers.values());
    }

    @Override
    public Set<CircleMarker> getCircleMarkers() {
        return new HashSet<CircleMarker>(circlemarkers.values());
    }

    @Override
    public Marker createMarker(String id, String label, String world, double x, double y, double z, MarkerIcon icon, boolean is_persistent) {
        return createMarker(id, label, false, world, x, y, z, icon, is_persistent);
    }
    @Override
    public Marker createMarker(String id, String label, boolean markup, String world, double x, double y, double z, MarkerIcon icon, boolean is_persistent) {
        if(id == null) {    /* If not defined, generate unique one */
            int i = 0;
            do {
                i++;
                id = "marker_" + i; 
            } while(markers.containsKey(id));
        }
        if(icon == null) icon = deficon;
        if(markers.containsKey(id)) return null;    /* Duplicate ID? */
        if(!(icon instanceof MarkerIconImpl)) return null;
        /* If limited icons, and this isn't valid one, quit */
        if((allowedicons != null) && (allowedicons.containsKey(icon.getMarkerIconID()) == false)) return null;
        /* Create marker */
        is_persistent = is_persistent && this.ispersistent;
        MarkerImpl marker = new MarkerImpl(id, label, markup, world, x, y, z, (MarkerIconImpl)icon, is_persistent, this);
        markers.put(id, marker);    /* Add to set */
        if(is_persistent)
            MarkerAPIImpl.saveMarkers();
        
        MarkerAPIImpl.markerUpdated(marker, MarkerUpdate.CREATED);  /* Signal create */

        return marker;
    }

    @Override
    public Marker findMarker(String id) {
        return markers.get(id);
    }

    @Override
    public Marker   findMarkerByLabel(String lbl) {
        Marker match = null;
        int matchlen = Integer.MAX_VALUE;
        for(Marker m : markers.values()) {
            if(m.getLabel().contains(lbl)) {
                if(matchlen > m.getLabel().length()) {
                    match = m;
                    matchlen = m.getLabel().length();
                }
            }
        }
        return match;
    }

    @Override
    public String getMarkerSetID() {
        return setid;
    }

    @Override
    public String getMarkerSetLabel() {
        return label;
    }

    @Override
    public void setMarkerSetLabel(String lbl) {
        label = lbl;
        MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    @Override
    public boolean isMarkerSetPersistent() {
        return ispersistent;
    }

    @Override
    public Set<MarkerIcon> getAllowedMarkerIcons() {
        if(allowedicons != null)
            return new HashSet<MarkerIcon>(allowedicons.values());
        else
            return null;
    }

    @Override
    public void addAllowedMarkerIcon(MarkerIcon icon) {
        if(!(icon instanceof MarkerIconImpl)) return;
        if(allowedicons == null) return;
        allowedicons.put(icon.getMarkerIconID(), (MarkerIconImpl)icon);
        MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    @Override
    public void removeAllowedMarkerIcon(MarkerIcon icon) {
        if(!(icon instanceof MarkerIconImpl)) return;
        if(allowedicons == null) return;
        allowedicons.remove(icon.getMarkerIconID());
        MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }
    
    @Override
    public boolean isAllowedMarkerIcon(MarkerIcon icon) {
        if(allowedicons == null) return true;
        return allowedicons.containsKey(icon.getMarkerIconID());
    }

    @Override
    public Set<MarkerIcon> getMarkerIconsInUse() {
        HashSet<String> ids = new HashSet<String>();
        HashSet<MarkerIcon> icons = new HashSet<MarkerIcon>();
        for(Marker m : markers.values()) {
            MarkerIcon mi = m.getMarkerIcon();
            if(!ids.contains(mi.getMarkerIconID())) {
                ids.add(mi.getMarkerIconID());
                icons.add(mi);
            }
        }
        return icons;
    }

    @Override
    public void deleteMarkerSet() {
        MarkerAPIImpl.removeMarkerSet(this);    /* Remove from top level sets (notification from there) */
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
        cleanup();
    }
    /**
     * Insert marker from set
     * 
     * @param marker
     */
    void insertMarker(MarkerImpl marker) {
        markers.put(marker.getMarkerID(), marker);
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.markerUpdated(marker, MarkerUpdate.CREATED);
    }
    /**
     * Remove marker from set
     * 
     * @param marker
     */
    void removeMarker(MarkerImpl marker) {
        markers.remove(marker.getMarkerID());   /* Remove from set */
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.markerUpdated(marker, MarkerUpdate.DELETED);
    }
    /**
     * Insert marker from set
     * 
     * @param marker
     */
    void insertAreaMarker(AreaMarkerImpl marker) {
        areamarkers.put(marker.getMarkerID(),marker);   /* Add to set */
        if (marker.getBoostFlag()) {
            if (boostingareamarkers == null) {
                boostingareamarkers = new ConcurrentHashMap<String, AreaMarkerImpl>();
            }
            boostingareamarkers.put(marker.getMarkerID(), marker);
        }
        if ((marker.getGreetingText() != null) || (marker.getFarewellText() != null)) {
        	if (enterexitmarkers == null) {
        		enterexitmarkers = new ConcurrentHashMap<String, EnterExitMarker>();
        	}
        	enterexitmarkers.put(marker.getUniqueMarkerID(),  marker);
        }
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.areaMarkerUpdated(marker, MarkerUpdate.CREATED);
    }
    /**
     * Remove marker from set
     * 
     * @param marker
     */
    void removeAreaMarker(AreaMarkerImpl marker) {
        areamarkers.remove(marker.getMarkerID());   /* Remove from set */
        if (boostingareamarkers != null) {
            boostingareamarkers.remove(marker.getMarkerID());
            if (boostingareamarkers.isEmpty()) {
                boostingareamarkers = null;
            }
        }
        if (enterexitmarkers != null) {
        	enterexitmarkers.remove(marker.getUniqueMarkerID());
        	if (enterexitmarkers.isEmpty()) {
        		enterexitmarkers = null;
        	}
        }
        if (ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.areaMarkerUpdated(marker, MarkerUpdate.DELETED);
    }
    /**
     * Insert marker from set
     * 
     * @param marker
     */
    void insertPolyLineMarker(PolyLineMarkerImpl marker) {
        linemarkers.put(marker.getMarkerID(), marker);   /* Insert to set */
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.polyLineMarkerUpdated(marker, MarkerUpdate.CREATED);
    }
    /**
     * Remove marker from set
     * 
     * @param marker
     */
    void removePolyLineMarker(PolyLineMarkerImpl marker) {
        linemarkers.remove(marker.getMarkerID());   /* Remove from set */
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.polyLineMarkerUpdated(marker, MarkerUpdate.DELETED);
    }
    /**
     * Insert marker from set
     * 
     * @param marker
     */
    void insertCircleMarker(CircleMarkerImpl marker) {
        circlemarkers.put(marker.getMarkerID(), marker);   /* Insert to set */
        if (marker.getBoostFlag()) {
            if (boostingcirclemarkers == null) {
                boostingcirclemarkers = new ConcurrentHashMap<String, CircleMarkerImpl>();
            }
            boostingcirclemarkers.put(marker.getMarkerID(), marker);
        }
        if ((marker.getGreetingText() != null) || (marker.getFarewellText() != null)) {
        	if (enterexitmarkers == null) {
        		enterexitmarkers = new ConcurrentHashMap<String, EnterExitMarker>();
        	}
        	enterexitmarkers.put(marker.getUniqueMarkerID(),  marker);
        }        
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.circleMarkerUpdated(marker, MarkerUpdate.CREATED);
    }
    /**
     * Remove marker from set
     * 
     * @param marker
     */
    void removeCircleMarker(CircleMarkerImpl marker) {
        circlemarkers.remove(marker.getMarkerID());   /* Remove from set */
        if (boostingcirclemarkers != null) {
            boostingcirclemarkers.remove(marker.getMarkerID());
            if (boostingcirclemarkers.isEmpty()) {
                boostingcirclemarkers = null;
            }
        }
        if (enterexitmarkers != null) {
        	enterexitmarkers.remove(marker.getUniqueMarkerID());
        	if (enterexitmarkers.isEmpty()) {
        		enterexitmarkers = null;
        	}
        }
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.circleMarkerUpdated(marker, MarkerUpdate.DELETED);
    }

    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        if(!ispersistent)   /* Nothing if not persistent */
            return null;
        HashMap<String, Object> node = new HashMap<String, Object>();
        for(String id : markers.keySet()) {
            MarkerImpl m = markers.get(id);
            if(m.isPersistentMarker()) {
                node.put(id, m.getPersistentData());
            }
        }
        HashMap<String, Object> anode = new HashMap<String, Object>();
        for(String id : areamarkers.keySet()) {
            AreaMarkerImpl m = areamarkers.get(id);
            if(m.isPersistentMarker()) {
                anode.put(id, m.getPersistentData());
            }
        }
        HashMap<String, Object> lnode = new HashMap<String, Object>();
        for(String id : linemarkers.keySet()) {
            PolyLineMarkerImpl m = linemarkers.get(id);
            if(m.isPersistentMarker()) {
                lnode.put(id, m.getPersistentData());
            }
        }
        HashMap<String, Object> cnode = new HashMap<String, Object>();
        for(String id : circlemarkers.keySet()) {
            CircleMarkerImpl m = circlemarkers.get(id);
            if(m.isPersistentMarker()) {
                cnode.put(id, m.getPersistentData());
            }
        }
        /* Make top level node */
        HashMap<String, Object> setnode = new HashMap<String, Object>();
        setnode.put("label", label);
        if(allowedicons != null) {
            ArrayList<String> allowed = new ArrayList<String>(allowedicons.keySet());
            setnode.put("allowedicons", allowed);
        }
        setnode.put("markers", node);
        setnode.put("areas", anode);
        setnode.put("lines", lnode);
        setnode.put("circles", cnode);
        setnode.put("hide", hide_by_def);
        setnode.put("layerprio", prio);
        if (minzoom >= 0)
            setnode.put("minzoom", minzoom);
        if (maxzoom >= 0)
            setnode.put("maxzoom", maxzoom);
        if(deficon != null) {
            setnode.put("deficon", deficon.getMarkerIconID());
        }
        else {
            setnode.put("deficon", MarkerIcon.DEFAULT);
        }
        if(showlabels != null)
            setnode.put("showlabels", showlabels);
        return setnode;
    }

    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", setid); /* Get label */
        ConfigurationNode markernode = node.getNode("markers");
        if(markernode != null) {
            for(String id : markernode.keySet()) {
                MarkerImpl marker = new MarkerImpl(id, this);   /* Make and load marker */
                if(marker.loadPersistentData(markernode.getNode(id))) {
                    markers.put(id, marker);
                }
                else {
                    Log.info("Error loading marker '" + id + "' for set '" + setid + "'");
                    marker.cleanup();
                }
            }
        }
        ConfigurationNode areamarkernode = node.getNode("areas");
        if(areamarkernode != null) {
            for(String id : areamarkernode.keySet()) {
                AreaMarkerImpl marker = new AreaMarkerImpl(id, this);   /* Make and load marker */
                if(marker.loadPersistentData(areamarkernode.getNode(id))) {
                    areamarkers.put(id, marker);
                    if(marker.getBoostFlag()) {
                        if(boostingareamarkers == null) {
                            boostingareamarkers = new ConcurrentHashMap<String, AreaMarkerImpl>();
                        }
                        boostingareamarkers.put(id,  marker);
                    }
                    if ((marker.getGreetingText() != null) || (marker.getFarewellText() != null)) {
                    	if (enterexitmarkers == null) {
                    		enterexitmarkers = new ConcurrentHashMap<String, EnterExitMarker>();
                    	}
                    	enterexitmarkers.put(marker.getUniqueMarkerID(),  marker);
                    }
                }
                else {
                    Log.info("Error loading area marker '" + id + "' for set '" + setid + "'");
                    marker.cleanup();
                }
            }
        }
        ConfigurationNode linemarkernode = node.getNode("lines");
        if(linemarkernode != null) {
            for(String id : linemarkernode.keySet()) {
                PolyLineMarkerImpl marker = new PolyLineMarkerImpl(id, this);   /* Make and load marker */
                if(marker.loadPersistentData(linemarkernode.getNode(id))) {
                    linemarkers.put(id, marker);
                }
                else {
                    Log.info("Error loading line marker '" + id + "' for set '" + setid + "'");
                    marker.cleanup();
                }
            }
        }
        ConfigurationNode circlemarkernode = node.getNode("circles");
        if(circlemarkernode != null) {
            for(String id : circlemarkernode.keySet()) {
                CircleMarkerImpl marker = new CircleMarkerImpl(id, this);   /* Make and load marker */
                if(marker.loadPersistentData(circlemarkernode.getNode(id))) {
                    circlemarkers.put(id, marker);
                    if(marker.getBoostFlag()) {
                        if(boostingcirclemarkers == null) {
                            boostingcirclemarkers = new ConcurrentHashMap<String, CircleMarkerImpl>();
                        }
                        boostingcirclemarkers.put(id,  marker);
                    }
                    if ((marker.getGreetingText() != null) || (marker.getFarewellText() != null)) {
                    	if (enterexitmarkers == null) {
                    		enterexitmarkers = new ConcurrentHashMap<String, EnterExitMarker>();
                    	}
                    	enterexitmarkers.put(marker.getUniqueMarkerID(),  marker);
                    }
                }
                else {
                    Log.info("Error loading circle marker '" + id + "' for set '" + setid + "'");
                    marker.cleanup();
                }
            }
        }
        List<String> allowed = node.getList("allowedicons");
        if(allowed != null) {
            for(String id : allowed) {
                MarkerIconImpl icon = MarkerAPIImpl.getMarkerIconImpl(id);
                if(icon != null)
                    allowedicons.put(id, icon);
                else
                    Log.info("Error loading allowed icon '" + id + "' for set '" + setid + "'");
            }
        }
        hide_by_def = node.getBoolean("hide", false);
        prio = node.getInteger("layerprio", 0);
        minzoom = node.getInteger("minzoom", -1);
        maxzoom = node.getInteger("maxzoom", -1);
        if (minzoom == 0) minzoom = -1;
        if(node.containsKey("showlabels"))
            showlabels = node.getBoolean("showlabels", false);
        else
            showlabels = null;
        String defid = node.getString("deficon");
        if((defid != null) && (MarkerAPIImpl.api != null)) {
            deficon = MarkerAPIImpl.getMarkerIconImpl(defid);
        }
        else {
            deficon = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
        }
        ispersistent = true;
        
        return true;
    }
    @Override
    public void setHideByDefault(boolean hide) {
        if(hide_by_def != hide) {
            hide_by_def = hide;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public boolean getHideByDefault() {
        return hide_by_def;
    }
    @Override
    public void setLayerPriority(int prio) {
        if(this.prio != prio) {
            this.prio = prio;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public int getLayerPriority() {
        return this.prio;
    }

    @Override
    public AreaMarker createAreaMarker(String id, String lbl, boolean markup, String world, double[] x, double[] z, boolean persistent) {
        if(id == null) {    /* If not defined, generate unique one */
            int i = 0;
            do {
                i++;
                id = "area_" + i; 
            } while(areamarkers.containsKey(id));
        }
        if(areamarkers.containsKey(id)) return null;    /* Duplicate ID? */
        /* Create marker */
        persistent = persistent && this.ispersistent;
        AreaMarkerImpl marker = new AreaMarkerImpl(id, lbl, markup, world, x, z, persistent, this);
        areamarkers.put(id, marker);    /* Add to set */
        if(persistent)
            MarkerAPIImpl.saveMarkers();
        
        MarkerAPIImpl.areaMarkerUpdated(marker, MarkerUpdate.CREATED);  /* Signal create */

        return marker;
    }

    @Override
    public AreaMarker findAreaMarker(String id) {
        return areamarkers.get(id);
    }

    @Override
    public AreaMarker findAreaMarkerByLabel(String lbl) {
        AreaMarker match = null;
        int matchlen = Integer.MAX_VALUE;
        for(AreaMarker m : areamarkers.values()) {
            if(m.getLabel().contains(lbl)) {
                if(matchlen > m.getLabel().length()) {
                    match = m;
                    matchlen = m.getLabel().length();
                }
            }
        }
        return match;
    }

    @Override
    public PolyLineMarker createPolyLineMarker(String id, String lbl, boolean markup, String world, double[] x, double[] y, double[] z, boolean persistent) {
        if(id == null) {    /* If not defined, generate unique one */
            int i = 0;
            do {
                i++;
                id = "line_" + i; 
            } while(linemarkers.containsKey(id));
        }
        if(linemarkers.containsKey(id)) return null;    /* Duplicate ID? */
        /* Create marker */
        persistent = persistent && this.ispersistent;
        PolyLineMarkerImpl marker = new PolyLineMarkerImpl(id, lbl, markup, world, x, y, z, persistent, this);
        linemarkers.put(id, marker);    /* Add to set */
        if(persistent)
            MarkerAPIImpl.saveMarkers();
        
        MarkerAPIImpl.polyLineMarkerUpdated(marker, MarkerUpdate.CREATED);  /* Signal create */

        return marker;
    }

    @Override
    public PolyLineMarker findPolyLineMarker(String id) {
        return linemarkers.get(id);
    }

    @Override
    public PolyLineMarker findPolyLineMarkerByLabel(String lbl) {
        PolyLineMarker match = null;
        int matchlen = Integer.MAX_VALUE;
        for(PolyLineMarker m : linemarkers.values()) {
            if(m.getLabel().contains(lbl)) {
                if(matchlen > m.getLabel().length()) {
                    match = m;
                    matchlen = m.getLabel().length();
                }
            }
        }
        return match;
    }

    @Override
    public void setMinZoom(int minzoom) {
        if (minzoom < 0) minzoom = -1;
        if(this.minzoom != minzoom) {
            this.minzoom = minzoom;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public int getMinZoom() {
        return this.minzoom;
    }
    @Override
    public void setMaxZoom(int maxzoom) {
        if (maxzoom < 0) maxzoom = -1;
        if(this.maxzoom != maxzoom) {
            this.maxzoom = maxzoom;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public int getMaxZoom() {
        return this.maxzoom;
    }
    @Override
    public void setLabelShow(Boolean show) {
        if(show == showlabels) return;
        if((show == null) || (show.equals(showlabels) == false)) {
            showlabels = show;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public Boolean getLabelShow() {
        return showlabels;
    }

    @Override
    public CircleMarker createCircleMarker(String id, String lbl,
            boolean markup, String world, double x, double y, double z,
            double xr, double zr, boolean persistent) {
        if(id == null) {    /* If not defined, generate unique one */
            int i = 0;
            do {
                i++;
                id = "circle_" + i; 
            } while(circlemarkers.containsKey(id));
        }
        if(circlemarkers.containsKey(id)) return null;    /* Duplicate ID? */
        /* Create marker */
        persistent = persistent && this.ispersistent;
        CircleMarkerImpl marker = new CircleMarkerImpl(id, lbl, markup, world, x, y, z, xr, zr, persistent, this);
        circlemarkers.put(id, marker);    /* Add to set */
        if(persistent)
            MarkerAPIImpl.saveMarkers();
        
        MarkerAPIImpl.circleMarkerUpdated(marker, MarkerUpdate.CREATED);  /* Signal create */

        return marker;
    }

    @Override
    public CircleMarker findCircleMarker(String id) {
        return circlemarkers.get(id);
    }

    @Override
    public CircleMarker findCircleMarkerByLabel(String lbl) {
        CircleMarker match = null;
        int matchlen = Integer.MAX_VALUE;
        for(CircleMarker m : circlemarkers.values()) {
            if(m.getLabel().contains(lbl)) {
                if(matchlen > m.getLabel().length()) {
                    match = m;
                    matchlen = m.getLabel().length();
                }
            }
        }
        return match;
    }

    @Override
    public void setDefaultMarkerIcon(MarkerIcon defmark) {
        if(deficon != defmark) {
            deficon = defmark;
            MarkerAPIImpl.markerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }

    @Override
    public MarkerIcon getDefaultMarkerIcon() {
        return deficon;
    }
    
    final boolean testTileForBoostMarkers(DynmapWorld w, HDPerspective perspective, double tile_x, double tile_y, double tile_dim) {
        if (boostingareamarkers != null) {
            for (AreaMarkerImpl am : boostingareamarkers.values()) {
                if (am.testTileForBoostMarkers(w, perspective, tile_x, tile_y, tile_dim)) {
                    return true;
                }
            }
        }
        if (boostingcirclemarkers != null) {
            for (CircleMarkerImpl cm : boostingcirclemarkers.values()) {
                if (cm.testTileForBoostMarkers(w, perspective, tile_x, tile_y, tile_dim)) {
                    return true;
                }
            }
        }
        return false;
    }
	/**
	 * Add entered markers to set based on given coordinates
	 */
    @Override
	public void addEnteredMarkers(Set<EnterExitMarker> entered, String worldid, double x, double y, double z) {
    	if (enterexitmarkers == null) return;
		for (EnterExitMarker m : enterexitmarkers.values()) {
			if (m.testIfPointWithinMarker(worldid, x, y, z)) {
				entered.add(m);
			}
		}
    }
}
