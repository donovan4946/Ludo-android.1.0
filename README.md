# Ludorum Android 1.0.3 — Performance Update

Passe dédiée à la fluidité, sans changement de DA.

## Images
- déduplication des téléchargements d'une même image ;
- une image présente dans plusieurs sections n'est téléchargée qu'une fois ;
- décodage échantillonné pour éviter les bitmaps inutilement énormes ;
- cache mémoire augmenté à 32 Mo ;
- timeouts réseau raccourcis.

## Catalogue / accueil
- cache mémoire WooCommerce :
  - produits : 30 secondes ;
  - fiche produit : 30 secondes ;
  - catégories : 5 minutes ;
- retour vers Accueil/Boutique beaucoup plus rapide ;
- anciennes requêtes ne construisent plus des vues/images si l'utilisateur
  a déjà changé d'écran.

## Ajouter au panier
- suppression du second GET WooCommerce bloquant après un AJAX réussi ;
- `fragments/cart_hash` reste la validation réelle de l'ajout ;
- le bouton peut afficher `✓ Ajouté` immédiatement après la réponse Woo ;
- le bandeau TTC récupère ensuite le montant réel du panier.

## Bandeau panier
- cache très court (650 ms) pour éviter deux requêtes identiques au même instant ;
- suppression de la double requête au démarrage d'une Activity.

## WebView / panier / compte
- injection JavaScript rendue idempotente ;
- suppression d'une vague complète de re-skin inutile ;
- MutationObserver limité aux changements de structure DOM, au lieu de surveiller
  toutes les modifications d'attributs de toute la page ;
- suppression du preraster hors écran inutile ;
- fond blanc immédiat pour limiter les flashs de chargement ;
- cache WebView standard conservé pour ne pas afficher un panier périmé.

## Important
Aucun cache long n'est utilisé pour le Panier/Checkout.
Les données commerciales sensibles au temps restent relues depuis WooCommerce.

## Version
- versionCode : 103
- versionName : 1.0.3
- artefact GitHub : `ludorum-android-v1.0.3-performance-debug`
