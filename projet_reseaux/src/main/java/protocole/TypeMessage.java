package protocole;

import java.util.Set;

// Types de messages définis dans PROTOCOLE.md
public final class TypeMessage {
    // Client -> serveur
    public static final String HELLO = "HELLO";
    public static final String NEW_GAME = "NEW_GAME";
    public static final String LETTER = "LETTER";
    public static final String SAVE = "SAVE";
    public static final String RESUME = "RESUME";

    // Serveur -> client
    public static final String WELCOME = "WELCOME";
    public static final String STATE = "STATE";
    public static final String ERROR = "ERROR";

    public static final Set<String> REQUETES = Set.of(HELLO, NEW_GAME, LETTER, SAVE, RESUME);
    public static final Set<String> REPONSES = Set.of(WELCOME, STATE, ERROR);

    private TypeMessage() { }
}