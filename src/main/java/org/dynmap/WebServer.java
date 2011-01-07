package org.dynmap;

import java.io.*;
import java.net.*;
import java.util.*;
import org.bukkit.*;
import org.dynmap.debug.Debugger;

import java.util.logging.Logger;

public class WebServer extends Thread {

	public static final String VERSION = "Huncraft";
	protected static final Logger log = Logger.getLogger("Minecraft");

	private Debugger debugger;
	
	private ServerSocket sock = null;
	private boolean running = false;

	private MapManager mgr;
	private Server server;

	public WebServer(int port, MapManager mgr, Server server, Debugger debugger) throws IOException
	{
		this.mgr = mgr;
		this.server = server;
		this.debugger = debugger;
		sock = new ServerSocket(port, 5, mgr.bindaddress.equals("0.0.0.0") ? null : InetAddress.getByName(mgr.bindaddress));
		running = true;
		start();
		log.info("map WebServer started on port " + port);
	}

	public void run()
	{
		while (running) {
			try {
				Socket socket = sock.accept();
				WebServerRequest requestThread = new WebServerRequest(socket, mgr, server, debugger);
				requestThread.start();
			}
			catch (IOException e) {
				log.info("map WebServer.run() stops with IOException");
				break;
			}
		}
		log.info("map WebServer run() exiting");
	}

	public void shutdown()
	{
		try {
			if(sock != null) {
				sock.close();
			}
		} catch(IOException e) {
			log.info("map stop() got IOException while closing socket");
		}
		running = false;
	}
}
