package org.dynmap.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private ServerSocket sock = null;
    private boolean running = false;

    private InetAddress bindAddress;
    private int port;

    public SortedMap<String, HttpHandler> handlers = new TreeMap<String, HttpHandler>(Collections.reverseOrder());

    public HttpServer(InetAddress bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public void startServer() throws IOException {
        sock = new ServerSocket(port, 5, bindAddress);
        running = true;
        start();
        log.info("Dynmap WebServer started on " + bindAddress + ":" + port);
    }

    public void run() {
        try {
            while (running) {
                try {
                    Socket socket = sock.accept();
                    HttpServerConnection requestThread = new HttpServerConnection(socket, this);
                    requestThread.start();
                } catch (IOException e) {
                    log.info("map WebServer.run() stops with IOException");
                    break;
                }
            }
            log.info("map WebServer run() exiting");
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Exception on WebServer-thread", ex);
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
