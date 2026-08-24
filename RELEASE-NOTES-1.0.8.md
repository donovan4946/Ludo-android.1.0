# Ludorum Android 1.0.8 — Cart Remove Fix

Refonte de la suppression depuis les croix rouges du panier premium.

## WooCommerce classique
La croix Ludorum récupère maintenant directement l'URL WooCommerce
signée `remove_item + nonce` puis charge cette URL.
Elle ne dépend donc plus d'un clic sur un lien caché.

## WooCommerce Blocks / templates JS
Si aucune URL remove_item n'existe, un fallback déclenche explicitement
le véritable contrôle WooCommerce via un MouseEvent.

## UX
- zone tactile croix : 42 x 42 px ;
- protection contre les doubles clics ;
- affichage `…` pendant la suppression ;
- carte atténuée pendant l'opération ;
- bandeau TTC rafraîchi après modification.

Aucun popup supplémentaire n'a été ajouté.

Version : 1.0.8
versionCode : 108
artefact : ludorum-android-v1.0.8-cart-remove-fix-debug
