package org.dynmap.fabric_1_16_4;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.dynmap.common.DynmapCommandSender;

/* Handler for generic console command sender */
public class FabricCommandSender implements DynmapCommandSender {
    private ServerCommandSource sender;

    protected FabricCommandSender() {
        sender = null;
    }

    public FabricCommandSender(ServerCommandSource send) {
        sender = send;
    }

    @Override
    public boolean hasPrivilege(String privid) {
        return true;
    }

    @Override
    public void sendMessage(String msg) {
        if (sender != null) {
            Text ichatcomponent = new LiteralText(msg);
            sender.sendFeedback(ichatcomponent, false);
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
