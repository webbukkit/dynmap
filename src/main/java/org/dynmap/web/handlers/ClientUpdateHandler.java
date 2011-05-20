package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.dynmap.Client;
import org.dynmap.DynmapPlugin;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.HttpStatus;
import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class ClientUpdateHandler implements HttpHandler {
    private DynmapPlugin plugin;
    private boolean showHealth;

    public ClientUpdateHandler(DynmapPlugin plugin, boolean showHealth) {
        this.plugin = plugin;
        this.showHealth = showHealth;
    }

    Pattern updatePathPattern = Pattern.compile("world/([^/]+)/([0-9]*)");
    private static final HttpStatus WorldNotFound = new HttpStatus(HttpStatus.NotFound.getCode(), "World Not Found");
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {

        Matcher match = updatePathPattern.matcher(path);

        if (!match.matches()) {
            response.status = HttpStatus.Forbidden;
            return;
        }

        String worldName = match.group(1);
        String timeKey = match.group(2);

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            response.status = WorldNotFound;
            return;
        }

        long current = System.currentTimeMillis();
        long since = 0;

        if (path.length() > 0) {
            try {
                since = Long.parseLong(timeKey);
            } catch (NumberFormatException e) {
            }
        }

        JSONObject u = new JSONObject();
        //plugin.events.trigger("buildclientupdate", update);
        s(u, "timestamp", current);
        s(u, "servertime", world.getTime() % 24000);
        s(u, "hasStorm", world.hasStorm());
        s(u, "isThundering", world.isThundering());

        s(u, "players", new JSONArray());
        Player[] players = plugin.playerList.getVisiblePlayers();
        for(int i=0;i<players.length;i++) {
            Player p = players[i];
            Location pl = p.getLocation();
            JSONObject jp = new JSONObject();
            s(jp, "type", "player");
            s(jp, "name", p.getDisplayName());
            s(jp, "account", p.getName());
            s(jp, "world", p.getWorld().getName());
            s(jp, "x", pl.getX());
            s(jp, "y", pl.getY());
            s(jp, "z", pl.getZ());
            if (showHealth) {
                s(jp, "health", p.getHealth());
            }
            a(u, "players", jp);
        }

        s(u, "updates", new JSONArray());
        for(Object update : plugin.mapManager.getWorldUpdates(worldName, since)) {
            if (update instanceof Client.Tile) {
                Client.Tile tile = (Client.Tile)update;
                JSONObject t = new JSONObject();
                s(t, "type", "tile");
                s(t, "timestamp", tile.timestamp);
                s(t, "name", tile.name);
                a(u, "updates", t);
            }
        }

        byte[] bytes = u.toJSONString().getBytes();

        String dateStr = new Date().toString();
        response.fields.put(HttpField.Date, dateStr);
        response.fields.put(HttpField.ContentType, "text/plain");
        response.fields.put(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        response.fields.put(HttpField.LastModified, dateStr);
        response.fields.put(HttpField.ContentLength, Integer.toString(bytes.length));
        response.status = HttpStatus.OK;

        BufferedOutputStream out = null;
        out = new BufferedOutputStream(response.getBody());
        out.write(bytes);
        out.flush();
    }
}
