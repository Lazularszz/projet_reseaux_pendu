# Notes de conception

Le métier a été conçu pour que les extensions s'ajoutent sans réécrire l'existant, grâce aux
points d'extension de `Partie` (`verifierCoupAutorise`, `apresCoup`, `apresExpiration`, `terminer`).

## Déjà réalisé

Les extensions réalisées n'ont réécrit aucune logique existante : `PartieMulti` hérite de `Partie`,
et `proposerMot` est une méthode ajoutée à côté de `proposerLettre`. Seul `GestionnaireParties`
a été étendu : `creerPartie` choisit désormais entre `Partie` et `PartieMulti`, le gestionnaire
tient en plus un index des parties par code, et la purge retire ce code. Le comportement du solo n'a pas changé.

- **Parties à deux joueurs** : `PartieMulti extends Partie` (tour de jeu via `verifierCoupAutorise`
  et `apresCoup`, vainqueur, forfait via `apresExpiration` et `terminer`). Le code de partie
  aléatoire et `rejoindrePartieParCode` sont dans `GestionnaireParties`.
- **Notifications à l'adversaire** : `Session.setEcouteur`, avec une file d'envoi par connexion
  côté serveur (une notification ne doit jamais écrire sur une socket pendant que la partie
  est verrouillée).
- **Idempotence** : `Session.executerUneFois`, appelée par `ClientHandler` pour `LETTER`, `WORD` et `SAVE`.
- **Mot entier** (`WORD`) : `Partie.proposerMot`, qui réutilise les mêmes points d'extension
  que `proposerLettre`.

## Restant à faire

- Chronomètre : un champ de date dans `PartieMulti`, vérifié dans `verifierCoupAutorise`
  (le code d'erreur `TIMEOUT` est déjà réservé).
- Lobby : `partiesEnAttente()` dans le gestionnaire (un filtre sur `parties.values()`), et les
  messages `LIST_ROOMS` etc.
- Chat, salles : de nouvelles classes et méthodes, diffusées via les écouteurs.
- Quitter définitivement : une méthode `quitter(Session)` dans `Partie`.
- Interface graphique : le contrôleur ne gère pour l'instant que le solo.
- Après un `RESUME`, le client ne renvoie pas encore la dernière requête sans réponse (section 8
  du protocole). Le serveur gère déjà l'idempotence : il ne manque que ce rejeu côté client.

## Limites connues

- Plus de deux joueurs : les tours et le code de partie fonctionnent déjà pour N joueurs. Restent
  à définir la règle de fin (stocker un perdant plutôt qu'un vainqueur), le forfait (continuer
  sans le joueur expiré) et, si l'hôte décide du démarrage, la condition `presents ==
  nbJoueursRequis` de `recalculerEtat`.
- Une retransmission de `NEW_GAME` ou de `JOIN_GAME` après une coupure ne peut pas être
  dédoublonnée : la session n'existe pas encore côté client si la réponse a été perdue, donc il
  n'a ni `session_id` ni `jeton`. Un nouveau `NEW_GAME` crée une deuxième partie (l'orpheline
  expirera) ; un nouveau `JOIN_GAME` échoue avec `PLAYER_NOT_ALLOWED`, la place étant prise par
  une session dont le jeton est perdu.
- Quand un joueur perd par forfait (expiration), son adversaire connecté n'est pas notifié :
  `notifierTous` est privée dans `Partie`. Il le découvre à sa prochaine requête.
- Tout est en mémoire : les parties sont perdues si le serveur redémarre.

## Pistes d'amélioration

- **Liste « mes parties » côté client** : le client ne garde aujourd'hui qu'une paire
  session_id + token (la dernière partie, via `Preferences`). Lancer une nouvelle partie
  écrase la précédente, qui reste sur le serveur mais devient inaccessible.
  À faire : stocker une liste de paires, proposer un choix de partie à reprendre dans
  l'interface, et retirer une paire quand la partie est terminée ou renvoie UNKNOWN_SESSION.
  Ne touche ni le serveur ni le métier.
- **Sauvegarde sur disque** des parties et des sessions, pour survivre à un redémarrage.