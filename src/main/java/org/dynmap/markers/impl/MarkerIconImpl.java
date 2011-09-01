package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.markers.MarkerIcon;

class MarkerIconImpl implements MarkerIcon {
    private String iconid;
    private String label;
    
    MarkerIconImpl(String id) {
        iconid = id;
        label = id;
    }

    MarkerIconImpl(String id, String lbl) {
        iconid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
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

    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        HashMap<String, Object> node = new HashMap<String, Object>();
        node.put("label", label);

        return node;
    }

    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", iconid);

        return true;
    }

}
