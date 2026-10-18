package eva.facade;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("no launch order " + id);
    }
}
