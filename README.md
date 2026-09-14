# Spectra (Android)

Native rewrite of the Spectra audio analyzer: pick a track, get a spectrogram
plus a full technical report (peak/RMS/dynamic range/clipping, spectral
cutoff / "fake lossless" detection).

## Status: Phase 1 (confirmed working on-device, memory-hardened)

Confirmed building and successfully analyzing FLAC files end-to-end on a
real device. One round of fixes since the first working build:

- **Streaming spectrogram computation.** The DSP core used to hold every
  STFT frame of the *entire* track in memory before downsampling for
  display — fine for a 3-minute song, but a 15+ minute file could need
  well over a gigabyte just for that intermediate array. It now folds
  each frame directly into the small display buckets and discards it,
  so peak memory no longer scales with track length. Verified this gives
  bit-identical results to the old approach on the same test suite, and
  added a 20-minute synthetic-track test to confirm it no longer explodes.
- **No more double-buffered PCM.** The decoder used to accumulate decoded
  audio as a list of chunks, then copy that into a second, separate float
  array — briefly holding two full copies of the track in memory. It now
  writes directly into one growable buffer per channel.
- **20-minute cap with a clear message.** Even with both fixes above, a
  genuinely long file's raw decoded PCM (unavoidably ~23MB/minute/channel
  at 48kHz) can still be too much for a phone's heap. Rather than risk an
  OOM crash, files longer than 20 minutes now fail with a clear,
  in-app message instead of crashing outright. If you need to analyze
  long DJ mixes/mixtapes routinely, the real fix is decoding straight
  into the streaming analyzer without ever buffering the whole track —
  a bigger change, worth doing as its own follow-up if this comes up.
- **Unsupported codecs (e.g. ALAC) fail clearly** instead of crashing —
  `MediaCodec.createDecoderByType()` throwing now surfaces as a normal
  in-app error card.
- `android:largeHeap="true"` added as a safety margin on top of all of
  the above.

**What's implemented:**
- Jetpack Compose UI (dark theme, matches the earlier web version's look)
- File picking via Storage Access Framework (`OpenDocument`)
- Metadata via `MediaMetadataRetriever` — title/artist/album/genre/year/
  track/disc/bitrate/duration/cover art
- Decode via Android's built-in `MediaExtractor` + `MediaCodec` — covers
  every format Android guarantees: AAC, MP3, FLAC, Vorbis, Opus, WAV/PCM
- Spectrogram (STFT), peak/RMS/dynamic range, clipping detection, and the
  spectral-cutoff "fake lossless" heuristic — pure-Kotlin DSP core in
  `dsp/SpectrogramAnalyzer.kt`, ported from the web version's `worker.js`
  and cross-checked against it with matching synthetic-signal tests
  (same sine/noise/clipping test cases, same results to 3+ decimal places)

**Deliberately deferred to Phase 2** (see chat for the reasoning):
- **ALAC support.** Stock `MediaCodec` has no guaranteed ALAC decoder.
  Plan: add [`org.jellyfin.media3:media3-ffmpeg-decoder`](https://github.com/jellyfin/media3-ffmpeg-decoder)
  as a Gradle dependency (a prebuilt AAR — no NDK build step needed) and
  wire it in as the decode path for formats `MediaCodec` can't handle.
  Commented-out dependency lines are already left in `app/build.gradle.kts`
  as a starting point.
- **LUFS (integrated loudness) + True Peak.** The web version got these
  for free from ffmpeg's `ebur128` filter. Once the FFmpeg decoder extension
  is wired in for ALAC anyway, reuse the same FFmpeg binary to run
  `ebur128` for loudness measurement — one dependency, two features.
- **Decoder consistency.** The original design decision (one decoder for
  every format, for reproducible results across devices) is fully
  restored once Phase 2 lands, since everything will route through
  FFmpeg the same way the web version does.
- **Extended tags** (ISRC, composer, freeform comments). `MediaMetadataRetriever`
  doesn't expose these; would need a dedicated tag-parsing library
  (e.g. `jaudiotagger`) layered on top of `metadata/MetadataReader.kt`.

## Confidence level, file by file

- `dsp/SpectrogramAnalyzer.kt` — **compiled and tested** in this
  environment (`kotlinc`), with results cross-checked against the web
  version's test suite. Highest confidence.
- `decode/AudioDecoder.kt`, `metadata/MetadataReader.kt` — plain Android
  SDK APIs (`MediaCodec`/`MediaExtractor`/`MediaMetadataRetriever`),
  written against long-stable, well-documented patterns, but **not
  compiled** (no Android SDK available here).
- `ui/*.kt`, `MainActivity.kt` — Jetpack Compose, **not compiled** (no
  Compose libraries available here to check against). Reviewed by hand
  for type/import consistency, but this is the most likely place for a
  small mistake (an import, a parameter name) to surface on first build.

If the first `gradle assembleDebug` run fails, it's most likely a minor
Compose API/import mismatch — paste the error back and it's a quick fix.

## Build

```
gradle assembleDebug
```
GitHub Actions (`.github/workflows/build.yml`) does this automatically on
every push and uploads the APK as a build artifact — no local Android
Studio needed, matching the android-music-app workflow.

## Version pins

`build.gradle.kts` files pin specific AGP/Kotlin/Compose/AndroxdX versions
that were current and known-good at write time. If Android Studio or
Gradle suggests newer ones when you open this, that's normal — bump them.
