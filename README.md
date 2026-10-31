# Anti-Espion (Android)

Appli qui assombrit l'écran par-dessus toutes les applis pour que seul toi, de près, puisses lire.
Marche sur écran OLED et LCD (c'est du logiciel, pas un vrai filtre matériel).

## Ce que fait l'appli

- **Filtre sombre** réglable (10 à 100 %, limité à environ 80 % réel par Android pour laisser passer les touches).
- **Bande de lecture** : tout est sombre sauf une bande que tu déplaces avec la poignée blanche sur le bord droit.
- **Détection d'un 2e visage** : la caméra frontale compte les visages ; à 2 ou plus, l'écran passe en noir, puis revient quand la personne part. Rien n'est enregistré.

## Limites

- Ce n'est pas un vrai écran anti-espion (angle de vue fermé) : ça, c'est du matériel (film ou dalle spéciale).
- Le voisin doit être dans le champ de la caméra pour être détecté.
- Android 14 minimum (`minSdk 34`).
- La détection par caméra consomme de la batterie.
- Code non testé sur un téléphone : il n'a pas pu être compilé là où il a été écrit.

## Compiler l'APK

### Option 1 : Android Studio (PC/Mac/Linux)
Ouvrir le dossier, laisser Gradle synchroniser, puis **Build > Build APK(s)**.
L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

### Option 2 : GitHub (sans rien installer)
1. Créer un dépôt GitHub et y envoyer tout le contenu de ce dossier (y compris `.github`).
2. Onglet **Actions** > **Compiler l'APK** > **Run workflow**.
3. Quand c'est vert, télécharger l'artefact `anti-espion-apk` : l'APK est dedans.

## Installer

Ouvrir l'APK sur le téléphone (autoriser l'installation d'applis inconnues si demandé),
lancer l'appli, activer l'interrupteur, accepter « Affichage par-dessus les autres applis ».
