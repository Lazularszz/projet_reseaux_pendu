package metier.exceptions;

public class UnknownGameException extends PenduException {
    public UnknownGameException(String message) {
        super("UNKNOWN_GAME", message);
    }
}