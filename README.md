<div align="center">

<img src="assets/logo.png" width="128" alt="mauve">

# mauve

A fork of exteraless, the open-source take on exteraGram, with a permission model for plugins

[![Channel](https://img.shields.io/badge/Telegram-@mauveApp-2CA5E0?logo=telegram&logoColor=white)](https://t.me/mauveApp)

</div>

---

## What this is

mauve is a fork of [exteraless](https://github.com/exteraless/exteraless). exteraless itself is a fork of
[NagramX](https://github.com/risin42/NagramX) that implements exteraGram's features
as open source. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to sync with upstream.

The main focus is **plugin security**. In exteraGram, a plugin has no restrictions at all.
Here, plugins have a permission model: a plugin declares what it needs, you see that
on the install screen, and it gets only what it declared.

The project is **in beta**: some exteraGram features haven't been ported yet. If you
notice something missing, open an issue or write to the [channel](https://t.me/mauveApp?direct).

* Package name: `org.xchunk.mauve`
* 64-bit devices only (`arm64-v8a`, `x86_64`)

### What's inside

**Plugins**

* Compatible with exteraGram plugins: single-file `.plugin`, `.elyx`/`.eaf` archives,
  DEX plugins and Java method hooks (Xposed API)
* Permissions for network, files, reading and sending messages, intents, settings, hooks
  and native libraries. A call without permission is quietly denied and logged
* A per-plugin activity log with search and export
* Crash protection: a plugin that keeps crashing the app is disabled, and repeated
  launch crashes bring up safe mode
* Export and import of all plugins with their settings
* Python 3.11 on a custom [Chaquopy](https://github.com/exteraless/chaquopy) build

**Client**

* exteraGram settings: appearance, chats, icon packs, the side menu, the Pill Stack,
  settings search
* A Material 3 music player with a customizable cover, mini player, queue
  and synced lyrics
* Ghost mode and saving deleted and edited messages (with per-chat exceptions)
* A custom AI service instead of Telegram's built-in editor and summaries
* Monet themes, including the Inugram style
* QR login for other devices from the side menu

### Links

* Channel: [@mauveApp](https://t.me/mauveApp)
* Source code: [github.com/x-mauve/mauve4A](https://github.com/x-mauve/mauve4A)
* Upstream: [github.com/exteraless/exteraless](https://github.com/exteraless/exteraless)

### Building

You need JDK 21, the Android SDK with NDK and CMake, and Python 3.11 on the build machine.

1. Clone the repository with its submodules:

    ```bash
    git clone --recursive --shallow-submodules https://github.com/x-mauve/mauve4A.git mauve
    ```

    If you already cloned it without submodules:

    ```bash
    git submodule update --init --recursive --depth=1
    ```

2. Get `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` from [my.telegram.org](https://my.telegram.org/auth)
   and create `local.properties` in the project root:

   ```properties
   TELEGRAM_APP_ID=<your_app_id>
   TELEGRAM_APP_HASH=<your_app_hash>
   ```

   If `python3` on your system isn't 3.11, point to the right interpreter:

   ```properties
   CHAQUOPY_BUILD_PYTHON=/usr/bin/python3.11
   ```

3. To sign the APK, add your own `TMessagesProj/release.keystore` and append to `local.properties`:

   ```properties
   KEYSTORE_PASS=<keystore_password>
   ALIAS_NAME=<key_alias>
   ALIAS_PASS=<key_password>
   ```

   The repository intentionally doesn't include a keystore. The build still works without
   one: the APK is signed with the Android debug key.

4. For push notifications, add your own `TMessagesProj/google-services.json`
   (Firebase, package name `org.xchunk.mauve`).

5. Optional: your own keys in `local.properties`: `MAPS_API_KEY` (Google Maps; the key is
   tied to the package and signature, so someone else's shows an empty map) and
   `GLYPH_API_KEY` (Nothing Glyph). For the metadata channel, set its numeric ID without
   the `-100` prefix in `BaseRemoteHelper.CHANNEL_METADATA_ID`.

6. Build with `./gradlew :TMessagesProj:assembleDebug` or open the project in Android Studio.
   The Python runtime is built from the `TMessagesProj/chaquopy` submodule during the
   build; Cython is installed automatically.

**About ABIs.** Only 64-bit `arm64-v8a` and `x86_64` are built: Chaquopy builds
Python only for those. The `NATIVE_TARGET` variable sets the target: `arm64-v8a` or
`x86_64` (one ABI, faster), empty or `universal` (one APK with both), or `SKIP`
(no native part, only Java and resources).

### Building with GitHub Actions

You need two repository secrets:

* `LOCAL_PROPERTIES`: the contents of `local.properties` in base64:

  ```bash
  base64 -w0 local.properties
  ```

* `RELEASE_KEYSTORE`: the keystore file in base64:

  ```bash
  base64 -w0 TMessagesProj/release.keystore
  ```

Then run the **Release Build** workflow. The finished APK is in the run artifacts.

### Design credits

The icons and visual design inherited from exteraGram were created by its designer,
[@the8055u](https://t.me/the8055u), and the [@BlueprintDsgn](https://t.me/BlueprintDsgn) studio.
The rights to these materials belong to their authors.

### Acknowledgments

- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [Inugram](https://github.com/teidesu/inugram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [NagramX](https://github.com/risin42/NagramX)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
