package org.dynmap.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.dynmap.Log;

public class HttpServer extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private ServerSocket sock = null;
    private Thread listeningThread;

    private InetAddress bindAddress;
    private int port;
    private boolean check_banned_ips;
    private int max_sessions;

    public SortedMap<String, HttpHandler> handlers = new TreeMap<String, HttpHandler>(Collections.reverseOrder());
    
    private Object lock = new Object();
    private HashSet<HttpServerConnection> active_connections = new HashSet<HttpServerConnection>();
    private HashSet<HttpServerConnection> keepalive_connections = new HashSet<HttpServerConnection>();

    public HttpServer(InetAddress bindAddress, int port, boolean check_banned_ips, int max_sessions) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.check_banned_ips = check_banned_ips;
        this.max_sessions = max_sessions;
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
            ServerSocket s = sock;
            while (listeningThread == Thread.currentThread()) {
                try {
                    Socket socket = s.accept();
                    if(checkForBannedIp(socket.getRemoteSocketAddress())) {
                    	try { socket.close(); } catch (IOException iox) {}
                    	socket = null;
                    }

                    HttpServerConnection requestThread = new HttpServerConnection(socket, this);
                    synchronized(lock) {
                        active_connections.add(requestThread);
                        requestThread.start();
                        /* If we're at limit, wait here until we're free to accept another */
                        while((listeningThread == Thread.currentThread()) &&
                                (active_connections.size() >= max_sessions)) {
                            lock.wait(500);
                        }
                    }
                } catch (IOException e) {
                    if(listeningThread != null) /* Only report this if we didn't initiate the shutdown */
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
        listeningThread = null;
        try {
            if (sock != null) {
                sock.close();
                sock = null;
            }
            /* And kill off the active connections */
            HashSet<HttpServerConnection> sc;
            synchronized(lock) {
                sc = new HashSet<HttpServerConnection>(active_connections);
            }
            for(HttpServerConnection c : sc) {
                c.shutdownConnection();
            }
        } catch (IOException e) {
            Log.warning("Exception while closing socket for webserver shutdown", e);
        }
    }

    public boolean canKeepAlive(HttpServerConnection c) {
        synchronized(lock) {
            /* If less than half of our limit are keep-alive, approve */
            if(keepalive_connections.size() < (max_sessions/2)) {
                keepalive_connections.add(c);
                return true;
            }
        }
        return false;
    }
    
    public void connectionEnded(HttpServerConnection c) {
        synchronized(lock) {
            active_connections.remove(c);
            keepalive_connections.remove(c);
            lock.notifyAll();
        }
    }
    
    private HashSet<String> banned_ips = new HashSet<String>();
    private HashSet<String> banned_ips_notified = new HashSet<String>();
    private long last_loaded = 0;
    private long lastmod = 0;
    private static final long BANNED_RELOAD_INTERVAL = 15000;	/* Every 15 seconds */
    
    private void loadBannedIPs() {
    	banned_ips.clear();
    	banned_ips_notified.clear();
    	File f = new File("banned-ips.txt");
    	if(f.exists() == false)
    		return;
    	if(f.lastModified() == lastmod) {
    		return;
    	}
    	lastmod = f.lastModified();
    	BufferedReader rdr = null;
    	try {
    		rdr = new BufferedReader(new FileReader(f));
    		String line;
    		while((line = rdr.readLine()) != null) {
    			line = line.trim().toLowerCase();	/* Trim it and case normalize it */
    			if((line.length() == 0) || (line.charAt(0) == '#')) {	/* Blank or comment? */
    				continue;
    			}
    			banned_ips.add(line);
    		}
    	} catch (IOException iox) {
    		Log.severe("Error reading banned-ips.txt!");
    	} finally {
    		if(rdr != null) {
    			try { rdr.close(); } catch (IOException iox) {}
    			rdr = null;
    		}
    	}
    }
    /* Return true if address is banned */
    public boolean checkForBannedIp(SocketAddress socketAddress) {
    	if(!check_banned_ips)
    		return false;
    	
    	long t = System.currentTimeMillis();
    	if((t < last_loaded) || ((t-last_loaded) > BANNED_RELOAD_INTERVAL)) {
    		loadBannedIPs();
    		last_loaded = t;
    	}
    	/* Follow same technique as MC uses - toString the SocketAddress and clip out string between "/" and ":" */
    	String ip = socketAddress.toString();
    	ip = ip.substring(ip.indexOf("/") + 1);
    	ip = ip.substring(0, ip.indexOf(":"));
    	if(banned_ips.contains(ip)) {
    		if(banned_ips_notified.contains(ip) == false) {
    			Log.info("Rejected connection by banned IP address - " + socketAddress.toString());
    			banned_ips_notified.add(ip);
    		}
    		return true;
    	}
    	return false;
    }
}
