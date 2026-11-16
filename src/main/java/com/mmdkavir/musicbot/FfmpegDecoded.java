package com.mmdkavir.musicbot;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes anything FFmpeg understands (audio of mp4/mkv/webm, m4a, aac, flac, ogg...) to 48 kHz mono s16le. */
final class FfmpegDecoded implements Decoded {
    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private final Process proc;
    private final InputStream out;
    private final long dur;

    FfmpegDecoded(String source) throws Exception {
        String exe = Ffmpeg.find();
        if (exe == null) throw new IOException("FFmpeg not installed - click 'Install FFmpeg' in the panel");
        dur = probeDuration(exe, source);
        proc = new ProcessBuilder(exe, "-v", "quiet", "-nostdin", "-i", source,
                "-vn", "-map", "0:a:0?", "-f", "s16le", "-ac", "1", "-ar", "48000", "-")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        out = new BufferedInputStream(proc.getInputStream(), 65536);
    }

    private static long probeDuration(String exe, String source) {
        try {
            Process p = new ProcessBuilder(exe, "-nostdin", "-i", source).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            Thread t = new Thread(() -> {
                try { sb.append(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)); }
                catch (IOException ignored) { }
            });
            t.setDaemon(true);
            t.start();
            t.join(8000);
            p.destroyForcibly();
            Matcher m = DURATION.matcher(sb.toString());
            if (m.find()) {
                double s = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                return (long) (s * 1000);
            }
        } catch (Exception ignored) { }
        return -1;
    }

    @Override public double rate() { return 48000; }
    @Override public long durationMs() { return dur; }

    @Override
    public float nextMono() throws Exception {
        int lo = out.read();
        if (lo < 0) return Float.NaN;
        int hi = out.read();
        if (hi < 0) return Float.NaN;
        return (short) ((hi << 8) | lo);
    }

    @Override
    public void close() {
        proc.destroy();
        try { out.close(); } catch (Exception ignored) { }
    }
}
