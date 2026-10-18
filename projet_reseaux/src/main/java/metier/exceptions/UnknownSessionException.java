package metier.exceptions;

public class UnknownSessionException extends PenduException {
    public UnknownSessionException(String message) {
        super("UNKNOWN_SESSION", message);
    }
}