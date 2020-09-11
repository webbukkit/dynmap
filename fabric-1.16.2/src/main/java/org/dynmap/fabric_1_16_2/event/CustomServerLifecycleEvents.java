package org.dynmap.fabric_1_16_2.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class CustomServerLifecycleEvents {
    public static final Event<ServerLifecycleEvents.ServerStarted> SERVER_STARTED_PRE_WORLD_LOAD =
            EventFactory.createArrayBacked(ServerLifecycleEvents.ServerStarted.class, (callbacks) -> (server) -> {
                for (ServerLifecycleEvents.ServerStarted callback : callbacks) {
                    callback.onServerStarted(server);
                }
            });
}
