# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

mauve is an Android Telegram client (package `org.xchunk.mauve`). It's a fork of
[exteraless](https://github.com/exteraless/exteraless), which is a fork of NagramX
that reimplements exteraGram features as open source. The main focus is a permission
model for Python plugins. See `README.md` and `CONTRIBUTING.md`.

## Rules

- **Atomic commits.** Every change goes in its own commit: one commit, one logical
  change. Don't bundle unrelated fixes, renames, or formatting into the same commit.
  Commit messages are short, imperative, in plain American English
  (e.g. `Fix preview title showing exteraless instead of mauve`).
- **Comments.** Write code comments in plain American English, the way a human
  engineer would in production code: short, to the point, explaining *why* rather
  than restating *what*. No filler, no AI-style narration, no "Added this to..."
  notes. Match the comment density of the surrounding code. Older comments in
  Russian exist; leave them alone unless you're already touching that code.
- Never commit `local.properties`, `release.keystore`, or other secrets.
- Add user-visible changes to `CHANGELOG.md` under `[Unreleased]`
  (Keep a Changelog format).

## Versioning

mauve is versioned independently of upstream Telegram, in `gradle.properties`:

- `MAUVE_VERSION_NAME`: `X.Y.Z`, `X.Y.Z-preview.N` or `X.Y.Z-rc.N`
  (preview < rc < final). Release tags are the same with a `v` prefix,
  e.g. `v0.1.0-preview.1`.
- `MAUVE_VERSION_CODE`: the APK `versionCode`. Bump it by one for every
  published build, previews included. Never lower it, or users can't update
  without reinstalling.
- `APP_VERSION_NAME` / `APP_VERSION_CODE` are the upstream Telegram version.
  They stay in `BuildVars.BUILD_VERSION_STRING`, which plugins check their
  `app_version` against, so only change them on an upstream sync.

## Build

```bash
git submodule update --init --recursive --depth=1
./gradlew :TMessagesProj:assembleDebug
```

- `local.properties` must contain `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH`.
- Only `arm64-v8a` and `x86_64` are supported (Chaquopy ships Python 3.12 only for
  them). `NATIVE_TARGET` picks `arm64-v8a`, `universal`, or `SKIP` (Java and
  resources only, fastest for quick checks).
- CI workflows live in `.github/workflows/` (`pr`, `release`).

## Tests

Static checks for the plugin engine, plain CPython, no device or JVM needed:

```bash
python3 -m pytest tests/ -q
python3 -m pytest tests/test_plugin_parse.py -q   # single file
```

They need an external plugin corpus (`EXTERALESS_PLUGIN_CORPUS`,
`EXTERALESS_PLUGIN_ARCHIVES`) and skip if it's missing. R8 checks read
`TMessagesProj/build/outputs/mapping/release/`. `tests/baselines/*.json` lists
known gaps; remove entries when a gap is fixed. Details in `tests/README.md`.

## Code layout

- `TMessagesProj/src/main/java/org/telegram/` — upstream Telegram client code.
- `TMessagesProj/src/main/java/tw/nekomimi/` — Nagram/NagramX code.
- `TMessagesProj/src/main/java/com/exteragram/messenger/` — exteraGram-compatible
  API surface (config, plugins, badges, preferences) that plugins depend on.
- `TMessagesProj/src/main/java/app/exteraless/` — exteraless/mauve features
  (appearance, plugins, pillstack, drawer, badges, updater, etc.).
- `TMessagesProj/src/main/python/` and `TMessagesProj/chaquopy/` — Python plugin
  runtime.
- `TMessagesProj/jni/` — native code.

## Upstream sync

Merge (don't rebase) `upstream/main` on a separate branch. On conflicts, keep the
mauve package name, `mauve` strings, `@mauveApp` links, and icons. Full steps in
`CONTRIBUTING.md`.
