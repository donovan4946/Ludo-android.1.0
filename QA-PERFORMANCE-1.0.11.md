# Ludorum Android 1.0.11 — Safe Performance + QA

Passe volontairement prudente : aucune réécriture du moteur panier.

## Optimisations appliquées
- Au démarrage, l'Accueil n'est plus construit puis jeté lorsqu'une route Produit/Catégorie/Favoris/Boutique est demandée.
- Un clic sur Accueil lorsqu'Accueil est déjà affiché remonte simplement en haut au lieu de reconstruire les 3 sections.
- Un clic sur Favoris déjà actif remonte en haut au lieu de refaire toute la liste.
- Dans WebActivity, Compte/Panier déjà actifs ne rechargent plus la même URL.
- Lors d'un vrai changement Compte <-> Panier, l'ancien chargement est arrêté avant le nouveau.
- Le skin JS Ludorum n'est plus injecté deux fois à moins de 1,8 s sur la même URL.
- Le MutationObserver JS lourd ne tourne plus sur LudoMatch/fiche option ; seulement Panier ou Compte.
- Le rafraîchissement du ticker à onPageFinished n'est plus forcé.
- Les callbacks obsolètes ProductSection sont ignorés aussi en cas d'erreur.

## Hyperliens / routes corrigés
- https://ludorum.fr/ -> Accueil natif.
- /favoris/ -> Favoris natifs.
- /boutique/ et /shop/ -> Boutique native.
- /produit/... -> fiche produit native.
- /categorie-produit/... -> catalogue natif filtré.
- recherche produit -> recherche native.
- Panier / Compte / Checkout restent dans le WebView WooCommerce prévu.

## Audit interactions
- OK — Accueil natif
- OK — Accueil depuis Web/Panier
- OK — Compte
- OK — Favoris natifs depuis Main
- OK — Favoris natifs depuis Web
- OK — Panier
- OK — Retour boutique
- OK — Racine ludorum.fr -> Accueil natif
- OK — Lien /favoris -> Favoris natifs
- OK — Fiche produit native
- OK — Catégorie native
- OK — Recherche native
- OK — LudoMatch
- OK — LudoMatch Groupe
- OK — Produit variable options
- OK — Ajouter au panier quantité exacte
- OK — Croix rouge -1
- OK — Croix rouge supprime à quantité 1
- OK — Boutons quantité +/-
- OK — Checkout
- OK — mailto/tel/sms/geo/market
- OK — Intent Android
- OK — Paiement popup WebView
- OK — Portrait verrouillé
- OK — Home startup sans rendu inutile
- OK — Home actif sans reconstruction
- OK — Web tabs sans reload inutile
- OK — Skin WebView throttlé
- OK — Observer JS limité
- OK — Ticker pageFinish non forcé
- OK — Stale callback ProductSection bloqué

## Contrôles de régression
- OK — Un seul WebActivity.onResume
- OK — Store API quantité conservée
- OK — PUT cart/items conservé
- OK — Panier premium stable
- OK — DA bottom nav conservée
- OK — Pas de Toast Web
- OK — Portrait
- OK — Version 1.0.11
- OK — Artifact

Validation effectuée : audit statique des sources + syntaxe JavaScript.
Le binaire Android final reste compilé par GitHub Actions.

Version : 1.0.11
versionCode : 111
Artefact : ludorum-android-v1.0.11-safe-perf-qa-debug
