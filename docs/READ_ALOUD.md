# Read aloud

## Getting the good voices

Recto speaks through whichever text-to-speech engine Android is set to use.
Out of the box that is usually Google's, which is fine but not what you are
here for.

For genuinely high-fidelity, fully offline neural voices:

1. Download a **sherpa-onnx TTS engine APK** from
   <https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html>

   Pick one matching your phone's architecture (almost certainly
   `arm64-v8a`) and a voice you like. A good English starting point is
   `...-en-tts-engine-vits-piper-en_US-libritts_r-medium.apk`.

2. Install it and open it once.

3. Android **Settings -> Accessibility -> Text-to-speech output**, and set
   it as the preferred engine.

4. In Recto: **menu (three dots) -> Read aloud**, and pick the voice.

Those are real Piper neural voices running on the phone's CPU. No network,
no account, nothing leaves the device.

## Why Recto does not bundle the voice

This came up directly, and the reasoning is worth writing down because the
obvious answer is wrong.

Bundling sherpa-onnx plus a Piper voice inside Recto would add roughly
**55 MB** to the APK. That part is widely known. Three things are less
obvious:

**It would not sound any better.** The engine APK runs the same Piper and
Kokoro ONNX models we would bundle. Identical weights, identical audio.
Bundling buys zero quality.

**It would force Recto from MIT to GPL-3.0.** Piper voices phonemise
through espeak-ng, which is GPL. This is not a theoretical worry - the
sherpa-onnx maintainers hit exactly this and opened
[issue #3731](https://github.com/k2-fsa/sherpa-onnx/issues/3731) to remove
espeak-ng, saying plainly that it "introduces license constraints that are
incompatible with the Apache-2.0 license of sherpa-onnx". Recto is MIT and
gets handed around as an APK, so a licence change is a real cost.

**It would make the sentence highlighting worse.** This is the one that
settled it. Android's `TextToSpeech` reports
`onRangeStart(utteranceId, start, end, frame)` - the engine tells us
exactly which characters it is speaking, right now. That is what drives the
highlight following along the page. A bundled ONNX model returns a
`FloatArray` of PCM samples with no word timings at all, so the highlight
would have to be guessed from elapsed audio time and would drift.

So bundling costs 55 MB, the licence, and the best feature. The engine APK
costs one install.

## If that ever changes

Everything above the voice is engine-agnostic. `SpeechEngine` in
`data/tts/SpeechEngine.kt` is the whole surface: prepare, speak one
utterance with callbacks, stop, list voices. `SystemSpeechEngine` is the
only implementation today.

Sentence splitting, highlighting, page turns, the notification, the sleep
timer and the speed controls all sit above that interface and would not
change. A bundled engine would implement `SpeechEngine` and nothing else
would move.

## How it works

**Sentence at a time, not page at a time.** `data/tts/Utterances.kt` splits
the book into speakable units. Sentence granularity is what makes the
highlight, pausing and page-turning possible at all - speak a whole page
and you cannot do any of the three.

Detection skips the usual false endings: decimals (`3.14`), initials
(`J. R. R. Tolkien`), and a short abbreviation list. A very long sentence
with no full stop gets broken at punctuation, then at a space, so no single
utterance exceeds 500 characters.

**Absolute offsets.** Utterances carry absolute character positions in the
book, the same coordinates as highlights and reading positions. Re-paginate
mid-sentence - rotate the phone, change the font - and playback still knows
where it is.

**The voice leads, the page follows.** When the sentence being read is not
on the visible page, the reader turns to it.

**Position is kept.** Stopping stores where the voice reached, so closing
the book after listening resumes in the right place.

## The bug worth remembering

Engine callbacks arrive on their own thread and can land *after* the
session they belong to has ended. Checking a `speaking` boolean is not
enough: stop, start again somewhere else, and a late `onDone` from the dead
chain advances the *new* queue, silently skipping a sentence.

Every playback session now carries a generation number, and callbacks are
ignored if it has moved on. The same guard fixes skip-sentence, where
`stop()` makes the engine fire `onDone` for the abandoned utterance and the
index would otherwise advance twice.

Simulated across 8 sequencing scenarios in `/tmp` during development:
sequential playback, skip with a stale callback, stop-then-restart, skips
at both ends, page turns, idle stop, and an empty queue.
