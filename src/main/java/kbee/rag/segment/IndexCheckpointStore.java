package kbee.rag.segment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistencia mínima de checkpoints de indexación.
 *
 * Por cada documento indexado (y commiteado en Solr)
 * se crea un archivo marcador vacío:
 *
 *   index/fallo-10002.done
 *
 * Si el marcador existe, el documento se considera
 * ya indexado y no se vuelve a procesar.
 */
public class IndexCheckpointStore {

    private static final String MARKER_EXTENSION = ".done";

    private final Path directory;

    public IndexCheckpointStore(Path directory) {

        this.directory = directory;

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo crear el directorio de checkpoints: "
                            + directory.toAbsolutePath(),
                    e
            );
        }
    }

    /**
     * @param documentName nombre base del documento
     *                     (ej: "fallo-10002")
     */
    public boolean isIndexed(String documentName) {
        return Files.exists(markerFor(documentName));
    }

    public void markIndexed(String documentName) {
        try {
            Path marker = markerFor(documentName);
            if (!Files.exists(marker)) {
                Files.createFile(marker);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo escribir el checkpoint de: "
                            + documentName,
                    e
            );
        }
    }

    /**
     * Cantidad total de documentos con checkpoint.
     */
    public long countIndexed() {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(MARKER_EXTENSION))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo listar el directorio de checkpoints: "
                            + directory.toAbsolutePath(),
                    e
            );
        }
    }

    private Path markerFor(String documentName) {
        return directory.resolve(documentName + MARKER_EXTENSION);
    }
}
