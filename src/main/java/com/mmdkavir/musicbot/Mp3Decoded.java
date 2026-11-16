package com.mmdkavir.musicbot;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.IOException;
import java.io.InputStream;

/** MP3 through JLayer (bundled via jar-in-jar). Duration is an estimate from the first frame's bitrate. */
final class Mp3Decoded implements Decoded {
    private final InputStream in;
    private final Bitstream bs;
    private final Decoder dec = new Decoder();
    private short[] cur = new short[0];
    private int pos, curLen, ch = 2;
    private final int rate;
    private long dur = -1;

    Mp3Decoded(InputStream in, long totalBytes) throws Exception {
        this.in = in;
        this.bs = new Bitstream(in);
        Header h = bs.readFrame();
        if (h == null) throw new IOException("Empty or invalid MP3");
        rate = h.frequency();
        if (totalBytes > 0 && h.bitrate() > 0) dur = totalBytes * 8000L / h.bitrate();
        decode(h);
    }

    private void decode(Header h) throws Exception {
        SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
        cur = sb.getBuffer();
        curLen = sb.getBufferLength();
        ch = Math.max(1, sb.getChannelCount());
        pos = 0;
        bs.closeFrame();
    }

    @Override public double rate() { return rate; }
    @Override public long durationMs() { return dur; }

    @Override
    public float nextMono() throws Exception {
        while (pos + ch > curLen) {
            Header h = bs.readFrame();
            if (h == null) return Float.NaN;
            decode(h);
        }
        float sum = 0;
        for (int c = 0; c < ch; c++) sum += cur[pos++];
        return sum / ch;
    }

    @Override
    public void close() {
        try { bs.close(); } catch (Exception ignored) { }
        try { in.close(); } catch (Exception ignored) { }
    }
}
