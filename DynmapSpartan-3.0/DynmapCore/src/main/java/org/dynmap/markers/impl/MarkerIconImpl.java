package org.dynmap.markers.impl;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.markers.MarkerIcon;

class MarkerIconImpl implements MarkerIcon {
    private String iconid;
    private String label;
    private boolean is_builtin;
    private MarkerSize size = MarkerSize.MARKER_16x16;
    
    MarkerIconImpl(String id) {
        iconid = id;
        label = id;
        is_builtin = false;
    }

    MarkerIconImpl(String id, String lbl, boolean is_builtin) {
        iconid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.is_builtin = is_builtin;
    }

    void cleanup() {
        
    }
    
    @Override
    public String getMarkerIconID() {
        return iconid;
    }

    @Override
    public String getMarkerIconLabel() {
        return label;
    }

    @Override
    public void setMarkerIconLabel(String lbl) {
        if(lbl == null) lbl = iconid;
        if(label.equals(lbl) == false) {
            label = lbl;
            MarkerAPIImpl.saveMarkers();
        }
    }
    
    @Override
    public void setMarkerIconImage(InputStream in) {
        if(MarkerAPIImpl.api.loadMarkerIconStream(this.iconid, in))
            MarkerAPIImpl.api.publishMarkerIcon(this);
    }

    @Override
    public void deleteIcon() {
        MarkerAPIImpl.removeIcon(this);
    }

    @Override
    public boolean isBuiltIn() {
        return is_builtin;
    }

    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        if(is_builtin)
            return null;
        
        HashMap<String, Object> node = new HashMap<String, Object>();
        node.put("label", label);

        return node;
    }

    boolean loadPersistentData(ConfigurationNode node, boolean isSafe) {
        if(is_builtin)
            return false;
        
        label = node.getString("label", iconid);

        return true;
    }

    @Override
    public MarkerSize getMarkerIconSize() {
        return size;
    }
    
    void setMarkerIconSize(MarkerSize sz) {
        size = sz;
    }
}
