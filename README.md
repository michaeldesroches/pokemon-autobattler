# Pokémon Auto Battler

Auto battler façon TFT / Super Auto Pets avec des Pokémon de 1re génération, écrit en **Java** avec **[libGDX](https://libgdx.com/)**. Jouable sur desktop et **dans le navigateur** grâce à [gdx-teavm](https://github.com/xpenatan/gdx-teavm) (Java compilé en JavaScript par TeaVM).

**Jouer en ligne :** https://michaeldesroches.github.io/pokemon-autobattler/

## Lancer le jeu

```bash
./gradlew :lwjgl3:run                    # desktop (Java 17+)
./gradlew :teavm:gdx_teavm_web_js_run    # web, puis ouvrir http://localhost:8080
```

## Règles

- Chaque manche : achète des Pokémon dans la boutique (coût 1 à 3 selon le tier), place-les sur ton camp du **plateau hexagonal** (4 lignes × 7 cases), puis lance le combat. Le combat est automatique.
- Au combat, chaque Pokémon **vise l'ennemi le plus proche**, s'en approche (recherche de chemin A*) jusqu'à être à portée, puis attaque. La **portée** dépend de la lignée : 1 case pour le corps à corps, 2 à 4 cases pour les attaquants à distance. Les Spectre visent toujours l'ennemi le plus affaibli.
- **Mana** : +10 par attaque, +5 par coup reçu. Mana plein → **capacité unique** de la lignée (23 capacités : Giga-Sangsue, Lance-Flammes, Provoc, Tonnerre, Psyko, Blizzard…). Survole un Pokémon pour voir la sienne.
- **Statuts** : brûlure (dégâts dans le temps, soins réduits de moitié), poison, gel, sommeil (rompu par les dégâts), paralysie (attaque 40 % plus lente), hâte, provocation, bouclier.
- **Prolongation** : après 30 s de combat, les dégâts augmentent de 20 % par seconde. Au bout de 45 s, le combat est perdu.
- **3 exemplaires identiques fusionnent** : évolution (Bulbizarre → Herbizarre → Florizarre) ou étoile supplémentaire pour les formes finales (jusqu'à 3★).
- La taille max de l'équipe augmente toutes les 2 manches (3 → 8).
- **Économie** : +5 or par manche, +1 par tranche de 10 or (max +5), +1 en cas de victoire, +1 en série de 3+. Relancer la boutique coûte 1 or. Vendre rembourse ce que tu as investi.
- **Défaite** : tu perds `manche + 2 × Pokémon adverses survivants` PV (60 PV au départ).
- La **table des types** s'applique (les immunités sont adoucies à x0.25).

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
| Acier | 1 | +30 % Défense |

## Développement

Voir **[CLAUDE.md](CLAUDE.md)** (structure, commandes, conventions, CI) et **[docs/ROADMAP.md](docs/ROADMAP.md)** (feuille de route vers une version façon TFT).

Chaque PR doit mettre à jour `CLAUDE.md` : c'est vérifié automatiquement par la CI.

## Crédits

- Sprites : [PokéAPI/sprites](https://github.com/PokeAPI/sprites).
- Police : [Fira Sans](https://github.com/mozilla/Fira) (SIL Open Font License), avec DejaVu Sans pour quelques symboles.

---

Projet de fan non officiel, sans but lucratif. Pokémon et les noms associés appartiennent à Nintendo, Game Freak et The Pokémon Company.
