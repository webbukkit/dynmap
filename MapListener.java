import java.util.logging.Logger;

public class MapListener extends PluginListener {
	private static final Logger log = Logger.getLogger("Minecraft");
	private MapManager mgr;

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

		return false;
	}
}
