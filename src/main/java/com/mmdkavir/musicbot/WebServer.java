package com.mmdkavir.musicbot;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny local HTTP server (loopback only) that serves the control page and a JSON API.
 * Safety: binds to 127.0.0.1, checks the Host header (anti DNS-rebinding), and requires a custom
 * header on POST (browsers will not send it cross-site without a preflight, which we never allow).
 */
public final class WebServer {
    public static final int PORT = 1385;

    private final Controller c;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MusicBot-Web");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;

    public WebServer(Controller c) { this.c = c; }

    public boolean isRunning() { return running; }

    public synchronized boolean start() {
        if (running) return true;
        try {
            ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getLoopbackAddress());
            running = true;
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Socket s = server.accept();
                        pool.execute(() -> handle(s));
                    } catch (IOException e) {
                        running = false;
                        return;
                    }
                }
            }, "MusicBot-Accept");
            t.setDaemon(true);
            t.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String line = readLine(in);
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], target = parts[1];
            Map<String, String> headers = new HashMap<>();
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(), h.substring(i + 1).trim());
            }
            String host = headers.getOrDefault("host", "");
            if (!host.equals("localhost:" + PORT) && !host.equals("127.0.0.1:" + PORT)) {
                respond(out, 403, "text/plain", "Forbidden");
                return;
            }
            int len = 0;
            try { len = Integer.parseInt(headers.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) { }
            byte[] body = new byte[0];
            if (len > 0 && len <= 65536) {
                body = in.readNBytes(len);
            }
            String path = target, query = "";
            int qi = target.indexOf('?');
            if (qi >= 0) { path = target.substring(0, qi); query = target.substring(qi + 1); }
            Map<String, String> p = new HashMap<>();
            parseForm(query, p);
            parseForm(new String(body, StandardCharsets.UTF_8), p);

            if (method.equals("GET")) {
                if (path.equals("/") || path.equals("/index.html")) {
                    try (InputStream res = WebServer.class.getResourceAsStream("/web/index.html")) {
                        byte[] page = res == null ? "index.html missing".getBytes() : res.readAllBytes();
                        respond(out, 200, "text/html; charset=utf-8", page);
                    }
                } else if (path.equals("/api/state")) {
                    respond(out, 200, "application/json", c.stateJson());
                } else {
                    respond(out, 404, "text/plain", "Not found");
                }
                return;
            }
            if (!method.equals("POST") || !"1".equals(headers.get("x-musicbot"))) {
                respond(out, 403, "text/plain", "Forbidden");
                return;
            }
            respond(out, 200, "application/json", route(path, p));
        } catch (Exception ignored) {
            // client went away or timed out
        }
    }

    private String route(String path, Map<String, String> p) {
        switch (path) {
            case "/api/play" -> c.playIndex(num(p.get("i"), -1));
            case "/api/pause" -> c.pause();
            case "/api/resume" -> c.resume();
            case "/api/stop" -> c.stop();
            case "/api/next" -> c.next(false);
            case "/api/prev" -> c.prev();
            case "/api/volume" -> c.setVolume(num(p.get("v"), 50));
            case "/api/repeat" -> c.setRepeat("1".equals(p.get("on")));
            case "/api/shuffle" -> c.setShuffle("1".equals(p.get("on")));
            case "/api/add-link" -> c.addLink(p.get("url"));
            case "/api/add-folder" -> c.addFolder(p.get("path"), "1".equals(p.get("sub")));
            case "/api/remove" -> c.remove(num(p.get("i"), -1));
            case "/api/clear" -> c.clear();
            case "/api/monitor" -> c.setMonitor("1".equals(p.get("on")));
            case "/api/install-ffmpeg" -> Ffmpeg.install();
            case "/api/join" -> {
                String id = p.getOrDefault("id", "").trim();
                c.setVoice(id.isEmpty() ? "Enter a group ID" : VoiceGroup.join(id, p.get("pw")));
            }
            case "/api/leave" -> c.setVoice(VoiceGroup.leave());
            case "/api/browse" -> {
                return "{\"path\":" + Controller.q(browse()) + "}";
            }
            default -> { return "{\"msg\":\"Unknown action\"}"; }
        }
        return c.stateJson();
    }

    /** Opens a native folder chooser on the PC and returns the chosen path ("" if cancelled/unavailable). */
    private static String browse() {
        final String[] result = {""};
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog owner = new JDialog();
                owner.setAlwaysOnTop(true);
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Choose a music folder");
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                    result[0] = fc.getSelectedFile().getAbsolutePath();
                }
                owner.dispose();
            });
        } catch (Throwable t) {
            return "";
        }
        return result[0];
    }

    private static int num(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static void parseForm(String s, Map<String, String> into) {
        if (s == null || s.isEmpty()) return;
        for (String kv : s.split("&")) {
            int i = kv.indexOf('=');
            try {
                String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
                String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
                into.put(k, v);
            } catch (Exception ignored) { }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 8192) throw new IOException("line too long");
        }
        if (ch == -1 && b.size() == 0) return null;
        return b.toString(StandardCharsets.UTF_8);
    }

    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        respond(out, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 403 ? " Forbidden" : " Not Found") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
