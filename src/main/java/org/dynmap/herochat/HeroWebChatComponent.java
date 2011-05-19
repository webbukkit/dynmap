package org.dynmap.herochat;

import org.dynmap.ChatEvent;
import org.dynmap.Component;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Event;

public class HeroWebChatComponent extends Component {
    HeroChatHandler handler;
    public HeroWebChatComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        handler = new HeroChatHandler(configuration, plugin, plugin.getServer());
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                /* Let HeroChat take a look - only broadcast to players if it doesn't handle it */
                if (!handler.sendWebMessageToHeroChat(t.name, t.message)) {
                    plugin.getServer().broadcastMessage("[WEB]" + t.name + ": " + t.message);
                }
            }
        });
    }

}
