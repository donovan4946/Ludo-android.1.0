# Ludorum Android 1.0.0

Première version fonctionnelle complète de l'application Android Ludorum.

## Expérience native

- accueil Android Ludorum ;
- recherche produits ;
- catégories WooCommerce synchronisées ;
- suppression des catégories techniques / Non classé / jetons-compteurs ;
- Nouveautés ;
- Promotions ;
- Meilleures ventes ;
- catalogue paginé ;
- cartes produits alignées ;
- LudoMatch et LudoMatch Groupe intégrés à l'application.

## Compte

- onglet Compte dans la barre basse ;
- connexion au compte WordPress / WooCommerce ;
- même session WooCommerce utilisée pendant le parcours d'achat ;
- texte « vos adresses » supprimé de la présentation mobile de Mon compte ;
- navigation Compte ↔ Panier sécurisée.

## Favoris

- cœur vide rouge sur les produits ;
- cœur rouge plein lorsque le produit est favori ;
- second appui pour retirer ;
- état persistant sur l'appareil ;
- page Favoris Android native ;
- ajout au panier possible depuis Favoris.

Note : la wishlist 1.0.0 est locale à l'application et n'est pas encore
synchronisée côté serveur avec TI WooCommerce Wishlist.

## Panier

- véritable ajout WooCommerce via `wc-ajax=add_to_cart` ;
- ajout séquentiel fiable : plusieurs ajouts augmentent réellement le panier ;
- cookies WooCommerce partagés avec le WebView ;
- lecture de contrôle via WooCommerce Store API ;
- panier premium Ludorum ;
- une seule interface panier visible ;
- interface WooCommerce d'origine masquée ;
- quantité avec boutons − / + ;
- suppression produit ;
- sous-total et total ;
- code promo masqué sur la première étape Panier ;
- résumé de commande Ludorum ;
- bouton « Passer à la commande ».

## Commande et paiement

- Panier → Checkout / Commande ;
- `wc-ajax=checkout` autorisé ;
- `wc-api` autorisé ;
- order-pay et order-received autorisés ;
- Stripe / PayPal / 3D Secure et intents bancaires conservés ;
- boutique WooCommerce technique bloquée dans l'application.

## Interface / Android

- logo officiel Ludorum ;
- icône d'application « L » Ludorum ;
- barre Android inférieure respectée ;
- navigation basse : Accueil / Compte / Favoris / Panier ;
- scroll WebView optimisé ;
- overlays transparents neutralisés ;
- réseaux sociaux dans un dock repliable ;
- dock réseaux replié par défaut ;
- trackers non essentiels filtrés dans le WebView.

## Release

- `versionName`: `1.0.0`
- `versionCode`: `100`
- artefact GitHub Actions : `ludorum-android-v1.0.0-debug`
