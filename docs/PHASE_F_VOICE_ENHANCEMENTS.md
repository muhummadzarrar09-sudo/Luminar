# Phase F — Voice Enhancements

## What Changed

The TTS system now has **7 voice profiles** with preset speed/pitch combos, a profile cycling button, pitch control, and a redesigned audio control bar.

### Voice Profiles

| Profile | Speed | Pitch | Best for |
|---------|-------|-------|----------|
| 🎙 Natural | 1.0× | 1.0 | Normal reading |
| 🎙 Slow & Clear | 0.75× | 0.95 | Language learning, comprehension |
| 🎙 Fast Reader | 1.35× | 1.0 | Speed reading, skimming |
| 🎙 Deep Voice | 0.9× | 0.75 | Audiobook feel, male voice |
| 🎙 High Voice | 1.0× | 1.3 | Female voice, bright tone |
| 🎙 Narrator | 0.85× | 0.9 | Storytelling, bedtime reading |
| 🎙 Speed Run | 1.75× | 1.05 | Power users, fast review |

### New TTS Control Bar

**Before:**
```
[ ⏹ ] [ ⏮ ] [ ▶ ] [ ⏭ ] 3/24
```

**After:**
```
┌──────────────────────────────────┐
│  ⏹   ⏮    ▶    ⏭    3/24      │  Controls
│        🎙 Narrator  ×0.85       │  Profile + speed
└──────────────────────────────────┘
```

- Tap "🎙 Narrator" → cycles to next profile
- Speed indicator shows current multiplier
- Profile applies both speed AND pitch simultaneously
- If already speaking, profile change takes effect immediately

### Technical Details

- `VoiceProfile` enum with 7 presets, each with `speed` and `pitch` values
- `TtsController` now has `setPitch()`, `setVoiceProfile()`, `cycleVoiceProfile()` methods
- `applyVoiceProfile()` sets both speech rate and pitch atomically
- Profile restarts speech from current chunk when changed mid-reading
- Available system voice count tracked in `TtsState.availableVoiceCount`

### VoiceBox Integration (Future)

VoiceBox (github.com/jamiepine/voicebox) is a local-first AI voice cloning studio with a REST API. Future integration would:
1. Connect to VoiceBox server on LAN (same pattern as Ollama)
2. Use cloned voice profiles for TTS instead of system voices
3. Support 7 TTS engines including Qwen3-TTS and Chatterbox
4. Enable custom voice cloning from reference audio

This is architecturally ready — the `TtsController` interface is designed for swappable backends.

### Files Changed
- `TtsController.kt` — Complete rewrite: `VoiceProfile` enum (7 presets), pitch control, profile cycling, voice count tracking
- `ReaderScreen.kt` — Redesigned TTS control bar with profile row, cycle button, speed indicator
