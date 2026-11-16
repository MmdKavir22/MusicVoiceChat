package com.mmdkavir.musicbot;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

/**
 * Mixes music into the outgoing microphone stream.
 * NOTE: MergeClientSoundEvent and its mergeAudio(short[]) signature must be verified against
 * the voicechat-api version in gradle.properties. If the build fails here, the API differs.
 */
public final class MusicBotVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() { return "musicbot"; }

    @Override
    public void initialize(VoicechatApi api) { }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, this::onMerge);
    }

    private void onMerge(MergeClientSoundEvent event) {
        short[] frame = MusicBotClient.PLAYER.pollFrame();
        if (frame != null) {
            event.mergeAudio(frame);
        }
    }
}
