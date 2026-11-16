package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decodes a track on a worker thread into 48 kHz mono 16-bit PCM frames (960 samples = 20 ms).
 * The voice chat thread only polls frames; it never blocks.
 * Formats: whatever the JDK decodes (WAV/AU/AIFF). MP3/OGG/FLAC need extra libraries (later phase).
 */
public final class MusicPlayer {
    public static final int RATE = 48000;
    public static final int FRAME = 960;

    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(150); // ~3 s
    private final AtomicLong polledFrames = new AtomicLong();
    private volatile Thread worker;
    private volatile boolean paused;
    private volatile boolean active;
    private volatile float gain = 0.5f;
    private volatile String status = "Idle";
    private volatile long durationMs = -1;
    private volatile Runnable onFinish = () -> { };

    public String status() { return status; }
    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public long positionMs() { return polledFrames.get() * 20L; }
    public long durationMs() { return durationMs; }
    public void setGain(float g) { gain = Math.max(0f, Math.min(2f, g)); }
    public void setOnFinish(Runnable r) { onFinish = r; }
    public void pause() { if (active) { paused = true; status = "Paused"; } }
    public void resume() { if (active) { paused = false; status = "Playing"; } }

    public synchronized void play(Track track) {
        stop();
        queue.clear();
        polledFrames.set(0);
        durationMs = -1;
        active = true;
        paused = false;
        status = "Loading " + track.name();
        Thread t = new Thread(() -> decode(track), "MusicBot-Decoder");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    public synchronized void stop() {
        active = false;
        paused = false;
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
        polledFrames.incrementAndGet();
        short[] out = new short[f.length];
        float g = gain;
        for (int i = 0; i < f.length; i++) {
            int v = Math.round(f[i] * g);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    private void decode(Track track) {
        Thread me = Thread.currentThread();
        try (AudioInputStream raw = track.open()) {
            AudioFormat src = raw.getFormat();
            int ch = src.getChannels();
            if (raw.getFrameLength() > 0 && src.getFrameRate() > 0) {
                durationMs = (long) (raw.getFrameLength() * 1000.0 / src.getFrameRate());
            }
            AudioFormat pcmFmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(), 16, ch, ch * 2, src.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFmt, raw)) {
                status = "Playing";
                stream(pcm, ch, src.getSampleRate(), me);
            }
        } catch (InterruptedException e) {
            return; // stopped
        } catch (Exception e) {
            if (worker == me) {
                status = "Error: " + e.getMessage();
                active = false;
            }
            return;
        }
        if (worker != me) return;
        try {
            while (!queue.isEmpty() && worker == me) Thread.sleep(50);
        } catch (InterruptedException ignored) { return; }
        if (worker == me) {
            active = false;
            status = "Finished";
            onFinish.run();
        }
    }

    private void stream(AudioInputStream in, int ch, float srcRate, Thread me)
            throws IOException, InterruptedException {
        final double step = srcRate / (double) RATE;
        final int frameBytes = ch * 2;
        byte[] buf = new byte[frameBytes * 4096];
        int len = 0, idx = 0;
        float prev = 0f, cur = 0f;
        double frac = 0;
        short[] out = new short[FRAME];
        int n = 0;
        boolean first = true;
        while (worker == me) {
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
            float next = sum / ch;
            if (first) { prev = next; cur = next; first = false; frac = 0; continue; }
            prev = cur;
            cur = next;
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
        if (n > 0 && worker == me) queue.offer(out, 1, TimeUnit.SECONDS);
    }
}
