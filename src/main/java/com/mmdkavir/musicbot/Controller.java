package com.mmdkavir.musicbot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/** Queue + settings shared by the web UI. All public methods are thread-safe. */
public final class Controller {
    private static final String[] EXTS = {".mp3", ".wav", ".m4a", ".mp4", ".aac", ".flac", ".ogg", ".opus",
            ".wma", ".webm", ".mkv", ".avi", ".mov", ".m4b"};
    private final MusicPlayer player;
    private final List<Track> tracks = new ArrayList<>();
    private final Random random = new Random();
    private int current = -1;
    private boolean repeat, shuffle;
    private int volume = 50;      // volume other players hear (voice)
    private int localVolume = 50;  // volume you hear
    private String message = "";
    private String voice = "Not set";

    public Controller(MusicPlayer player) {
        this.player = player;
        player.setGain(volume / 100f);
        player.setLocalGain(localVolume / 100f);
        player.setMonitor(true);
        player.setOnFinish(() -> next(true));
    }

    public synchronized void playIndex(int i) {
        if (i < 0 || i >= tracks.size()) { message = "Invalid track"; return; }
        current = i;
        player.play(tracks.get(i));
    }

    public synchronized void next(boolean auto) {
        if (tracks.isEmpty()) return;
        int n;
        if (shuffle && tracks.size() > 1) {
            do { n = random.nextInt(tracks.size()); } while (n == current);
        } else {
            n = current + 1;
            if (n >= tracks.size()) {
                if (repeat) n = 0;
                else { if (auto) current = -1; player.stop(); if (auto) message = "Queue finished"; return; }
            }
        }
        playIndex(n);
    }

    public synchronized void prev() {
        if (tracks.isEmpty()) return;
        playIndex(Math.max(0, current - 1));
    }

    public synchronized void stop() { player.stop(); }
    public synchronized void pause() { player.pause(); }
    public synchronized void resume() { player.resume(); }

    public synchronized void setVolume(int v) {
        volume = Math.max(0, Math.min(200, v));
        player.setGain(volume / 100f);
    }

    public synchronized void setLocalVolume(int v) {
        localVolume = Math.max(0, Math.min(200, v));
        player.setLocalGain(localVolume / 100f);
    }

    public synchronized void setRepeat(boolean b) { repeat = b; }
    public synchronized void setShuffle(boolean b) { shuffle = b; }
    public synchronized void setMonitor(boolean on) { player.setMonitor(on); }
    public synchronized void setMessage(String m) { message = m; }
    public synchronized void setVoice(String s) { voice = s; }

    public synchronized void remove(int i) {
        if (i < 0 || i >= tracks.size()) return;
        tracks.remove(i);
        if (i < current) current--;
        else if (i == current) { player.stop(); current = -1; }
    }

    public synchronized void clear() {
        player.stop();
        tracks.clear();
        current = -1;
    }

    public synchronized void addLink(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            message = "Link must start with http:// or https://";
            return;
        }
        tracks.add(Track.ofUrl(url.trim()));
        message = "Link added";
        if (!player.isActive()) playIndex(tracks.size() - 1);
    }

    public synchronized void addFolder(String path, boolean subfolders) {
        File dir = new File(path == null ? "" : path.trim());
        if (!dir.isDirectory()) { message = "Folder not found"; return; }
        List<Path> found;
        try (Stream<Path> s = Files.walk(dir.toPath(), subfolders ? 6 : 1)) {
            found = s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String ext : EXTS) if (n.endsWith(ext)) return true;
                return false;
            }).sorted().toList();
        } catch (IOException e) {
            message = "Cannot read folder: " + e.getMessage();
            return;
        }
        if (found.isEmpty()) { message = "No supported audio/video files in that folder"; return; }
        int first = tracks.size();
        for (Path p : found) tracks.add(Track.ofFile(p.toFile()));
        message = "Added " + found.size() + " tracks";
        if (!player.isActive()) playIndex(first);
    }

    public record Snapshot(String status, boolean active, boolean paused, long pos, long dur, int current,
                           boolean repeat, boolean shuffle, int volume, String voice, String msg,
                           List<String> queue, boolean ffOk, String ff, boolean monitor, long calls,
                           int localVolume, long mixed) { }

    public synchronized Snapshot snapshot() {
        List<String> names = new ArrayList<>();
        for (Track t : tracks) names.add(t.name());
        return new Snapshot(player.status(), player.isActive(), player.isPaused(), player.positionMs(),
                player.durationMs(), current, repeat, shuffle, volume, voice, message, names,
                Ffmpeg.available(), Ffmpeg.status(), player.isMonitor(), player.calls(),
                localVolume, player.mixed());
    }

    public synchronized String stateJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"status\":").append(q(player.status()))
          .append(",\"active\":").append(player.isActive())
          .append(",\"paused\":").append(player.isPaused())
          .append(",\"pos\":").append(player.positionMs())
          .append(",\"dur\":").append(player.durationMs())
          .append(",\"current\":").append(current)
          .append(",\"repeat\":").append(repeat)
          .append(",\"shuffle\":").append(shuffle)
          .append(",\"volume\":").append(volume)
          .append(",\"voice\":").append(q(voice))
          .append(",\"msg\":").append(q(message))
          .append(",\"ffmpeg\":").append(q(Ffmpeg.status()))
          .append(",\"ffmpegOk\":").append(Ffmpeg.available())
          .append(",\"monitor\":").append(player.isMonitor())
          .append(",\"calls\":").append(player.calls())
          .append(",\"mixed\":").append(player.mixed())
          .append(",\"localVolume\":").append(localVolume)
          .append(",\"queue\":[");
        for (int i = 0; i < tracks.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q(tracks.get(i).name()));
        }
        sb.append("]}");
        return sb.toString();
    }

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
