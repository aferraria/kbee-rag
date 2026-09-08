package kbee.rag.segment;

public class SegmentProcessingException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SegmentProcessingException(Throwable cause) {
        super(cause);
    }
}