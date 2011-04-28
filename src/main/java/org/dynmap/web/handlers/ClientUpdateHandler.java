package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.Client;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.HttpStatus;
import org.dynmap.web.Json;

public class ClientUpdateHandler implements HttpHandler {
    private MapManager mapManager;
    private PlayerList playerList;
    private Server server;

    public ClientUpdateHandler(MapManager mapManager, PlayerList playerList, Server server) {
        this.mapManager = mapManager;
        this.playerList = playerList;
        this.server = server;
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
        
        World world = server.getWorld(worldName);
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
        
        Client.Update update = new Client.Update();
        update.timestamp = current;
        update.servertime = world.getTime() % 24000;
        
        
        Player[] players = playerList.getVisiblePlayers();
        update.players = new Client.Player[players.length];
        for(int i=0;i<players.length;i++) {
            Player p = players[i];
            Location pl = p.getLocation();
            update.players[i] = new Client.Player(p.getName(), pl.getWorld().getName(), pl.getX(), pl.getY(), pl.getZ());
        }
        
        update.updates = mapManager.getWorldUpdates(worldName, since);
        
        
        byte[] bytes = Json.stringifyJson(update).getBytes();

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