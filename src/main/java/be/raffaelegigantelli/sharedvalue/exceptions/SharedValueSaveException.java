package be.raffaelegigantelli.sharedvalue.exceptions;

public class SharedValueSaveException extends RuntimeException {

    public SharedValueSaveException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharedValueSaveException(String message) {
        super(message);
    }

}
