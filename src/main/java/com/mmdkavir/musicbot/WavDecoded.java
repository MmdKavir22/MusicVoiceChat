package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** WAV/AU/AIFF through the JDK's javax.sound. */
final class WavDecoded implements Decoded {
    private final AudioInputStream raw;
    private final AudioInputStream pcm;
    private final int ch;
    private final int frameBytes;
    private final float rate;
    private final long dur;
    private final byte[] buf;
    private int len, idx;

    WavDecoded(AudioInputStream raw) throws Exception {
        this.raw = raw;
        AudioFormat src = raw.getFormat();
        ch = src.getChannels();
        rate = src.getSampleRate();
        frameBytes = ch * 2;
        dur = (raw.getFrameLength() > 0 && src.getFrameRate() > 0)
                ? (long) (raw.getFrameLength() * 1000.0 / src.getFrameRate()) : -1;
        AudioFormat f = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, ch, frameBytes, rate, false);
        pcm = AudioSystem.getAudioInputStream(f, raw);
        buf = new byte[frameBytes * 4096];
    }

    @Override public double rate() { return rate; }
    @Override public long durationMs() { return dur; }

    @Override
    public float nextMono() throws Exception {
        if (idx + frameBytes > len) {
            int n = pcm.read(buf, 0, buf.length);
            if (n <= 0) return Float.NaN;
            len = n - (n % frameBytes);
            idx = 0;
            if (len < frameBytes) return Float.NaN;
        }
        float sum = 0;
        for (int c = 0; c < ch; c++) {
            int lo = buf[idx] & 0xff, hi = buf[idx + 1];
            sum += (short) ((hi << 8) | lo);
            idx += 2;
        }
        return sum / ch;
    }

    @Override
    public void close() {
        try { pcm.close(); } catch (Exception ignored) { }
        try { raw.close(); } catch (Exception ignored) { }
    }
}
