package org.dynmap;

import java.util.ArrayList;
import java.util.LinkedList;

import org.bukkit.event.player.PlayerChatEvent;

public class ChatQueue {

    public class ChatMessage {
        public long time;
        public String playerName;
        public String message;

        public ChatMessage(PlayerChatEvent event) {
            time = System.currentTimeMillis();
            playerName = event.getPlayer().getName();
            message = event.getMessage();
        }
    }

    /* a list of recent chat message */
    private LinkedList<ChatMessage> messageQueue;

    /* remember up to this old chat messages (ms) */
    private static final int maxChatAge = 120000;

    public ChatQueue() {
        messageQueue = new LinkedList<ChatMessage>();
    }

    /* put a chat message in the queue */
    public void pushChatMessage(PlayerChatEvent event) {
        synchronized (MapManager.lock) {
            messageQueue.add(new ChatMessage(event));
        }
    }

    public ChatMessage[] getChatMessages(long cutoff) {

        ArrayList<ChatMessage> queue = new ArrayList<ChatMessage>();
        ArrayList<ChatMessage> updateList = new ArrayList<ChatMessage>();
        queue.addAll(messageQueue);

        long now = System.currentTimeMillis();
        long deadline = now - maxChatAge;

        synchronized (MapManager.lock) {

            for (ChatMessage message : queue) {
                if (message.time < deadline) {
                    messageQueue.remove(message);
                } else if (message.time >= cutoff) {
                    updateList.add(message);
                }
            }
        }
        ChatMessage[] messages = new ChatMessage[updateList.size()];
        updateList.toArray(messages);
        return messages;
    }

}
