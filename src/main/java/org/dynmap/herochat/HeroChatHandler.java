package org.dynmap.herochat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.bukkit.Server;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;
import org.dynmap.Client;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;

public class HeroChatHandler {
    private static final String DEF_CHANNEL = "Global";
    private static final List<String> DEF_CHANNELS = Collections
            .singletonList(DEF_CHANNEL);

    private List<String> hcchannels;
    private String hcwebinputchannel;
    private DynmapPlugin plugin;
    private HeroChatChannel hcwebinputchan;

    private class OurPluginListener extends ServerListener {
        @Override
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin plugin = event.getPlugin();
            String name = plugin.getDescription().getName();

            if (name.equals("HeroChat")) {
                activateHeroChat(plugin);
            }
        }
    }

    /* Reflection-based access wrapper for ChannelChatEvent from HeroChat */
    private static class HeroChatChannelChatEvent {
        @SuppressWarnings("rawtypes")
        private static Class channelchatevent;
        private static Method getsource;
        private static Method getmessage;
        private static Method issentbyplayer;
        private static boolean isgood = false;
        private Event evt;

        @SuppressWarnings("unchecked")
        public static boolean initialize() {
            try {
                channelchatevent = Class
                        .forName("com.herocraftonline.dthielke.herochat.event.ChannelChatEvent");
                getsource = channelchatevent.getMethod("getSource", new Class[0]);
                getmessage = channelchatevent.getMethod("getMessage", new Class[0]);
                issentbyplayer = channelchatevent.getMethod("isSentByPlayer", new Class[0]);
                isgood = true;
            } catch (ClassNotFoundException cnfx) {
            } catch (NoSuchMethodException nsmx) {
            }
            return isgood;
        }

        public HeroChatChannelChatEvent(Event evt) {
            this.evt = evt;
        }

        public static boolean isInstance(Event evt) {
            return channelchatevent.isInstance(evt);
        }

        public String getSource() {
            try {
                return (String) getsource.invoke(evt);
            } catch (Exception x) {
                return null;
            }
        }

        public String getMessage() {
            try {
                return (String) getmessage.invoke(evt);
            } catch (Exception x) {
                return null;
            }
        }
        
        public boolean isSentByPlayer() {
            try {
                return (Boolean) issentbyplayer.invoke(evt);
            } catch (Exception x) {
                return true;
            }
        }
    }

    /* Reflection-based access wrapper for ChannelEvent from HeroChat */
    private static class HeroChatChannelEvent {
        @SuppressWarnings("rawtypes")
        private static Class channelevent;
        private static Method getchannel;
        private static Method iscancelled;
        private static boolean isgood = false;
        private Event evt;

        @SuppressWarnings("unchecked")
        public static boolean initialize() {
            try {
                channelevent = Class
                        .forName("com.herocraftonline.dthielke.herochat.event.ChannelEvent");
                getchannel = channelevent.getMethod("getChannel", new Class[0]);
                iscancelled = channelevent.getMethod("isCancelled", new Class[0]);
                isgood = true;
            } catch (ClassNotFoundException cnfx) {
            } catch (NoSuchMethodException nsmx) {
            }
            return isgood;
        }

        public HeroChatChannelEvent(Event evt) {
            this.evt = evt;
        }

        public static boolean isInstance(Event evt) {
            return channelevent.isInstance(evt);
        }

        public HeroChatChannel getChannel() {
            try {
                Object o;
                o = getchannel.invoke(evt);
                if (o != null) {
                    return new HeroChatChannel(o);
                }
            } catch (Exception x) {
            }
            return null;
        }

        public boolean isCancelled() {
            try {
                return (Boolean) iscancelled.invoke(evt);
            } catch (Exception x) {
                return true;
            }
        }
    }

    /* Reflection-based access wrapper for Channel from HeroChat */
    private static class HeroChatChannel {
        @SuppressWarnings("rawtypes")
        private static Class channel;
        private static Method getname;
        private static Method getnick;
        private static Method sendmessage;
        private static boolean isgood = false;
        private Object chan;

        @SuppressWarnings("unchecked")
        public static boolean initialize() {
            try {
                channel = Class
                        .forName("com.herocraftonline.dthielke.herochat.channels.Channel");
                getname = channel.getMethod("getName");
                getnick = channel.getMethod("getNick", new Class[0]);
                sendmessage = channel.getMethod("sendMessage", new Class[] {
                    String.class, String.class, String.class, boolean.class } );
                isgood = true;
            } catch (ClassNotFoundException cnfx) {
            } catch (NoSuchMethodException nsmx) {
                Log.severe(nsmx);
            }
            return isgood;
        }

        public HeroChatChannel(Object chan) {
            this.chan = chan;
        }

        public String getName() {
            try {
                return (String) getname.invoke(chan);
            } catch (Exception x) {
                return null;
            }
        }

        public String getNick() {
            try {
                return (String) getnick.invoke(chan);
            } catch (Exception x) {
                return null;
            }
        }

        public void sendMessage(String source, String msg, String format, boolean sentByPlayer) {
            try {
                sendmessage.invoke(chan, source, msg, format, sentByPlayer);
            } catch (Exception x) {
            }
        }
    }

    private class OurEventListener extends CustomEventListener {
        /**
         * Handle custom events
         */
        @Override
        public void onCustomEvent(Event event) {
            if (HeroChatChannelEvent.isInstance(event)) {
                HeroChatChannelEvent ce = new HeroChatChannelEvent(event);
                /* Snoop for our web channel - we'll need it, and we'll see it before it matters,
                 * since anyone that joins the channel will give us an event (and reflection on
                 * the plugin class to get the manager didn't work, due to a dependency on the IRC
                 * plugin that may not be present....)
                 */
                HeroChatChannel c = ce.getChannel();
                if (ce.isCancelled())
                    return;
                if((hcwebinputchannel != null) && ((hcwebinputchannel.equals(c.getName())) ||
                        (hcwebinputchannel.equals(c.getNick())))) {
                    hcwebinputchan = c;
                }                
                if (HeroChatChannelChatEvent.isInstance(event)) {
                    HeroChatChannelChatEvent cce = new HeroChatChannelChatEvent(
                        event);
                    /* Match on name or nickname of channel */
                    if (hcchannels.contains(c.getName()) ||
                            hcchannels.contains(c.getNick())) {
                        if(cce.isSentByPlayer()) {  /* Player message? */
                            org.bukkit.entity.Player p = plugin.getServer().getPlayer(cce.getSource());
                            if((p != null) && (plugin.mapManager != null)) {
                                plugin.mapManager.pushUpdate(new Client.ChatMessage("player", 
                                                                                    c.getNick(),
                                                                                    p.getDisplayName(),
                                                                                    cce.getMessage(),
                                                                                    p.getName()));
                            }
                        }
                    }
                }
            }
        }
    }

    public HeroChatHandler(ConfigurationNode cfg, DynmapPlugin plugin, Server server) {
        /* If we're enabling hero chat support */
        Log.verboseinfo("HeroChat support configured");
        this.plugin = plugin;
        /* Now, get the monitored channel list */
        hcchannels = cfg.getStrings("herochatchannels", DEF_CHANNELS);
        /* And get channel to send web messages */
        hcwebinputchannel = cfg.getString("herochatwebchannel", DEF_CHANNEL);
        Plugin hc = server.getPluginManager().getPlugin("HeroChat");
        if(hc != null) {
            activateHeroChat(hc);
        }
        else {
            /* Set up to hear when HeroChat is enabled */
            server.getPluginManager().registerEvent(Event.Type.PLUGIN_ENABLE,
                new OurPluginListener(), Event.Priority.Normal, plugin);
        }
    }

    private void activateHeroChat(Plugin herochat) {
        if (HeroChatChannelChatEvent.initialize() == false) {
            Log.severe("Cannot load HeroChat chat event class!");
            return;
        }
        if (HeroChatChannel.initialize() == false) {
            Log.severe("Cannot load HeroChat channel class!");
            return;
        }
        if (HeroChatChannelEvent.initialize() == false) {
            Log.severe("Cannot load HeroChat channel event class!");
            return;
        }
        /* Register event handler */
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT,
                new OurEventListener(), Event.Priority.Monitor, plugin);
        Log.verboseinfo("HeroChat integration active");
    }
    /**
     * Send message from web to appropriate HeroChat channel
     * @param sender - sender ID
     * @param message - message
     * @return true if herochat is handling this, false if not
     */
    public boolean sendWebMessageToHeroChat(String sender, String message) {
        if(hcwebinputchannel != null) { /* Are we handling them? */
            if(hcwebinputchan != null) {    /* Have we seen it yet?  Maybe no if nobody has logged on or
                                             * joined it, but then who would see it anyway?
                                             */
                hcwebinputchan.sendMessage(sender, message, "{default}", false);
            }
            return true;
        }
        return false;
    }
}
