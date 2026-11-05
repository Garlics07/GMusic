# Fase 3 — Libreria, Playlist Locali, Home da Cronologia, Esplora (IT), Ricerca Completa e Pagine Dettaglio

## Cosa è stato implementato
1. **Libreria e Playlist locali (`LibraryScreen`, `LikedSongsScreen`, `PlaylistDetailScreen`)**:
   - Sezione fissata "Canzoni preferite" con gradiente Material You e icona cuore.
   - Creazione, rinomina, eliminazione di playlist locali in Room (`playlists`, `playlist_tracks`).
   - Copertina automatica con mosaico 2x2 delle prime 4 tracce (`PlaylistMosaicCover`).
   - Riordinamento drag-and-drop (e controlli rapidi su/giù) dei brani nella playlist con salvataggio dell'ordine in Room.
   - Menu contestuale su ogni brano (`TrackRowWithMenu`) per aggiungere/rimuovere dai preferiti, aggiungere a una playlist esistente o nuova (`AddToPlaylistDialog`), o navigare a pagina Artista/Album.
2. **Home (`HomeScreen`, `HomeViewModel`)**:
   - Saluto dinamico in base all'ora in italiano.
   - Griglia 2x3 "Riproduzioni recenti" dalla cronologia locale (nascosta se vuota).
   - Caroselli orizzontali "Consigliati per te" e "Mix per [Artista]" generati dalla cronologia locale tramite radio/correlati InnerTube (`fetchRadioTracks`), con fallback al feed anonimo/autenticato YT Music e caricamento paginato.
3. **Esplora Italia (`ExploreScreen`, `ExploreViewModel`)**:
   - Classifiche Italia, Nuove uscite e griglia Generi/Stati d'animo con parametri `gl=IT` e `hl=it`.
4. **Ricerca Completa (`SearchScreen`, `SearchViewModel`)**:
   - Filtri a chip (`Tutto`, `Brani`, `Artisti`, `Album`, `Playlist`), suggerimenti live mentre si digita (`music/get_search_suggestions` + NewPipe fallback) e cronologia ricerche cancellabile.
5. **Pagine Dettaglio (`AlbumScreen`, `ArtistScreen`)**:
   - Pagina Album con copertina HD, artista, lista tracce, Riproduci/Casuale, Salva in libreria.
   - Pagina Artista con immagine circolare HD, brani popolari, avvio radio artista, caroselli Album, Singoli e Artisti simili.
