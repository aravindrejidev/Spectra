# Spectra – UI Redesign Patch

പുതിയ **Modern UI** (dark glass + red accent, design kit അനുസരിച്ച്) ഇപ്പോൾ **default** ആണ്.
പഴയ skeuomorphic (rack unit) UI **Classic** എന്ന പേരിൽ അങ്ങനെ തന്നെ നിലനിർത്തിയിട്ടുണ്ട്.

**മാറ്റാൻ:** Settings → Appearance → **Interface style** → Modern / Classic (തൽക്ഷണം മാറും, restart വേണ്ട; തിരഞ്ഞെടുപ്പ് സേവ് ആകും).

## എങ്ങനെ apply ചെയ്യാം
ഈ zip project root-ൽ (`Spectra-main/`) extract ചെയ്ത് overwrite ചെയ്യുക. പാത്തുകൾ project-ലെ പോലെ തന്നെയാണ്.
Dependency / Gradle / Manifest മാറ്റങ്ങൾ **ഇല്ല** (icons എല്ലാം Canvas-ൽ വരച്ചതാണ്).

## പുതിയ ഫയലുകൾ (4)
- `ui/theme/Pal.kt` – രണ്ട് look-നും ഉള്ള palette (`Pal`), Modern നിറങ്ങൾ (`ModernColors`), `isModernUi`
- `ui/ModernKit.kt` – Modern components: card, button, gauge, slider, switch, chips, notice, check rows…
- `ui/ModernHome.kt` – header, Input drop-zone, Analyzing ring, Fault notice, Checks, Style picker
- `ui/ModernIcons.kt` – icon set (Folder, Play, Gear, Share, Eject…)

## മാറ്റിയ ഫയലുകൾ (20)
- `settings/AppSettings.kt` – `uiStyle` (0 = Modern default, 1 = Classic), `ui_style` ആയി സേവ് ചെയ്യുന്നു
- `ui/Skeuo.kt` – `RackPanel`, `HwButton`, `Led`, `Lcd`, `VuMeter`, `LedLadder`, `HwFader`… ഇപ്പോൾ selected look അനുസരിച്ച് Modern/Classic വരയ്ക്കും. **Classic code ഒന്നും മാറിയിട്ടില്ല**, `Classic…` എന്ന പേരിലേക്ക് മാത്രം മാറ്റി.
- `ui/Motion.kt`, `ui/theme/Theme.kt`, `AnalyzerScreen`, `Cards`, `SettingsControls`, `SettingsScreen`, `BatchScreen`, `TrophyUi`, `SpotGame`, `SelfTestUnit`, `TipsUnit`, `FileFactsUnit`, `ShareDialog`, `SpectrogramView`, `TimelineUnit`, `UpdateDialog`, `UpdateFx` – നിറങ്ങൾ `Pal`-ലേക്ക്, Modern-ന് വേണ്ട layout/വാചകങ്ങൾ
- `ui/ReportImage.kt` – 2 വരി മാത്രം: export image-ന് `classicSevColor` (share ചെയ്യുന്ന image/PDF ഏത് look-ൽ ആയാലും ഒരുപോലെ ഇരിക്കാൻ)

## Modern UI-യിൽ എന്തൊക്കെ
- Header: equalizer logo (loading-ൽ വേഗത്തിൽ നൃത്തം ചെയ്യും), "SPECT**RA**" wordmark, status dot, gear (അമർത്തുമ്പോൾ കറങ്ങും)
- Input: dashed drop-zone (dash നീങ്ങുന്നു, folder icon പൊങ്ങിയാടുന്നു, പിന്നിൽ bars), ചുവന്ന **Load Track** button (glow + light sweep), format chips
- Analyzing: progress ring (%), ECG pulse line, animated dots, smooth bar
- Result: verdict **status chip** (No lossy sign / Inconclusive / Suspicious / Likely fake), half-circle LUFS gauge (count-up), smooth level bars, stat tiles
- Settings: pill toggles, slider (knob വലുതാകും), glass back/gear buttons, Style picker (preview tiles)
- Fault: ചുവന്ന notice, ഒരു തവണ shake

## Micro animations
card enter (fade + slide + scale, stagger) · title accent bar വളരൽ · button press scale + finger-ripple · primary button light sweep ·
switch knob spring + tick · slider knob grow · check rows tick വരയ്ക്കൽ · tiles/chips/medals pop-in ·
ring/gauge/bar fill · pulsing status dot · background glow breathing · screen change fade + slide.
**Settings → Appearance → Reduce motion** ഓൺ ആക്കിയാൽ ഇവയെല്ലാം നിർത്തും.

## പരിശോധന (സത്യസന്ധമായി)
ഈ environment-ൽ Android SDK / Gradle (Maven) ഇല്ല, അതിനാൽ **full `gradle assembleDebug` ഇവിടെ ഓടിച്ചിട്ടില്ല**. ചെയ്തത്:
1. മാറ്റിയ എല്ലാ 24 Kotlin ഫയലുകളും tree-sitter കൊണ്ട് syntax check – error ഇല്ല.
2. Kotlin 2.0.20 compiler + Compose API stubs ഉപയോഗിച്ച് foundation ഫയലുകൾ (Pal, Theme, AppSettings, Skeuo, Motion, ModernKit, ModernHome, ModernIcons, SettingsControls, UpdateFx) type-check – error ഇല്ല. (ബോധപൂർവം തെറ്റ് ചേർത്ത് checker പ്രവർത്തിക്കുന്നു എന്നും ഉറപ്പാക്കി.)
3. ബാക്കി screen ഫയലുകൾ compile ചെയ്ത് ഞാൻ ചേർത്ത/മാറ്റിയ വരികളിലെ error പരിശോധിച്ചു: ഞാൻ ചേർത്ത ഒരു symbol-ഉം unresolved അല്ല.
4. പുതിയ പേരുകൾ project-ലെ നിലവിലുള്ളവയുമായി collide ചെയ്യുന്നില്ല.

അതിനാൽ ആദ്യ build-ൽ ചെറിയ compile പിശക് വന്നാൽ log അയച്ചാൽ ഉടൻ ശരിയാക്കാം. Phone-ൽ ഓടിച്ച് കാണാതെ സ്പേസിങ്/നിറം fine-tune ചെയ്യേണ്ടി വരാം.

## ഇപ്പോൾ ചെയ്യാത്തത്
- Kit-ലെ ഐക്കൺ-ടൈൽ bottom navigation (Home/Stats/Folder/Settings) ചേർത്തിട്ടില്ല – app-ൽ ഇപ്പോൾ ആ രീതിയിലുള്ള navigation ഇല്ല. വേണമെങ്കിൽ അടുത്ത ഘട്ടം.
- Kit-ലെ "Analysis Options" checkbox card ചേർത്തിട്ടില്ല – app-ൽ അങ്ങനെയൊരു feature ഇല്ല (feature ഉണ്ടാക്കാതെ UI മാത്രം വെക്കണ്ട എന്ന് കരുതി). Settings → Analysis-ലെ options-ന് അതേ style ഉണ്ട്.
- Share ചെയ്യുന്ന image/PDF-ന്റെ ഡിസൈൻ മാറ്റിയിട്ടില്ല (രണ്ട് look-ലും ഒരുപോലെ).
