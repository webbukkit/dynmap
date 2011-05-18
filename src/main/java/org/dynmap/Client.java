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

        public Player(String name, String world, double x, double y, double z) {
            this.name = ChatColor.stripColor(name);
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class PlayerHealth extends Player {
        public int health;

        public PlayerHealth(String name, String world, double x, double y, double z, int health) {
            super(name, world, x, y, z);
            this.health = health;
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

        public ChatMessage(String source, String playerName, String message) {
            this.source = source;
            this.playerName = ChatColor.stripColor(playerName);
            this.message = ChatColor.stripColor(message);
        }
    }

    public static class PlayerJoinMessage extends Stamped {
        public String type = "playerjoin";
        public String playerName;
        public PlayerJoinMessage(String playerName) {
            this.playerName = ChatColor.stripColor(playerName);
        }
    }

    public static class PlayerQuitMessage extends Stamped {
        public String type = "playerquit";
        public String playerName;
        public PlayerQuitMessage(String playerName) {
            this.playerName = ChatColor.stripColor(playerName);
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
