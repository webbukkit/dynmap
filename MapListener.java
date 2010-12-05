import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

public class MapListener extends PluginListener {
	private static final Logger log = Logger.getLogger("Minecraft");
	private MapManager mgr;
	private ArrayList<MapMarker> markers;

	public MapListener(MapManager mgr)
	{
		this.mgr = mgr;
	}

	@Override
	public boolean onBlockCreate(Player player, Block blockPlaced, Block blockClicked, int itemInHand)
	{
		if(mgr.touch(blockPlaced.getX(), blockPlaced.getY(), blockPlaced.getZ()))
			mgr.debug(player.getName() + " touch " + blockPlaced.getX() + "," + blockPlaced.getY() + "," + blockPlaced.getZ() + " from onBlockCreate");
		return false;
	}

	@Override
	public boolean onBlockDestroy(Player player, Block block)
	{
		int x = block.getX();
		int y = block.getY();
		int z = block.getZ();
		if(x == 0 && y == 0 && z == 0)
			return false;

		if(mgr.touch(x, y, z))
			mgr.debug(player.getName() + " touch " + x + "," + y + "," + z + " from onBlockBreak");

		return false;
	}

	@Override
	public boolean onCommand(Player player, String[] split)
	{
		if(!player.canUseCommand(split[0]))
			return false;

		if(split[0].equals("/map_wait")) {
			if(split.length < 2) {
				mgr.renderWait = 1000;
			} else {
				try {
					mgr.renderWait = Integer.parseInt(split[1]);
				} catch(NumberFormatException e) {
					player.sendMessage(Colors.Rose + "Invalid number");
				}
			}
			return true;
		}

		if(split[0].equals("/map_regen")) {
			mgr.regenerate((int) player.getX(), (int) player.getY(), (int) player.getZ());
			player.sendMessage(Colors.Rose + "Map regeneration in progress");
			return true;
		}

		if(split[0].equals("/map_stat")) {
			player.sendMessage(Colors.Rose + "Stale tiles: " + mgr.getStaleCount() + " Recent updates: " + mgr.getRecentUpdateCount());
			return true;
		}

		if(split[0].equals("/map_debug")) {
			mgr.debugPlayer = player.getName();
			return true;
		}

		if(split[0].equals("/map_nodebug")) {
			mgr.debugPlayer = null;
			return true;
		}
		
		if(split[0].equals("/addmarker")) {
			if(split.length < 2)
			{
				player.sendMessage("Map> " + Colors.Red + "Usage: /map_addmarker [name]");
			}
			else
			{
				if (mgr.addMapMarker(player, split[1], player.getX(), player.getY(), player.getZ()))
				{
					player.sendMessage("Map> " + Colors.White + "Marker \"" + split[1] + "\" added successfully");
				}
			}
			return true;
		}
		
		if(split[0].equals("/removemarker")) {
			if(split.length < 2)
			{
				player.sendMessage("Map> " + Colors.Red + "Usage: /map_removemarker [name]");
			}
			else
			{
				if (mgr.removeMapMarker(player, split[1]))
				{
					player.sendMessage("Map> " + Colors.White + "Marker \"" + split[1] + "\" removed successfully");
				}
			}
			return true;
		}
		
		if(split[0].equals("/listmarkers")) {
			String msg = "";
			Collection<MapMarker> values = mgr.markers.values();
	    	Iterator<MapMarker> it = values.iterator();
	    	while(it.hasNext())
	    	{
	    		MapMarker marker = it.next();
	    		String line = " - " + marker.name + " (" + marker.owner + ")\t";
	    		msg += line;
	    	}
	    	player.sendMessage("" + Colors.White + msg);
			return true;
		}
		
		if(split[0].equals("/tpmarker")) {
			if(split.length < 2)
			{
				player.sendMessage("Map> " + Colors.Red + "Usage: /map_tpmarker [name]");
			}
			else
			{
				if (mgr.teleportToMapMarker(player, split[1]))
				{
					//player.sendMessage("Map> " + Colors.White + "");
				}
			}
			return true;
		}

		return false;
	}
}
