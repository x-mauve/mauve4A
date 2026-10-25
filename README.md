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
* Ported so far: settings screens, chat appearance and options, icon packs, the Pill
  Stack, the side menu, and the Python plugin engine

### Links

* Channel: [@mauveApp](https://t.me/mauveApp)
* Source code: [github.com/x-mauve/mauve4A](https://github.com/x-mauve/mauve4A)
* Upstream: [github.com/exteraless/exteraless](https://github.com/exteraless/exteraless)

### Building

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

5. Replace the project metadata:

    - the Google Maps key in the `com.google.android.maps.v2.API_KEY` entry in `TMessagesProj/src/main/AndroidManifest.xml`;
    - `BaseRemoteHelper.CHANNEL_METADATA_ID`: the numeric ID of your metadata channel, without the `-100` prefix.

6. Build with `./gradlew :TMessagesProj:assembleDebug` or open the project in Android Studio.

**About ABIs.** Only 64-bit `arm64-v8a` and `x86_64` are built: Chaquopy builds
Python 3.12 only for those, and configuration fails on `armeabi-v7a`. The `NATIVE_TARGET`
variable sets the target: `arm64-v8a` (one ABI, faster), `universal` (both), or
`SKIP` (no native part, only Java and resources).

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
