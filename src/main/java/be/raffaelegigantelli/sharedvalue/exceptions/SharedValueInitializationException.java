package be.raffaelegigantelli.sharedvalue.exceptions;

public class SharedValueInitializationException extends RuntimeException {

    public SharedValueInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharedValueInitializationException(String message) {
        super(message);
    }

}
