package metier.exceptions;

public class InvalidLetterException extends PenduException {
    public InvalidLetterException(String message) {
        super("INVALID_LETTER", message);
    }
}