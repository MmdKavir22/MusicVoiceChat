# MusicBot (prototype, phase 1)

Client-side Fabric mod that mixes a local WAV file into your outgoing Simple Voice Chat microphone stream.
Targets Minecraft 26.2 and 26.3 (Java 25).

## Requirements
Minecraft 26.2 or 26.3, Fabric Loader 0.20.1+, Fabric API, Simple Voice Chat (for 26.3 only a snapshot build exists).

## Build with GitHub Actions
1. Push this project to a GitHub repo.
2. Open the **Actions** tab, choose **Build**, click **Run workflow**.
3. When finished, open the run and download `MusicBot-Minecraft-26.2-Fabric` or `MusicBot-Minecraft-26.3-Fabric`.
4. Extract the ZIP and copy the JAR to the `mods` folder.

## Use
In game, type `/music`. The mod prints `http://localhost:1385` in chat and opens it in your browser.
In the web panel you can: add MP3/WAV direct links, pick a folder on your PC (Browse or type a path) and queue all MP3/WAV files in it,
use Play/Pause/Skip/Prev/Stop, repeat, shuffle, volume, and join a Simple Voice Chat group by ID (the mod sends `/voicechat join <id>`).
The web server listens on 127.0.0.1 only (not reachable from other computers).

## Status / limitations
- Not tested; the `/music` command classes (ClientCommands) and `Minecraft.player.connection.sendCommand` names are unverified for 26.x; the `MergeClientSoundEvent` API names are unverified and may need adjusting if the build fails.
- MP3 and WAV only (OGG/FLAC/M4A/video not yet). MP3 uses JLayer bundled via jar-in-jar (unverified). No MP3/video/URL, playlist, sync, or DJ system yet.
- No Gradle wrapper is committed; the workflow installs Gradle itself.
