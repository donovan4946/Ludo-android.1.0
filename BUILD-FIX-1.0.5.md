# Ludorum Android 1.0.5 — Build Fix

Erreur GitHub corrigée :

`WebActivity.java: method onResume() is already defined`

Cause :
WebActivity contenait deux méthodes `onResume()`.

Correction :
une seule méthode `onResume()` est conservée et elle exécute désormais :
- `web.onResume()` pour reprendre le WebView ;
- `cartTicker.refresh(true)` pour mettre à jour le bandeau.

Un audit automatique vérifie désormais l'absence de doublons parmi :
onCreate, onStart, onResume, onPause, onStop, onDestroy,
onNewIntent, onSaveInstanceState, onRestoreInstanceState et onBackPressed.

Artefact GitHub attendu :
`ludorum-android-v1.0.5-buildfix-debug`
