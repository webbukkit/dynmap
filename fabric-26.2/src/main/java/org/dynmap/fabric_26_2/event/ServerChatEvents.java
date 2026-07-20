package org.dynmap.fabric_26_2.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public class ServerChatEvents {
    private ServerChatEvents() {
    }

    public static Event<ServerChatCallback> EVENT = EventFactory.createArrayBacked(ServerChatCallback.class,
            (listeners) -> (player, message) -> {
                for (ServerChatCallback callback : listeners) {
                    callback.onChatMessage(player, message);
                }
            }
    );

    @FunctionalInterface
    public interface ServerChatCallback {
        void onChatMessage(ServerPlayer player, String message);
    }
}
