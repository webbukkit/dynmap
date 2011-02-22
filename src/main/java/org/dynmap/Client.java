package org.dynmap;

public class Client {
    public static class Update {
        public long timestamp;
        public long servertime;
        public Player[] players;
        public Object[] updates;
    }

    public static class Player {
        public String type = "player";
        public String name;
        public String world;
        public double x, y, z;

        public Player(String name, String world, double x, double y, double z) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Stamped {
        public long timestamp = System.currentTimeMillis();
    }
    
    public static class ChatMessage extends Stamped {
        public String type = "chat";
        public String playerName;
        public String message;

        public ChatMessage(String playerName, String message) {
            this.playerName = playerName;
            this.message = message;
        }
    }
    
    public static class WebChatMessage extends Stamped {
        public String type = "webchat";
        public String playerName;
        public String message;

        public WebChatMessage(String playerName, String message) {
            this.playerName = playerName;
            this.message = message;
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
