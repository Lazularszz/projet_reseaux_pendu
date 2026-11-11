package protocole.exceptions;

import metier.exceptions.PenduException;

// Erreur de protocole (JSON mal formé, champ manquant, type inconnu).
// Même mécanisme que les erreurs métier : le serveur n'a qu'à lire getCode().
public class InvalidMessageException extends PenduException {
    public InvalidMessageException(String message) {
        super("INVALID_MESSAGE", message);
    }
}