# Protocole applicatif du jeu du pendu en réseau

## 1. Service rendu

Le serveur héberge des parties de pendu. Un client peut créer des parties, proposer des lettres ou le mot entier, jouer contre un adversaire, sauvegarder une partie, puis la reprendre plus tard (même après une coupure réseau). Un même client peut jouer plusieurs parties, sur une même connexion.

Le **serveur est autoritaire** : il choisit le mot, vérifie chaque coup, décide de la victoire ou de la défaite. Le client n'envoie que des intentions (« je propose la lettre e ») et affiche l'état reçu. Le mot caché n'est jamais envoyé avant la fin de la partie.

Informations conservées par le serveur pour chaque partie : numéro, mot à trouver, lettres proposées, nombre d'erreurs, état, le code de partie, le joueur dont c’est le tour et le vainqueur, et pour chaque joueur une session (identifiant, jeton secret, présence, date de déconnexion).

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

| Champ | Présence                                                      | Rôle |
|---|---------------------------------------------------------------|---|
| `type` | obligatoire                                                   | Type du message (voir section 4). |
| `request_id` | obligatoire dans les requêtes qui modifient l'état (`NEW_GAME`, `JOIN_GAME`, `LETTER`, `WORD`, `SAVE`) | Entier choisi par le client, strictement croissant dans une même session. Recopié dans la réponse. |
| `session_id` | obligatoire sauf pour `HELLO`, `NEW_GAME` et `JOIN_GAME` | Identifie la session visée. Public, non secret. |
| `payload` | selon le type                                                 | Objet contenant les champs propres au message. |

Un champ inconnu est ignoré. Un champ obligatoire absent ou d'un mauvais type donne `INVALID_MESSAGE`.

Pour `NEW_GAME` et `JOIN_GAME`, `request_id` est unique dans la connexion (la session n'existe pas encore). Il est numéroté dans la session pour tous les messages suivants.

## 4. Messages

### 4.1 Client vers serveur

| Type | Payload | Réponse | Erreurs possibles |
|---|---|---|---|
| `HELLO` | `pseudonym` (texte, 1 à 20 caractères, obligatoire) | `WELCOME` | `INVALID_MESSAGE`, `INVALID_STATE` (déjà identifié) |
| `NEW_GAME` | `players` (entier, 1 ou 2, facultatif, défaut 1) | `STATE` (avec `token`) | `INVALID_STATE` (pas de `HELLO`), `INVALID_MESSAGE` |
| `JOIN_GAME` | `code` (texte, obligatoire) | `STATE` (avec `token`) | `INVALID_STATE` (pas de `HELLO`, ou partie terminée), `UNKNOWN_GAME`, `PLAYER_NOT_ALLOWED` (partie complète), `INVALID_MESSAGE` |
| `LETTER` | `letter` (une seule lettre) | `STATE` | `INVALID_LETTER`, `INVALID_STATE`, `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED` (dont : pas son tour), `INVALID_MESSAGE` |
| `WORD` | `word` (texte, obligatoire) | `STATE` | `INVALID_LETTER` (autre chose que des lettres), `INVALID_STATE`, `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED` (dont : pas son tour), `INVALID_MESSAGE` |
| `SAVE` | aucun | `STATE` (état `SAUVEGARDEE`) | `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED` |
| `RESUME` | `token` (texte, obligatoire) | `STATE` | `INVALID_STATE` (pas de `HELLO`), `UNKNOWN_SESSION`, `PLAYER_NOT_ALLOWED`, `INVALID_MESSAGE` |

**`HELLO` est obligatoire** : c'est le premier message de toute connexion, y compris avant un `RESUME`. Tout autre message reçu avant `HELLO` donne `INVALID_STATE`. Le pseudonyme sert à identifier le joueur dans les parties multijoueur. Pour une session reprise, le pseudonyme reste celui de la création de la session.

`SAVE` remplace le `QUIT` du sujet : il n'y a pas d'abandon définitif, quitter une partie revient toujours à la sauvegarder. **`SAVE` ne ferme pas la connexion** : le client peut aussitôt créer ou reprendre une autre partie. Une fermeture de la socket sauvegarde toutes les sessions encore rattachées à la connexion. 

`WORD` propose **le mot entier**, en solo comme à deux. Si le mot est exact, la partie est gagnée immédiatement par celui qui l'a proposé. S'il est faux, c'est une erreur de plus (sans cette pénalité, on pourrait deviner sans risque). Il suit les mêmes règles que `LETTER` : partie `EN_COURS`, et à son tour dans une partie à deux.

### 4.2 Serveur vers client

| Type | Payload | Quand |
|---|---|---|
| `WELCOME` | `pseudonym` | Réponse à `HELLO`. |
| `STATE` | voir ci-dessous | Réponse à `NEW_GAME`, `JOIN_GAME`, `LETTER`, `WORD`, `SAVE`, `RESUME`. Envoyé aussi spontanément (sans `request_id`) à l'adversaire dans une partie à deux. |
| `ERROR` | `code`, `message` | Toute requête refusée. |

**Payload de `STATE`**

| Champ | Type | Remarque |
|---|---|---|
| `state` | texte | `EN_ATTENTE`, `EN_COURS`, `GAGNEE`, `PERDUE` ou `SAUVEGARDEE`. |
| `word` | texte | Mot masqué, par exemple `_e__e`. |
| `remaining` | entier | Erreurs encore permises. |
| `errors` | entier | Erreurs commises. |
| `result` | texte, facultatif | Résultat du coup : `CORRECTE`, `INCORRECTE`, `DEJA_PROPOSEE`, `GAGNANT`, `PERDANT`. Présent seulement en réponse à `LETTER` ou `WORD` (pour `WORD` : `INCORRECTE`, `GAGNANT` ou `PERDANT`). |
| `token` | texte, facultatif | Jeton secret de reprise. **Présent uniquement dans la réponse à `NEW_GAME` et à `JOIN_GAME`**, jamais ailleurs. |
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

{"type":"WORD","request_id":3,"session_id":"3f2b9c1e","payload":{"word":"chat"}}
{"type":"STATE","request_id":3,"session_id":"3f2b9c1e","payload":
  {"state":"GAGNEE","word":"chat","remaining":7,"errors":0,"result":"GAGNANT","letters":"acht","revealed":"chat"}}
```

### 4.4 JOIN_GAME (client vers serveur)

Rejoint une partie à deux joueurs à partir de son code.

| Champ | Où | Description |
|---|---|---|
| `request_id` | racine, obligatoire | Identifiant de la requête, unique dans la connexion. |
| `code` | payload, obligatoire | Code de la partie (non sensible à la casse), communiqué par son créateur. |

Réponse : un `STATE` qui contient le `token` de la nouvelle session, comme pour `NEW_GAME`.
Erreurs : `UNKNOWN_GAME` (code inconnu), `PLAYER_NOT_ALLOWED` (partie complète),
`INVALID_STATE` (partie terminée).

### 4.5 Parties à deux joueurs

`NEW_GAME` accepte `players` valant 1 (défaut) ou 2. Avec 2, la partie reste `EN_ATTENTE`
jusqu'à l'arrivée du second joueur. Le mot est partagé et les joueurs proposent une lettre
à tour de rôle, le créateur commençant.

- Celui qui **complète le mot** gagne. Il peut aussi proposer **le mot entier** (`WORD`) pendant son tour : exact, il gagne immédiatement ; faux, c'est une erreur de plus et le tour passe.
- Si le pendu est complet, celui qui a commis la **dernière erreur** (lettre ou mot) perd, et l'autre gagne.
- Si un joueur est déconnecté plus longtemps que le délai de reprise, il perd par **forfait**. L'adversaire, s'il est connecté, ne reçoit pas de notification : il le constate à sa prochaine requête (`INVALID_STATE`).
- Une lettre déjà proposée ne fait pas perdre son tour.

Champs supplémentaires du `STATE` (parties à deux joueurs seulement) :

| Champ | Type | Description |
|---|---|---|
| `code` | texte | Code de la partie, présent tant qu'elle est `EN_ATTENTE`. |
| `players` | tableau de textes | Pseudonymes, dans l'ordre d'arrivée. |
| `your_turn` | booléen | `true` si c'est au destinataire de jouer. Présent si `state` vaut `EN_COURS`. |
| `winner` | texte | Pseudonyme du vainqueur. Présent si `state` vaut `GAGNEE` ou `PERDUE`. |
| `you_won` | booléen | `true` si le destinataire a gagné. Présent en même temps que `winner`. |

Dans une partie à deux, `state` décrit la **partie** (mot trouvé ou pendu complet) ;
c'est `you_won` qui dit si le destinataire a gagné ou perdu.

Le serveur envoie spontanément un `STATE` (sans `request_id`) à l'autre joueur quand
l'adversaire joue, arrive, se déconnecte ou reprend sa partie.

Erreur supplémentaire : `PLAYER_NOT_ALLOWED` si une lettre ou un mot est proposé hors de son tour.

Machine à états d'une partie à deux : `EN_ATTENTE` (un seul joueur connecté) passe à
`EN_COURS` quand les deux sont connectés, puis à `GAGNEE` ou `PERDUE`. Si un joueur se
déconnecte, la partie repasse à `EN_ATTENTE`, et à `SAUVEGARDEE` si les deux sont partis.
`LETTER` et `WORD` ne sont acceptés qu'en `EN_COURS` et pour le joueur dont c'est le tour.

## 5. Machine à états d'une partie

États : `EN_ATTENTE`, `EN_COURS`, `GAGNEE`, `PERDUE`, `SAUVEGARDEE`.

Règle générale : l'état dépend de la présence des joueurs. Avec `p` joueurs connectés sur `n` requis :

| Condition                        | État |
|----------------------------------|---|
| partie déjà gagnée ou perdue     | inchangé (états terminaux) |
| `p = 0` et au moins un coup joué | `SAUVEGARDEE` |
| `p = n`                          | `EN_COURS` |
| sinon                            | `EN_ATTENTE` |

| Depuis | Événement                                                                  | Vers |
|---|----------------------------------------------------------------------------|---|
| (création) | `NEW_GAME`, `n = 1`                                                        | `EN_COURS` |
| (création) | `NEW_GAME`, `n > 1`                                                        | `EN_ATTENTE` |
| `EN_ATTENTE` | tous les joueurs présents                                                  | `EN_COURS` |
| `EN_COURS` | `LETTER` qui complète le mot, ou `WORD` exact                              | `GAGNEE` |
| `EN_COURS` | `LETTER` ou `WORD` qui atteint le maximum d'erreurs                        | `PERDUE` |
| `EN_COURS` | un joueur part, d'autres restent                                           | `EN_ATTENTE` |
| `EN_COURS`, `EN_ATTENTE` ou `SAUVEGARDEE` | un joueur expire (partie à deux, adversaire non expiré)                    | `PERDUE` (forfait, l'autre gagne) |
| `EN_COURS` ou `EN_ATTENTE` | le dernier joueur connecté part (`SAVE` ou coupure), au moins un coup joué | `SAUVEGARDEE` |
| `SAUVEGARDEE` | `RESUME` valide, partie complète                                           | `EN_COURS` |
| `SAUVEGARDEE` | `RESUME` valide, joueurs manquants                                         | `EN_ATTENTE` |
| `SAUVEGARDEE` | délai de conservation dépassé                                              | supprimée |

Une partie multijoueur où aucun coup n'a été joué (personne n'a encore rejoint) reste `EN_ATTENTE` même quand tout le monde est parti. Elle suit la même règle de conservation que les parties sauvegardées : elle est supprimée quand toutes ses sessions ont expiré.

**Messages autorisés par état**

| État | `LETTER` / `WORD`                                                  | `SAVE` | `RESUME` | `NEW_GAME` |
|---|--------------------------------------------------------------------|---|---|---|
| `EN_ATTENTE` | `INVALID_STATE`                                                    | oui | oui | oui |
| `EN_COURS` | oui (à son tour dans une partie à deux, sinon `PLAYER_NOT_ALLOWED`)  | oui | sans effet (déjà connectée) | oui |
| `GAGNEE` / `PERDUE` | `INVALID_STATE`                                                    | sans effet | oui (renvoie l'état final) | oui |
| `SAUVEGARDEE` | `PLAYER_NOT_ALLOWED` (session déconnectée)                         | sans effet | oui | oui |

`NEW_GAME` concerne une nouvelle partie : son autorisation ne dépend pas de l'état des autres parties du joueur.

`JOIN_GAME` n’est possible que tant que la partie n’est pas complète (`PLAYER_NOT_ALLOWED` sinon) ni terminée (`INVALID_STATE`).

## 6. Erreurs

Format : `{"type":"ERROR","payload":{"code":"...","message":"..."}}`.

| Code | Signification                                                                                                   | Exemple |
|---|-----------------------------------------------------------------------------------------------------------------|---|
| `INVALID_MESSAGE` | JSON mal formé, champ obligatoire absent, type inconnu, longueur incorrecte                                     | `LETTER` sans `payload.letter` |
| `INVALID_STATE` | Opération interdite dans l'état courant                                                                         | `LETTER` sur une partie terminée, message avant `HELLO` |
| `INVALID_LETTER` | Ce n'est pas une lettre unique (`LETTER`), ou le mot proposé contient autre chose que des lettres (`WORD`)                                                                                 | `"1"`, `"ab1"` |
| `UNKNOWN_SESSION` | Session absente ou expirée                                                                                      | `RESUME` après le délai de conservation |
| `UNKNOWN_GAME` | Code de partie inconnu ou partie supprimée | `JOIN_GAME` avec un code faux |
| `PLAYER_NOT_ALLOWED` | Joueur non autorisé : mauvais jeton, session non rattachée à la connexion, session déconnectée, partie complète, ce n'est pas son tour | `RESUME` avec un faux jeton |
| `TIMEOUT` | Délai dépassé (réservé : inactivité, chrono)                                                                    | non utilisé en version 1 |

**Erreur de transport et erreur applicative.** Une erreur applicative est renvoyée dans un message `ERROR` et la connexion reste ouverte. Une erreur de transport (connexion coupée, timeout réseau) ferme la socket : le serveur déconnecte les sessions encore rattachées à cette connexion (état `SAUVEGARDEE` pour celles dont plus personne n'est présent) et attend un `RESUME`. Seul `INVALID_MESSAGE` pour une longueur illégale ferme aussi la connexion, car le découpage du flux n'est plus fiable.

## 7. Connexions, sessions et reprise

### 7.1 Une connexion, plusieurs sessions

Une connexion TCP n'est pas liée à une seule partie. Elle porte :

- un joueur, créé au `HELLO` à partir du pseudonyme ;
- un ensemble de **sessions rattachées**.

`NEW_GAME`, `JOIN_GAME` et `RESUME` rattachent la session créée ou reprise à la connexion. Chaque message de jeu désigne sa partie par `session_id`, et le serveur refuse (`PLAYER_NOT_ALLOWED`) tout `session_id` qui n'est pas rattaché à la connexion : connaître un identifiant ne suffit pas, il faut avoir créé la session ou prouvé la possession du jeton. `SAVE` détache une session sans fermer la connexion. À la fermeture de la connexion, toutes les sessions encore rattachées sont déconnectées.

### 7.2 Création et preuve de propriété

`session_id` est un UUID aléatoire. Le jeton est tiré avec `SecureRandom` (16 octets, encodés en Base64 URL) et envoyé une seule fois, dans la réponse à `NEW_GAME` ou `JOIN_GAME`. `session_id` identifie, le jeton authentifie. `RESUME` exige les deux. Le serveur compare le jeton en temps constant. Un id inexistant donne `UNKNOWN_SESSION`, un mauvais jeton `PLAYER_NOT_ALLOWED`.

### 7.3 Durée de conservation

Les sessions déconnectées sont conservées pendant un délai configurable côté serveur, **7 jours par défaut**. Le but de l'expiration n'est pas de punir le joueur, mais de borner la mémoire du serveur : une partie sauvegardée volontairement doit rester disponible longtemps.

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

**Exception : `NEW_GAME` et `JOIN_GAME`.** La session n'existe pas encore côté client si la réponse est perdue : il n'a ni `session_id` ni jeton, et la requête ne peut pas être dédoublonnée. Retransmettre `NEW_GAME` crée une deuxième partie (l'orpheline expirera). Retransmettre `JOIN_GAME` depuis une nouvelle connexion échoue avec `PLAYER_NOT_ALLOWED` (partie complète) : la place de l'adversaire est prise par une session dont le jeton est perdu, jusqu'à son expiration.

## 9. Concurrence côté serveur

Un thread (virtuel) par client lit les messages, les décode, appelle le gestionnaire de parties et écrit la réponse. Les parties sont protégées par leur propre verrou, et la table des sessions est concurrente : plusieurs parties évoluent en parallèle sans interférer.

Les `STATE` destinés à un autre joueur passent par une **file d'envoi propre à sa connexion**, vidée par un thread d'écriture. Les notifications sont déclenchées pendant que la partie est verrouillée : elles ne doivent jamais écrire directement sur une socket, sinon un client lent bloquerait toute la partie.

## 10. Évolutions prévues (sans changer le format)

Messages additionnels possibles pour les salles et la discussion : `LIST_ROOMS`, `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `CHAT`.