<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Try/Catch Consistency Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.0]

### Added

- Per-file statistical consensus over checked-exception handling for
  a method called 8+ times: flags the minority call sites that break
  with the majority's own established pattern (a convention learned
  from the file's own code, not a pre-written rule).
- Gutter warning icon with a tooltip naming the majority pattern and
  how many call sites support it.
- Review/star CTA: after 10 distinct real findings, a one-time
  notification asks whether to rate the plugin on Marketplace, with a
  permanent "Don't ask again" option. Standard mechanism used
  catalog-wide since 2026-08-24.

[Unreleased]: https://github.com/GapHunterLabs/trycatch-consistency-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/trycatch-consistency-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/trycatch-consistency-companion/commits/0.1.0
