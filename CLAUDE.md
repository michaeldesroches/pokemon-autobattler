# CLAUDE.md — Pokémon Auto Battler

Contexte projet pour Claude (et pour les humains). **Ce fichier doit être mis à jour dans chaque PR** : la CI (`.github/workflows/claude-md.yml`) échoue sinon. Exception possible avec le label `skip-claude-md` (typo, changement purement cosmétique).

## Le projet

Auto battler façon TFT avec des Pokémon de 1re génération, écrit en **Java** avec **libGDX 1.14**. Il tourne sur desktop (LWJGL3) et dans le navigateur (TeaVM via [gdx-teavm](https://github.com/xpenatan/gdx-teavm)). Le build web est déployé sur GitHub Pages : https://michaeldesroches.github.io/pokemon-autobattler/

Objectif long terme : une version façon TFT (grille hexagonale, déplacements, objets, lobby 8 joueurs). Voir `docs/ROADMAP.md`.

## État actuel

- Étape 0 faite : passage de Swing + CheerpJ à libGDX + TeaVM (PR #1).
- Étape 1 faite : moteur façon TFT (PR #2) — champ de bataille hexagonal 7×8 (4 lignes par camp), déplacements A*, portée par lignée (1 à 4 cases), mana et une capacité unique par lignée (23), statuts (brûlure, poison, gel/étourdissement, sommeil, paralysie, hâte, provocation, bouclier), prolongation après 30 s.
- Étape 2 faite : méta-jeu (PR `etape-2-meta-jeu`) — niveau/XP (niveau 3 à 9 = taille d'équipe, fixe les chances de tier), pool partagé (18/14/10 exemplaires par Pokémon de base selon le tier), objets (6 composants → 21 objets combinés, 3 max par Pokémon, inventaire de 10), manches contre des Pokémon sauvages (2, 6, 10, 14…) qui donnent des composants.
- Gameplay : boutique 3 tiers, banc 8 places, fusion par 3 (évolution puis étoiles, objets et exemplaires conservés), économie avec intérêts et séries, adversaires générés selon la manche (avec objets à partir de la manche 5), 16 synergies de type.
- Reporté : classes (Starter…) et carrousel. Prochaine étape : étape 3 (visuel 3/4) ou étape 4 (lobby avec 7 bots) — voir `docs/ROADMAP.md`.

## Structure

| Chemin | Contenu |
| --- | --- |
| `core/src/main/java/autobattler/` | Logique pure, **sans dépendance libGDX** : `PType`, `Species`, `Pokedex` (espèces + kit portée/capacité par lignée), `Ability`, `Item` (composants, objets combinés, effets), `Unit` (+ objets tenus, exemplaires), `Synergies`, `Hex` (grille, distances, A*), `Fighter`, `Battle` (moteur), `GameState`, `Sim` |
| `core/src/test/java/autobattler/` | Tests JUnit 5 : `HexTest` (grille, A*), `BattleTest` (kits, fin des combats, pas de superposition, déterminisme), `ItemTest` (recettes, équipement, stats), `GameStateTest` (XP, pool partagé, fusion avec objets, butin) |
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
./gradlew :core:test                     # tests du moteur
./gradlew build                          # compile tout
python3 tools/gen_assets.py              # régénère assets/ (Pillow requis)
```

## Conventions et contraintes

- **Langue** : code (noms) en anglais, commentaires, messages de jeu et docs en français.
- **TeaVM** : pas d'AWT/Swing, pas de réflexion, éviter `String.format` (utiliser des helpers comme `GameView.fmt2`). Le code compile en bytecode Java 17 (`options.release = 17`, requis par gdx-teavm).
- **Logique séparée du rendu** : rien dans `autobattler.*` (hors `gfx`) ne doit importer libGDX, pour que `Sim` et de futurs tests tournent sans GPU. Les couleurs des types sont des `int` 0xRRGGBB (`PType.rgb`).
- **Coordonnées** : la mise en page est en 1000×690 avec origine en haut à gauche ; `Canvas` convertit vers le repère libGDX (y vers le haut). `FitViewport` gère la mise à l'échelle. Hexagones de rayon 28 px à partir de (200, 58) : centre x = 200 + 24,25 + 48,5 × (col + 0,5 si ligne impaire), y = 86 + 42 × ligne (utile pour le test de fumée). Inventaire d'objets : 5×2 cases de 46×38 à partir de (728, 432). Boutons : Relancer (650,540), Acheter XP (824,540), Verrouiller (650,590), Vendre (650,636), Combat (824,590).
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
