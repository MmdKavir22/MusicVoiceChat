package com.mmdkavir.musicbot;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decodes a track on a worker thread into 48 kHz mono 16-bit PCM frames (960 samples = 20 ms).
 * The voice chat thread only polls frames; it never blocks.
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
        try (Decoded d = track.open()) {
            if (worker != me) return;
            durationMs = d.durationMs();
            status = "Playing";
            stream(d, me);
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

    private void stream(Decoded d, Thread me) throws Exception {
        final double step = d.rate() / (double) RATE;
        float prev = 0f, cur = 0f;
        double frac = 0;
        short[] out = new short[FRAME];
        int n = 0;
        boolean first = true;
        while (worker == me) {
            float next = d.nextMono();
            if (Float.isNaN(next)) break;
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
