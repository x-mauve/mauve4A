# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Glyph:** support for Nothing Phone (4b).
- **Glyph:** rules that pick which glyphs light up for new messages, incoming
  calls and voice recording, each with its own switch.
- **Glyph:** rules for single chats, which replace the event rules for
  messages and calls from that chat.
- **Glyph:** a preview in the rule editor that plays exactly the glyphs you
  picked.

### Changed

- **Glyph:** every message now flashes the glyphs right away. The 2.5 second
  pause between flashes is gone.
- **Glyph:** an incoming call takes the glyphs over from a voice recording
  and gives them back when it ends.
- **Glyph:** the settings moved from General to Experimental Features. The
  old per-event toggles are replaced by rules and reset to their defaults.
- **Glyph:** while Glyph Interface is off, the rest of its settings are
  dimmed.

### Fixed

- **Glyph:** the glyphs no longer flash for messages that show no notification,
  such as messages in the chat that is open or with notifications turned off.
- **Glyph:** the mauve logo on the Glyph Matrix of Phone (3) and Phone (4a) Pro
  is now larger and sharper, with two separate chevrons, the left one dimmer.

## [0.2.1] - 2026-11-10

### Changed

- **Upstream:** synced with exteraless, now based on Telegram for Android 13.2.1.
  This brings word-synced lyrics from more sources, player covers from Apple Music,
  queue reordering by drag, category tabs in the channel feed, a startup folder
  option, bot privacy settings, units and currency rates in inline results,
  a wider plugin SDK with stricter permissions, and many crash fixes.

### Removed

- **Player:** the Last.fm now playing feature, removed upstream.
- **Profile:** the mute icon next to the name, dropped in the upstream profile redesign.

### Fixed

- **Badges:** the mauve badge no longer sits far from the premium star
  in the chat list, chat header, and profile.

## [0.2.0] - 2026-11-02

### Added

- **Updater:** the mauve settings header now shows a pill update button
  under the version when a newer release is found: download runs in the
  background, can be canceled or left and resumed, and the downloaded APK
  is remembered across restarts until a newer version is installed.

### Changed

- **Badges:** mauve badges now show next to names in the chat list, chats and
  user lists, even for Premium users, and never take the emoji status slot
  anymore. The client mark shows there too when there is no unique badge.
- **Badges:** the mauve User badge menu now lists "Aether Integration"
  (native integration with the Aether bot) instead of "Safe Plugins", and
  "Useful and Powerful Features" instead of "Deep Customization", with
  matching descriptions.

## [0.1.0] - 2026-10-27

### Changed

- **Notifications:** the status-bar notification icon now uses the mauve
  app-logo arrows instead of the old exteraless mark.

### Added

- **Profile badges:** tapping the custom badge next to a name now shows a
  bulletin with the badge description and a "Learn More" button that opens a
  sheet explaining the unique mauve badge.
- **Profile client mark:** the "This user uses mauve" line is now tappable and
  shows a bulletin with a "Learn More" button that opens a sheet about mauve.
- **Player API (experimental):** an internal API that can intercept music
  player controls: play, pause, next, previous, seek, shuffle/order and repeat.
  Groundwork for On-Device Playback Control. Turn it on in Experimental
  Features → Playback → Enable Player API.

### Fixed

- **Sticker Shape → Message:** read/delivery status ticks no longer shift left
  and stick to the time bubble on stickers (both in the chat and in the
  settings preview).
- **Appearance → Title Text:** the chat list preview now shows `mauve` when the
  "mauve" title option is selected instead of the leftover `exteraless`.

### Changed

- mauve now has its own version, independent of the upstream Telegram one,
  starting at `0.1.0-preview.1` (versionCode `10000`). The About screen and the
  update checker use it; plugins still check against the Telegram version.
- The default download folder for saved files is now `Downloads/mauve`
  (previously `Downloads/exteraless`). Existing users who did not override the
  setting are migrated to the new default on the next launch.
