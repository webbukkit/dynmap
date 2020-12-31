package org.dynmap;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.servlet.ClientUpdateServlet;
import org.dynmap.servlet.SendMessageServlet;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class InternalClientUpdateComponent extends ClientUpdateComponent {
    protected long jsonInterval;
    protected long currentTimestamp = 0;
    protected long lastTimestamp = 0;
    protected long lastChatTimestamp = 0;
    private long last_confighash;
    private ConcurrentHashMap<String, JSONObject> updates = new ConcurrentHashMap<String, JSONObject>();
    private JSONObject clientConfiguration = null;
    private static InternalClientUpdateComponent singleton;
    
    public InternalClientUpdateComponent(final DynmapCore dcore, final ConfigurationNode configuration) {
        super(dcore, configuration);
        dcore.addServlet("/up/world/*", new ClientUpdateServlet(dcore));

        if (dcore.isInternalWebServerDisabled) {
        	Log.severe("Using InternalClientUpdateComponent with disable-webserver=true is not supported: there will likely be problems");        	
        }
        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        final Boolean allowwebchat = configuration.getBoolean("allowwebchat", false);
        final Boolean hidewebchatip = configuration.getBoolean("hidewebchatip", false);
        final Boolean trust_client_name = configuration.getBoolean("trustclientname", false);
        final float webchatInterval = configuration.getFloat("webchat-interval", 1);
        final String spammessage = dcore.configuration.getString("spammessage", "You may only chat once every %interval% seconds.");
        final Boolean use_player_ip = configuration.getBoolean("use-player-login-ip", true);
        final Boolean req_player_ip = configuration.getBoolean("require-player-login-ip", false);
        final Boolean block_banned_player_chat = configuration.getBoolean("block-banned-player-chat", false);
        final Boolean req_login = configuration.getBoolean("webchat-requires-login", false);
        final Boolean chat_perm = configuration.getBoolean("webchat-permissions", false);
        final int length_limit = configuration.getInteger("chatlengthlimit", 256);
        final List<String> trustedproxy = dcore.configuration.getStrings("trusted-proxies", null);

        dcore.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", allowwebchat);
                s(t, "webchat-interval", webchatInterval);
                s(t, "webchat-requires-login", req_login);
                s(t, "chatlengthlimit", length_limit);
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
                this.require_login = req_login;
                this.chat_perms = chat_perm;
                this.lengthlimit = length_limit;
                this.core = dcore;
                if(trustedproxy != null) {
                    for(String s : trustedproxy) {
                        this.proxyaddress.add(s.trim());
                    }
                }
                else {
                    this.proxyaddress.add("127.0.0.1");
                    this.proxyaddress.add("0:0:0:0:0:0:0:1");
                }
                onMessageReceived.addListener(new Event.Listener<Message> () {
                    @Override
                    public void triggered(Message t) {
                        core.webChat(t.name, t.message);
                    }
                });
            }};
            dcore.addServlet("/up/sendmessage", messageHandler);
        }
        core.getServer().scheduleServerTask(new Runnable() {
            @Override
            public void run() {
                currentTimestamp = System.currentTimeMillis();
                if(last_confighash != core.getConfigHashcode()) {
                    writeConfiguration();
                }
                writeUpdates();
//                if (allowwebchat) {
//                    handleWebChat();
//                }
//                if(core.isLoginSupportEnabled())
//                    handleRegister();
                lastTimestamp = currentTimestamp;
                core.getServer().scheduleServerTask(this, jsonInterval/50);
            }}, jsonInterval/50);
        
        core.events.addListener("initialized", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });
        core.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });

        /* Initialize */
        writeConfiguration();
        writeUpdates();
        
        singleton = this;
    }
    @SuppressWarnings("unchecked")
    protected void writeUpdates() {
        if(core.mapManager == null) return;
        //Handles Updates
        for (DynmapWorld dynmapWorld : core.mapManager.getWorlds()) {
            JSONObject update = new JSONObject();
            update.put("timestamp", currentTimestamp);
            ClientUpdateEvent clientUpdate = new ClientUpdateEvent(currentTimestamp - 30000, dynmapWorld, update);
            clientUpdate.include_all_users = true;
            core.events.trigger("buildclientupdate", clientUpdate);

            updates.put(dynmapWorld.getName(), update);
        }
    }
    protected void writeConfiguration() {
        JSONObject clientConfiguration = new JSONObject();
        core.events.trigger("buildclientconfiguration", clientConfiguration);
        this.clientConfiguration = clientConfiguration;
        last_confighash = core.getConfigHashcode();
    }
    public static JSONObject getWorldUpdate(String wname) {
        if(singleton != null) {
            return singleton.updates.get(wname);
        }
        return null;
    }
    public static JSONObject getClientConfig() {
        if(singleton != null)
            return singleton.clientConfiguration;
        return null;
    }
}
