import java.io.*;
import java.net.*;
import java.util.*;
import org.bukkit.*;

import java.util.logging.Logger;

public class WebServer extends Thread {

	public static final String VERSION = "Huncraft";
	protected static final Logger log = Logger.getLogger("Minecraft");

	private ServerSocket sock = null;
	private boolean running = false;

	private MapManager mgr;

	public WebServer(int port, MapManager mgr) throws IOException
	{
		this.mgr = mgr;
		sock = new ServerSocket(port, 5, InetAddress.getByName("127.0.0.1"));
		running = true;
		start();
		log.info("map WebServer started on port " + port);
	}

	public void run()
	{
		while (running) {
			try {
				Socket socket = sock.accept();
				WebServerRequest requestThread = new WebServerRequest(socket, mgr);
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
