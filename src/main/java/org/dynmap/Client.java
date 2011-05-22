package org.dynmap;

import org.bukkit.ChatColor;

public class Client {
    public static class Update {
        public long timestamp;
        public long servertime;
        public boolean hasStorm;
        public boolean isThundering;
        public Player[] players;
        public Object[] updates;
    }

    public static class Player {
        public String type = "player";
        public String name;
        public String world;
        public double x, y, z;
        public int health;
        public int armor;
        public String account;

        public Player(String name, String world, double x, double y, double z, int health, int armor, String account) {
            this.name = ChatColor.stripColor(name);
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
            this.armor = armor;
            this.account = account;
        }
    }

    public static class Stamped {
        public long timestamp = System.currentTimeMillis();
    }

    public static class ChatMessage extends Stamped {
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

    public static class PlayerJoinMessage extends Stamped {
        public String type = "playerjoin";
        public String playerName;
        public String account;
        public PlayerJoinMessage(String playerName, String playeraccount) {
            this.playerName = ChatColor.stripColor(playerName);
            this.account = playeraccount;
        }
    }

    public static class PlayerQuitMessage extends Stamped {
        public String type = "playerquit";
        public String playerName;
        public String account;
        public PlayerQuitMessage(String playerName, String playeraccount) {
            this.playerName = ChatColor.stripColor(playerName);
            this.account = playeraccount;
        }
    }

    public static class Tile extends Stamped {
        public String type = "tile";
        public String name;

        public Tile(String name) {
            this.name = name;
        }
    }
}
