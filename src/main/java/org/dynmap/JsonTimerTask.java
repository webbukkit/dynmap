package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.TimerTask;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.dynmap.web.Json;

class JsonTimerTask extends TimerTask {
    private final DynmapPlugin plugin;
    private Server server;
    private MapManager mapManager;
    private Configuration configuration;

    public JsonTimerTask(DynmapPlugin instance, Configuration config) {
        this.plugin = instance;
        this.server = this.plugin.getServer();
        this.mapManager = this.plugin.getMapManager();
        this.configuration = config;
    }

    public void run() {
        for (World world : this.server.getWorlds()) {
            long current = System.currentTimeMillis();

            Client.Update update = new Client.Update();

            update.timestamp = current;
            update.servertime = world.getTime() % 24000;

            Player[] players = mapManager.playerList.getVisiblePlayers();
            update.players = new Client.Player[players.length];
            for (int i = 0; i < players.length; i++) {
                Player p = players[i];
                Location pl = p.getLocation();
                update.players[i] = new Client.Player(p.getName(), pl.getWorld().getName(), pl.getX(), pl.getY(), pl.getZ());
            }

            update.updates = mapManager.getWorldUpdates(world.getName(), current - (configuration.getInt("jsonfile-interval", 1) + 10000));

            File webpath = new File(this.configuration.getString("webpath", "web"), "dynmap_" + world.getName() + ".json");
            File outputFile;
            if (webpath.isAbsolute())
                outputFile = webpath;
            else {
                outputFile = new File(plugin.getDataFolder(), webpath.toString());
            }
            try {
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(Json.stringifyJson(update).getBytes());
                fos.close();
            } catch (FileNotFoundException ex) {
                System.out.println("FileNotFoundException : " + ex);
            } catch (IOException ioe) {
                System.out.println("IOException : " + ioe);
            }
        }
    }
}