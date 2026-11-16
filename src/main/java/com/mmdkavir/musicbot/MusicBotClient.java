package com.mmdkavir.musicbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import java.awt.EventQueue;

public final class MusicBotClient implements ClientModInitializer {
    public static final MusicPlayer PLAYER = new MusicPlayer();

    @Override
    public void onInitializeClient() {
        // UNVERIFIED API names for 26.x: if the build fails here, the class names changed.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("music").executes(ctx -> {
                    EventQueue.invokeLater(MusicPanel::show);
                    return 1;
                })));
    }
}
