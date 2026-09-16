# Changelog

## v0.1.0 — Phase 1 (initial working build)

**Added**
- Jetpack Compose UI: file picker, metadata card, audio-quality card,
  spectrogram with a channel toggle
- Metadata via `MediaMetadataRetriever`
- Decode via `MediaExtractor` + `MediaCodec` (AAC, MP3, FLAC, Vorbis,
  Opus, WAV/PCM)
- FFT/STFT spectrogram, peak/RMS/dynamic range, clipping detection, and
  the spectral-cutoff "fake lossless" heuristic
- GitHub Actions CI building a debug APK on every push

**Fixed during initial bring-up**
- Missing `gradle.properties` (`android.useAndroidX=true`) — the very
  first CI build failed before compiling any code because of this
- A bad import (`androidx.compose.foundation.layout.weight`) — `.weight()`
  is a `RowScope`/`ColumnScope` member, not a top-level function; the
  import resolved to an inaccessible internal symbol and broke the build

**Fixed after real-device testing (memory)**
- The DSP core used to hold every STFT frame of the *entire* track in
  memory before downsampling for display. Fine for a short track, but
  the intermediate array could pass a gigabyte for a longer one. It now
  folds each frame directly into the display buckets and discards it, so
  peak memory no longer scales with track length. Verified this produces
  identical output to the old two-pass version on the same synthetic
  sine/noise/clipping test cases.
- The decoder used to accumulate output as a list of chunks, then copy
  that into a separate final array — briefly holding two full copies of
  the decoded track. It now writes directly into one growable buffer per
  channel.
- Embedded cover art was decoded at full resolution for a 72dp thumbnail.
  Some FLAC rips embed very large scans; this could allocate tens of MB
  for a single image. Now decoded pre-downsampled.
- `catch (e: Exception)` doesn't catch `OutOfMemoryError` (it extends
  `Error`, not `Exception`) — a memory-heavy file would close the whole
  app with no on-screen message at all instead of showing an error card.
  Now caught and reported explicitly.
- Added a hard sample-count ceiling inside the decode loop itself
  (independent of the upfront duration check, which only helps if the
  file's tagged duration is accurate) as a second line of defense against
  unbounded memory growth.
- Unsupported codecs (e.g. ALAC, not yet supported — see
  [Roadmap](README.md#roadmap)) now fail with a clear message instead of
  crashing.
