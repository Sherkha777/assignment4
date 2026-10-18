package eva.facade;

public class OrderNotCompensableException extends RuntimeException {

    public OrderNotCompensableException(long id, ChainStatus status) {
        super("launch order " + id + " is " + status + " and cannot be compensated");
    }
}
