package org.dynmap;

import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;

import java.io.*;
import java.util.*;

public class PlayerList {
    private DynmapServerInterface server;
    private HashSet<String> hiddenPlayerNames = new HashSet<>();
    private File hiddenPlayersFile;
    private ConfigurationNode configuration;
    private DynmapPlayer[] online;
    private HashMap<String, Set<String>> invisibility_asserts = new HashMap<>();
    private HashMap<String, Set<String>> visibility_asserts = new HashMap<>();

    public PlayerList(DynmapServerInterface server, File hiddenPlayersFile, ConfigurationNode configuration) {
        this.server = server;
        this.hiddenPlayersFile = hiddenPlayersFile;
        this.configuration = configuration;
        updateOnlinePlayers(null);
    }

    public void save() {
        OutputStream stream;
        try {
            stream = new FileOutputStream(hiddenPlayersFile);
            OutputStreamWriter writer = new OutputStreamWriter(stream);
            for (String player : hiddenPlayerNames) {
                writer.write(player);
                writer.write("\n");
            }
            writer.close();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        try {
            Scanner scanner = new Scanner(hiddenPlayersFile);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                hiddenPlayerNames.add(line);
            }
            scanner.close();
        } catch (FileNotFoundException e) {
        }
    }

    public void hide(String playerName) {
        hiddenPlayerNames.add(playerName.toLowerCase());
        save();
    }

    public void show(String playerName) {
        hiddenPlayerNames.remove(playerName.toLowerCase());
        save();
    }

    public void setVisible(String playerName, boolean visible) {
        if (visible ^ configuration.getBoolean("display-whitelist", false))
            show(playerName);
        else
            hide(playerName);
    }

    public void assertVisiblilty(String playerName, boolean visible, String plugin_id) {
        playerName = playerName.toLowerCase();
        if(visible) {
            Set<String> ids = visibility_asserts.computeIfAbsent(playerName, k -> new HashSet<>());
            ids.add(plugin_id);
        }
        else {
            Set<String> ids = visibility_asserts.get(playerName);
            if(ids != null) {
                ids.remove(plugin_id);
                if(ids.isEmpty()) {
                    visibility_asserts.remove(playerName);
                }
            }
        }
    }

    public void assertInvisiblilty(String playerName, boolean invisible, String plugin_id) {
        playerName = playerName.toLowerCase();
        if(invisible) {
            Set<String> ids = invisibility_asserts.computeIfAbsent(playerName, k -> new HashSet<>());
            ids.add(plugin_id);
        }
        else {
            Set<String> ids = invisibility_asserts.get(playerName);
            if(ids != null) {
                ids.remove(plugin_id);
                if(ids.isEmpty()) {
                    invisibility_asserts.remove(playerName);
                }
            }
        }
    }
    
    public List<DynmapPlayer> getVisiblePlayers(String worldName) {
        ArrayList<DynmapPlayer> visiblePlayers = new ArrayList<>();
        DynmapPlayer[] onlinePlayers = online;    /* Use copied list - we don't call from server thread */
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (DynmapPlayer p : onlinePlayers) {
            if (p == null) continue;
            if ((worldName != null) && (!p.getWorld().equals(worldName))) continue;
            String pname = p.getName().toLowerCase();
            if (useWhitelist == hiddenPlayerNames.contains(pname)) {
                if (!invisibility_asserts.containsKey(pname)) {
                    visiblePlayers.add(p);
                }
            } else if (visibility_asserts.containsKey(pname)) {
                visiblePlayers.add(p);
            }
        }
        return visiblePlayers;
    }

    public List<DynmapPlayer> getVisiblePlayers() {
        return getVisiblePlayers(null);
    }
    
    public List<DynmapPlayer> getHiddenPlayers() {
        ArrayList<DynmapPlayer> hidden = new ArrayList<>();
        DynmapPlayer[] onlinePlayers = online;    /* Use copied list - we don't call from server thread */
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (DynmapPlayer p : onlinePlayers) {
            if (p == null) continue;
            String pname = p.getName().toLowerCase();
            if (useWhitelist == hiddenPlayerNames.contains(pname)) {
                if (invisibility_asserts.containsKey(pname)) {
                    hidden.add(p);
                }
            } else if (!visibility_asserts.containsKey(pname)) {
                hidden.add(p);
            }
        }
        return hidden;
    }

    public boolean isVisiblePlayer(String p) {
        p = p.toLowerCase();
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        return (useWhitelist == hiddenPlayerNames.contains(p)) && (!invisibility_asserts.containsKey(p));
    }

    /**
     * Call this from server thread to update player list safely
     */
    void updateOnlinePlayers(String skipone) {    
        DynmapPlayer[] players = server.getOnlinePlayers();
        DynmapPlayer[] pl = new DynmapPlayer[players.length];
        System.arraycopy(players, 0, pl, 0, pl.length);
        if(skipone != null) {
            for(int i = 0; i < pl.length; i++)
                if(pl[i].getName().equals(skipone))
                    pl[i] = null;
        }
        online = pl;
    }
}
