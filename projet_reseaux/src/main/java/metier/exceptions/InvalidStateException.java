package metier.exceptions;

public class InvalidStateException extends PenduException {
    public InvalidStateException(String message) {
        super("INVALID_STATE", message);
    }
}