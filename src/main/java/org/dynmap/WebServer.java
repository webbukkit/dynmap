package org.dynmap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.dynmap.debug.Debugger;

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
		try {
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
		} catch (Exception ex) {
			debugger.error("Exception on WebServer-thread: " + ex.toString());
		}
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
