package org.dynmap.neoforge_1_21_4;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import org.dynmap.DynmapLocation;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.neoforge_1_21_4.DynmapPlugin.TexturesPayload;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * Player access abstraction class
 */
public class NeoForgePlayer extends NeoForgeCommandSender implements DynmapPlayer {
	private DynmapPlugin plugin;
	ServerPlayer player;
	private final String skinurl;
	private final UUID uuid;

	private static final Gson gson = new GsonBuilder().create();

	public NeoForgePlayer(DynmapPlugin plugin, ServerPlayer player) {
		this.plugin = plugin;
		this.player = player;
		String url = null;
		if (player != null) {
			uuid = player.getUUID();
			GameProfile prof = player.getGameProfile();
			if (prof != null) {
				Property textureProperty = Iterables.getFirst(prof.getProperties().get("textures"), null);

				if (textureProperty != null) {
					TexturesPayload result = null;
					try {
						String json = new String(Base64.getDecoder().decode(textureProperty.value()),
								StandardCharsets.UTF_8);
						result = gson.fromJson(json, TexturesPayload.class);
					} catch (JsonParseException e) {
					}
					if ((result != null) && (result.textures != null) && (result.textures.containsKey("SKIN"))) {
						url = result.textures.get("SKIN").url;
					}
				}
			}
		} else {
			uuid = null;
		}
		skinurl = url;
	}

	@Override
	public boolean isConnected() {
		return true;
	}

	@Override
	public String getName() {
		if (player != null) {
			String n = player.getName().getString();
			;
			return n;
		} else
			return "[Server]";
	}

	@Override
	public String getDisplayName() {
		if (player != null) {
			String n = player.getDisplayName().getString();
			return n;
		} else
			return "[Server]";
	}

	@Override
	public boolean isOnline() {
		return true;
	}

	@Override
	public DynmapLocation getLocation() {
		if (player == null) {
			return null;
		}
		Vec3 v = player.position();
		return toLoc(player.serverLevel(), v.x, v.y, v.z);
	}

	private DynmapLocation toLoc(ServerLevel level, double x, double y, double z) {
		return new DynmapLocation(plugin.getWorld(level).getName(), x, y, z);
	}

	@Override
	public String getWorld() {
		if (player == null) {
			return null;
		}

		if (player.serverLevel() != null) {
			return plugin.getWorld((ServerLevel) player.serverLevel()).getName();
		}

		return null;
	}

	public static final Connection getNetworkManager(ServerGamePacketListenerImpl nh) {
		return nh.getConnection();
	}

	@Override
	public InetSocketAddress getAddress() {
		if ((player != null) && (player instanceof ServerPlayer)) {
			ServerGamePacketListenerImpl nsh = ((ServerPlayer) player).connection;
			if ((nsh != null) && (getNetworkManager(nsh) != null)) {
				SocketAddress sa = getNetworkManager(nsh).getRemoteAddress();
				if (sa instanceof InetSocketAddress) {
					return (InetSocketAddress) sa;
				}
			}
		}
		return null;
	}

	@Override
	public boolean isSneaking() {
		if (player != null) {
			return player.getPose() == Pose.CROUCHING;
		}

		return false;
	}

	@Override
	public double getHealth() {
		if (player != null) {
			double h = player.getHealth();
			if (h > 20)
				h = 20;
			return h; // Scale to 20 range
		} else {
			return 0;
		}
	}

	@Override
	public int getArmorPoints() {
		if (player != null) {
			return player.getArmorValue();
		} else {
			return 0;
		}
	}

	@Override
	public DynmapLocation getBedSpawnLocation() {
		return null;
	}

	@Override
	public long getLastLoginTime() {
		return 0;
	}

	@Override
	public long getFirstLoginTime() {
		return 0;
	}

	@Override
	public boolean hasPrivilege(String privid) {
		if (player != null)
			return plugin.hasPerm(player, privid);
		return false;
	}

	@Override
	public boolean isOp() {
		return plugin.isOp(player.getName().getString());
	}

	@Override
	public void sendMessage(String msg) {
		Component ichatcomponent = Component.literal(msg);
		player.sendSystemMessage(ichatcomponent);
	}

	@Override
	public boolean isInvisible() {
		if (player != null) {
			return player.isInvisible();
		}
		return false;
	}

	@Override
	public boolean isSpectator() {
		if (player != null) {
			return player.isSpectator();
		}
		return false;
	}

	@Override
	public int getSortWeight() {
		Integer wt = plugin.sortWeights.get(getName());
		if (wt != null)
			return wt;
		return 0;
	}

	@Override
	public void setSortWeight(int wt) {
		if (wt == 0) {
			plugin.sortWeights.remove(getName());
		} else {
			plugin.sortWeights.put(getName(), wt);
		}
	}

	@Override
	public boolean hasPermissionNode(String node) {
		if (player != null)
			return plugin.hasPermNode(player, node);
		return false;
	}

	@Override
	public String getSkinURL() {
		return skinurl;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Send title and subtitle text (called from server thread)
	 */
	@Override
	public void sendTitleText(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		if (player instanceof ServerPlayer) {
			ServerPlayer mp = (ServerPlayer) player;
			ClientboundSetTitlesAnimationPacket times = new ClientboundSetTitlesAnimationPacket(fadeInTicks,
					stayTicks, fadeOutTicks);
			mp.connection.send(times);
			if (title != null) {
				ClientboundSetTitleTextPacket titlepkt = new ClientboundSetTitleTextPacket(
						Component.literal(title));
				mp.connection.send(titlepkt);
			}

			if (subtitle != null) {
				ClientboundSetSubtitleTextPacket subtitlepkt = new ClientboundSetSubtitleTextPacket(
						Component.literal(subtitle));
				mp.connection.send(subtitlepkt);
			}
		}
	}
}
