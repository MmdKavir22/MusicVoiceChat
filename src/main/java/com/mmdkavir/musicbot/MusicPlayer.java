package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decodes a track on a worker thread into 48 kHz mono 16-bit PCM frames (960 samples = 20 ms) and feeds two outputs
 * with independent volumes:
 *  - the voice chat stream (polled by the Simple Voice Chat audio thread) -> what other players hear
 *  - your own speakers (a local playback thread) -> what you hear
 * With local playback on, the local output paces the decoder and voice frames are taken from the same stream.
 */
public final class MusicPlayer {
    public static final int RATE = 48000;
    public static final int FRAME = 960;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    private final BlockingQueue<short[]> sendQueue = new ArrayBlockingQueue<>(150);
    private final BlockingQueue<short[]> localQueue = new ArrayBlockingQueue<>(150);
    private final AtomicLong playedFrames = new AtomicLong();
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong mixed = new AtomicLong();
    private volatile long lastPollNanos;
    private volatile Thread worker;
    private volatile Thread localThread;
    private volatile SourceDataLine line;
    private volatile boolean paused, active, monitor;
    private volatile float sendGain = 0.5f, localGain = 0.5f;
    private volatile String status = "Idle";
    private volatile long durationMs = -1;
    private volatile Runnable onFinish = () -> { };

    public String status() { return status; }
    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public boolean isMonitor() { return monitor; }
    public long positionMs() { return playedFrames.get() * 20L; }
    public long durationMs() { return durationMs; }
    public long calls() { return calls.get(); }
    public long mixed() { return mixed.get(); }
    public void setGain(float g) { sendGain = clampGain(g); }
    public void setLocalGain(float g) { localGain = clampGain(g); }
    public void setOnFinish(Runnable r) { onFinish = r; }

    private static float clampGain(float g) { return Math.max(0f, Math.min(2f, g)); }

    public void pause() {
        if (!active) return;
        paused = true;
        status = "Paused";
        SourceDataLine l = line;
        if (l != null) l.stop();
    }

    public void resume() {
        if (!active) return;
        paused = false;
        status = "Playing";
        SourceDataLine l = line;
        if (l != null) l.start();
    }

    /** Turns local playback (hearing the music yourself) on or off. */
    public synchronized void setMonitor(boolean on) {
        if (on == monitor) return;
        if (on) {
            try {
                SourceDataLine l = AudioSystem.getSourceDataLine(FORMAT);
                l.open(FORMAT, 24000); // 0.25 s buffer
                if (!paused) l.start();
                line = l;
                monitor = true;
                final Thread[] self = new Thread[1];
                self[0] = new Thread(() -> localLoop(self[0]), "MusicBot-Local");
                self[0].setDaemon(true);
                localThread = self[0];
                self[0].start();
            } catch (Exception e) {
                status = "Local playback failed: " + e.getMessage();
            }
        } else {
            monitor = false;
            Thread t = localThread;
            localThread = null;
            if (t != null) t.interrupt();
            SourceDataLine l = line;
            line = null;
            if (l != null) { l.stop(); l.flush(); l.close(); }
            localQueue.clear();
        }
    }

    public synchronized void play(Track track) {
        stop();
        sendQueue.clear();
        localQueue.clear();
        playedFrames.set(0);
        durationMs = -1;
        active = true;
        paused = false;
        status = "Loading " + track.name();
        SourceDataLine l = line;
        if (l != null) l.start();
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
        sendQueue.clear();
        localQueue.clear();
        SourceDataLine l = line;
        if (l != null) l.flush();
        status = "Stopped";
    }

    /** Called from the voice chat audio thread. Returns a frame to mix into the outgoing voice, or null. */
    public short[] pollFrame() {
        calls.incrementAndGet();
        lastPollNanos = System.nanoTime();
        if (!active || paused) return null;
        short[] f = sendQueue.poll();
        if (f == null) return null;
        mixed.incrementAndGet();
        if (!monitor) playedFrames.incrementAndGet();
        return scale(f, sendGain);
    }

    private static short[] scale(short[] f, float g) {
        short[] out = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            int v = Math.round(f[i] * g);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    private void localLoop(Thread me) {
        try {
            while (localThread == me && monitor) {
                if (paused || !active) { Thread.sleep(30); continue; }
                short[] f = localQueue.poll(50, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                SourceDataLine l = line;
                if (l == null) break;
                short[] s = scale(f, localGain);
                byte[] b = new byte[s.length * 2];
                for (int i = 0; i < s.length; i++) { b[2 * i] = (byte) s[i]; b[2 * i + 1] = (byte) (s[i] >> 8); }
                l.write(b, 0, b.length); // blocks until there is room: this paces playback in real time
                playedFrames.incrementAndGet();
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            status = "Local playback error: " + e.getMessage();
        }
    }

    private boolean emit(short[] f, Thread me) throws InterruptedException {
        if (monitor) {
            while (worker == me && monitor && !localQueue.offer(f, 100, TimeUnit.MILLISECONDS)) { /* wait */ }
            // only feed the voice stream while Simple Voice Chat is actually asking for audio (avoids stale buffers)
            if (System.nanoTime() - lastPollNanos < 1_000_000_000L) {
                if (!sendQueue.offer(f)) { sendQueue.poll(); sendQueue.offer(f); }
            }
        } else {
            while (worker == me && !sendQueue.offer(f, 100, TimeUnit.MILLISECONDS)) { /* wait */ }
        }
        return worker == me;
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
            while (worker == me && (monitor ? !localQueue.isEmpty() : !sendQueue.isEmpty())) Thread.sleep(50);
            if (worker == me && monitor) Thread.sleep(300); // let the speaker buffer drain
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
                    if (!emit(out, me)) return;
                    out = new short[FRAME];
                    n = 0;
                }
                frac += step;
            }
            frac -= 1.0;
        }
        if (n > 0 && worker == me) emit(out, me);
    }
}
