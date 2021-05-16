package jp.mydns.myuminecraft.takecore;

public class MisconfigurationException extends Exception {
    static final long serialVersionUID = 1;

    public MisconfigurationException() {
    }

    public MisconfigurationException(String message) {
        super(message);
    }

    public MisconfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MisconfigurationException(Throwable cause) {
        super(cause);
    }
}
