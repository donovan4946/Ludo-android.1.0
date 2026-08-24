# Ludorum Android 1.0.4 — Build Recovery

Corrections exactes issues du log GitHub :

1. ApiClient :
   suppression du cache générique Map<String,Object>.
   Les caches ProductPage, Product et ProductCategory sont maintenant
   totalement séparés et typés.

2. MainActivity :
   réécriture complète de loadCategoryBySlug().
   La requête utilise uniquement `categoryRequestGeneration`.

3. WebActivity :
   conservation du correctif `allowProductPage`.

Validation supplémentaire :
ApiClient.java a été réellement compilé avec javac 17 et des stubs locaux.

Artefact attendu :
`ludorum-android-v1.0.4-build-recovery-debug`
