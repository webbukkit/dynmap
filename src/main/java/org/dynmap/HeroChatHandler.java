package org.dynmap;

import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.event.CustomEventListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.config.Configuration;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import java.util.List;
import java.util.Collections;
import java.lang.reflect.Method;

public class HeroChatHandler {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private static final String DEF_CHANNEL = "Global";
    private static final List<String> DEF_CHANNELS = Collections
            .singletonList(DEF_CHANNEL);

    private List<String> hcchannels;
    private String hcwebinputchannel;
    private DynmapPlugin plugin;

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
        private static Class channelchatevent;
        private static Method getchannel;
        private static Method getsource;
        private static Method getmessage;
        private static Method iscancelled;
        private static boolean isgood = false;
        private Event evt;

        @SuppressWarnings("unchecked")
        public static boolean initialize() {
            try {
                channelchatevent = Class
                        .forName("com.herocraftonline.dthielke.herochat.event.ChannelChatEvent");
                getchannel = channelchatevent.getMethod("getChannel",
                        new Class[0]);
                getsource = channelchatevent.getMethod("getSource",
                        new Class[0]);
                getmessage = channelchatevent.getMethod("getMessage",
                        new Class[0]);
                iscancelled = channelchatevent.getMethod("isCancelled",
                        new Class[0]);
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
        private static Class channel;
        private static Method getname;
        private static boolean isgood = false;
        private Object chan;

        @SuppressWarnings("unchecked")
        public static boolean initialize() {
            try {
                channel = Class
                        .forName("com.herocraftonline.dthielke.herochat.channels.Channel");
                getname = channel.getMethod("getName", new Class[0]);
                isgood = true;
            } catch (ClassNotFoundException cnfx) {
            } catch (NoSuchMethodException nsmx) {
            }
            return isgood;
        }

        public HeroChatChannel(Object chan) {
            this.chan = chan;
        }

        public static boolean isInstance(Object obj) {
            return channel.isInstance(obj);
        }

        public String getName() {
            try {
                return (String) getname.invoke(chan);
            } catch (Exception x) {
                return null;
            }
        }
    }

    private class OurEventListener extends CustomEventListener {
        /**
         * Handle custom events
         */
        @Override
        public void onCustomEvent(Event event) {
            if (HeroChatChannelChatEvent.isInstance(event)) {
                HeroChatChannelChatEvent cce = new HeroChatChannelChatEvent(
                        event);
                if (cce.isCancelled())
                    return;
                HeroChatChannel c = cce.getChannel();
                if (hcchannels.contains(c.getName())) {
                    plugin.mapManager.pushUpdate(new Client.ChatMessage(
                            "player", "[" + c.getName() + "] "
                                    + cce.getSource(), cce.getMessage()));
                }
            }
        }
    }

    public HeroChatHandler(Configuration cfg, DynmapPlugin plugin, Server server) {
        /* If we're enabling hero chat support */
        if (cfg.getNode("web").getBoolean("enableherochat", false)) {
            log.info("[dynmap] HeroChat support configured");
            this.plugin = plugin;
            /* Now, get the monitored channel list */
            hcchannels = cfg.getNode("web").getStringList("herochatchannels",
                    DEF_CHANNELS);
            /* And get channel to send web messages */
            hcwebinputchannel = cfg.getNode("web").getString(
                    "herochatwebchannel", DEF_CHANNEL);
            /* Set up to hear when HeroChat is enabled */
            server.getPluginManager().registerEvent(Event.Type.PLUGIN_ENABLE,
                    new OurPluginListener(), Event.Priority.Normal, plugin);
        }
    }

    private void activateHeroChat(Plugin herochat) {
        if (HeroChatChannelChatEvent.initialize() == false) {
            log.severe("[dynmap] Cannot load HeroChat event class!");
            return;
        }
        if (HeroChatChannel.initialize() == false) {
            log.severe("[dynmap] Cannot load HeroChat channel class!");
            return;
        }
        /* Register event handler */
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT,
                new OurEventListener(), Event.Priority.Monitor, plugin);
        log.info("[dynmap] HeroChat integration active");
    }
}
