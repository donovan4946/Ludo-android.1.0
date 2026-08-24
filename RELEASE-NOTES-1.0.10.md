# Ludorum Android 1.0.10 — Home Routing Fix

Bug :
Panier -> Accueil pouvait afficher Favoris.

Cause réelle :
le bouton Accueil du WebView ne demandait aucun écran précis.
Il remettait simplement au premier plan l'ancienne MainActivity.
Si celle-ci était restée sur Favoris avant l'ouverture du Panier,
Favoris réapparaissait.

Correction :
- Panier / Compte WebView -> Accueil envoie maintenant `screen=home`;
- MainActivity traite explicitement `screen=home`;
- showHome() réinitialise les modes Boutique, Favoris et Produit.

Les correctifs quantité exacte et croix -1 de la v1.0.9 sont conservés.

Version : 1.0.10
versionCode : 110
artefact : ludorum-android-v1.0.10-home-routing-fix-debug
