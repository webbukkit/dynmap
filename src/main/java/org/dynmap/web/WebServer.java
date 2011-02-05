package org.dynmap.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.debug.Debugger;

public class WebServer extends Thread {

    public static final String VERSION = "Huncraft";
    protected static final Logger log = Logger.getLogger("Minecraft");

    private Debugger debugger;

    private ServerSocket sock = null;
    private boolean running = false;

    private MapManager mgr;
    private World world;
    private PlayerList playerList;
    private ConfigurationNode configuration;

    public WebServer(MapManager mgr, World world, PlayerList playerList, Debugger debugger, ConfigurationNode configuration) throws IOException {
        this.mgr = mgr;
        this.world = world;
        this.playerList = playerList;
        this.configuration = configuration;
        this.debugger = debugger;

        String bindAddress = configuration.getString("webserver-bindaddress", "0.0.0.0");
        int port = configuration.getInt("webserver-port", 8123);

        sock = new ServerSocket(port, 5, bindAddress.equals("0.0.0.0")
                ? null
                : InetAddress.getByName(bindAddress));
        running = true;
        start();
        log.info("Dynmap WebServer started on " + bindAddress + ":" + port);
    }

    public void run() {
        try {
            while (running) {
                try {
                    Socket socket = sock.accept();
                    WebServerRequest requestThread = new WebServerRequest(socket, mgr, world, playerList, configuration, debugger);
                    requestThread.start();
                } catch (IOException e) {
                    log.info("map WebServer.run() stops with IOException");
                    break;
                }
            }
            log.info("map WebServer run() exiting");
        } catch (Exception ex) {
            debugger.error("Exception on WebServer-thread: " + ex.toString());
        }
    }

    public void shutdown() {
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException e) {
            log.info("map stop() got IOException while closing socket");
        }
        running = false;
    }
}
