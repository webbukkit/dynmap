package org.dynmap;

import java.io.IOException;
import java.io.Writer;

import org.bukkit.ChatColor;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerSet;
import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;

public class Client {
    public static class Update implements JSONAware, JSONStreamAware {
        public long timestamp = System.currentTimeMillis();

        @Override
        public String toJSONString() {
            return org.dynmap.web.Json.stringifyJson(this);
        }

        @Override
        public void writeJSONString(Writer w) throws IOException {
            // TODO: This isn't the best...
            w.write(toJSONString());
        }
    }

    public static class ChatMessage extends Update {
        public String type = "chat";
        public String source;
        public String playerName;
        public String message;
        public String account;
        public String channel;
        public ChatMessage(String source, String channel, String playerName, String message, String playeraccount) {
            this.source = source;
            this.playerName = ChatColor.stripColor(playerName);
            this.message = ChatColor.stripColor(message);
            this.account = playeraccount;
            this.channel = channel;
        }
    }

    public static class PlayerJoinMessage extends Update {
        public String type = "playerjoin";
        public String playerName;
        public String account;
        public PlayerJoinMessage(String playerName, String playeraccount) {
            this.playerName = ChatColor.stripColor(playerName);
            this.account = playeraccount;
        }
    }

    public static class PlayerQuitMessage extends Update {
        public String type = "playerquit";
        public String playerName;
        public String account;
        public PlayerQuitMessage(String playerName, String playeraccount) {
            this.playerName = ChatColor.stripColor(playerName);
            this.account = playeraccount;
        }
    }

    public static class Tile extends Update {
        public String type = "tile";
        public String name;

        public Tile(String name) {
            this.name = name;
        }
    }

    public static class DayNight extends Update {
        public String type = "daynight";
        public boolean isday;

        public DayNight(boolean isday) {
            this.isday = isday;
        }
    }

    public static class ComponentMessage extends Update {
        public String type = "component";
        /* Each subclass must provide 'ctype' string for component 'type' */
    }
}
