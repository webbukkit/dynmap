package org.dynmap.fabric_26_2.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.util.Arrays;

import org.dynmap.fabric_26_2.DynmapPlugin;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DynmapCommandExecutor implements Command<CommandSourceStack> {
    private final String cmd;
    private final DynmapPlugin plugin;

    DynmapCommandExecutor(String cmd, DynmapPlugin plugin) {
        this.cmd = cmd;
        this.plugin = plugin;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();

        final LiteralCommandNode<CommandSourceStack> command = literal(this.cmd)
                .executes(this)
                .build();

        final ArgumentCommandNode<CommandSourceStack, String> args = argument("args", greedyString())
                .executes(this)
                .build();

        // So this becomes "cmd" [args]
        command.addChild(args);

        // Add command to the command dispatcher via root node.
        root.addChild(command);
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
    public String getUsage(CommandSourceStack commandSource) {
        return "Run /" + cmd + " help for details on using command";
    }
}
