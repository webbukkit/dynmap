package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import static org.dynmap.JSONUtils.*;


public class JsonFileClientUpdateComponent extends ClientUpdateComponent {
    protected TimerTask task;
    protected Timer timer;
    protected long jsonInterval;
    protected long lastTimestamp = 0;
    protected JSONParser parser = new JSONParser();
    public JsonFileClientUpdateComponent(final DynmapPlugin plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        task = new TimerTask() {
            @Override
            public void run() {
                writeUpdates();
                if (configuration.getBoolean("allowwebchat", false)) {
                    handleWebChat();
                }
            }
        };
        timer = new Timer();
        timer.scheduleAtFixedRate(task, jsonInterval, jsonInterval);
        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "jsonfile", true);
            }
        });
        plugin.events.addListener("initialized", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
            }
        });
    }
    
    protected File getStandaloneFile(String filename) {
        File webpath = new File(plugin.configuration.getString("webpath", "web"), "standalone/" + filename);
        if (webpath.isAbsolute())
            return webpath;
        else
            return new File(plugin.getDataFolder(), webpath.toString());
    }
    
    protected void writeConfiguration() {
        File outputFile;
        JSONObject clientConfiguration = new JSONObject();
        plugin.events.trigger("buildclientconfiguration", clientConfiguration);
        outputFile = getStandaloneFile("dynmap_config.json");

        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(clientConfiguration.toJSONString().getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-configuration-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-configuration-file.", ioe);
        }
    }
    
    protected void writeUpdates() {
        long current = System.currentTimeMillis();
        File outputFile;

        //Handles Updates
        for (DynmapWorld dynmapWorld : plugin.mapManager.worlds.values()) {
            World world = dynmapWorld.world;
            current = System.currentTimeMillis();

            JSONObject update = new JSONObject();
            update.put("timestamp", current);
            ClientUpdateEvent clientUpdate = new ClientUpdateEvent(current, dynmapWorld, update);
            plugin.events.trigger("buildclientupdate", clientUpdate);

            outputFile = getStandaloneFile("dynmap_" + world.getName() + ".json");
            try {
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(Json.stringifyJson(update).getBytes());
                fos.close();
            } catch (FileNotFoundException ex) {
                Log.severe("Exception while writing JSON-file.", ex);
            } catch (IOException ioe) {
                Log.severe("Exception while writing JSON-file.", ioe);
            }
            plugin.events.<ClientUpdateEvent>trigger("clientupdatewritten", clientUpdate);
        }
        lastTimestamp = System.currentTimeMillis();
        
        plugin.events.<Object>trigger("clientupdateswritten", null);
    }
    
    protected void handleWebChat() {
        File webchatFile = getStandaloneFile("dynmap_webchat.json");
        
        if (webchatFile.exists() && lastTimestamp != 0) {
            JSONArray jsonMsgs = null;
            try {
                FileReader inputFileReader = new FileReader(webchatFile);
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
                        String name = String.valueOf(o.get("name"));
                        String message = String.valueOf(o.get("message"));
                        webChat(name, message);
                    }
                }
            }
        }
    }
    
    protected void webChat(String name, String message) {
        // TODO: Change null to something meaningful.
        plugin.mapManager.pushUpdate(new Client.ChatMessage("web", null, name, message, null));
        Log.info("[WEB]" + name + ": " + message);
        ChatEvent event = new ChatEvent("web", name, message);
        plugin.events.trigger("webchat", event);
    }
}
