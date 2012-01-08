package org.dynmap;

import org.dynmap.Event.Listener;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.SendMessageHandler;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class InternalClientUpdateComponent extends ClientUpdateComponent {

    public InternalClientUpdateComponent(final DynmapPlugin plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        final boolean allowwebchat = configuration.getBoolean("allowwebchat", false);
        final boolean hidewebchatip = configuration.getBoolean("hidewebchatip", false);
        final boolean trust_client_name = configuration.getBoolean("trustclientname", false);
        final boolean useplayerloginip = configuration.getBoolean("use-player-login-ip", true);
        final boolean checkuserban = configuration.getBoolean("block-banned-player-chat", true);
        final boolean requireplayerloginip = configuration.getBoolean("require-player-login-ip", false);
        final float webchatInterval = configuration.getFloat("webchat-interval", 1);
        final String spammessage = plugin.configuration.getString("spammessage", "You may only chat once every %interval% seconds.");

        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", allowwebchat);
                s(t, "webchat-interval", webchatInterval);
            }
        });
        
        plugin.webServer.handlers.put("/up/", new ClientUpdateHandler(plugin));
        
        if (allowwebchat) {
            SendMessageHandler messageHandler = new SendMessageHandler() {{
                maximumMessageInterval = (int)(webchatInterval * 1000);
                spamMessage = "\""+spammessage+"\"";
                hideip = hidewebchatip;
                this.plug_in = plugin;
                this.trustclientname = trust_client_name;
                this.use_player_login_ip = useplayerloginip;
                this.require_player_login_ip = requireplayerloginip;
                this.check_user_ban = checkuserban;
                onMessageReceived.addListener(new Listener<SendMessageHandler.Message>() {
                    @Override
                    public void triggered(Message t) {
                        webChat(t.name, t.message);
                    }
                });
            }};

            plugin.webServer.handlers.put("/up/sendmessage", messageHandler);
        }
    }
    
    protected void webChat(String name, String message) {
        if(plugin.mapManager == null)
            return;
        // TODO: Change null to something meaningful.
        plugin.mapManager.pushUpdate(new Client.ChatMessage("web", null, name, message, null));
        Log.info(unescapeString(plugin.configuration.getString("webprefix", "\u00A72[WEB] ")) + name + ": " + unescapeString(plugin.configuration.getString("websuffix", "\u00A7f")) + message);
        ChatEvent event = new ChatEvent("web", name, message);
        plugin.events.trigger("webchat", event);
    }
}
