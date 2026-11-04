# Notes de conception : extensions prévues

Le métier solo est terminé. Ces points sont des ajouts prévus sans modifier le code existant,
grâce aux points d'extension de `Partie` (`verifierCoupAutorise`, `apresCoup`, `apresExpiration`, `terminer`).

## Ajouts prévus (purement additifs)

- Tour de jeu : `PartieMulti extends Partie` redéfinit `verifierCoupAutorise` (refus avec `PLAYER_NOT_ALLOWED` si ce n’est pas son tour) et `apresCoup` (passage au joueur suivant).
- Forfait ou annulation quand un joueur expire : `apresExpiration` dans `PartieMulti`, avec `terminer(...)`.
- Chronomètre : un champ de date dans la sous-classe, vérifié dans `verifierCoupAutorise`.
- Lobby : `partiesEnAttente()` dans le gestionnaire (un filtre sur `parties.values()`), et les messages `LIST_ROOMS` etc.
- Mes parties sauvegardées : filtre sur `sessions.values()` par `getJoueur()`.
- Idempotence : faite via `Session.executerUneFois`, le `ClientHandler` doit l'appeler avant `proposerLettre`.
- Chat, salles : de nouvelles classes et méthodes, diffusées via les écouteurs.
- Quitter définitivement : une méthode `quitter(Session)` dans `Partie`.

## Limites connues

- Un nombre de joueurs variable (démarrage décidé par l'hôte) obligerait à modifier la condition
`presents == nbJoueursRequis` dans `recalculerEtat`. Tant que l'effectif est fixe (2 en PvP), cette condition reste valable.
- Une retransmission de `NEW_GAME` après une coupure ne peut pas être dédoublonnée : la session n'existe pas encore côté client si la réponse a été perdue, donc il n'a ni `session_id` ni `jeton`, et un nouveau `NEW_GAME` crée une deuxième partie. Ce n'est pas grave, la partie orpheline expirera, mais cela vaut le coup d'en être conscient.