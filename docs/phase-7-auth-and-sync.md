# Fase 7 — Login Google/YouTube Music, Salvataggio Cookie Cifrato e Sincronizzazione Account

## Cosa è stato implementato
1. **Autenticazione WebView e Manuale (`WebLoginScreen`, `AuthCredentialStore`)**:
   - Login WebView a Google / YouTube Music con User-Agent Chrome Mobile standard, estrazione automatica dei cookie (`SAPISID`, `__Secure-3PAPISID`, `LOGIN_INFO`) e di `VISITOR_DATA`.
   - Schermata/dialogo di fallback `"Incolla cookie manualmente"` in Impostazioni.
   - Archiviazione cifrata con Android Keystore tramite `EncryptedSharedPreferences` (`AES256_SIV` / `AES256_GCM`), senza mai loggare i cookie.
   - Calcolo dinamico dell'header `Authorization: SAPISIDHASH <timestamp>_<sha1>` per le richieste autenticate verso `https://music.youtube.com`.
2. **Sincronizzazione con YouTube Music (`InnerTubeClient`, `YtMusicSyncWorker`, `YtMusicSyncScheduler`)**:
   - Quando l'utente è autenticato:
     - La Home mostra il feed personalizzato dell'account YouTube Music.
     - I like/dislike (`like/like`, `like/removelike`) e le playlist create/modificate (`playlist/create`, `browse/edit_playlist`, `playlist/delete`) vengono accodati in `WorkManager` (`YtMusicSyncWorker`) con politica di retry automatico (`Result.retry()`) in caso di assenza temporanea di rete.
   - Il database locale Room rimane sempre la fonte primaria per la UI; in modalità Ospite tutto funziona al 100% in locale.
