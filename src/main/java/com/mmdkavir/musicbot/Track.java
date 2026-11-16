package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URI;

/** A playable item: a local file or a direct audio link. */
public record Track(String name, File file, String url) {
    public static Track ofFile(File f) { return new Track(f.getName(), f, null); }

    public static Track ofUrl(String url) {
        String n = url;
        int i = url.lastIndexOf('/');
        if (i >= 0 && i < url.length() - 1) n = url.substring(i + 1);
        return new Track(n, null, url);
    }

    public AudioInputStream open() throws Exception {
        if (file != null) return AudioSystem.getAudioInputStream(file);
        return AudioSystem.getAudioInputStream(URI.create(url).toURL());
    }

    @Override public String toString() { return (url != null ? "[link] " : "") + name; }
}
