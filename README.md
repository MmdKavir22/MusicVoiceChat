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
In game, type `/music` to open the MusicBot window. Add WAV files or a direct WAV link to the queue, press Play/Skip/Prev/Pause/Stop.
Enter a Simple Voice Chat group ID (and password if any) and press **Join group**: the mod sends `/voicechat join <id>` for you.

## Status / limitations
- Not tested; the `/music` command classes (ClientCommands) and `Minecraft.player.connection.sendCommand` names are unverified for 26.x; the `MergeClientSoundEvent` API names are unverified and may need adjusting if the build fails.
- Only WAV/AU/AIFF so far. No MP3/video/URL, playlist, sync, or DJ system yet.
- No Gradle wrapper is committed; the workflow installs Gradle itself.
