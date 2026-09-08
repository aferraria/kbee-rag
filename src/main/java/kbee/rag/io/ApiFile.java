package kbee.rag.io;

import java.io.IOException;
import java.io.InputStream;

public interface ApiFile {

    String name();

    InputStream openStream()
            throws IOException;
}