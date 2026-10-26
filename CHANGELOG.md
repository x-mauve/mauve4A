# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Profile badges:** tapping the custom badge next to a name now shows a
  bulletin with the badge description and a "Learn More" button that opens a
  sheet explaining the unique mauve badge.
- **Profile client mark:** the "This user uses mauve" line is now tappable and
  shows a bulletin with a "Learn More" button that opens a sheet about mauve.

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
