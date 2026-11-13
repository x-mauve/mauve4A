# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.2] - 2026-11-13

### Fixed

- **Badges:** the mauve badge in the chat list now sits as close to the name as
  in the chat header and profile when the user has no Premium status.
- **Badges:** the Premium status and the mauve badge after it no longer sit
  above the middle of the name in the chat list.

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
