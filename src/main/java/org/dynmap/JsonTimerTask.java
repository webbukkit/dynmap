package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.TimerTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.dynmap.web.Json;

class JsonTimerTask extends TimerTask
{
  private final DynmapPlugin plugin;
  private Server server;
  private PlayerList playerList;
  private MapManager mapManager;
  private Configuration configuration;

  public JsonTimerTask(DynmapPlugin instance, Configuration config)
  {
    this.plugin = instance;
    this.server = this.plugin.getServer();
    this.playerList = new PlayerList(this.server);
    this.mapManager = this.plugin.getMapManager();
    this.configuration = config;
  }

  public void run() {
    long current = System.currentTimeMillis();

    Client.Update update = new Client.Update();
    update.timestamp = current;
    update.servertime = ((World)this.server.getWorlds().get(0)).getTime();

    Player[] players = this.playerList.getVisiblePlayers();
    update.players = new Client.Player[players.length];
    for (int i = 0; i < players.length; i++) {
      Player p = players[i];
      update.players[i] = new Client.Player(p.getName(), p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
    }
    update.updates = this.mapManager.updateQueue.getUpdatedObjects(current - 10L);

    File webpath = new File(this.configuration.getString("webpath", "web"), "dynmap.json");
    File outputFile;
    if (webpath.isAbsolute())
      outputFile = webpath;
    else {
      outputFile = new File(DynmapPlugin.dataRoot, webpath.toString());
    }
    try
    {
      FileOutputStream fos = new FileOutputStream(outputFile);
      fos.write(Json.stringifyJson(update).getBytes());
      fos.close();
    }
    catch (FileNotFoundException ex)
    {
      System.out.println("FileNotFoundException : " + ex);
    }
    catch (IOException ioe)
    {
      System.out.println("IOException : " + ioe);
    }
  }
}