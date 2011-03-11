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
    private Thread listeningThread;

    private InetAddress bindAddress;
    private int port;

    public SortedMap<String, HttpHandler> handlers = new TreeMap<String, HttpHandler>(Collections.reverseOrder());

    public HttpServer(InetAddress bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public void startServer() throws IOException {
        sock = new ServerSocket(port, 5, bindAddress);
        listeningThread = this;
        start();
        log.info("Dynmap WebServer started on " + bindAddress + ":" + port);
    }

    public void run() {
        try {
            while (listeningThread == Thread.currentThread()) {
                try {
                    Socket socket = sock.accept();
                    HttpServerConnection requestThread = new HttpServerConnection(socket, this);
                    requestThread.start();
                } catch (IOException e) {
                    log.info("map WebServer.run() stops with IOException");
                    break;
                }
            }
            log.info("Webserver shut down.");
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Exception on WebServer-thread", ex);
        }
    }

    public void shutdown() {
        log.info("Shutting down webserver...");
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException e) {
            log.log(Level.INFO, "Exception while closing socket for webserver shutdown", e);
        }
        listeningThread = null;
    }
}
