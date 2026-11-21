# CLAUDE.md — Pokémon Auto Battler

Contexte projet pour Claude (et pour les humains). **Ce fichier doit être mis à jour dans chaque PR** : la CI (`.github/workflows/claude-md.yml`) échoue sinon. Exception possible avec le label `skip-claude-md` (typo, changement purement cosmétique).

## Le projet

Auto battler façon TFT avec des Pokémon de 1re génération, écrit en **Java** avec **libGDX 1.14**. Il tourne sur desktop (LWJGL3) et dans le navigateur (TeaVM via [gdx-teavm](https://github.com/xpenatan/gdx-teavm)). Le build web est déployé sur GitHub Pages : https://michaeldesroches.github.io/pokemon-autobattler/

Objectif long terme : une version façon TFT (grille hexagonale, déplacements, objets, lobby 8 joueurs). Voir `docs/ROADMAP.md`.

## État actuel

- Étape 0 de la roadmap faite : passage de Swing + CheerpJ à libGDX + TeaVM (PR `libgdx`).
- Gameplay : boutique 3 tiers, banc 8 places, plateau 2×4, fusion par 3 (évolution puis étoiles), économie avec intérêts et séries, adversaires générés selon la manche, 16 synergies de type, attaques spéciales à 100 d'énergie.
- Prochaine étape : étape 1 (moteur façon TFT : hexagones, déplacements A*, portée, capacités uniques).

## Structure

| Chemin | Contenu |
| --- | --- |
| `core/src/main/java/autobattler/` | Logique pure, **sans dépendance libGDX** : `PType`, `Species`, `Pokedex`, `Unit`, `Synergies`, `Fighter`, `Battle`, `GameState`, `Sim` |
| `core/src/main/java/autobattler/gfx/` | Rendu et entrées libGDX : `AutoBattlerGame` (ApplicationAdapter), `GameView` (mise en page, dessin, souris), `Canvas` (dessin en coordonnées haut-gauche), `Assets`, `Rect` |
| `lwjgl3/` | Lanceur desktop (`Lwjgl3Launcher`) |
| `teavm/` | Lanceur web (`WebLauncher`) + configuration du plugin `com.github.xpenatan.gdx-teavm` |
| `assets/` | **Généré** par `tools/gen_assets.py` : `sprites.png` + `sprites.txt` (atlas), `ui/*.png`, `fonts/*.fnt|png` |
| `tools/gen_assets.py` | Génère les assets (sprites PokéAPI rognés, textures UI, polices bitmap Fira Sans en 2x) |
| `tools/smoke-test/` | Test de fumée Playwright du build web (captures d'écran) |
| `docs/ROADMAP.md` | Feuille de route vers la version façon TFT |

## Commandes

```bash
./gradlew :lwjgl3:run                    # jouer en desktop
./gradlew :teavm:gdx_teavm_web_js_run    # build web + serveur local http://localhost:8080
./gradlew :teavm:gdx_teavm_web_js_build  # build web -> teavm/build/dist/js/webapp
./gradlew :core:simulate                 # bot qui joue 300 parties (équilibrage)
./gradlew build                          # compile tout
python3 tools/gen_assets.py              # régénère assets/ (Pillow requis)
```

## Conventions et contraintes

- **Langue** : code (noms) en anglais, commentaires, messages de jeu et docs en français.
- **TeaVM** : pas d'AWT/Swing, pas de réflexion, éviter `String.format` (utiliser des helpers comme `GameView.fmt2`). Le code compile en bytecode Java 17 (`options.release = 17`, requis par gdx-teavm).
- **Logique séparée du rendu** : rien dans `autobattler.*` (hors `gfx`) ne doit importer libGDX, pour que `Sim` et de futurs tests tournent sans GPU. Les couleurs des types sont des `int` 0xRRGGBB (`PType.rgb`).
- **Coordonnées** : la mise en page est en 1000×690 avec origine en haut à gauche ; `Canvas` convertit vers le repère libGDX (y vers le haut). `FitViewport` gère la mise à l'échelle.
- **Assets** : ne pas éditer `assets/` à la main, modifier `tools/gen_assets.py` puis le relancer. Les sprites et polices sont en 2x (polices affichées à l'échelle 0.5, sprites en filtrage Nearest).
- **Pokédex** : ajouter une espèce = une ligne `add(...)` dans `Pokedex.java` puis `python3 tools/gen_assets.py` (le script lit les numéros dans `Pokedex.java`).
- **Équilibrage** : après un changement de stats ou de règles, lancer `./gradlew :core:simulate` et noter la manche moyenne (référence actuelle : ~9,3 pour le bot naïf).

## CI / déploiement

- `.github/workflows/build.yml` : sur chaque push et PR, compile, lance la simulation, construit le web, puis lance le test de fumée dans Chromium (captures en artefact `screenshots`). Sur `main`, déploie sur GitHub Pages.
- GitHub Pages doit être configuré avec la source **GitHub Actions** (Settings → Pages).
- `.github/workflows/claude-md.yml` : vérifie que chaque PR modifie `CLAUDE.md`. Pour le rendre bloquant, l'ajouter comme check requis dans la protection de la branche `main`.

## Notes pour Claude

- L'environnement cloud de Claude n'a pas accès à Maven Central ni au portail Gradle : impossible d'y lancer `./gradlew`. Pour vérifier la compilation localement, compiler contre les sources de libGDX (clone partiel de `libgdx/libgdx`, dossier `gdx/src`), puis s'appuyer sur la CI (logs lisibles avec `gh run view --log`). Hors `main`, le workflow imprime les captures du test de fumée en base64 dans les logs.
- Versions alignées sur la matrice de gdx-teavm : gdx-teavm 1.6.2 ↔ libGDX 1.14.2 ↔ TeaVM 0.15.0, Gradle 9.6.1.
- Sprites : PokéAPI/sprites. Polices : Fira Sans (OFL), repli DejaVu Sans pour ★ ● →.
