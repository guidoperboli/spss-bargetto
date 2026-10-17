package gurobi;

/**
 * Gurobi exception representation compatible with com.gurobi.gurobi.GRBException.
 */
public class GRBException extends Exception {

    private final int errorCode;

    public GRBException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public GRBException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
