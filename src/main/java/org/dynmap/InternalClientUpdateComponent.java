package org.dynmap;

import org.dynmap.web.handlers.ClientUpdateHandler;

public class InternalClientUpdateComponent extends ClientUpdateComponent {

    public InternalClientUpdateComponent(DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.webServer.handlers.put("/up/", new ClientUpdateHandler(plugin));
    }

}
