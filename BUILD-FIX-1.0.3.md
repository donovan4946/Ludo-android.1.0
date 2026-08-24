# Ludorum Android 1.0.3 — Build Fix

Correction de compilation de WebActivity :
`allowProductPage` était utilisé par le Native Shop Router mais n'était
pas déclaré comme champ de la classe.

Correctif :
`private boolean allowProductPage = false;`

Aucun changement fonctionnel ou graphique supplémentaire.
Les optimisations de la v1.0.3 sont conservées.

Artefact GitHub attendu :
`ludorum-android-v1.0.3-performance-fixed-debug`
