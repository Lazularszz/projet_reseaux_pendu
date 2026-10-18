package metier.exceptions;

public abstract class PenduException extends RuntimeException {
    private final String code;

    protected PenduException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}