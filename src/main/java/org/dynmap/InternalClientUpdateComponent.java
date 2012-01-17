package org.dynmap;

import org.dynmap.servlet.ClientUpdateServlet;
import org.dynmap.servlet.SendMessageServlet;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class InternalClientUpdateComponent extends ClientUpdateComponent {

    public InternalClientUpdateComponent(final DynmapCore dcore, final ConfigurationNode configuration) {
        super(dcore, configuration);
        dcore.addServlet("/up/world/*", new ClientUpdateServlet(dcore));

        final Boolean allowwebchat = configuration.getBoolean("allowwebchat", false);
        final Boolean hidewebchatip = configuration.getBoolean("hidewebchatip", false);
        final Boolean trust_client_name = configuration.getBoolean("trustclientname", false);
        final float webchatInterval = configuration.getFloat("webchat-interval", 1);
        final String spammessage = dcore.configuration.getString("spammessage", "You may only chat once every %interval% seconds.");
        final Boolean use_player_ip = configuration.getBoolean("use-player-login-ip", true);
        final Boolean req_player_ip = configuration.getBoolean("require-player-login-ip", false);
        final Boolean block_banned_player_chat = configuration.getBoolean("block-banned-player-chat", false);

        dcore.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", allowwebchat);
                s(t, "webchat-interval", webchatInterval);
            }
        });

        if (allowwebchat) {
            @SuppressWarnings("serial")
            SendMessageServlet messageHandler = new SendMessageServlet() {{
                maximumMessageInterval = (int)(webchatInterval * 1000);
                spamMessage = "\""+spammessage+"\"";
                hideip = hidewebchatip;
                this.trustclientname = trust_client_name;
                this.use_player_login_ip = use_player_ip;
                this.require_player_login_ip = req_player_ip;
                this.check_user_ban = block_banned_player_chat;
                this.core = dcore;
                
                onMessageReceived.addListener(new Event.Listener<Message> () {
                    @Override
                    public void triggered(Message t) {
                        webChat(t.name, t.message);
                    }
                });
            }};
            dcore.addServlet("/up/sendmessage", messageHandler);
        }
    }

    protected void webChat(String name, String message) {
        if(core.mapManager == null)
            return;
        // TODO: Change null to something meaningful.
        core.mapManager.pushUpdate(new Client.ChatMessage("web", null, name, message, null));
        Log.info(unescapeString(core.configuration.getString("webprefix", "\u00A72[WEB] ")) + name + ": " + unescapeString(core.configuration.getString("websuffix", "\u00A7f")) + message);
                ChatEvent event = new ChatEvent("web", name, message);
        core.events.trigger("webchat", event);
    }
}
