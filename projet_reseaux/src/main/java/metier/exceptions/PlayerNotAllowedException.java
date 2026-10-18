package metier.exceptions;

public class PlayerNotAllowedException extends PenduException {
    public PlayerNotAllowedException(String message) {
        super("PLAYER_NOT_ALLOWED", message);
    }
}