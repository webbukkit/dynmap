package org.dynmap.neoforge_1_21_4;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import org.dynmap.common.DynmapCommandSender;

/**
 * Handler for generic console command sender
 */
public class NeoForgeCommandSender implements DynmapCommandSender {
	private CommandSourceStack sender;

	protected NeoForgeCommandSender() {
		sender = null;
	}

	public NeoForgeCommandSender(CommandSourceStack send) {
		sender = send;
	}

	@Override
	public boolean hasPrivilege(String privid) {
		return true;
	}

	@Override
	public void sendMessage(String msg) {
		if (sender != null) {
			Component ichatcomponent = Component.literal(msg);
			sender.sendSuccess(() -> ichatcomponent, true);
		}
	}

	@Override
	public boolean isConnected() {
		return false;
	}

	@Override
	public boolean isOp() {
		return true;
	}

	@Override
	public boolean hasPermissionNode(String node) {
		return true;
	}
}
