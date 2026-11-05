# Fase 4 — Funzionalità Avanzate del Player (Testi LRCLIB, Palette Adattiva, Coda, Autoplay Infinito, Shared Element, Crossfade)

## Cosa è stato implementato
1. **Testi sincronizzati tramite LRCLIB (`LyricsRepository`, `SyncedLyricsSheetContent`)**:
   - Query automatica a `lrclib.net/api/get` con fallback a `lrclib.net/api/search`, salvataggio nella cache Room (`lyrics_cache`).
   - Visualizzazione karaoke riga per riga con scorrimento automatico (`animateScrollToItem`) e tocco sulla riga per fare seek al timestamp corrispondente.
   - Se un brano non ha testo disponibile, il pulsante "Testo" viene nascosto completamente.
2. **Adaptive Player Palette (`extractAccessiblePaletteColor`, `animateColorAsState`)**:
   - Estrazione del colore dominante/vibrante dalla copertina tramite `androidx.palette:palette-ktx` con normalizzazione HSL per garantire il contrasto di accessibilità WCAG AA.
   - Transizione fluida dei colori tramite `animateColorAsState` sia nel Player a schermo intero che nella `MiniPlayerBar` e nel tema globale quando attivo in Impostazioni.
3. **Coda visibile e riordinabile (`QueueBottomSheetContent`)**:
   - Bottom sheet che mostra la coda corrente, evidenzia il brano attivo e permette di saltare a qualsiasi elemento, spostare i brani su/giù (`controller.moveMediaItem`) o rimuoverli (`controller.removeMediaItem`).
4. **Infinite Autoplay**:
   - Quando restano `<= 3` brani in coda, `GMusicMediaLibraryService` recupera su `Dispatchers.IO` 10 brani dalla radio YT Music del brano corrente (deduplicati rispetto alla coda e alla cronologia recente) e li accoda sul Main thread.
5. **Animazione Shared-Element / Container-Transform e Gesti Swipe**:
   - Transizione fluida (`SharedTransitionLayout`, `sharedBounds`, `sharedElement`) tra `MiniPlayerBar` e `FullScreenPlayerSheet`.
   - Gesti swipe sinistra/destra (avanti/indietro) e giù (stop/chiusura) sia sul MiniPlayer che sulla copertina HD del Player.
6. **Crossfade (`CrossfadeController`)**:
   - Rampe di volume morbide configurabili 0–12 secondi in Impostazioni, eseguite sul Main thread senza interrompere la `MediaSession` o Android Auto.
