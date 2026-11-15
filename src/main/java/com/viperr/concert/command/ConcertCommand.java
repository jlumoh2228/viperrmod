package com.viperr.concert.command;

import com.mojang.brigadier.CommandDispatcher;
import com.viperr.concert.concert.ConcertManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ConcertCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("viperr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean started = ConcertManager.startConcert(player);
                            if (!started) {
                                ctx.getSource().sendFailure(Component.literal("Show is already running! Use /viperr stop"));
                                return 0;
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ConcertManager.stopConcert(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Show stopped."), true);
                            return 1;
                        })
                )
        );
    }
}
