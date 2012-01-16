package org.dynmap;

import java.io.IOException;
import java.io.Writer;

import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;
import org.dynmap.common.DynmapChatColor;

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
            this.playerName = Client.stripColor(playerName);
            this.message = DynmapChatColor.stripColor(message);
            this.account = playeraccount;
            this.channel = channel;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof ChatMessage) {
                ChatMessage m = (ChatMessage)o;
                return m.source.equals(source) && m.playerName.equals(playerName) && m.message.equals(message);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return source.hashCode() ^ playerName.hashCode() ^ message.hashCode();
        }
    }

    public static class PlayerJoinMessage extends Update {
        public String type = "playerjoin";
        public String playerName;
        public String account;
        public PlayerJoinMessage(String playerName, String playeraccount) {
            this.playerName = Client.stripColor(playerName);
            this.account = playeraccount;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PlayerJoinMessage) {
                PlayerJoinMessage m = (PlayerJoinMessage)o;
                return m.playerName.equals(playerName);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return playerName.hashCode();
        }
    }

    public static class PlayerQuitMessage extends Update {
        public String type = "playerquit";
        public String playerName;
        public String account;
        public PlayerQuitMessage(String playerName, String playeraccount) {
            this.playerName = Client.stripColor(playerName);
            this.account = playeraccount;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PlayerQuitMessage) {
                PlayerQuitMessage m = (PlayerQuitMessage)o;
                return m.playerName.equals(playerName);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return playerName.hashCode();
        }
    }

    public static class Tile extends Update {
        public String type = "tile";
        public String name;

        public Tile(String name) {
            this.name = name;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof Tile) {
                Tile m = (Tile)o;
                return m.name.equals(name);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static class DayNight extends Update {
        public String type = "daynight";
        public boolean isday;

        public DayNight(boolean isday) {
            this.isday = isday;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof DayNight) {
                return true;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return 12345;
        }
    }

    public static class ComponentMessage extends Update {
        public String type = "component";
        /* Each subclass must provide 'ctype' string for component 'type' */
    }

    public static String stripColor(String s) {
        s = DynmapChatColor.stripColor(s);    /* Strip standard color encoding */
        /* Handle Essentials nickname encoding too */
        int idx = 0;
        while((idx = s.indexOf('&', idx)) >= 0) {
            char c = s.charAt(idx+1);   /* Get next character */
            if(c == '&') {  /* Another ampersand */
                s = s.substring(0, idx) + s.substring(idx+1);
            }
            else {
                s = s.substring(0, idx) + s.substring(idx+2);
            }
            idx++;
        }
        return s;
    }
}
