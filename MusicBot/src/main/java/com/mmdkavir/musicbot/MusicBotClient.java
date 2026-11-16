package com.mmdkavir.musicbot;

import net.fabricmc.api.ClientModInitializer;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class MusicBotClient implements ClientModInitializer {
    public static final MusicPlayer PLAYER = new MusicPlayer();

    @Override
    public void onInitializeClient() {
        // Separate Swing window = no dependency on Minecraft GUI/keybind APIs in the prototype.
        EventQueue.invokeLater(MusicBotClient::openPanel);
    }

    private static void openPanel() {
        if (GraphicsEnvironment.isHeadless()) return;
        JFrame f = new JFrame("MusicBot");
        f.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        JLabel status = new JLabel("Idle");
        JLabel file = new JLabel("No file selected");
        final File[] selected = new File[1];

        JButton choose = new JButton("Choose audio (WAV)");
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                selected[0] = fc.getSelectedFile();
                file.setText(selected[0].getName());
            }
        });
        JButton play = new JButton("Play");
        play.addActionListener(e -> { if (selected[0] != null) PLAYER.play(selected[0]); });
        JButton pause = new JButton("Pause");
        pause.addActionListener(e -> PLAYER.pause());
        JButton resume = new JButton("Resume");
        resume.addActionListener(e -> PLAYER.resume());
        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> PLAYER.stop());
        JSlider vol = new JSlider(0, 200, 50);
        vol.addChangeListener(e -> PLAYER.setGain(vol.getValue() / 100f));

        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(choose); p.add(file);
        JPanel row = new JPanel(new GridLayout(1, 0, 4, 4));
        row.add(play); row.add(pause); row.add(resume); row.add(stop);
        p.add(row);
        p.add(new JLabel("Music volume"));
        p.add(vol);
        p.add(status);
        f.setContentPane(p);
        f.pack();
        f.setAlwaysOnTop(true);
        f.setVisible(true);
        new Timer(300, e -> status.setText(PLAYER.status())).start();
    }
}
