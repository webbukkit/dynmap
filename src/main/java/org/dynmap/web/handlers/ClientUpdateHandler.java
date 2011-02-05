package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Date;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.ChatQueue;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.TileUpdate;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;

public class ClientUpdateHandler implements HttpHandler {
    private MapManager mapManager;
    private PlayerList playerList;
    private World world;
    public ClientUpdateHandler(MapManager mapManager, PlayerList playerList, World world) {
        this.mapManager = mapManager;
        this.playerList = playerList;
        this.world = world;
    }
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
        int current = (int) (System.currentTimeMillis() / 1000);
        long cutoff = 0;

        if (path.length() > 0) {
            try {
                cutoff = ((long) Integer.parseInt(path)) * 1000;
            } catch (NumberFormatException e) {
            }
        }

        StringBuilder sb = new StringBuilder();
        long relativeTime = world.getTime() % 24000;
        sb.append(current + " " + relativeTime + "\n");

        Player[] players = playerList.getVisiblePlayers();
        for (Player player : players) {
            sb.append("player " + player.getName() + " " + player.getLocation().getX() + " " + player.getLocation().getY() + " " + player.getLocation().getZ() + "\n");
        }

        TileUpdate[] tileUpdates = mapManager.staleQueue.getTileUpdates(cutoff);
        for (TileUpdate tu : tileUpdates) {
            sb.append("tile " + tu.tile.getName() + "\n");
        }

        ChatQueue.ChatMessage[] messages = mapManager.chatQueue.getChatMessages(cutoff);
        for (ChatQueue.ChatMessage cu : messages) {
            sb.append("chat " + cu.playerName + " " + cu.message + "\n");
        }

        //debugger.debug("Sending " + players.length + " players, " + tileUpdates.length + " tile-updates, and " + messages.length + " chats. " + path + ";" + cutoff);

        byte[] bytes = sb.toString().getBytes();

        String dateStr = new Date().toString();
        response.fields.put("Date", dateStr);
        response.fields.put("Content-Type", "text/plain");
        response.fields.put("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        response.fields.put("Last-modified", dateStr);
        response.fields.put("Content-Length", Integer.toString(bytes.length));
        BufferedOutputStream out = new BufferedOutputStream(response.getBody());
        out.write(bytes);
        out.flush();
    }
}