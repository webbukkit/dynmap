package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

public class PlayerList {
    private Server server;
    private HashSet<String> hiddenPlayerNames = new HashSet<String>();
    private File hiddenPlayersFile;
    private Configuration configuration;

    public PlayerList(Server server, File hiddenPlayersFile, Configuration configuration) {
        this.server = server;
        this.hiddenPlayersFile = hiddenPlayersFile;
        this.configuration = configuration;
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

    // TODO: Clean this up... one day
    public Player[] getVisiblePlayers(String worldName) {
        ArrayList<Player> visiblePlayers = new ArrayList<Player>();
        Player[] onlinePlayers = server.getOnlinePlayers();
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (int i = 0; i < onlinePlayers.length; i++) {
            Player p = onlinePlayers[i];
            if (p.getWorld().getName().equals(worldName) && !(useWhitelist ^ hiddenPlayerNames.contains(p.getName().toLowerCase()))) {
                visiblePlayers.add(p);
            }
        }
        Player[] result = new Player[visiblePlayers.size()];
        visiblePlayers.toArray(result);
        return result;
    }
    
    public Player[] getVisiblePlayers() {
        ArrayList<Player> visiblePlayers = new ArrayList<Player>();
        Player[] onlinePlayers = server.getOnlinePlayers();
        boolean useWhitelist = configuration.getBoolean("display-whitelist", false);
        for (int i = 0; i < onlinePlayers.length; i++) {
            Player p = onlinePlayers[i];
            if (!(useWhitelist ^ hiddenPlayerNames.contains(p.getName().toLowerCase()))) {
                visiblePlayers.add(p);
            }
        }
        Player[] result = new Player[visiblePlayers.size()];
        visiblePlayers.toArray(result);
        return result;
    }
}
