# Fase 5 — Impostazioni, Equalizzatore a 5 Bande + BassBoost + Virtualizer e Download Offline

## Cosa è stato implementato
1. **Equalizzatore in Impostazioni (`GMusicEqualizerManager`, `SettingsScreen`)**:
   - Interruttore principale, 6 preset con i valori dB richiesti sulle 5 bande (`60 Hz`, `230 Hz`, `910 Hz`, `3.6 kHz`, `14 kHz`):
     - `Normale`: `0, 0, 0, 0, 0`
     - `Bass Boost`: `+6, +4, 0, 0, 0`
     - `Rock`: `+4, +2, -2, +3, +5`
     - `Pop`: `-1, +2, +4, +2, -1`
     - `Jazz`: `+3, +1, -2, +2, +4`
     - `Vocale`: `-2, 0, +4, +3, 0`
   - 5 slider individuali (`-15..+15 dB`), slider `BassBoost` (`0–100%`) e `Virtualizer` (`0–100%`), persistiti in `SettingsDataStore`.
   - Aggancio automatico all'`audioSessionId` di ExoPlayer con broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` / `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` e disattivazione sicura se un effetto non è supportato dal dispositivo.
2. **Download Offline (`GMusicDownloadService`, `OfflineDownloadController`, `DownloadModule`)**:
   - `DownloadManager` e `DownloadService` Media3 in foreground con notifica d'avanzamento, condivisione della `SimpleCache` (così la riproduzione offline preferisce automaticamente i file scaricati senza rete) e aggiornamento del flag `isDownloaded` in Room.
3. **Impostazioni complete (`SettingsScreen`, `SettingsViewModel`)**:
   - Scelta tema (sistema / Adaptive Player), crossfade `0–12 s`, qualità audio (`Automatica` / `Alta Opus 251`), spazio cache usato + pulsante `Svuota cache audio`, `Elimina tutti i download`, configurazione istanze `Piped` / `Invidious`, stato account e inserimento cookie.
4. **Rafforzamento `StreamResolver`**:
   - Priorità a `InnerTube` (`ANDROID_VR 1.62.27` / `IOS 20.03.02`) + `NewPipeExtractor v0.26.5` con verifica preventiva Range `bytes=0-1023` (`verifyStreamPlayable`) per scartare istantaneamente qualsiasi URL con `HTTP 403` prima di passarlo a ExoPlayer.
