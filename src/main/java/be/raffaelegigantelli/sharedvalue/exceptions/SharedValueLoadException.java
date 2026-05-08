package be.raffaelegigantelli.sharedvalue.exceptions;

public class SharedValueLoadException extends RuntimeException {

    public SharedValueLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharedValueLoadException(String message) {
        super(message);
    }

}
