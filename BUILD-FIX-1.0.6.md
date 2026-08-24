# Ludorum Android 1.0.6 — Shop Return Fix

Bug corrigé :
depuis le Panier, le bouton/lien
« Revenir vers la boutique / Continuer mes achats »
pouvait ouvrir Favoris.

Correction :
- détection explicite des liens retour boutique ;
- routage prioritaire vers `screen=shop` ;
- cible native : Boutique Ludorum ;
- le JavaScript du panier force aussi ces boutons vers `/boutique/`
  afin d'éviter qu'un template WooCommerce injecte une mauvaise URL.

Favoris reste accessible uniquement via son propre onglet/route explicite.

Artefact GitHub :
`ludorum-android-v1.0.6-shop-return-fix-debug`
