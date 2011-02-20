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

    public static class ChatMessage {
        public String type = "chat";
        public String playerName;
        public String message;
		public long timestamp;

        public ChatMessage(String playerName, String message, long timestamp) {
            this.playerName = playerName;
            this.message = message;
			this.timestamp = timestamp;
        }
    }

    public static class Tile {
        public String type = "tile";
        public String name;
		public long timestamp;

        public Tile(String name, long timestamp) {
            this.name = name;
			this.timestamp = timestamp;
        }
    }
}
