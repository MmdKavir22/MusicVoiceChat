package com.mmdkavir.musicbot;

import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Locale;

/** A playable item: a local file or a direct audio link (MP3 or WAV). */
public record Track(String name, File file, String url) {
    public static Track ofFile(File f) { return new Track(f.getName(), f, null); }

    public static Track ofUrl(String url) {
        String n = url;
        int q = n.indexOf('?');
        if (q >= 0) n = n.substring(0, q);
        int i = n.lastIndexOf('/');
        if (i >= 0 && i < n.length() - 1) n = n.substring(i + 1);
        return new Track(n, null, url);
    }

    public Decoded open() throws Exception {
        boolean mp3 = name.toLowerCase(Locale.ROOT).endsWith(".mp3");
        if (file != null) {
            if (mp3) return new Mp3Decoded(new BufferedInputStream(new FileInputStream(file)), file.length());
            return new WavDecoded(AudioSystem.getAudioInputStream(file));
        }
        URLConnection c = URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        InputStream in = new BufferedInputStream(c.getInputStream());
        try {
            if (mp3) return new Mp3Decoded(in, c.getContentLengthLong());
            return new WavDecoded(AudioSystem.getAudioInputStream(in));
        } catch (Exception e) {
            in.close();
            throw e;
        }
    }

    @Override public String toString() { return name; }
}
