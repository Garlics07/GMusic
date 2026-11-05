# Fase 6 — Android Auto (`MediaLibraryService`) e Widget Glance (4x1 e 4x2)

## Cosa è stato implementato
1. **Android Auto (`GMusicMediaLibraryService`)**:
   - Albero di navigazione condiviso con l'interfaccia telefono, notifiche di sistema, schermata di blocco e widget:
     - `Home` (`NODE_HOME`): brani dai caroselli consigliati.
     - `Cronologia` (`NODE_HISTORY`): ultime riproduzioni locali.
     - `Playlist` (`NODE_PLAYLISTS`): cartelle sfogliabili per ciascuna playlist locale (`playlist_{id}`).
     - `Brani piaciuti` (`NODE_LIKED`): tutti i brani preferiti.
   - Paginazione completa tramite `page` e `pageSize` (max 50 elementi per pagina) in `onGetChildren` e `onGetSearchResult`.
   - Ricerca su schermo e vocale (`"Ehi Google, riproduci X su GMusic"`) tramite `onSearch` + `onGetSearchResult`, con avvio immediato della riproduzione per le richieste vocali.
   - Azione personalizzata `Cuore` (`ICON_HEART_FILLED` / `ICON_HEART_UNFILLED`, `ACTION_TOGGLE_LIKE`) sincronizzata in tempo reale con i preferiti locali su display auto, notifica e schermata di blocco.
   - Dichiarazione `res/xml/automotive_app_desc.xml` e `<meta-data android:name="com.google.android.gms.car.application" ... />` nel Manifest.
2. **Widget Glance in stile Spotify (`GMusicGlanceWidget`, `GMusicWidgetReceiver`)**:
   - Widget ridimensionabili `4x1` (`SMALL_4X1`) e `4x2` (`MEDIUM_4X2`) basati su Jetpack Glance.
   - Mostra titolo, artista, stato preferito (`♥`/`♡`), controlli Precedente/Play-Pausa/Successivo e apre l'app al tocco.
   - Stato inattivo: mostra il prompt `"♫ Apri GMusic"`.
   - Aggiornamento in tempo reale ad ogni cambio di traccia, stato play/pausa o like in `GMusicMediaLibraryService`.

## Come testare con la Desktop Head Unit (DHU) di Android Auto
1. Sul telefono, apri l'app **Android Auto** -> Impostazioni -> tocca 10 volte la versione per sbloccare le opzioni sviluppatore -> nel menu in alto a destra seleziona **Avvia server unità principale (Start head unit server)**.
2. Sul PC Windows con Android SDK, esegui:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```
3. Nella finestra DHU, seleziona **GMusic** dal selettore app multimediali per sfogliare `Home`, `Cronologia`, `Playlist`, `Brani piaciuti`, usare il pulsante Cuore o inviare comandi vocali.
