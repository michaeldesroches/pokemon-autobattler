# Feuille de route vers une version façon TFT

Passer du prototype à une version façon TFT demande un vrai effort, mais il se découpe bien en étapes. Le plus dur n'est pas la vue 3/4. Ce sont trois choses : le **moteur avec déplacements**, le **contenu** (une capacité unique par Pokémon) et le **multijoueur**.

Les estimations supposent un dev seul en soirées, environ 10 h par semaine.

## État d'avancement

| Étape | Statut |
| --- | --- |
| 0. Décision technique | ✅ Fait : libGDX 1.14 + TeaVM (gdx-teavm), desktop LWJGL3 |
| 1. Moteur de combat façon TFT | ✅ Fait : hexagones, A*, portée, 23 capacités, statuts |
| 2. Méta-jeu TFT | À faire (prochaine étape) |
| 3. Visuel 3/4 | À faire |
| 4. Lobby à 8 joueurs | À faire |
| 5. Contenu et équilibrage | En continu |

## Étape 0 : décision technique (1 soirée) — fait

Swing + CheerpJ suffisait pour le prototype. Pour quelque chose qui ressemble à TFT (beaucoup d'animations, effets visuels, son), le jeu est passé sur **libGDX** : un vrai moteur de jeu 2D en Java, avec export web via TeaVM ([gdx-teavm](https://github.com/xpenatan/gdx-teavm)).

- Le build web est compilé et déployé sur GitHub Pages par GitHub Actions.
- Le moteur (`Battle`, `GameState`, `Pokedex`) a été réutilisé tel quel. Seul l'affichage a été réécrit.

## Étape 1 : moteur de combat façon TFT (2 à 3 semaines) — fait

C'est le cœur du jeu, à valider en premier.

Ce qui a été livré : grille 7×4 par camp, déplacements A*, portée de 1 à 4 cases selon la lignée, mana (+10 par attaque, +5 par coup reçu), une capacité unique par lignée (23 au total), statuts (brûlure qui réduit les soins, poison, gel, sommeil, paralysie, hâte, provocation, bouclier) et prolongation après 30 s (dégâts +20 % par seconde). Tests JUnit du moteur et bot de simulation à jour.

Plan initial :

- Une grille hexagonale de 7×4 cases par camp.
- Des déplacements : chaque unité avance vers sa cible avec une recherche de chemin (A*), en gérant les collisions et les cases occupées.
- Une portée d'attaque : corps à corps (1 case) ou distance (2 à 4 cases), selon le Pokémon.
- Le mana et une **capacité propre à chaque Pokémon** : zone, ligne, saut sur l'arrière-ligne, bouclier, soin, étourdissement.
- Des effets de statut : brûlure, gel, paralysie, poison, provocation.
- Le bot de simulation (`./gradlew :core:simulate`) reste utilisable pour vérifier qu'aucun combat ne bloque.

## Étape 2 : méta-jeu TFT (2 à 3 semaines)

- Un niveau de joueur avec de l'XP à acheter. Le niveau fixe la taille de l'équipe et les chances d'obtenir chaque tier en boutique.
- Un **pool partagé** : chaque Pokémon existe en nombre limité d'exemplaires pour tous les joueurs.
- **Les objets** : des composants qui se combinent à deux, avec environ 8 composants et 36 objets possibles. Thème Pokémon : Restes, Mouchoir Choix, Orbe Vie.
- Des manches contre des Pokémon sauvages qui donnent des objets, et éventuellement un carrousel.
- Des synergies refaites autour des types, plus des « classes » (Starter, Légendaire, Fossile) pour multiplier les combinaisons.

## Étape 3 : visuel 3/4 (2 à 3 semaines)

- Les sprites animés de Donjon Mystère (PMDCollab SpriteCollab) en 8 directions : marche, attaque, dégâts, K.O. Leur licence interdit l'usage commercial et demande de créditer les artistes.
- Un plateau en perspective, l'affichage trié par profondeur, et le glisser-déposer sur une grille hexagonale.
- Des effets visuels par type et par capacité, des projectiles, le texte des dégâts, les secousses d'écran.
- Une interface façon TFT (portraits des joueurs, PV, historique), plus le son et la musique.

## Étape 4 : lobby à 8 joueurs (1 à 6 semaines)

Trois niveaux d'ambition, à faire dans cet ordre :

1. **8 joueurs, dont 7 bots** (1 semaine) : jouable en solo, sans serveur. C'est la priorité.
2. **Asynchrone** (1 à 2 semaines) : tu affrontes les équipes enregistrées d'autres joueurs, comme dans Super Auto Pets. Un petit backend Spring Boot suffit.
3. **Temps réel** (3 à 5 semaines) : un serveur qui fait autorité, en WebSocket, avec des manches synchronisées, la reconnexion et l'anti-triche. C'est le plus gros morceau. Le serveur peut tourner sur un cluster Kubernetes perso.

## Étape 5 : contenu et équilibrage (en continu)

Avec 60 Pokémon et autant de capacités uniques, les objets et les synergies, c'est le travail qui ne s'arrête jamais. Le simulateur aide beaucoup, mais rien ne remplace des parties testées par de vrais joueurs.

## Récapitulatif

| Objectif | Étapes | Durée estimée (soirées) |
| --- | --- | --- |
| TFT jouable en solo contre des bots, visuel simple | 0 → 2 + bots | ~1,5 à 2 mois |
| La même chose, en beau (3/4 animé) | + 3 | ~2,5 à 3 mois |
| Multijoueur asynchrone | + 4 (async) | ~3 mois |
| Multijoueur temps réel à 8 | + 4 (temps réel) | ~4 à 5 mois |

Si c'est Claude qui code, les étapes 1 et 2 tiennent en quelques sessions chacune. Ce qui limite alors, ce sont les choix de design et les tests en jeu, pas l'écriture du code.

**Recommandation :** faire l'étape 1 (grille hexagonale, déplacements, capacités uniques) pour valider que le cœur du jeu est fun avant d'investir dans les graphismes et le réseau.
