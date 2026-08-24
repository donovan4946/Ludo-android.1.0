# Ludorum Android 1.0.9 — Exact Quantity

## Ajout panier
Nouveau moteur basé sur WooCommerce Store API.

Règle :
- 1 clic = +1 exemplaire exactement.
- lecture de la quantité avant ajout ;
- POST add-item quantity=1 ;
- lecture de la quantité renvoyée ;
- si Woo/plugin renvoie une quantité différente, PUT update-item pour
  remettre exactement ancienne quantité + 1.
- blocage d'un second ajout du même produit tant que le premier n'est
  pas terminé.

## Croix rouge
Nouvelle sémantique :
- quantité 6 -> croix -> 5
- quantité 2 -> croix -> 1
- quantité 1 -> croix -> suppression complète de la ligne

Version : 1.0.9
versionCode : 109
artefact : ludorum-android-v1.0.9-exact-quantity-debug
