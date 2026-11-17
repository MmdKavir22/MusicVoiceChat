package com.mmdkavir.musicbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

public final class MusicBotClient implements ClientModInitializer {
    public static final MusicPlayer PLAYER = new MusicPlayer();
    public static final Controller CONTROLLER = new Controller(PLAYER);
    private static final WebServer WEB = new WebServer(CONTROLLER);
    private static volatile boolean openGui;

    @Override
    public void onInitializeClient() {
        WEB.start();
        // The screen is opened on the next client tick, because the chat screen closes right after a command runs.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGui) {
                openGui = false;
                client.setScreen(new MusicScreen());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("music")
                        .executes(ctx -> {
                            openGui = true;
                            return 1;
                        })
                        .then(ClientCommands.literal("web").executes(ctx -> {
                            String url = "http://localhost:" + WebServer.PORT;
                            if (!WEB.start()) {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "MusicBot: port " + WebServer.PORT + " is busy, cannot start the web panel"));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(Component.literal("MusicBot panel: " + url));
                            openBrowser(url);
                            return 1;
                        }))));
    }

    private static void openBrowser(String url) {
        Thread t = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) { }
        }, "MusicBot-OpenBrowser");
        t.setDaemon(true);
        t.start();
    }
}
