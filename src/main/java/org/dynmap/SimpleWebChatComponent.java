package org.dynmap;

public class SimpleWebChatComponent extends Component {

    public SimpleWebChatComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                plugin.getServer().broadcastMessage("[WEB]" + t.name + ": " + t.message);
            }
        });
    }

}
