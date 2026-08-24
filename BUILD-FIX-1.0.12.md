# Ludorum Android 1.0.12 — Illegal Forward Reference Fix

Erreur GitHub exacte :
`CartService.java: illegal forward reference`

Cause :
`CART_ADD_API` utilisait `CART_API` avant la déclaration de `CART_API`.

Ordre corrigé :
1. `BASE`
2. `CART_API`
3. `CART_ADD_API`

Aucun changement fonctionnel du panier :
- Store API conservée ;
- ajout +1 exact conservé ;
- croix rouge -1 conservée ;
- optimisations et routages de la v1.0.11 conservés.

Un contrôle automatique cherche également les autres forward references
évidentes entre constantes String statiques.

Version : 1.0.12
versionCode : 112
artefact : ludorum-android-v1.0.12-forward-ref-fix-debug
