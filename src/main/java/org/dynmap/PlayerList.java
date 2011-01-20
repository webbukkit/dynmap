package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

import org.bukkit.Server;
import org.bukkit.entity.Player;

public class PlayerList {
	private Server server;
	private HashSet<String> hiddenPlayerNames = new HashSet<String>();
	private File hiddenPlayersFile = new File(DynmapPlugin.dataRoot, "hiddenplayers.txt");
	
	public PlayerList(Server server) {
		this.server = server;
	}
	
	public void save() {
		OutputStream stream;
		try {
			stream = new FileOutputStream(hiddenPlayersFile);
			OutputStreamWriter writer = new OutputStreamWriter(stream);
			for(String player : hiddenPlayerNames) {
				writer.write(player);
				writer.write("\n");
			}
			writer.close();
			stream.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void load() {
		try {
			Scanner scanner = new Scanner(hiddenPlayersFile);
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
				hiddenPlayerNames.add(line);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			return;
		}		
	}
	
	public void hide(String playerName) {
		hiddenPlayerNames.add(playerName);
		save();
	}
	
	public void show(String playerName) {
		hiddenPlayerNames.remove(playerName);
		save();
	}
	
	public void setVisible(String playerName, boolean visible) {
		if (visible) show(playerName); else hide(playerName);
	}
	
	public Player[] getVisiblePlayers() {
		ArrayList<Player> visiblePlayers = new ArrayList<Player>();
		Player[] onlinePlayers = server.getOnlinePlayers();
		for(int i=0;i<onlinePlayers.length;i++){
			Player p = onlinePlayers[i];
			if (!hiddenPlayerNames.contains(p.getName())) {
				visiblePlayers.add(p);
			}
		}
		Player[] result = new Player[visiblePlayers.size()];
		visiblePlayers.toArray(result);
		return result;
	}
}
