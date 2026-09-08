package kbee.rag.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class PathFile
        implements ApiFile {

    private final Path path;

    public PathFile(
            Path path) {

        this.path =
                Objects.requireNonNull(
                        path,
                        "path"
                );
    }

    @Override
    public String name() {

        return path
                .getFileName()
                .toString();
    }

    @Override
    public InputStream openStream()
            throws IOException {

        return Files.newInputStream(
                path
        );
    }

    public Path path() {

        return path;
    }

    @Override
    public String toString() {

        return path.toString();
    }
}