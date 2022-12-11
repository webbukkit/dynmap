package org.dynmap.markers.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.markers.PlayerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class PlayerSetImpl implements PlayerSet {
    private String setid;
    private HashSet<String> players;
    private boolean symmetric;
    private boolean ispersistent;
    
    PlayerSetImpl(String id) {
        setid = id;
        players = new HashSet<String>();
        symmetric = true;
    }
    
    PlayerSetImpl(String id, boolean symmetric, Set<String> players, boolean persistent) {
        setid = id;
        this.symmetric = symmetric;
        this.players = new HashSet<String>(players);
        ispersistent = persistent;
    }
    
    void cleanup() {
        players.clear();
    }

    @Override
    public String getSetID() {
        return setid;
    }

    @Override
    public boolean isPersistentSet() {
        return ispersistent;
    }

    @Override
    public void deleteSet() {
        MarkerAPIImpl.removePlayerSet(this);    /* Remove from top level sets (notification from there) */
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
        cleanup();
    }
    
    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        if(!ispersistent)   /* Nothing if not persistent */
            return null;
        /* Make top level node */
        HashMap<String, Object> setnode = new HashMap<String, Object>();
        ArrayList<String> playlist = new ArrayList<String>(players);
        setnode.put("players", playlist);
        setnode.put("symmetric", symmetric);
        return setnode;
    }

    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node, boolean isSafe) {
        List<String> plist = node.getList("players");
        if(plist != null) {
            players.clear();
            for(String id : plist) {
                players.add(id.toLowerCase());
            }
        }
        symmetric = node.getBoolean("symmetric", true);
        
        ispersistent = true;
        
        return true;
    }
    @Override
    public void setSymmetricSet(boolean symmetric) {
        if(this.symmetric != symmetric) {
            this.symmetric = symmetric;
            MarkerAPIImpl.playerSetUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public boolean isSymmetricSet() {
        return symmetric;
    }

    @Override
    public Set<String> getPlayers() {
        return players;
    }

    @Override
    public void setPlayers(Set<String> players) {
        if(players.size() == this.players.size()) {
            boolean match = true;
            for(String s : players) {
                if(this.players.contains(s.toLowerCase()) == false) {
                    match = false;
                    break;
                }
            }
            if(match)
                return;
        }
        this.players.clear();
        for(String id : players) {
            this.players.add(id.toLowerCase());
        }
        MarkerAPIImpl.playerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    @Override
    public void addPlayer(String player) {
        player = player.toLowerCase();
        if(!players.add(player)) return;
        MarkerAPIImpl.playerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    @Override
    public void removePlayer(String player) {
        player = player.toLowerCase();
        if(!players.remove(player)) return;
        MarkerAPIImpl.playerSetUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    @Override
    public boolean isPlayerInSet(String player) {
        player = player.toLowerCase();
        return players.contains(player);
    }

}
