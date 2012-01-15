package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;

public class PlayerList {
    private DynmapServerInterface server;
    private HashSet<String> hiddenPlayerNames = new HashSet<String>();
    private File hiddenPlayersFile;
    private ConfigurationNode configuration;
    private DynmapPlayer[] online;

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
            return;
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

    public List<DynmapPlayer> getVisiblePlayers(String worldName) {
        ArrayList<DynmapPlayer> visiblePlayers = new ArrayList<DynmapPlayer>();
        DynmapPlayer[] onlinePlayers = online;    /* Use copied list - we don't call from server thread */
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (int i = 0; i < onlinePlayers.length; i++) {
            DynmapPlayer p = onlinePlayers[i];
            if(p == null) continue;
            if((worldName != null) && (p.getWorld().equals(worldName) == false)) continue;
            
            if (!(useWhitelist ^ hiddenPlayerNames.contains(p.getName().toLowerCase()))) {
                visiblePlayers.add(p);
            }
        }
        return visiblePlayers;
    }

    public List<DynmapPlayer> getVisiblePlayers() {
        return getVisiblePlayers(null);
    }
    
    public List<DynmapPlayer> getHiddenPlayers() {
        ArrayList<DynmapPlayer> hidden = new ArrayList<DynmapPlayer>();
        DynmapPlayer[] onlinePlayers = online;    /* Use copied list - we don't call from server thread */
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (int i = 0; i < onlinePlayers.length; i++) {
            DynmapPlayer p = onlinePlayers[i];
            if(p == null) continue;
            if (useWhitelist ^ hiddenPlayerNames.contains(p.getName().toLowerCase())) {
                hidden.add(p);
            }
        }
        return hidden;
    }

    public boolean isVisiblePlayer(String p) {
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        return (!(useWhitelist ^ hiddenPlayerNames.contains(p.toLowerCase())));
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
