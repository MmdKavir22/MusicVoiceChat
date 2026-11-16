package com.mmdkavir.musicbot;

/** A decoder that yields mono float samples (16-bit range) at its native sample rate. */
public interface Decoded extends AutoCloseable {
    double rate();

    /** Duration in ms, or -1 if unknown. */
    long durationMs();

    /** Next mono sample, or NaN at end of stream. */
    float nextMono() throws Exception;

    @Override
    void close();
}
