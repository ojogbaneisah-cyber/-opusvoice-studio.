# OpusVoice Studio: Character.AI Call Bridge, Chime Interceptor & Native Voice Note Dispatch

An integrated 48kHz Mono voice call bridge and audio processing studio that suppresses Character.AI post-speech UI chimes in real time, provides a dedicated live call deck with connection telemetry and mute/end-call controls, and dispatches finished recordings directly to WhatsApp, Telegram, and Google Chat as native voice notes.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions have been confirmed and govern the implementation:

- **Confirmed Decision 1 — Per-App Auto-Formatting for Voice Note Intents**: When exporting a voice note to an external messenger, the engine automatically encodes and packages the audio to match each platform's native push-to-talk (PTT) specification (`PTT-YYYYMMDD-WAxxxx.opus` with `audio/ogg; codecs=opus` for WhatsApp, and 48kHz Mono `.ogg` Ogg/Opus for Telegram and Google Chat) with `ClipData` and URI read grants.
- **Confirmed Decision 2 — Dual-Layer Chime Suppression (Background AudioSession DSP + PCM Stream Filter)**: Chime suppression operates on both levels simultaneously: a background Android service attaches `DynamicsProcessing` / `Equalizer` notch and fast-attack limiter filters to the active audio output session while the in-app PCM stream processor applies a turn-boundary mute gate (`300ms–2200ms`) and biquad spectral notch (`1,550Hz` center) on captured and synthesized buffers.
- **Confirmed Decision 3 — Full Live Call Deck Interface**: The live Character.AI call screen features a full call control deck including a live connection status indicator badge, real-time 48kHz waveform & chime-gate telemetry, dedicated Mute/Unmute Caller Mic button, Speakerphone routing toggle, End Call control, Chime-Blocker service toggle, and one-tap PTT forwarding of call replies.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **Native Voice Note Intent Exporter**: Encodes trimmed or live-captured 48kHz Mono audio into platform-specific Opus containers and launches targeted `ACTION_SEND` intents with `FileProvider` URIs and `ClipData` so WhatsApp, Telegram, and Google Chat receive them as native voice notes.
  2. **Character.AI Chime Stream Interceptor & Service**: Runs both a background audio session DSP interceptor (`DynamicsProcessing` pre/post-EQ notches + transient limiter in `MODE_IN_COMMUNICATION`) and a frame-level 48kHz PCM biquad notch + post-speech holdoff gate that eliminates the synthetic UI confirmation chimes triggered right after a speaker finishes talking.
  3. **Live Character.AI Voice Call Interface**: Provides an interactive call console with an active connection status indicator, live elapsed call timer, caller mute/unmute toggle, speakerphone toggle, end-call button, real-time chime suppression counter, and instant forwarding of character replies to messaging apps.
- **Target Audience / Persona**: Users who conduct live voice calls with Character.AI personas—or bridge Character.AI into phone/messenger conversations—and need clean, chime-free vocal output and one-tap native voice note sharing.
- **Key Value**: Eliminates the immersion-breaking "ding/chime" sound effects after every speech turn on Character.AI calls while turning every generated or recorded clip into a ready-to-send WhatsApp, Telegram, or Google Chat voice bubble.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Live Character.AI Voice Call & Chime Suppression Flow**:
     - User opens the **c.ai Call** tab and sees the **Live Connection Status Banner** (`STANDBY`, `LIVE CALL CONNECTED`, `CHIME GATE MUTING`, or `CALL ENDED`), elapsed call duration, and active **Chime Interceptor Telemetry** (frames inspected, chimes suppressed, active audio session state).
     - Tapping **Start Call** activates the background Chime Interceptor Service, arms the post-speech holdoff gate (`950ms` default), and initiates the Character.AI call session.
     - During the call, the user can toggle **Mute / Unmute Mic**, **Listen to Caller**, **Speakerphone**, or **End Call** from the primary call control bar. Whenever a speech turn ends, the post-speech chime gate automatically clamps any synthetic `800Hz–2.8kHz` UI blip before passing human vocal frequencies (`300Hz–3.4kHz`).
  2. **Direct Native Voice Note Export Flow**:
     - From either the **Voice Notes** trimmer console or the bottom of the **c.ai Call** deck, the user taps **WhatsApp**, **Telegram**, or **Google Chat**.
     - The engine automatically formats the active 48kHz Mono buffer into the target app's native container (`PTT-*.opus` for WhatsApp, `.ogg` for Telegram/Google Chat), attaches `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`, and opens the target messenger (or system share sheet fallback if the app is not installed on the device).
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Precision acoustic broadcast console—high-contrast dark slate surfaces paired with crimson recording accents and emerald live-connection telemetry.
  - *Color Palette & Mood*:
    - `--background`: Deep Studio Obsidian (`#0F1117`) / Light Surface (`#F8FAFC`)
    - `--surface`: Elevated Console Card (`#181B24`) with subtle border (`#2E3446`)
    - `--primary`: Broadcast Crimson (`#E11D48`) for primary actions, recording states, and active navigation pills
    - `--accent-connected`: Signal Emerald (`#10B981`) for active call connection rings, live status indicator, and zero-chime shield badge
    - `--accent-gate`: Amber Holdoff (`#F59E0B`) when the post-speech chime gate is actively muting a turn-transition chime
  - *Typography & Hierarchy*: Bold uppercase section headers with wide letter-spacing (`0.8sp`) paired with high-contrast primary body copy (`#F8FAFC`) and tabular numeric readouts for call timers, dBFS meters, and millisecond gate holdoff values.
  - *Component Styling & Layout*: 4-tab bottom navigation (`Voice Notes`, `c.ai Call`, `Voice Clone`, `Saved Clips`) respecting system `WindowInsets.navigationBars`, minimum `48dp` interactive touch targets across all call controls, and custom `Canvas` waveform visualizers.
- **Interactive Feedback & Motion**:
  - Pulsing emerald connection status dot when a call is active, dynamic waveform bar color shifts (Emerald when capturing caller speech, Amber when the chime holdoff gate is muting a post-speech blip, Crimson when the character voice is speaking), and toast confirmation showing the exact exported PTT filename and target messenger.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Per-Messenger Container & MIME Auto-Formatting**
  - *Chosen Approach*: Automatically map WhatsApp exports to `PTT-YYYYMMDD-WAxxxx.opus` (`audio/ogg; codecs=opus`) and Telegram / Google Chat exports to `.ogg` (`audio/ogg`) encoded at 48,000 Hz Mono via hardware/platform `MediaCodec` + `MediaMuxer`.
  - *Why*: Messaging apps inspect both the file extension and MIME stream header to decide whether to render an incoming share as a playable voice bubble with a waveform vs. a generic file attachment.
  - *Alternatives Considered*: Sharing raw `.wav` files universally, which forces messengers to display a generic document download card instead of a native voice note player.
- **Decision 2: Hybrid AudioSession DSP Service + PCM Stream Biquad Notch**
  - *Chosen Approach*: Combine an Android background service (`CharacterAiChimeInterceptorService`) that attaches `DynamicsProcessing` / `Equalizer` notch bands to the audio session with an in-app 48kHz PCM stream filter (`ChimeStreamInterceptor`).
  - *Why*: When bridging calls with the external Character.AI app, the background service shapes the device's `MODE_IN_COMMUNICATION` output stream; when running in-app live character calls or cleaning recorded takes, the PCM stream interceptor provides sample-accurate turn-boundary chime gating and live suppression telemetry.
  - *Alternatives Considered*: Relying solely on a static low-pass filter, which muffles human consonant clarity above `2kHz` without eliminating the core `1.4kHz–2.1kHz` chime fundamental.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:
```
┌───────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (4 Focused Tabs)                      │
│   [Voice Notes]        [c.ai Call]        [Voice Clone]    [Saved Clips]  │
└─────────┬───────────────────┬───────────────────┬────────────────┬────────┘
          │                   │                   │                │
          ▼                   ▼                   ▼                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     StudioViewModel (MVVM State Holder)                   │
│  • characterCallState: StateFlow<CharacterCallState> (Connection, Mute,   │
│    Elapsed Timer, Speakerphone, Chime Gate Window, Active Persona)        │
│  • chimeInterceptorStats: StateFlow<ChimeInterceptorStats>                │
│  • loadedOpusAudio: StateFlow<LoadedAudioBuffer?>                         │
└─────────┬──────────────────────────────────┬──────────────────────────────┘
          │                                  │
          ▼                                  ▼
┌──────────────────────────────────┐ ┌──────────────────────────────────────┐
│   ChimeStreamInterceptor & Svc   │ │  OpusAudioEngine & Intent Exporter   │
│  • CharacterAiChimeInterceptor   │ │  • 48kHz 16-bit Mono AudioRecord     │
│    Service (AudioSession EQ &    │ │  • Hardware MediaCodec Ogg/Opus      │
│    DynamicsProcessing Limiter)   │ │  • VoiceNoteIntentExporter:          │
│  • PCM Biquad Notch (1550Hz) &   │ │    WhatsApp (PTT-*.opus)             │
│    Turn-End Gate (300ms-2200ms)  │ │    Telegram / Chat (48kHz .ogg)      │
└──────────────────────────────────┘ └──────────────────────────────────────┘
```

- **Data Model & State**:
  - `CharacterCallState`: Tracks `isCallActive`, `isCallMuted`, `isListeningToCaller`, `isChimeGateMuting`, `callDurationSeconds`, `speakerphoneEnabled`, `chimeBlockerEnabled`, `postSpeechMuteWindowMs`, `spectralNotchEnabled`, `telephonyBandpassEnabled`, `characterName`, `characterPersona`, `lastCallerStatement`, `lastCharacterReply`, and `callStatusText`.
  - `ChimeInterceptorStats` & `ChimeInterceptorConfig`: Reactive flows exposing `isServiceRunning`, `interceptedFramesCount`, `chimesSuppressedCount`, `lastDetectedChimeFreqHz`, and `currentGateState` to the call deck UI.
- **Interactive Component & State Mapping**:
  - **Connection Status Indicator Header**: Displays a color-coded status badge (`CONNECTED • LIVE`, `MUTED`, `CHIME GATE ACTIVE`, or `STANDBY`), live call duration timer (`MM:SS`), and background interceptor service state.
  - **Call Control Bar (Start/End Call, Mute Mic, Listen to Caller, Speakerphone)**:
    - *Start / End Call*: Starts/stops `CharacterAiChimeInterceptorService`, starts/resets the call duration coroutine timer, sets `AudioManager.MODE_IN_COMMUNICATION`, and triggers the character greeting or clean hang-up state.
    - *Mute / Unmute Mic*: Toggles `isCallMuted` in `CharacterCallState`, pauses/resumes microphone frame capture in `OpusAudioEngine`, and updates the connection banner immediately.
    - *Chime Gate & DSP Controls*: Updates `ChimeStreamInterceptor.globalConfig` in real time when the user adjusts the post-speech mute window slider (`300ms–2200ms`) or toggles the Spectral Chime Notch and Telephony Voice Bandpass filters.
  - **Messenger Voice-Note Dispatch Bar**: Invokes `VoiceNoteIntentExporter.exportVoiceNoteToApp(...)` after encoding the trimmed/de-chimed 48kHz Mono buffer into the target messenger's native container.
