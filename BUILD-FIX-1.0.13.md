# Ludorum Android 1.0.13 — Lambda Compile Fix

Erreur GitHub exacte :
`CartService.java: local variables referenced from a lambda expression must be final or effectively final`

Cause :
`after` peut être réassigné après une normalisation de quantité, puis
`after.itemsCount` était relu à l'intérieur de `MAIN.post(() -> ...)`.

Correction :
- `final int finalItemsCount = after.itemsCount;`
- `final List<String> setCookies = after.setCookies;`
- la lambda n'accède plus à la variable mutable `after`.

Validation renforcée :
`CartService.java` a été réellement compilé avec javac 17 après correction.

Le warning GitHub Node.js 20/24 n'est pas la cause de cet échec de compilation.

Version : 1.0.13
versionCode : 113
artefact : ludorum-android-v1.0.13-lambda-fix-debug
