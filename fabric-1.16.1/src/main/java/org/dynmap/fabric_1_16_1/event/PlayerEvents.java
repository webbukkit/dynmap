package org.dynmap.fabric_1_16_1.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerEvents {
    private PlayerEvents() {
    }

    public static Event<PlayerLoggedIn> PLAYER_LOGGED_IN = EventFactory.createArrayBacked(PlayerLoggedIn.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedIn callback : listeners) {
                    callback.onPlayerLoggedIn(player);
                }
            }
    );

    public static Event<PlayerLoggedOut> PLAYER_LOGGED_OUT = EventFactory.createArrayBacked(PlayerLoggedOut.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedOut callback : listeners) {
                    callback.onPlayerLoggedOut(player);
                }
            }
    );

    public static Event<PlayerChangedDimension> PLAYER_CHANGED_DIMENSION = EventFactory.createArrayBacked(PlayerChangedDimension.class,
            (listeners) -> (player) -> {
                for (PlayerChangedDimension callback : listeners) {
                    callback.onPlayerChangedDimension(player);
                }
            }
    );

    public static Event<PlayerRespawn> PLAYER_RESPAWN = EventFactory.createArrayBacked(PlayerRespawn.class,
            (listeners) -> (player) -> {
                for (PlayerRespawn callback : listeners) {
                    callback.onPlayerRespawn(player);
                }
            }
    );

    @FunctionalInterface
    public interface PlayerLoggedIn {
        void onPlayerLoggedIn(ServerPlayerEntity player);
    }

    @FunctionalInterface
    public interface PlayerLoggedOut {
        void onPlayerLoggedOut(ServerPlayerEntity player);
    }

    @FunctionalInterface
    public interface PlayerChangedDimension {
        void onPlayerChangedDimension(ServerPlayerEntity player);
    }

    @FunctionalInterface
    public interface PlayerRespawn {
        void onPlayerRespawn(ServerPlayerEntity player);
    }
}
