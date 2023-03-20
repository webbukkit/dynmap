package org.dynmap.fabric_1_19_4.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

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
        void onChatMessage(ServerPlayerEntity player, String message);
    }
}