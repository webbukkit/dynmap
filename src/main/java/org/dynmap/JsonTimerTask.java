package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class JsonTimerTask extends TimerTask {
    private final DynmapPlugin plugin;
    private Server server;
    private MapManager mapManager;
    private ConfigurationNode configuration;
    private ConfigurationNode regions;
    private static final JSONParser parser = new JSONParser();
    private long lastTimestamp = 0;

    public JsonTimerTask(DynmapPlugin instance, ConfigurationNode config) {
        this.plugin = instance;
        this.server = this.plugin.getServer();
        this.mapManager = this.plugin.getMapManager();
        this.configuration = config;
    }

    public void run() {
        long jsonInterval = configuration.getInteger("jsonfile-interval", 1) * 1000;
        long current = System.currentTimeMillis();
        File outputFile;
        boolean showHealth = configuration.getBoolean("health-in-json", false);

        //Handles Reading WebChat
        if (configuration.getNode("web").getBoolean("allowwebchat", false)) {
            File webChatPath = new File(this.configuration.getString("webpath", "web"), "standalone/dynmap_webchat.json");
            if (webChatPath.isAbsolute())
                outputFile = webChatPath;
            else {
                outputFile = new File(plugin.getDataFolder(), webChatPath.toString());
            }
            if (webChatPath.exists() && lastTimestamp != 0) {
                JSONArray jsonMsgs = null;
                try {
                    FileReader inputFileReader = new FileReader(webChatPath);
                    jsonMsgs = (JSONArray) parser.parse(inputFileReader);
                    inputFileReader.close();
                } catch (IOException ex) {
                    Log.severe("Exception while reading JSON-file.", ex);
                } catch (ParseException ex) {
                    Log.severe("Exception while parsing JSON-file.", ex);
                }

                if (jsonMsgs != null) {
                    Iterator<?> iter = jsonMsgs.iterator();
                    while (iter.hasNext()) {
                        JSONObject o = (JSONObject) iter.next();
                        if (Long.parseLong(String.valueOf(o.get("timestamp"))) >= (lastTimestamp)) {
                            plugin.webChat(String.valueOf(o.get("name")), String.valueOf(o.get("message")));
                        }
                    }
                }
            }
        }

        //Handles Updates
        for (DynmapWorld dynmapWorld : plugin.mapManager.worlds.values()) {
            World world = dynmapWorld.world;
            current = System.currentTimeMillis();

            Client.Update update = new Client.Update();
            WorldUpdate worldUpdate = new WorldUpdate(dynmapWorld, update);
            plugin.events.trigger("buildingupdate", worldUpdate);
            update.timestamp = current;
            update.servertime = world.getTime() % 24000;
            update.hasStorm = world.hasStorm();
            update.isThundering = world.isThundering();

            Player[] players = plugin.playerList.getVisiblePlayers();
            update.players = new Client.Player[players.length];
            for (int i = 0; i < players.length; i++) {
                Player p = players[i];
                Location pl = p.getLocation();
                update.players[i] = new Client.Player(p.getDisplayName(), pl.getWorld().getName(), pl.getX(), pl.getY(), pl.getZ(), showHealth?p.getHealth():-1,
                        p.getName());
            }

            update.updates = mapManager.getWorldUpdates(world.getName(), current - (jsonInterval + 10000));

            plugin.events.trigger("buildupdate", worldUpdate);

            File webWorldPath = new File(this.configuration.getString("webpath", "web"), "standalone/dynmap_" + world.getName() + ".json");
            if (webWorldPath.isAbsolute())
                outputFile = webWorldPath;
            else {
                outputFile = new File(plugin.getDataFolder(), webWorldPath.toString());
            }
            try {
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(Json.stringifyJson(update).getBytes());
                fos.close();
            } catch (FileNotFoundException ex) {
                Log.severe("Exception while writing JSON-file.", ex);
            } catch (IOException ioe) {
                Log.severe("Exception while writing JSON-file.", ioe);
            }
            plugin.events.<Object>trigger("updatewritten", worldUpdate);
        }
        lastTimestamp = System.currentTimeMillis();
        
        plugin.events.<Object>trigger("updateswritten", null);
    }
}
