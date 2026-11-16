package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Decodes a file on a worker thread into 48 kHz mono 16-bit PCM frames (960 samples = 20 ms).
 * The voice chat thread only polls frames; it never blocks.
 * Prototype: formats supported by the JDK (WAV/AU/AIFF). MP3/OGG/FLAC/video come in a later phase.
 */
public final class MusicPlayer {
    public static final int RATE = 48000;
    public static final int FRAME = 960;

    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(150); // ~3 s
    private volatile Thread worker;
    private volatile boolean paused;
    private volatile boolean active;
    private volatile float gain = 0.5f;
    private volatile String status = "Idle";

    public String status() { return status; }
    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public void setGain(float g) { gain = Math.max(0f, Math.min(2f, g)); }
    public void pause() { paused = true; status = "Paused"; }
    public void resume() { if (active) { paused = false; status = "Playing"; } }

    public synchronized void play(File file) {
        stop();
        queue.clear();
        active = true;
        paused = false;
        status = "Loading " + file.getName();
        Thread t = new Thread(() -> decode(file), "MusicBot-Decoder");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    public synchronized void stop() {
        active = false;
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
        queue.clear();
        status = "Stopped";
    }

    /** Called from the voice chat audio thread. Returns a frame to mix, or null. */
    public short[] pollFrame() {
        if (!active || paused) return null;
        short[] f = queue.poll();
        if (f == null) return null;
        short[] out = new short[f.length];
        float g = gain;
        for (int i = 0; i < f.length; i++) {
            int v = Math.round(f[i] * g);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    private void decode(File file) {
        Thread me = Thread.currentThread();
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file)) {
            AudioFormat src = raw.getFormat();
            int ch = src.getChannels();
            AudioFormat pcmFmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(), 16, ch, ch * 2, src.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFmt, raw)) {
                status = "Playing " + file.getName();
                stream(pcm, ch, src.getSampleRate(), me);
            }
        } catch (InterruptedException e) {
            // stopped
        } catch (Exception e) {
            status = "Error: " + e.getMessage();
            active = false;
            return;
        }
        if (worker == me) { // natural end
            try {
                while (!queue.isEmpty() && worker == me) Thread.sleep(50);
            } catch (InterruptedException ignored) { }
            if (worker == me) { active = false; status = "Finished"; }
        }
    }

    private void stream(AudioInputStream in, int ch, float srcRate, Thread me)
            throws IOException, InterruptedException {
        final double step = srcRate / (double) RATE;
        final int frameBytes = ch * 2;
        byte[] buf = new byte[frameBytes * 4096];
        int len = 0, idx = 0;
        float prev = 0f, cur = Float.NaN;
        double frac = 0;
        short[] out = new short[FRAME];
        int n = 0;
        boolean first = true;
        while (worker == me) {
            // fetch next source sample (mono mix)
            float next;
            if (idx + frameBytes > len) {
                len = in.read(buf, 0, buf.length);
                idx = 0;
                if (len < frameBytes) break;
            }
            float sum = 0;
            for (int c = 0; c < ch; c++) {
                int lo = buf[idx] & 0xff, hi = buf[idx + 1];
                sum += (short) ((hi << 8) | lo);
                idx += 2;
            }
            next = sum / ch;
            if (first) { prev = next; cur = next; first = false; frac = 0; continue; }
            prev = cur;
            cur = next;
            // emit output samples while frac < 1
            while (frac < 1.0) {
                float v = prev + (cur - prev) * (float) frac;
                out[n++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
                if (n == FRAME) {
                    while (worker == me && !queue.offer(out, 100, TimeUnit.MILLISECONDS)) { /* wait */ }
                    out = new short[FRAME];
                    n = 0;
                }
                frac += step;
            }
            frac -= 1.0;
        }
        if (n > 0 && worker == me) { // pad last frame
            queue.offer(out, 1, TimeUnit.SECONDS);
        }
    }
}
