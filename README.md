# Spectra

![Build](https://github.com/gh9aravind/SpectraPro-mobile/actions/workflows/build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)

**Upload a track. See exactly what's really in it.**

Spectra is a native Android app that takes any audio file and gives you a
full technical breakdown: a spectrogram, peak/RMS/dynamic range, clipping
detection, and a spectral-cutoff heuristic that flags **"fake lossless"**
files — tracks tagged as FLAC/lossless that were actually upscaled from a
lossy source (a common issue with files pulled from unofficial sources).

Everything runs **on-device**. No file is ever uploaded anywhere — the app
doesn't even request network access.

## Features

- 📊 **Spectrogram** — linear frequency axis, with a channel toggle
  (combined / Ch 1 / Ch 2)
- 🎚️ **Level analysis** — peak, RMS, dynamic range, clipping detection,
  per-channel breakdown
- 🕵️ **Spectral cutoff detection** — flags likely lossy-to-lossless
  transcodes by finding where a track's frequency content is artificially
  cut off, and comparing that against known lossy-encoder signatures
- 🏷️ **Full metadata** — title, artist, album, genre, release date,
  track/disc numbers, embedded cover art
- 📁 **Broad format support** — FLAC, WAV, MP3, AAC, Vorbis, Opus
  (ALAC support is planned — see [Roadmap](#roadmap))
- 🔒 **Private by design** — no internet permission, nothing leaves your
  phone

## Screenshots

<!--
  Add a few screenshots here once you have some you're happy sharing
  publicly, e.g.:
  <p float="left">
    <img src="docs/screenshot-upload.png" width="240" />
    <img src="docs/screenshot-report.png" width="240" />
    <img src="docs/screenshot-spectrogram.png" width="240" />
  </p>
-->

## Tech stack

- **Kotlin** + **Jetpack Compose** for the UI
- **`MediaExtractor` / `MediaCodec`** (Android's own APIs) for decoding —
  no NDK/native dependency in this phase
- A hand-written **FFT / STFT DSP core**, streaming rather than
  buffering a whole track's analysis in memory, so peak memory stays
  roughly constant regardless of track length
- **`MediaMetadataRetriever`** for tags and embedded artwork

## Getting started

Clone and build with Gradle — no Android Studio required:

```bash
git clone https://github.com/gh9aravind/SpectraPro-mobile
cd SpectraPro-mobile
gradle assembleDebug
```

Or just push to `main` — GitHub Actions (`.github/workflows/build.yml`)
builds a debug APK on every push and uploads it as a workflow artifact,
so you can build and test entirely from a phone with no local toolchain.

## Architecture

```
ui/          Jetpack Compose screens, cards, spectrogram rendering
decode/      MediaExtractor + MediaCodec -> per-channel Float PCM
metadata/    MediaMetadataRetriever -> tags + cover art
dsp/         FFT/STFT, level stats, spectral-cutoff heuristic
model/       UI state
```

The DSP core (`dsp/SpectrogramAnalyzer.kt`) has no Android dependencies —
it's plain Kotlin, unit-testable on a normal JVM.

## Roadmap

- [ ] **ALAC support** via [`org.jellyfin.media3:media3-ffmpeg-decoder`](https://github.com/jellyfin/media3-ffmpeg-decoder)
      (a prebuilt AAR, no NDK build step needed)
- [ ] **LUFS (integrated loudness) + True Peak**, via the same FFmpeg
      dependency's `ebur128` filter
- [ ] Extended tags (ISRC, composer, freeform comments) via a dedicated
      tag-parsing library
- [ ] Fully streaming decode for arbitrarily long files (currently capped
      at 20 minutes to stay within a phone's memory budget)

See [CHANGELOG.md](CHANGELOG.md) for what's already shipped.

## Contributing

This started as a personal tool, so there's no formal contributing guide
yet — issues and PRs are still welcome.

## License

MIT — see [LICENSE](LICENSE).
