# Pokémon Auto Battler

Auto battler façon TFT / Super Auto Pets avec des Pokémon de 1re génération (plus quelques Pokémon des générations suivantes choisis pour leur rôle), écrit en **Java** avec **[libGDX](https://libgdx.com/)**. Jouable sur desktop et **dans le navigateur** grâce à [gdx-teavm](https://github.com/xpenatan/gdx-teavm) (Java compilé en JavaScript par TeaVM).

**Jouer en ligne :** https://pokemon-autobattler.michael-desroches-perso.workers.dev (accès réservé via Cloudflare Access, voir [docs/DEPLOIEMENT-CLOUDFLARE.md](docs/DEPLOIEMENT-CLOUDFLARE.md))

## Lancer le jeu

```bash
./gradlew :lwjgl3:run                    # desktop (Java 17+)
./gradlew :teavm:gdx_teavm_web_js_run    # web, puis ouvrir http://localhost:8080
```

## Règles

- **Lobby de 8** : tu affrontes 7 champions d'arène de Kanto (Pierre, Ondine, Major Bob, Érika, Koga, Morgane, Auguste, Giovanni), chacun avec ses goûts (une classe et un type préférés). À chaque manche, tu combats l'un d'eux, sans retomber deux fois de suite sur le même ; si le nombre de survivants est impair, quelqu'un affronte le « fantôme » d'un autre joueur. Le dernier en vie gagne. Le classement est affiché à droite.
- Chaque manche : achète des Pokémon dans la boutique (coût 1 à 3 selon le tier), place-les sur ton camp du **plateau hexagonal** (4 lignes × 7 cases), puis lance le combat. Le combat est automatique.
- Au combat, chaque Pokémon **vise l'ennemi le plus proche**, s'en approche (recherche de chemin A*) jusqu'à être à portée, puis attaque. La **portée** dépend de la lignée : 1 case pour le corps à corps, 2 à 4 cases pour les attaquants à distance. Les Spectre visent toujours l'ennemi le plus affaibli.
- **Mana** : +10 par attaque, +5 par coup reçu. Mana plein → **capacité unique** de la lignée (31 capacités : Giga-Sangsue, Lance-Flammes, Provoc, Tonnerre, Psyko, Blizzard, Séisme, Tisse Ombre…). Survole un Pokémon pour voir la sienne.
- **Statuts** : brûlure (dégâts dans le temps, soins réduits de moitié), poison, gel, sommeil (rompu par les dégâts), paralysie (attaque 40 % plus lente), hâte, provocation, bouclier.
- **Prolongation** : après 30 s de combat, les dégâts augmentent de 20 % par seconde. Au bout de 45 s, le combat est perdu.
- **3 exemplaires identiques fusionnent** : évolution (Bulbizarre → Herbizarre → Florizarre) ou étoile supplémentaire pour les formes finales (jusqu'à 3★).
- **Niveau** (3 à 9) = nombre max de Pokémon sur le plateau. +2 XP par manche, ou achète 4 XP pour 4 or. Le niveau augmente aussi les chances de voir des Pokémon de tier 2 et 3 en boutique.
- **Pool partagé** entre les 8 joueurs : chaque Pokémon de base existe en un nombre limité d'exemplaires (18 / 14 / 10 selon le tier, 4 pour les légendaires). Ce que les bots achètent ne sort plus dans ta boutique ; les Pokémon reviennent au pool à la revente ou quand un joueur est éliminé.
- **Objets** : 6 composants (Bandeau Muscle, Lunettes Sages, Vive Griffe, Plaque Fer, Baie Oran, Encens Mer). Glisse-les sur un Pokémon (3 objets max) ; deux composants sur le même Pokémon se combinent en l'un des 21 objets (Orbe Vie, Griffe Rasoir, Casque Brut, Grelot Coque, Restes, Ceinture Force…). Survole un objet pour voir ses recettes.
- **Pokémon sauvages** : les manches 2, 6, 10, 14… opposent des Pokémon sauvages ; elles rapportent des composants (2 à 3 en cas de victoire, 1 sinon) et coûtent moins de PV en cas de défaite.
- **Économie** : +5 or par manche, +1 par tranche de 10 or (max +5), +1 en cas de victoire, +1 en série de 3+. Relancer la boutique coûte 1 or. Vendre rembourse ce que tu as investi (et rend ses objets).
- **Défaite** : tu perds `2 + manche / 2 + Pokémon adverses survivants` PV (60 PV au départ, moitié moins contre les sauvages). Au temps limite, les deux joueurs perdent.
- La **table des types** s'applique (les immunités sont adoucies à x0.25).
- **Légendaires** (tier 4, 5 or, à partir du niveau 7 : 5 / 10 / 20 % aux niveaux 7 / 8 / 9) : Artikodin (Gardien, Grand Froid), Électhor (Tireur, Fatal-Foudre), Sulfura (Mystique, Déflagration), Mewtwo (Mystique, Frappe Psy), Rayquaza (Combattant, Draco Ascension), Darkrai (Assassin, Trou Noir). 4 exemplaires chacun, pas d'évolution, stats renforcées : un légendaire 1★ vaut un tier 3 évolué 2★.
- **Pokémon des générations suivantes** : quelques lignées choisies parce qu'elles incarnent un rôle (Galekid, Obalie, Riolu, Griknot, Farfuret, Absol, Étourmi, Tarsal, Rémoraid, Larvibule, Brindibou ; les trois en plus remplacent Otaria, Roucool et Chenipan, qui collaient mal à leur classe), et des évolutions apparues plus tard (Onix → Steelix, Insécateur → Cizayox, Magnéton → Magnézone). Le type Ténèbres arrive avec elles (sans le type Fée : Gardevoir reste Psy).
- **Classes** : chaque lignée a un rôle (Gardien, Combattant, Assassin, Mystique, Tireur), affiché par une icône de couleur sur le plateau, le banc et la boutique. Les Gardiens et Combattants vont devant, les Mystiques et Tireurs derrière ; un contour orange signale un Pokémon mal placé. À partir de 2 Assassins, ils sautent sur l'arrière-ligne adverse au début du combat.

### Classes (lignées distinctes d'une même classe sur le plateau)

| Classe | Lignées | Palier | Bonus (pour les Pokémon de la classe) |
|---|---|---|---|
| Gardien | Carapuce, Racaillou, Onix (→ Steelix), Ronflex, Obalie, Galekid | 2 / 4 | Bouclier de 20 % / 35 % PV pendant 5 s au début |
| Combattant | Machoc, Osselait, Caninos, Minidraco, Ptitard, Riolu, Griknot | 2 / 4 | +15 % / +30 % PV, vol de vie 10 % / 20 % |
| Assassin | Fantominus, Étourmi, Rattata, Insécateur (→ Cizayox), Farfuret, Absol | 2 / 3 | Saut sur l'arrière-ligne au début, 30 % / 50 % de critique, +15 % / +30 % dégâts |
| Mystique | Abra, Magnéti (→ Magnézone), Lokhlass, Mystherbe, Goupix, Tarsal | 2 / 4 | +35 % / +70 % puissance des capacités, +20 mana au départ |
| Tireur | Pikachu, Salamèche, Larvibule, Bulbizarre, Rémoraid, Brindibou | 2 / 3 | +20 % / +30 % vitesse d'attaque (et +1 portée au 2e palier) |
| Starter | Bulbizarre, Salamèche, Carapuce, Pikachu, Brindibou | 3 | +20 % PV et Attaque |
| Légendaire | Artikodin, Électhor, Sulfura, Mewtwo, Rayquaza, Darkrai | 1 / 2 | **Toute l'équipe** : +10 % Attaque (et +15 % PV avec 2 légendaires) |

### Synergies (lignées distinctes d'un même type sur le plateau)

| Type | Palier | Bonus (pour les Pokémon du type) |
|---|---|---|
| Normal | 2 / 3 | +20 % / +40 % PV |
| Feu | 2 / 3 | +25 % / +50 % Attaque |
| Eau | 2 / 4 | +30 % / +60 % mana gagné |
| Plante | 2 | Régénère 3 % PV/s |
| Électrik, Insecte | 2 | +30 % vitesse d'attaque |
| Glace | 2 | 20 % de chance de geler 1 s |
| Combat | 2 | Vol de vie 25 % |
| Poison | 2 / 3 | Empoisonne (2 % / 4 % PV max/s pendant 3 s) |
| Sol | 2 / 3 | +20 % / +40 % PV |
| Vol | 2 / 3 | 15 % / 30 % d'esquive |
| Roche | 2 | +35 % Défense |
| Psy | 1 | +30 mana au départ |
| Spectre | 1 | 20 % d'esquive |
| Dragon | 1 | +25 % PV et Attaque |
| Acier | 2 / 3 | +30 % / +50 % Défense |
| Ténèbres | 2 | 25 % de coups critiques |

## Développement

Voir **[CLAUDE.md](CLAUDE.md)** (structure, commandes, conventions, CI) et **[docs/ROADMAP.md](docs/ROADMAP.md)** (feuille de route vers une version façon TFT).

Chaque PR doit mettre à jour `CLAUDE.md` : c'est vérifié automatiquement par la CI.

## Crédits

- Sprites animés et portraits : [PMDCollab SpriteCollab](https://github.com/PMDCollab/SpriteCollab), licence [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) — auteurs listés dans [CREDITS-PMD.md](CREDITS-PMD.md).
- Sprites de secours et icônes d'objets : [PokéAPI/sprites](https://github.com/PokeAPI/sprites).
- Bruitages et musique : synthétisés par `tools/gen_audio.py` pour ce projet.
- Police : [Fira Sans](https://github.com/mozilla/Fira) (SIL Open Font License), avec DejaVu Sans pour quelques symboles.

---

Projet de fan non officiel, sans but lucratif. Pokémon et les noms associés appartiennent à Nintendo, Game Freak et The Pokémon Company.
