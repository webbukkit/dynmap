package org.dynmap.fabric_26_2;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.dynmap.common.DynmapCommandSender;

/* Handler for generic console command sender */
public class FabricCommandSender implements DynmapCommandSender {
    private CommandSourceStack sender;

    protected FabricCommandSender() {
        sender = null;
    }

    public FabricCommandSender(CommandSourceStack send) {
        sender = send;
    }

    @Override
    public boolean hasPrivilege(String privid) {
        return true;
    }

    @Override
    public void sendMessage(String msg) {
        if (sender != null) {
            sender.sendSuccess(() -> Component.literal(msg), false);
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
