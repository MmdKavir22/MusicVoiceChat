package com.mmdkavir.musicbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** In-game control screen opened by /music. Only uses buttons and edit boxes (no custom mouse handling). */
public final class MusicScreen extends Screen {
    private static final int ROWS = 6;
    private static final int ACCENT = 0xFF7C5CFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFB0B0C8;

    private static String link = "", folder = "", gid = "", gpw = "";
    private static boolean sub = true;
    private static int page = 0;

    private final Controller c = MusicBotClient.CONTROLLER;
    private Button pauseBtn, subBtn;
    private EditBox sendBox, localBox;
    private int x0, top;
    private String sig = "";

    public MusicScreen() { super(Component.literal("MusicBot")); }

    private Button btn(String t, int x, int y, int w, Runnable r) {
        Button b = Button.builder(Component.literal(t), bb -> r.run()).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private EditBox edit(int x, int y, int w, String hint, String val, Consumer<String> onChange) {
        EditBox e = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
        e.setMaxLength(512);
        e.setHint(Component.literal(hint));
        e.setValue(val);
        e.setResponder(onChange);
        addRenderableWidget(e);
        return e;
    }

    @Override
    protected void init() {
        Controller.Snapshot s = c.snapshot();
        sig = signature(s);
        x0 = this.width / 2 - 200;
        top = Math.max(4, (this.height - 262) / 2);

        // Left column: controls
        btn("Prev", x0, top + 46, 46, c::prev);
        pauseBtn = btn("Pause", x0 + 48, top + 46, 46, () -> {
            if (c.snapshot().paused()) c.resume(); else c.pause();
        });
        btn("Skip", x0 + 96, top + 46, 46, () -> c.next(false));
        btn("Stop", x0 + 144, top + 46, 46, c::stop);
        // Volume rows: [-] [typed percent] "% label" [+]  (1% steps, 0.1% below 1%, or type an exact value like 0.5)
        btn("-", x0, top + 68, 20, () -> c.setVolume(c.snapshot().volume() - step(c.snapshot().volume(), false)));
        sendBox = percentBox(x0 + 22, top + 68, pct(s.volume()), v -> c.setVolume(v));
        btn("+", x0 + 170, top + 68, 20, () -> c.setVolume(c.snapshot().volume() + step(c.snapshot().volume(), true)));
        btn("-", x0, top + 90, 20, () -> c.setLocalVolume(c.snapshot().localVolume() - step(c.snapshot().localVolume(), false)));
        localBox = percentBox(x0 + 22, top + 90, pct(s.localVolume()), v -> c.setLocalVolume(v));
        btn("+", x0 + 170, top + 90, 20, () -> c.setLocalVolume(c.snapshot().localVolume() + step(c.snapshot().localVolume(), true)));
        btn(s.repeat() ? "Repeat: ON" : "Repeat: off", x0, top + 112, 94, () -> {
            c.setRepeat(!c.snapshot().repeat()); rebuildWidgets();
        });
        btn(s.shuffle() ? "Shuffle: ON" : "Shuffle: off", x0 + 96, top + 112, 94, () -> {
            c.setShuffle(!c.snapshot().shuffle()); rebuildWidgets();
        });
        btn(s.monitor() ? "Hear me: ON" : "Hear me: off", x0, top + 134, 94, () -> {
            c.setMonitor(!c.snapshot().monitor()); rebuildWidgets();
        });
        subBtn = btn(sub ? "Subfolders: ON" : "Subfolders: off", x0 + 96, top + 134, 94, () -> {
            sub = !sub; rebuildWidgets();
        });

        // Right column: queue
        int qx = x0 + 200;
        List<String> q = s.queue();
        int pages = Math.max(1, (q.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int k = 0; k < ROWS; k++) {
            int idx = page * ROWS + k;
            if (idx >= q.size()) break;
            String name = q.get(idx);
            if (name.length() > 24) name = name.substring(0, 23) + "~";
            final int i = idx;
            btn((idx == s.current() ? "> " : "") + name, qx, top + 12 + k * 20, 178, () -> c.playIndex(i));
            btn("x", qx + 180, top + 12 + k * 20, 20, () -> c.remove(i));
        }
        btn("<", qx, top + 134, 20, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
        btn(">", qx + 22, top + 134, 20, () -> { page++; rebuildWidgets(); });
        btn("Clear", qx + 44, top + 134, 46, c::clear);
        if (!s.ffOk()) btn("Install FFmpeg", qx + 92, top + 134, 108, Ffmpeg::installAsync);

        // Bottom: link, folder, voice group
        int by = top + 158;
        edit(x0, by, 296, "Direct link (mp3, wav, m4a, mp4...)", link, v -> link = v);
        btn("Add link", x0 + 298, by, 102, () -> { c.addLink(link); link = ""; rebuildWidgets(); });
        edit(x0, by + 22, 240, "Folder on this PC", folder, v -> folder = v);
        btn("Browse", x0 + 242, by + 22, 70, () -> {
            Thread t = new Thread(() -> {
                String p = WebServer.browse();
                if (p.isEmpty()) {
                    c.setMessage("No folder chosen (dialog cancelled or unavailable) - type the path instead");
                } else {
                    folder = p;
                    Minecraft.getInstance().execute(this::rebuildWidgets);
                }
            }, "MusicBot-Browse");
            t.setDaemon(true);
            t.start();
        });
        btn("Add folder", x0 + 314, by + 22, 86, () -> c.addFolder(folder, sub));
        edit(x0, by + 44, 150, "Voice group ID", gid, v -> gid = v);
        edit(x0 + 152, by + 44, 100, "Password", gpw, v -> gpw = v);
        btn("Join", x0 + 254, by + 44, 66, () -> {
            c.setVoice(gid.isBlank() ? "Enter a group ID" : VoiceGroup.join(gid, gpw));
        });
        btn("Leave", x0 + 322, by + 44, 78, () -> c.setVoice(VoiceGroup.leave()));
    }

    private EditBox percentBox(int x, int y, String val, java.util.function.DoubleConsumer apply) {
        EditBox e = new EditBox(this.font, x, y, 40, 20, Component.literal("Volume %"));
        e.setMaxLength(6);
        e.setFilter(t -> t.matches("\\d{0,3}(\\.\\d{0,2})?"));
        e.setValue(val);
        e.setResponder(t -> {
            if (!t.isEmpty()) {
                try { apply.accept(Double.parseDouble(t)); } catch (NumberFormatException ignored) { }
            }
        });
        addRenderableWidget(e);
        return e;
    }

    private static String signature(Controller.Snapshot s) {
        return s.queue().hashCode() + "," + s.current() + "," + s.ffOk() + "," + s.repeat() + "," + s.shuffle();
    }

    @Override
    public void tick() {
        super.tick();
        if (!signature(c.snapshot()).equals(sig)) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Controller.Snapshot s = c.snapshot();
        pauseBtn.setMessage(Component.literal(s.paused() ? "Resume" : "Pause"));
        syncBox(sendBox, s.volume());
        syncBox(localBox, s.localVolume());
        super.extractRenderState(g, mouseX, mouseY, delta);

        g.text(this.font, "MusicBot", x0, top, ACCENT, true);
        String title = s.current() >= 0 && s.current() < s.queue().size() ? s.queue().get(s.current()) : "Nothing playing";
        if (title.length() > 30) title = title.substring(0, 29) + "~";
        g.text(this.font, title, x0, top + 12, WHITE, true);
        g.text(this.font, s.status().length() > 16 ? s.status().substring(0, 15) + "~" : s.status(), x0, top + 24, GREY, true);
        g.text(this.font, fmt(s.pos()) + " / " + (s.dur() > 0 ? fmt(s.dur()) : "--:--"), x0 + 112, top + 24, GREY, true);

        int barW = 190;
        g.fill(x0, top + 35, x0 + barW, top + 41, 0xFF2A2A3C);
        if (s.dur() > 0) {
            int w = (int) Math.min(barW, s.pos() * barW / s.dur());
            g.fill(x0, top + 35, x0 + w, top + 41, ACCENT);
        }
        g.text(this.font, "% others hear", x0 + 64, top + 74, WHITE, true);
        g.text(this.font, "% you hear", x0 + 64, top + 96, WHITE, true);

        int pages = Math.max(1, (s.queue().size() + ROWS - 1) / ROWS);
        g.text(this.font, "Queue (" + s.queue().size() + ")  page " + (page + 1) + "/" + pages, x0 + 200, top, GREY, true);

        int by = top + 158;
        String v = s.voice().equals("Not set") ? "" : s.voice();
        g.text(this.font, v, x0, by + 68, 0xFF4CC9F0, true);
        g.text(this.font, s.msg(), x0, by + 80, 0xFFFFD27F, true);
        g.text(this.font, "Voice calls: " + s.calls() + " | mixed frames: " + s.mixed(), x0, by + 92, GREY, true);
    }

    private static double step(double cur, boolean up) {
        return (up ? cur < 1.0 : cur <= 1.0) ? 0.1 : 1.0;
    }

    private static String pct(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static void syncBox(EditBox b, double value) {
        if (b == null || b.isFocused()) return;
        String t = pct(value);
        if (!t.equals(b.getValue())) b.setValue(t);
    }

    private static String fmt(long ms) {
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }
}
