package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class MarkerSetImpl implements MarkerSet {
    private HashMap<String, MarkerImpl> markers = new HashMap<String, MarkerImpl>();
    private HashMap<String, AreaMarkerImpl> areamarkers = new HashMap<String, AreaMarkerImpl>();
    private String setid;
    private String label;
    private HashMap<String, MarkerIconImpl> allowedicons = null;
    private boolean hide_by_def;
    private boolean ispersistent;
    private int prio = 0;
    private int minzoom = 0;
    
    MarkerSetImpl(String id) {
        setid = id;
        label = id;
    }
    
    MarkerSetImpl(String id, String lbl, Set<MarkerIcon> iconlimit, boolean persistent) {
        setid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        if(iconlimit != null) {
            allowedicons = new HashMap<String, MarkerIconImpl>();
            for(MarkerIcon ico : iconlimit) {
                if(ico instanceof MarkerIconImpl) {
                    allowedicons.put(ico.getMarkerIconID(), (MarkerIconImpl)ico);
                }
            }
        }
        ispersistent = persistent;
    }
    
    void cleanup() {
        for(MarkerImpl m : markers.values())
            m.cleanup();
        for(AreaMarkerImpl m : areamarkers.values())
            m.cleanup();
        markers.clear();
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
     * Remove marker from set
     * 
     * @param marker
     */
    void removeAreaMarker(AreaMarkerImpl marker) {
        areamarkers.remove(marker.getMarkerID());   /* Remove from set */
        if(ispersistent && marker.isPersistentMarker()) {   /* If persistent */
            MarkerAPIImpl.saveMarkers();        /* Drive save */
        }
        MarkerAPIImpl.areaMarkerUpdated(marker, MarkerUpdate.DELETED);
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
        /* Make top level node */
        HashMap<String, Object> setnode = new HashMap<String, Object>();
        setnode.put("label", label);
        if(allowedicons != null) {
            ArrayList<String> allowed = new ArrayList<String>(allowedicons.keySet());
            setnode.put("allowedicons", allowed);
        }
        setnode.put("markers", node);
        setnode.put("areas", anode);
        setnode.put("hide", hide_by_def);
        setnode.put("layerprio", prio);
        setnode.put("minzoom", minzoom);
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
                }
                else {
                    Log.info("Error loading area marker '" + id + "' for set '" + setid + "'");
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
        minzoom = node.getInteger("minzoom", 0);
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
    public void setMinZoom(int minzoom) {
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

}
