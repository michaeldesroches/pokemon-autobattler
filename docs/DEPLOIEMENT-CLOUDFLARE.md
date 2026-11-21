# Déploiement sur Cloudflare

Le build web (TeaVM) est un site 100 % statique. Il est déployé sur **Cloudflare Workers en mode « static assets »** : pas de code de Worker, Cloudflare sert directement les fichiers depuis son CDN. La configuration est dans [`wrangler.jsonc`](../wrangler.jsonc).

C'est le seul hébergement du jeu : GitHub Pages a été retiré pour que le jeu ne soit pas accessible publiquement. L'accès est filtré par **Cloudflare Access** (voir plus bas).

## Ce qui est automatisé

Dans `.github/workflows/build.yml` :

| Événement | Job | Effet |
| --- | --- | --- |
| Push sur `main` | `cloudflare` | `wrangler deploy` : met à jour la production (`https://pokemon-autobattler.<ton-sous-domaine>.workers.dev`) |
| PR depuis une branche du dépôt | `cloudflare-preview` | `wrangler versions upload --preview-alias pr-<n>` : publie un **aperçu** (`https://pr-<n>-pokemon-autobattler.<ton-sous-domaine>.workers.dev`) sans toucher à la production, et le met en commentaire de la PR |

Le build web est fait une seule fois par le job `build` (artefact `webapp`), puis réutilisé.

Tant que les secrets ne sont pas configurés, ces deux jobs se terminent en succès sans rien déployer (avec une note dans le résumé du workflow).

## Mise en place (une seule fois)

1. **Compte Cloudflare** : un compte gratuit suffit (offre Workers Free).
2. **Sous-domaine workers.dev** : dans le tableau de bord Cloudflare → *Workers & Pages*, choisis ton sous-domaine `*.workers.dev` s'il n'existe pas encore.
3. **Jeton d'API** : *My Profile* → *API Tokens* → *Create Token* → modèle **« Edit Cloudflare Workers »**. Limite-le à ton compte. Copie le jeton.
4. **Identifiant de compte** : visible dans le tableau de bord (*Workers & Pages* → colonne de droite, « Account ID »).
5. **Secrets GitHub** : dans le dépôt → *Settings* → *Secrets and variables* → *Actions* → *New repository secret* :
   - `CLOUDFLARE_API_TOKEN` : le jeton de l'étape 3 ;
   - `CLOUDFLARE_ACCOUNT_ID` : l'identifiant de l'étape 4.
6. **Premier déploiement** : relance le dernier workflow de `main` (*Actions* → *Build* → *Re-run all jobs*) ou pousse un commit. Le Worker `pokemon-autobattler` est créé automatiquement au premier `wrangler deploy`. L'URL apparaît dans le résumé du job et dans l'environnement GitHub `cloudflare`.

## Restreindre l'accès avec Cloudflare Access

1. Tableau de bord Cloudflare → *Workers & Pages* → Worker `pokemon-autobattler` → *Settings* → *Domains & Routes*.
2. Sur **workers.dev**, clique sur *Enable Cloudflare Access* ; fais de même sur **Preview URLs** pour protéger les aperçus des PR.
3. *Manage Cloudflare Access* : indique les adresses e-mail autorisées (connexion par code à usage unique envoyé par e-mail).

Rien à changer dans le dépôt ni dans la CI : le déploiement continue normalement, seul l'accès au site est filtré.

## Dépannage

| Erreur dans le job `cloudflare` | Cause probable | Correction |
| --- | --- | --- |
| `No access to the specified resource` | `CLOUDFLARE_ACCOUNT_ID` ne correspond pas au compte du jeton (souvent un *Zone ID* copié à la place de l'*Account ID*), ou le jeton n'inclut pas ce compte dans « Account Resources » | Compare avec la sortie de l'étape « Diagnostic du jeton » (`wrangler whoami` liste les comptes accessibles et leurs identifiants), puis corrige le secret ou recrée le jeton |
| `Authentication error` / `Invalid API Token` | Jeton expiré, révoqué ou mal copié | Recrée le jeton et remplace le secret |
| `You cannot upload a new version of a Worker that does not yet exist` (aperçu de PR) | Aucun déploiement de production n'a encore réussi | Normal avant le premier déploiement : le job d'aperçu l'ignore |
| `You need a workers.dev subdomain` | Sous-domaine `workers.dev` jamais choisi | Tableau de bord → *Workers & Pages* → choisir le sous-domaine |

## Domaine personnalisé (optionnel)

Si un domaine est géré par Cloudflare, décommente le bloc `routes` de `wrangler.jsonc` :

```jsonc
"routes": [{ "pattern": "autobattler.mon-domaine.fr", "custom_domain": true }]
```

Cloudflare crée l'enregistrement DNS et le certificat au déploiement suivant.

## Déployer à la main

```bash
./gradlew :teavm:gdx_teavm_web_js_build   # produit teavm/build/dist/js/webapp
npx wrangler@4 login                      # une fois, ouvre le navigateur
npx wrangler@4 deploy                     # production
npx wrangler@4 versions upload --preview-alias test   # aperçu sans toucher à la production
npx wrangler@4 deploy --dry-run           # vérifie la configuration sans rien publier
```

## Limites à connaître

- Les fichiers statiques sont servis sans exécuter de code de Worker ; vérifie les quotas de l'offre gratuite sur la [page de tarification des Workers](https://developers.cloudflare.com/workers/platform/pricing/).
- 25 Mio maximum par fichier : le build actuel est très en dessous (l'atlas de sprites fait environ 1 Mo).
- Licence des sprites PMDCollab (CC BY-NC 4.0) : le site doit rester **non commercial** (pas de publicité, pas de paiement), quel que soit l'hébergeur.
