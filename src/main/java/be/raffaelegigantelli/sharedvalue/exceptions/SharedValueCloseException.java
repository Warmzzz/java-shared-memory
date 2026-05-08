package be.raffaelegigantelli.sharedvalue.exceptions;

public class SharedValueCloseException extends RuntimeException {

    public SharedValueCloseException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharedValueCloseException(String message) {
        super(message);
    }

}
