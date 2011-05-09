package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class JsonTimerTask extends TimerTask {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private final DynmapPlugin plugin;
    private Server server;
    private MapManager mapManager;
    private Configuration configuration;
    private ConfigurationNode regions;
    private static final JSONParser parser = new JSONParser();
    private long lastTimestamp = 0;

    public JsonTimerTask(DynmapPlugin instance, Configuration config) {
        this.plugin = instance;
        this.server = this.plugin.getServer();
        this.mapManager = this.plugin.getMapManager();
        this.configuration = config;
        for(ConfigurationNode type : configuration.getNodeList("web.components", null))
            if(type.getString("type").equalsIgnoreCase("regions")) {
                this.regions = type;
                break;
            }
    }

    public void run() {
        long jsonInterval = configuration.getInt("jsonfile-interval", 1) * 1000;
        long current = System.currentTimeMillis();
        File outputFile;

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
                    log.log(Level.SEVERE, "Exception while reading JSON-file.", ex);
                } catch (ParseException ex) {
                    log.log(Level.SEVERE, "Exception while parsing JSON-file.", ex);
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
        for (World world : this.server.getWorlds()) {
            //Parse region file for multi world style
            if(regions.getBoolean("useworldpath", false))
                parseRegionFile(world.getName() + "/" + regions.getString("filename", "regions.yml"), regions.getString("filename", "regions.yml").replace(".", "_" + world.getName() + ".yml"));

            current = System.currentTimeMillis();

            Client.Update update = new Client.Update();

            update.timestamp = current;
            update.servertime = world.getTime() % 24000;

            Player[] players = plugin.playerList.getVisiblePlayers();
            update.players = new Client.Player[players.length];
            for (int i = 0; i < players.length; i++) {
                Player p = players[i];
                Location pl = p.getLocation();
                update.players[i] = new Client.Player(p.getDisplayName(), pl.getWorld().getName(), pl.getX(), pl.getY(), pl.getZ());
            }

            update.updates = mapManager.getWorldUpdates(world.getName(), current - (jsonInterval + 10000));

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
                log.log(Level.SEVERE, "Exception while writing JSON-file.", ex);
            } catch (IOException ioe) {
                log.log(Level.SEVERE, "Exception while writing JSON-file.", ioe);
            }
        }
        lastTimestamp = System.currentTimeMillis();

        //Parse regions file for non worlds style
        if (!regions.getBoolean("useworldpath", false))
             parseRegionFile(regions.getString("filename", "regions.yml"), regions.getString("filename", "regions.yml"));
    }

    //handles parsing and writing region json files
    private void parseRegionFile(String regionFile, String outputFileName)
    {
        File outputFile;
        Configuration regionConfig = null;
        if(regions.getBoolean("useworldpath", false))
        {
            if(new File("plugins/"+regions.getString("name", "WorldGuard"), regionFile).exists())
                regionConfig = new Configuration(new File("plugins/"+regions.getString("name", "WorldGuard"), regionFile));
            else if(new File("plugins/"+regions.getString("name", "WorldGuard")+"/worlds", regionFile).exists())
                regionConfig = new Configuration(new File("plugins/"+regions.getString("name", "WorldGuard")+"/worlds", regionFile));
        }
        else
            regionConfig = new Configuration(new File("plugins/"+regions.getString("name", "WorldGuard"), regionFile));
        //File didn't exist
        if(regionConfig == null)
            return;
        regionConfig.load();

        outputFileName = outputFileName.substring(0, outputFileName.lastIndexOf("."))+".json";

        File webWorldPath = new File(this.configuration.getString("webpath", "web")+"/standalone/", outputFileName);
        Map<?, ?> regionData = (Map<?, ?>) regionConfig.getProperty(regions.getString("basenode", "regions"));
        if (webWorldPath.isAbsolute())
            outputFile = webWorldPath;
        else {
            outputFile = new File(plugin.getDataFolder(), webWorldPath.toString());
        }
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(Json.stringifyJson(regionData).getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            log.log(Level.SEVERE, "Exception while writing JSON-file.", ex);
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Exception while writing JSON-file.", ioe);
        }
    }
}
