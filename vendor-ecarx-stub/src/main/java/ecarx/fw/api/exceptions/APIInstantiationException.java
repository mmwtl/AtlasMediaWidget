package ecarx.fw.api.exceptions;

public class APIInstantiationException extends Exception {
    public APIInstantiationException(String message) {
        super(message);
    }

    public APIInstantiationException(String message, Throwable cause) {
        super(message, cause);
    }
}
