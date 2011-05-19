package org.dynmap.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.dynmap.Log;

public class HttpServer extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";

    private ServerSocket sock = null;
    private Thread listeningThread;

    private InetAddress bindAddress;
    private int port;

    public SortedMap<String, HttpHandler> handlers = new TreeMap<String, HttpHandler>(Collections.reverseOrder());

    public HttpServer(InetAddress bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public InetAddress getAddress() {
        return bindAddress;
    }
    
    public int getPort() {
        return port;
    }
    
    public void startServer() throws IOException {
        sock = new ServerSocket(port, 50, bindAddress); /* 5 too low - more than a couple users during render will get connect errors on some tile loads */
        listeningThread = this;
        start();
        Log.info("Dynmap WebServer started on " + bindAddress + ":" + port);
    }

    public void run() {
        try {
            while (listeningThread == Thread.currentThread()) {
                try {
                    Socket socket = sock.accept();
                    HttpServerConnection requestThread = new HttpServerConnection(socket, this);
                    requestThread.start();
                } catch (IOException e) {
                    Log.info("map WebServer.run() stops with IOException");
                    break;
                }
            }
            Log.info("Webserver shut down.");
        } catch (Exception ex) {
            Log.severe("Exception on WebServer-thread", ex);
        }
    }

    public void shutdown() {
        Log.info("Shutting down webserver...");
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException e) {
            Log.warning("Exception while closing socket for webserver shutdown", e);
        }
        listeningThread = null;
    }
}
