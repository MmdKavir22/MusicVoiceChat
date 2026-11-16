package com.mmdkavir.musicbot;

import net.minecraft.client.Minecraft;

/**
 * Joins/leaves a Simple Voice Chat group by sending SVC's own chat command as the local player.
 * The client API of SVC has no "join group" method, so this relies on the command
 * "/voicechat join <group id> [password]" existing and the player being allowed to use it.
 */
public final class VoiceGroup {
    private VoiceGroup() { }

    /** Returns a short status message. */
    public static String join(String id, String password) {
        String cmd = "voicechat join " + id.trim();
        if (password != null && !password.isBlank()) cmd += " " + password.trim();
        return send(cmd, "Join command sent");
    }

    public static String leave() { return send("voicechat leave", "Leave command sent"); }

    private static String send(String cmd, String ok) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "Not in a world/server";
        mc.execute(() -> mc.player.connection.sendCommand(cmd));
        return ok;
    }
}
