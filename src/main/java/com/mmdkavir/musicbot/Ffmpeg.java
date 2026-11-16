package com.mmdkavir.musicbot;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Locates (or downloads, on Windows) an FFmpeg executable used to decode mp4/m4a/flac/ogg/mkv/etc. */
final class Ffmpeg {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final Path DIR = Path.of("musicbot");
    private static final String DOWNLOAD = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    private static volatile String exe;
    private static volatile String status = "";
    private static volatile boolean installing;

    private Ffmpeg() { }

    static synchronized String find() {
        if (exe != null) return exe;
        Path local = DIR.resolve(WINDOWS ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(local)) {
            exe = local.toAbsolutePath().toString();
            return exe;
        }
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) exe = "ffmpeg";
        } catch (Exception ignored) { }
        return exe;
    }

    static boolean available() { return find() != null; }

    static String status() {
        if (installing) return status;
        if (available()) return "FFmpeg ready";
        return status.isEmpty() ? "FFmpeg not installed (needed for mp4, m4a, flac, ogg, mkv...)" : status;
    }

    static synchronized void install() {
        if (installing || available()) return;
        if (!WINDOWS) {
            status = "Install ffmpeg with your package manager (apt/brew), then restart the game";
            return;
        }
        installing = true;
        Thread t = new Thread(() -> {
            try {
                status = "Downloading FFmpeg...";
                Files.createDirectories(DIR);
                URLConnection c = URI.create(DOWNLOAD).toURL().openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                long total = c.getContentLengthLong();
                long[] read = {0};
                InputStream counted = new FilterInputStream(c.getInputStream()) {
                    @Override public int read(byte[] b, int off, int len) throws IOException {
                        int n = super.read(b, off, len);
                        if (n > 0) {
                            read[0] += n;
                            status = "Downloading FFmpeg... " + (read[0] >> 20) + (total > 0 ? "/" + (total >> 20) : "") + " MB";
                        }
                        return n;
                    }
                };
                boolean ok = false;
                try (ZipInputStream zin = new ZipInputStream(counted)) {
                    ZipEntry e;
                    while ((e = zin.getNextEntry()) != null) {
                        if (e.getName().endsWith("/bin/ffmpeg.exe")) {
                            Path tmp = DIR.resolve("ffmpeg.exe.part");
                            Files.copy(zin, tmp, StandardCopyOption.REPLACE_EXISTING);
                            Files.move(tmp, DIR.resolve("ffmpeg.exe"), StandardCopyOption.REPLACE_EXISTING);
                            ok = true;
                            break;
                        }
                    }
                }
                status = ok ? "FFmpeg installed" : "Download finished but ffmpeg.exe was not found in the archive";
            } catch (Exception e) {
                status = "FFmpeg download failed: " + e.getMessage() + " (put ffmpeg.exe in the 'musicbot' folder manually)";
            } finally {
                installing = false;
                synchronized (Ffmpeg.class) { exe = null; }
            }
        }, "MusicBot-FfmpegInstall");
        t.setDaemon(true);
        t.start();
    }
}
