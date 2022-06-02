package org.dynmap.fabric_1_19.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.dynmap.fabric_1_19.DynmapPlugin;

import java.util.Arrays;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DynmapCommandExecutor implements Command<ServerCommandSource> {
    private final String cmd;
    private final DynmapPlugin plugin;

    DynmapCommandExecutor(String cmd, DynmapPlugin plugin) {
        this.cmd = cmd;
        this.plugin = plugin;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        final RootCommandNode<ServerCommandSource> root = dispatcher.getRoot();

        final LiteralCommandNode<ServerCommandSource> command = literal(this.cmd)
                .executes(this)
                .build();

        final ArgumentCommandNode<ServerCommandSource, String> args = argument("args", greedyString())
                .executes(this)
                .build();

        // So this becomes "cmd" [args]
        command.addChild(args);

        // Add command to the command dispatcher via root node.
        root.addChild(command);
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // Commands in brigadier may be proxied in Minecraft via a syntax like `/execute ... ... run dmap [args]`
        // Dynmap will fail to parse this properly, so we find the starting position of the actual command being parsed after any forks or redirects.
        // The start position of the range specifies where the actual command dynmap has registered starts
        int start = context.getRange().getStart();
        String dynmapInput = context.getInput().substring(start);

        String[] args = dynmapInput.split("\\s+");
        plugin.handleCommand(context.getSource(), cmd, Arrays.copyOfRange(args, 1, args.length));
        return 1;
    }

    //    @Override // TODO: Usage?
    public String getUsage(ServerCommandSource commandSource) {
        return "Run /" + cmd + " help for details on using command";
    }
}
