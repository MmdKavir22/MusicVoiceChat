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
In game, type `/music` to open the in-game control screen (queue, controls, link, folder, voice group). For the browser version type `/music web`. The web version prints `http://localhost:1385` in chat and opens it in your browser.
In the web panel you can: add MP3/WAV direct links, pick a folder on your PC (Browse or type a path) and queue all MP3/WAV files in it,
use Play/Pause/Skip/Prev/Stop, repeat, shuffle, volume, and join a Simple Voice Chat group by ID (the mod sends `/voicechat join <id>`).
The web server listens on 127.0.0.1 only (not reachable from other computers).

## Releases
Every push and every manual run of the Build workflow (not pull requests) also publishes a GitHub Release named `v<mod_version>-build.<run number>`
with `MusicBot-26.2-Fabric.jar` and `MusicBot-26.3-Fabric.jar`. Repo Settings > Actions > General > Workflow permissions must allow write access if releases fail.

## Volumes
"Volume for others" is what players in your voice group hear; "Volume for me" is what you hear on your own speakers. Both are independent (0-200%).

## Status / limitations
- Not tested; the `/music` command classes (ClientCommands) and `Minecraft.player.connection.sendCommand` names are unverified for 26.x; the `MergeClientSoundEvent` API names are unverified and may need adjusting if the build fails.
- MP3/WAV decode natively (JLayer). mp4, m4a, aac, flac, ogg, opus, mkv, webm, avi, mov need FFmpeg: click Install FFmpeg in the panel (Windows, downloads from gyan.dev) or put ffmpeg.exe in the `musicbot` folder of your game directory. No MP3/video/URL, playlist, sync, or DJ system yet.
- No Gradle wrapper is committed; the workflow installs Gradle itself.
