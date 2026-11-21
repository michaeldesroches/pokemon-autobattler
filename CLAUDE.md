# CLAUDE.md — Pokémon Auto Battler

Contexte projet pour Claude (et pour les humains). **Ce fichier doit être mis à jour dans chaque PR** : la CI (`.github/workflows/claude-md.yml`) échoue sinon. Exception possible avec le label `skip-claude-md` (typo, changement purement cosmétique).

## Le projet

Auto battler façon TFT avec des Pokémon de 1re génération, écrit en **Java** avec **libGDX 1.14**. Il tourne sur desktop (LWJGL3) et dans le navigateur (TeaVM via [gdx-teavm](https://github.com/xpenatan/gdx-teavm)). Le build web est déployé sur GitHub Pages : https://michaeldesroches.github.io/pokemon-autobattler/

Objectif long terme : une version façon TFT (grille hexagonale, déplacements, objets, lobby 8 joueurs). Voir `docs/ROADMAP.md`.

## État actuel

- Étape 0 faite : passage de Swing + CheerpJ à libGDX + TeaVM (PR #1).
- Étape 1 faite : moteur façon TFT (PR #2) — champ de bataille hexagonal 7×8 (4 lignes par camp), déplacements A*, portée par lignée (1 à 4 cases), mana et une capacité unique par lignée (23), statuts (brûlure, poison, gel/étourdissement, sommeil, paralysie, hâte, provocation, bouclier), prolongation après 30 s.
- Étape 2 faite : méta-jeu (PR #3) — niveau/XP (niveau 3 à 9 = taille d'équipe, fixe les chances de tier), pool partagé (18/14/10 exemplaires par Pokémon de base selon le tier), objets (6 composants → 21 objets combinés, 3 max par Pokémon, inventaire de 10), manches contre des Pokémon sauvages (2, 6, 10, 14…) qui donnent des composants.
- Gameplay : boutique 3 tiers, banc 8 places, fusion par 3 (évolution puis étoiles, objets et exemplaires conservés), économie avec intérêts et séries, adversaires générés selon la manche (avec objets à partir de la manche 5), 16 synergies de type.
- Étape 3 faite : visuel (PR `etape-3-visuel`) — interface 16:9 (1280×720), plateau hexagonal en perspective, sprites animés PMDCollab (Idle/Walk/Attack/Hurt/Sleep, 8 directions dont 3 par symétrie) orientés vers leur cible, portraits, particules par type, tremblement d'écran, bruitages et musique générés par script, bouton son.
- Reporté : classes (Starter…), carrousel, portraits des autres joueurs (avec le lobby). Prochaine étape : étape 4 (lobby à 8 joueurs, d'abord 7 bots) — voir `docs/ROADMAP.md`.

## Structure

| Chemin | Contenu |
| --- | --- |
| `core/src/main/java/autobattler/` | Logique pure, **sans dépendance libGDX** : `PType`, `Species`, `Pokedex` (espèces + kit portée/capacité par lignée), `Ability`, `Item` (composants, objets combinés, effets), `Unit` (+ objets tenus, exemplaires), `Synergies`, `Hex` (grille, distances, A*), `Fighter`, `Battle` (moteur), `GameState`, `Sim` |
| `core/src/test/java/autobattler/` | Tests JUnit 5 : `HexTest` (grille, A*), `BattleTest` (kits, fin des combats, pas de superposition, déterminisme), `ItemTest` (recettes, équipement, stats), `GameStateTest` (XP, pool partagé, fusion avec objets, butin) |
| `core/src/main/java/autobattler/gfx/` | Rendu et entrées libGDX : `AutoBattlerGame` (ApplicationAdapter, tremblement de caméra), `GameView` (mise en page 1280×720, plateau en perspective, animations, particules, souris), `Canvas` (dessin en coordonnées haut-gauche), `Assets` (polices, textures UI, sprites PokéAPI de secours), `PmdSprites` (atlas animé PMD + portraits), `Sounds` (bruitages + musique), `Rect` |
| `lwjgl3/` | Lanceur desktop (`Lwjgl3Launcher`) |
| `teavm/` | Lanceur web (`WebLauncher`) + configuration du plugin `com.github.xpenatan.gdx-teavm` |
| `assets/` | **Généré** par `tools/gen_assets.py` : `pmd0.png`, `pmd1.png` + `pmd.txt` (sprites animés et portraits PMDCollab), `sprites.png` + `sprites.txt` (PokéAPI, secours), `ui/*.png`, `fonts/*.fnt|png` ; et par `tools/gen_audio.py` : `sfx/*.wav`, `music/theme.mp3` |
| `tools/gen_assets.py` | Génère les assets (atlas PMD rogné + index, portraits, `CREDITS-PMD.md`, sprites PokéAPI, textures UI, polices bitmap Fira Sans en 2x) |
| `tools/gen_audio.py` | Synthétise les bruitages et la musique (numpy + ffmpeg) |
| `CREDITS-PMD.md` | Crédits des sprites PMDCollab (CC BY-NC 4.0, obligatoires) — généré |
| `tools/smoke-test/` | Test de fumée Playwright du build web (captures d'écran) |
| `docs/ROADMAP.md` | Feuille de route vers la version façon TFT |

## Commandes

```bash
./gradlew :lwjgl3:run                    # jouer en desktop
./gradlew :teavm:gdx_teavm_web_js_run    # build web + serveur local http://localhost:8080
./gradlew :teavm:gdx_teavm_web_js_build  # build web -> teavm/build/dist/js/webapp
./gradlew :core:simulate                 # bot qui joue 300 parties (équilibrage)
./gradlew :core:test                     # tests du moteur
./gradlew build                          # compile tout
python3 tools/gen_assets.py              # régénère assets/ graphiques (Pillow requis, ~2 min au 1er lancement)
python3 tools/gen_audio.py               # régénère sfx/ et music/ (numpy + ffmpeg)
```

## Conventions et contraintes

- **Langue** : code (noms) en anglais, commentaires, messages de jeu et docs en français.
- **TeaVM** : pas d'AWT/Swing, pas de réflexion, éviter `String.format` (utiliser des helpers comme `GameView.fmt2`). Le code compile en bytecode Java 17 (`options.release = 17`, requis par gdx-teavm).
- **Logique séparée du rendu** : rien dans `autobattler.*` (hors `gfx`) ne doit importer libGDX, pour que `Sim` et de futurs tests tournent sans GPU. Les couleurs des types sont des `int` 0xRRGGBB (`PType.rgb`).
- **Coordonnées** : la mise en page est en 1280×720 avec origine en haut à gauche ; `Canvas` convertit vers le repère libGDX (y vers le haut). `FitViewport` gère la mise à l'échelle. Plateau en perspective : facteur `persp(ligne) = 0,78 + 0,32 × ligne / 7`, centre x = 560 + (col + 0,5 si ligne impaire − 3,25) × 84 × persp, y cumulé à partir de 126 (voir `GameView.ROW_Y` ; le test de fumée recalcule la même formule). Banc : 8 cases 82×76 à partir de (211, 480), pas 88. Objets : 5×2 cases 32×34 à partir de (12, 480). Boutique : 5 cartes 134×142 à partir de (213, 568), pas 140. Boutons : Relancer (928,568), Acheter XP (1102,568), Verrouiller (928,618), Vendre (1102,618), Combat (928,664, 340×46), Son (1196,9).
- **Sprites PMD** : dans `pmd.txt`, chaque image a un décalage (ox, oy) de son coin haut-gauche par rapport au point au sol (= centre de la frame PMD). Directions 0 bas, 1 bas-droite, 2 droite, 3 haut-droite, 4 haut (5 à 7 = miroir de 3 à 1). Les animations sont pilotées côté vue (`GameView.AnimState`) à partir des événements de `Battle` ; la logique ne connaît pas les animations.
- **Licence** : les sprites PMDCollab sont en CC BY-NC 4.0 → projet non commercial et `CREDITS-PMD.md` à garder à jour (régénéré par `gen_assets.py`).
- **Assets** : ne pas éditer `assets/` à la main, modifier `tools/gen_assets.py` puis le relancer. Les sprites et polices sont en 2x (polices affichées à l'échelle 0.5, sprites en filtrage Nearest).
- **Pokédex** : ajouter une espèce = une ligne `add(...)` dans `Pokedex.java` puis `python3 tools/gen_assets.py` (le script lit les numéros dans `Pokedex.java`).
- **Moteur de combat** (`Battle`) : pas fixe de 50 ms, déterministe à graine donnée. Chaque tick : statuts → déplacement en cours → (si non neutralisé) ciblage de l'ennemi le plus proche (provocation prioritaire, Spectre = le plus affaibli) → attaque si à portée, sinon un pas A* (`Hex.nextStep`). Mana : +10 par attaque, +5 par coup reçu ; à `maxMana`, la capacité remplace l'attaque. Dégâts = `DAMAGE_BASE (22) × puissance × Atq / Déf × efficacité × prolongation`.
- **Ajouter une capacité** : une entrée dans `Ability` (libellé, description, mana max, mana de départ), un `case` dans `Battle.cast`, puis l'associer à une lignée avec `kit(...)` dans `Pokedex`. Les zones émettent un événement `AREA` pour l'affichage.
- **Objets** : `Item` définit 6 composants (stats) et 21 combinés (stats des deux composants + un `Item.Effect`). Les effets sont lus dans `Fighter.applyItem` puis appliqués dans `Battle` (`hit`, `damage`, `basicAttack`, `cast`). Ajouter un objet = un `combine(...)` dans `Item` (toutes les paires doivent rester couvertes, cf. `ItemTest`) + l'éventuel effet. `Unit.equip` combine automatiquement deux composants.
- **Économie / niveau** (`GameState`) : `XP_TO_NEXT`, `ODDS` (chances par niveau), `POOL_SIZE`, `isPveRound`, `generateEnemy` (adversaires) et `generateWild` (sauvages). Les adversaires ne puisent pas dans le pool partagé (il servira aux bots de l'étape 4).
- **Grille** : cases indexées `row * 7 + col` (odd-r, hexagones pointe en haut). Camp joueur = lignes 4 à 7 (4 = première ligne), adversaire = lignes 0 à 3. `GameState.board[ligneLocale][col]` avec ligne locale 0 = première ligne ; conversion via `Hex.fromLocal`.
- **Équilibrage** : après un changement de stats ou de règles, lancer `./gradlew :core:simulate` et noter la manche moyenne (référence actuelle : ~10,2 pour le bot naïf, qui achète de l'XP au-delà de 14 or et équipe ses objets ; durée moyenne d'un combat 15 à 28 s, moins de 3 % au temps limite).

## CI / déploiement

- `.github/workflows/build.yml` : sur chaque push et PR, compile, lance les tests et la simulation, construit le web, puis lance le test de fumée dans Chromium (captures en artefact `screenshots`). Sur `main`, déploie sur GitHub Pages.
- GitHub Pages doit être configuré avec la source **GitHub Actions** (Settings → Pages).
- `.github/workflows/claude-md.yml` : vérifie que chaque PR modifie `CLAUDE.md`. Pour le rendre bloquant, l'ajouter comme check requis dans la protection de la branche `main`.

## Notes pour Claude

- L'environnement cloud de Claude n'a pas accès à Maven Central ni au portail Gradle : impossible d'y lancer `./gradlew`. Pour vérifier la compilation localement, compiler contre les sources de libGDX (clone partiel de `libgdx/libgdx`, dossier `gdx/src`, avec des bouchons pour `com.badlogic.gdx.jnigen`), et les tests contre de petits bouchons JUnit (`@Test`, `Assertions`) lancés par réflexion ; puis s'appuyer sur la CI (logs lisibles avec `gh run view --log`). Hors `main`, le workflow imprime les captures du test de fumée en base64 dans les logs.
- **Ouvrir une PR** : `gh pr create` échoue (GraphQL bloqué depuis les sessions Claude). Passer par l'API REST : `gh api repos/michaeldesroches/pokemon-autobattler/pulls -f title='…' -f head='<branche>' -f base='main' -F body=@corps.md`.
- Versions alignées sur la matrice de gdx-teavm : gdx-teavm 1.6.2 ↔ libGDX 1.14.2 ↔ TeaVM 0.15.0, Gradle 9.6.1.
- Sprites : PokéAPI/sprites. Polices : Fira Sans (OFL), repli DejaVu Sans pour ★ ● →.
