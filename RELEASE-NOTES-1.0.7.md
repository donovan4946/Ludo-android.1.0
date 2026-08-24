# Ludorum Android 1.0.7 — UI + Cart Stability

## Navigation basse
Nouvelle DA Ludorum :
- bandeau supérieur en 3 couleurs bleu / jaune / rouge ;
- Accueil actif : bleu ;
- Compte actif : jaune ;
- Favoris actif : rouge ;
- Panier actif : bleu ;
- icône + texte intégrés dans une vraie pastille de navigation ;
- petit marqueur de couleur sous chaque onglet ;
- ombre plus marquée sur l'onglet actif.

## Panier qui disparaissait
Cause corrigée :
le panier premium pouvait être inséré dans un conteneur WooCommerce que
le script masquait ensuite lui-même.

Nouveau fonctionnement :
- le panier premium est toujours inséré comme frère du panier WooCommerce ;
- aucun parent contenant `#ludorum-cart-premium` ne peut être masqué ;
- le script ne remonte plus arbitrairement dans les grands parents DOM ;
- si WooCommerce reconstruit temporairement son HTML, le panier premium
  déjà présent reste affiché au lieu de devenir blanc ;
- visibilité/opacity/display du shell Ludorum sont sécurisés.

## Version
versionName : 1.0.7
versionCode : 107
artefact GitHub :
`ludorum-android-v1.0.7-ui-cart-stability-debug`
