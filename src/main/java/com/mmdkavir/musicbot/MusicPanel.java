package com.mmdkavir.musicbot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.Supplier;

/** Dark-themed control window opened by /music. */
public final class MusicPanel {
    private static final Color BG = new Color(0x12, 0x12, 0x18);
    private static final Color CARD = new Color(0x1e, 0x1e, 0x2a);
    private static final Color FG = new Color(0xee, 0xee, 0xf5);
    private static final Color MUTED = new Color(0x9a, 0x9a, 0xb0);
    private static final Color ACCENT = new Color(0x7c, 0x5c, 0xff);

    private static MusicPanel instance;

    private final MusicPlayer player = MusicBotClient.PLAYER;
    private final DefaultListModel<Track> model = new DefaultListModel<>();
    private final JList<Track> list = new JList<>(model);
    private final JFrame frame = new JFrame("MusicBot");
    private final JLabel nowPlaying = label("Nothing playing", 16, true);
    private final JLabel status = label("Idle", 12, false);
    private final JLabel time = label("0:00 / --:--", 12, false);
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel voiceStatus = label("Voice group: not set", 12, false);
    private final JCheckBox repeat = new JCheckBox("Repeat queue");
    private int current = -1;

    public static void show() {
        if (instance == null) instance = new MusicPanel();
        instance.frame.setVisible(true);
        instance.frame.toFront();
    }

    private MusicPanel() {
        player.setOnFinish(() -> EventQueue.invokeLater(() -> next(true)));
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Now playing card
        JPanel np = card();
        np.add(nowPlaying);
        np.add(Box.createVerticalStrut(6));
        bar.setForeground(ACCENT);
        bar.setBackground(BG);
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(380, 8));
        np.add(bar);
        np.add(Box.createVerticalStrut(4));
        np.add(time);
        np.add(status);
        JPanel controls = new JPanel(new GridLayout(1, 0, 6, 0));
        controls.setOpaque(false);
        controls.add(button("Prev", () -> prev()));
        controls.add(button("Pause", () -> player.pause()));
        controls.add(button("Resume", () -> player.resume()));
        controls.add(button("Skip", () -> next(false)));
        controls.add(button("Stop", () -> { player.stop(); }));
        np.add(Box.createVerticalStrut(8));
        np.add(controls);
        np.add(Box.createVerticalStrut(8));
        np.add(label("Music volume", 12, false));
        JSlider vol = new JSlider(0, 200, 50);
        vol.setOpaque(false);
        vol.addChangeListener(e -> player.setGain(vol.getValue() / 100f));
        np.add(vol);
        root.add(np);
        root.add(Box.createVerticalStrut(10));

        // Queue card
        JPanel q = card();
        q.add(label("Queue (double-click to play)", 13, true));
        list.setBackground(BG);
        list.setForeground(FG);
        list.setSelectionBackground(ACCENT);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) playIndex(list.getSelectedIndex());
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(380, 120));
        sp.setBorder(BorderFactory.createEmptyBorder());
        q.add(sp);
        JPanel qb = new JPanel(new GridLayout(1, 0, 6, 0));
        qb.setOpaque(false);
        qb.add(button("Add files", this::addFiles));
        qb.add(button("Add link", this::addLink));
        qb.add(button("Remove", () -> { int i = list.getSelectedIndex(); if (i >= 0) model.remove(i); }));
        qb.add(button("Clear", () -> { player.stop(); model.clear(); current = -1; }));
        q.add(Box.createVerticalStrut(6));
        q.add(qb);
        repeat.setOpaque(false);
        repeat.setForeground(FG);
        q.add(repeat);
        root.add(q);
        root.add(Box.createVerticalStrut(10));

        // Voice group card
        JPanel v = card();
        v.add(label("Voice chat group", 13, true));
        JTextField id = new JTextField();
        JPasswordField pw = new JPasswordField();
        v.add(label("Group ID", 12, false));
        v.add(id);
        v.add(label("Password (optional)", 12, false));
        v.add(pw);
        JPanel vb = new JPanel(new GridLayout(1, 0, 6, 0));
        vb.setOpaque(false);
        vb.add(button("Join group", () -> {
            if (id.getText().isBlank()) { voiceStatus.setText("Enter a group ID"); return; }
            voiceStatus.setText(VoiceGroup.join(id.getText(), new String(pw.getPassword())));
        }));
        vb.add(button("Leave group", () -> voiceStatus.setText(VoiceGroup.leave())));
        v.add(Box.createVerticalStrut(6));
        v.add(vb);
        v.add(voiceStatus);
        root.add(v);

        frame.setContentPane(root);
        frame.pack();
        frame.setAlwaysOnTop(true);
        new Timer(250, e -> refresh()).start();
    }

    private void refresh() {
        status.setText(player.status());
        long pos = player.positionMs(), dur = player.durationMs();
        time.setText(fmt(pos) + " / " + (dur > 0 ? fmt(dur) : "--:--"));
        bar.setValue(dur > 0 ? (int) Math.min(1000, pos * 1000 / dur) : 0);
    }

    private void addFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) model.addElement(Track.ofFile(f));
            if (current < 0 && !player.isActive() && model.size() > 0) playIndex(0);
        }
    }

    private void addLink() {
        String url = JOptionPane.showInputDialog(frame, "Direct audio link (WAV):");
        if (url != null && !url.isBlank()) {
            model.addElement(Track.ofUrl(url.trim()));
            if (current < 0 && !player.isActive()) playIndex(model.size() - 1);
        }
    }

    private void playIndex(int i) {
        if (i < 0 || i >= model.size()) return;
        current = i;
        list.setSelectedIndex(i);
        Track t = model.get(i);
        nowPlaying.setText(t.name());
        player.play(t);
    }

    private void next(boolean auto) {
        if (model.isEmpty()) return;
        int n = current + 1;
        if (n >= model.size()) {
            if (repeat.isSelected()) n = 0;
            else { if (auto) { current = -1; nowPlaying.setText("Queue finished"); } else player.stop(); return; }
        }
        playIndex(n);
    }

    private void prev() {
        if (model.isEmpty()) return;
        playIndex(Math.max(0, current - 1));
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static JPanel card() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CARD);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel label(String text, int size, boolean bold) {
        JLabel l = new JLabel(text);
        l.setForeground(bold ? FG : MUTED);
        l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        return l;
    }

    private static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6, 10, 6, 10));
        b.addActionListener(e -> r.run());
        return b;
    }
}
