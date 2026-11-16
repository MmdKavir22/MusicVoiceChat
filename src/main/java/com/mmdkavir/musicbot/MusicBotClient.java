package com.mmdkavir.musicbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

public final class MusicBotClient implements ClientModInitializer {
    public static final MusicPlayer PLAYER = new MusicPlayer();
    public static final Controller CONTROLLER = new Controller(PLAYER);
    private static final WebServer WEB = new WebServer(CONTROLLER);

    @Override
    public void onInitializeClient() {
        WEB.start();
        // UNVERIFIED API names for 26.x: if the build fails here, the class names changed.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("music").executes(ctx -> {
                    String url = "http://localhost:" + WebServer.PORT;
                    if (!WEB.start()) {
                        ctx.getSource().sendFeedback(Component.literal(
                                "MusicBot: port " + WebServer.PORT + " is busy, cannot start the web panel"));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(Component.literal("MusicBot panel: " + url));
                    openBrowser(url);
                    return 1;
                })));
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
