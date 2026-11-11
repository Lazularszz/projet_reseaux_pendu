# Protocole applicatif du jeu du pendu en réseau

Version 1 (brouillon, révisé). Les points marqués **[À valider]** sont des choix à confirmer ensemble avant de coder.

## 1. Service rendu

Le serveur héberge des parties de pendu. Un client peut créer des parties, proposer des lettres, sauvegarder une partie, puis la reprendre plus tard (même après une coupure réseau). Un même client peut jouer plusieurs parties, sur une même connexion.

Le **serveur est autoritaire** : il choisit le mot, vérifie chaque coup, décide de la victoire ou de la défaite. Le client n'envoie que des intentions (« je propose la lettre e ») et affiche l'état reçu. Le mot caché n'est jamais envoyé avant la fin de la partie.

Informations conservées par le serveur pour chaque partie : numéro, mot à trouver, lettres proposées, nombre d'erreurs, état, et pour chaque joueur une session (identifiant, jeton secret, présence, date de déconnexion).

## 2. Transport et framing

- Transport : TCP. Le serveur a une socket d'écoute et une socket par client.
- Encodage des messages : JSON, UTF-8.
- **Framing par longueur** : chaque message est précédé de sa taille en octets, sur 4 octets, entier non signé en grand-boutiste (big-endian).

```
[ longueur N : 4 octets ][ N octets de JSON UTF-8 ]
```

Réception : lire exactement 4 octets (`readFully`), puis exactement N octets. Une lecture partielle est complétée en boucle, et plusieurs messages reçus d'un coup sont traités l'un après l'autre. Une longueur supérieure à **65 536 octets** (ou nulle) est refusée : le serveur répond `INVALID_MESSAGE` puis ferme la connexion, car le flux n'est plus synchronisé.

## 3. Format général d'un message

```json
{
  "type": "LETTER",
  "request_id": 17,
  "session_id": "3f2b...",
  "payload": { "letter": "e" }
}
```

| Champ | Présence | Rôle |
|---|---|---|
| `type` | obligatoire | Type du message (voir section 4). |
| `request_id` | obligatoire dans les requêtes qui modifient l'état (`NEW_GAME`, `LETTER`, `SAVE`) | Entier choisi par le client, strictement croissant dans une même session. Recopié dans la réponse. |
| `session_id` | obligatoire sauf pour `HELLO` et `NEW_GAME` | Identifie la session visée. Public, non secret. |
| `payload` | selon le type | Objet contenant les champs propres au message. |

Un champ inconnu est ignoré. Un champ obligatoire absent ou d'un mauvais type donne `INVALID_MESSAGE`.

Pour `NEW_GAME`, `request_id` est unique dans la connexion (la session n'existe pas encore). Il est numéroté dans la session pour tous les messages suivants.

## 4. Messages

### 4.1 Client vers serveur

| Type | Payload | Réponse | Erreurs possibles |
|---|---|---|---|
| `HELLO` | `pseudonym` (texte, 1 à 20 caractères, obligatoire) | `WELCOME` | `INVALID_MESSAGE`, `INVALID_STATE` (déjà identifié) |
| `NEW_GAME` | `players` (entier ≥ 1, facultatif, défaut 1) | `STATE` (avec `token`) | `INVALID_STATE` (pas de `HELLO`), `INVALID_MESSAGE` |
| `LETTER` | `letter` (une seule lettre) | `STATE` | `INVALID_LETTER`, `INVALID_STATE`, `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED`, `INVALID_MESSAGE` |
| `SAVE` | aucun | `STATE` (état `SAUVEGARDEE`) | `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED` |
| `RESUME` | `token` (texte, obligatoire) | `STATE` | `INVALID_STATE` (pas de `HELLO`), `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED`, `INVALID_MESSAGE` |

**`HELLO` est obligatoire** : c'est le premier message de toute connexion, y compris avant un `RESUME`. Tout autre message reçu avant `HELLO` donne `INVALID_STATE`. Le pseudonyme sert à identifier le joueur dans les parties multijoueur. Pour une session reprise, le pseudonyme reste celui de la création de la session.

`SAVE` remplace le `QUIT` du sujet : il n'y a pas d'abandon définitif, quitter une partie revient toujours à la sauvegarder. **`SAVE` ne ferme pas la connexion** : le client peut aussitôt créer ou reprendre une autre partie. Une fermeture de la socket sauvegarde toutes les sessions encore rattachées à la connexion.

### 4.2 Serveur vers client

| Type | Payload | Quand |
|---|---|---|
| `WELCOME` | `pseudonym` | Réponse à `HELLO`. |
| `STATE` | voir ci-dessous | Réponse à `NEW_GAME`, `LETTER`, `SAVE`, `RESUME`. |
| `ERROR` | `code`, `message` | Toute requête refusée. |

**Payload de `STATE`**

| Champ | Type | Remarque |
|---|---|---|
| `state` | texte | `EN_ATTENTE`, `EN_COURS`, `GAGNEE`, `PERDUE` ou `SAUVEGARDEE`. |
| `word` | texte | Mot masqué, par exemple `_e__e`. |
| `remaining` | entier | Erreurs encore permises. |
| `errors` | entier | Erreurs commises. |
| `result` | texte, facultatif | Résultat du coup : `CORRECTE`, `INCORRECTE`, `DEJA_PROPOSEE`, `GAGNANT`, `PERDANT`. Présent seulement en réponse à `LETTER`. |
| `token` | texte, facultatif | Jeton secret de reprise. **Présent uniquement dans la réponse à `NEW_GAME`** (et à un futur `JOIN_GAME`), jamais ailleurs. |
| `revealed` | texte, facultatif | Mot complet, présent uniquement si `state` vaut `GAGNEE` ou `PERDUE`. |
| `letters` | texte | Lettres déjà proposées, en minuscules, par ordre alphabétique (ex. `aeo`). Toujours présent, vide au début. |

Chaque `STATE` porte le `session_id` au niveau racine, ce qui permet au client de savoir de quelle partie il s'agit quand il en joue plusieurs. Le client doit mémoriser `session_id` et `token` de chaque partie pour pouvoir la reprendre.

**Payload de `ERROR`** : `code` (voir section 6) et `message` (description lisible). `request_id` est recopié quand l'erreur répond à une requête qui en portait un.

### 4.3 Exemples

```json
{"type":"HELLO","payload":{"pseudonym":"Alice"}}
{"type":"WELCOME","payload":{"pseudonym":"Alice"}}

{"type":"NEW_GAME","request_id":1,"payload":{"players":1}}
{"type":"STATE","request_id":1,"session_id":"3f2b9c1e","payload":
  {"state":"EN_COURS","word":"____","remaining":7,"errors":0,"token":"kQ3v9Zr-T1xPaB7mWc2LdA"}}

{"type":"LETTER","request_id":1,"session_id":"3f2b9c1e","payload":{"letter":"e"}}
{"type":"STATE","request_id":1,"session_id":"3f2b9c1e","payload":
  {"state":"EN_COURS","word":"_e__","remaining":7,"errors":0,"result":"CORRECTE","letters":"e"}}

{"type":"LETTER","request_id":2,"session_id":"3f2b9c1e","payload":{"letter":"1"}}
{"type":"ERROR","request_id":2,"payload":
  {"code":"INVALID_LETTER","message":"Une seule lettre est attendue."}}
```

## 5. Machine à états d'une partie

États : `EN_ATTENTE`, `EN_COURS`, `GAGNEE`, `PERDUE`, `SAUVEGARDEE`.

Règle générale : l'état dépend de la présence des joueurs. Avec `p` joueurs connectés sur `n` requis :

| Condition | État |
|---|---|
| partie déjà gagnée ou perdue | inchangé (états terminaux) |
| `p = 0` et au moins une lettre jouée | `SAUVEGARDEE` |
| `p = n` | `EN_COURS` |
| sinon | `EN_ATTENTE` |

| Depuis | Événement | Vers |
|---|---|---|
| (création) | `NEW_GAME`, `n = 1` | `EN_COURS` |
| (création) | `NEW_GAME`, `n > 1` | `EN_ATTENTE` |
| `EN_ATTENTE` | tous les joueurs présents | `EN_COURS` |
| `EN_COURS` | `LETTER` qui complète le mot | `GAGNEE` |
| `EN_COURS` | `LETTER` qui atteint le maximum d'erreurs | `PERDUE` |
| `EN_COURS` | un joueur part, d'autres restent | `EN_ATTENTE` |
| `EN_COURS` ou `EN_ATTENTE` | le dernier joueur connecté part (`SAVE` ou coupure), au moins une lettre jouée | `SAUVEGARDEE` |
| `SAUVEGARDEE` | `RESUME` valide, partie complète | `EN_COURS` |
| `SAUVEGARDEE` | `RESUME` valide, joueurs manquants | `EN_ATTENTE` |
| `SAUVEGARDEE` | délai de conservation dépassé | supprimée |

Une partie multijoueur où aucune lettre n'a été jouée (personne n'a encore rejoint) reste `EN_ATTENTE` même quand tout le monde est parti. Elle suit la même règle de conservation que les parties sauvegardées : elle est supprimée quand toutes ses sessions ont expiré.

**Messages autorisés par état**

| État | `LETTER` | `SAVE` | `RESUME` | `NEW_GAME` |
|---|---|---|---|---|
| `EN_ATTENTE` | `INVALID_STATE` | oui | oui | oui |
| `EN_COURS` | oui | oui | sans effet (déjà connectée) | oui |
| `GAGNEE` / `PERDUE` | `INVALID_STATE` | sans effet | oui (renvoie l'état final) | oui |
| `SAUVEGARDEE` | `PLAYER_NOT_ALLOWED` (session déconnectée) | sans effet | oui | oui |

`NEW_GAME` concerne une nouvelle partie : son autorisation ne dépend pas de l'état des autres parties du joueur.

## 6. Erreurs

Format : `{"type":"ERROR","payload":{"code":"...","message":"..."}}`.

| Code | Signification | Exemple |
|---|---|---|
| `INVALID_MESSAGE` | JSON mal formé, champ obligatoire absent, type inconnu, longueur incorrecte | `LETTER` sans `payload.letter` |
| `INVALID_STATE` | Opération interdite dans l'état courant | `LETTER` sur une partie terminée, message avant `HELLO` |
| `INVALID_LETTER` | Ce n'est pas une lettre unique | `"1"` |
| `UNKNOWN_SESSION` | Session absente ou expirée | `RESUME` après le délai de conservation |
| `UNKNOWN_GAME` | Partie absente ou supprimée | rejoindre une partie inexistante (multi) |
| `PLAYER_NOT_ALLOWED` | Joueur non autorisé : mauvais jeton, session non rattachée à la connexion, session déconnectée, partie complète | `RESUME` avec un faux jeton |
| `TIMEOUT` | Délai dépassé (réservé : inactivité, chrono) | non utilisé en version 1 |

**Erreur de transport et erreur applicative.** Une erreur applicative est renvoyée dans un message `ERROR` et la connexion reste ouverte. Une erreur de transport (connexion coupée, timeout réseau) ferme la socket : le serveur déconnecte les sessions encore rattachées à cette connexion (état `SAUVEGARDEE` pour celles dont plus personne n'est présent) et attend un `RESUME`. Seul `INVALID_MESSAGE` pour une longueur illégale ferme aussi la connexion, car le découpage du flux n'est plus fiable.

## 7. Connexions, sessions et reprise

### 7.1 Une connexion, plusieurs sessions

Une connexion TCP n'est pas liée à une seule partie. Elle porte :

- un joueur, créé au `HELLO` à partir du pseudonyme ;
- un ensemble de **sessions rattachées**.

`NEW_GAME` et `RESUME` rattachent la session créée ou reprise à la connexion. Chaque message de jeu désigne sa partie par `session_id`, et le serveur refuse (`PLAYER_NOT_ALLOWED`) tout `session_id` qui n'est pas rattaché à la connexion : connaître un identifiant ne suffit pas, il faut avoir créé la session ou prouvé la possession du jeton. `SAVE` détache une session sans fermer la connexion. À la fermeture de la connexion, toutes les sessions encore rattachées sont déconnectées.

### 7.2 Création et preuve de propriété

`session_id` est un UUID aléatoire. Le jeton est tiré avec `SecureRandom` (16 octets, encodés en Base64 URL) et envoyé une seule fois, dans la réponse à `NEW_GAME`. `session_id` identifie, le jeton authentifie. `RESUME` exige les deux. Le serveur compare le jeton en temps constant. Un id inexistant donne `UNKNOWN_SESSION`, un mauvais jeton `PLAYER_NOT_ALLOWED`.

### 7.3 Durée de conservation

Les sessions déconnectées sont conservées pendant un délai configurable côté serveur, **7 jours par défaut** **[À valider]**. Le but de l'expiration n'est pas de punir le joueur mais de borner la mémoire du serveur : une partie sauvegardée volontairement doit rester disponible longtemps.

- Une tâche périodique (une fois par minute) marque les sessions expirées et supprime les parties dont toutes les sessions ont expiré.
- Les parties **terminées** (`GAGNEE`, `PERDUE`) sont conservées beaucoup moins longtemps (10 minutes par défaut), le temps de permettre une retransmission ou une reprise qui affiche le résultat.
- Au-delà du délai, `RESUME` donne `UNKNOWN_SESSION`.

**Limite de la version 1** : les parties vivent en mémoire. Si le serveur est redémarré, toutes les sessions sont perdues, quel que soit le délai. Une persistance sur disque (sérialisation des parties et sessions au démarrage et à l'arrêt) est une extension possible, sans changement du protocole.

### 7.4 Reprise

`RESUME` reconnecte la session, la rattache à la connexion courante et renvoie un `STATE` complet : le client retrouve exactement l'état précédent (mot, erreurs, état).

### 7.5 Ancienne connexion

Après une coupure brutale (câble débranché, Wi-Fi perdu), le serveur peut ne pas s'apercevoir pendant plusieurs minutes que l'ancienne socket est morte : la session reste marquée connectée. Si le serveur refusait le `RESUME` pour cette raison, le joueur serait bloqué jusqu'à la détection de la coupure.

Règle : un `RESUME` avec `session_id` et jeton valides est toujours accepté. Si la session était rattachée à une autre connexion, le serveur la détache de l'ancienne (qui est fermée) et la rattache à la nouvelle. La fermeture tardive de l'ancienne connexion ne déconnecte pas les sessions qu'elle n'a plus : seule la connexion à laquelle une session est rattachée peut la déconnecter.

## 8. Ambiguïté après coupure et retransmission

Cas : le client envoie `LETTER e`, le serveur la traite, la réponse est perdue. Le client ne sait pas si la lettre a été appliquée.

Règle : chaque requête modifiante porte un `request_id` strictement croissant dans la session. Le serveur mémorise, par session, la réponse associée à chaque `request_id` traité (au moins les 20 dernières).

- Requête avec un `request_id` déjà traité : le serveur **n'applique pas l'opération une deuxième fois** et renvoie la réponse mémorisée, à l'identique.
- Requête avec un nouveau `request_id` : traitement normal.

Procédure côté client après une coupure : se reconnecter, envoyer `HELLO` puis `RESUME`, puis **renvoyer la dernière requête sans réponse avec le même `request_id`**. Si elle avait été traitée, le client reçoit la réponse mémorisée. Sinon, elle est traitée maintenant.

Sans ce mécanisme, la lettre serait rejouée et renverrait `DEJA_PROPOSEE`, ce qui masquerait son vrai résultat. Il ne s'agit pas d'un « exactly-once » distribué complet : la mémoire des réponses est limitée et disparaît avec la session.

## 9. Concurrence côté serveur

Un thread (virtuel) par client lit les messages, les décode, appelle le gestionnaire de parties et écrit la réponse. Les parties sont protégées par leur propre verrou, et la table des sessions est concurrente : plusieurs parties évoluent en parallèle sans interférer.

## 10. Évolutions prévues (sans changer le format)

Messages additionnels pour le multijoueur et les salles : `JOIN_GAME`, `LIST_ROOMS`, `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `CHAT`. Le serveur pourra aussi envoyer des `STATE` non sollicités (sans `request_id`) lorsqu'un autre joueur joue ou se déconnecte.