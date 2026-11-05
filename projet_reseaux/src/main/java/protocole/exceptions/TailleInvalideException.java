package protocole.exceptions;

import java.io.IOException;

public class TailleInvalideException extends IOException {
    public TailleInvalideException(int taille) {
        super("Taille de message invalide : " + taille);
    }
}