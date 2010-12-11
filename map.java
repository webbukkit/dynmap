import java.util.logging.Logger;
import java.io.IOException;

public class map extends Plugin {

	protected static final Logger log = Logger.getLogger("Minecraft");

	private WebServer server = null;
	private MapManager mgr = null;
	private MapListener listener = null;

	@Override
	public void enable() {
		log.info("Map INIT");

		mgr = new MapManager();
		mgr.startManager();

		try {
			server = new WebServer(mgr.serverport, mgr);
		} catch(IOException e) {
			log.info("position failed to start WebServer (IOException)");
		}

		listener = new MapListener(mgr);
	}

	@Override
	public void disable() {
		log.info("Map UNINIT");

		mgr.stopManager();

		if(server != null) {
			server.shutdown();
			server = null;
		}
	}

	@Override
	public void initialize() {
		etc.getLoader().addListener(PluginLoader.Hook.COMMAND, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.BLOCK_CREATED, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.BLOCK_DESTROYED, listener, this, PluginListener.Priority.MEDIUM);

		etc.getInstance().addCommand("/map_wait", " [wait] - set wait between tile renders (ms)");
		etc.getInstance().addCommand("/map_stat", " - query number of tiles in render queue");
		etc.getInstance().addCommand("/map_regen", " - regenerate entire map");
		etc.getInstance().addCommand("/map_debug", " - send map debugging messages");
		etc.getInstance().addCommand("/map_nodebug", " - disable map debugging messages");
		etc.getInstance().addCommand("/addsign", " [name] - adds a named sign to the map");
		etc.getInstance().addCommand("/removesign", " [name] - removes a named sign to the map");
		etc.getInstance().addCommand("/listsigns", " - list all named signs");
		etc.getInstance().addCommand("/tpsign", " [name] - teleport to a named sign");
	}
}
