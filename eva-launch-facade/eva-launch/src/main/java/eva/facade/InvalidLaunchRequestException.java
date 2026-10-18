package eva.facade;

public class InvalidLaunchRequestException extends RuntimeException {

    public InvalidLaunchRequestException(String message) {
        super(message);
    }
}
