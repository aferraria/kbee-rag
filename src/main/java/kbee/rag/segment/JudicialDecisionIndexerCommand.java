package kbee.rag.segment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kbee.rag.embedding.EmbeddingService;
import kbee.rag.io.ApiFile;
import kbee.rag.io.JudicialFileParser;
import kbee.rag.io.PathFile;
import kbee.rag.io.TextFile;
import kbee.rag.search.SegmentDao;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@ConditionalOnProperty(
        prefix = "judicial-indexer",
        name = "enabled",
        havingValue = "true"
)
public class JudicialDecisionIndexerCommand
        implements CommandLineRunner {

    /*
     * =================================================
     * DEPENDENCIAS
     * =================================================
     */

    private final Segmenter segmenter;

    private final JudicialFileParser legalFileParser;

    private final SegmentEnhancer segmentEnhancer;

    private final EmbeddingService embeddingService;

    private final SegmentDao segmentDao;

    /*
     * =================================================
     * CONFIGURACIÓN
     * =================================================
     */

    private final Path directory;

    private final int segmentSize;

    private final int overlap;

    private final int embeddingBatchSize;

    private final int commitEveryDocuments;
    
    private final int enrichmentBatchSize;

    /*
     * =================================================
     * CONSTRUCTOR
     * =================================================
     */

    public JudicialDecisionIndexerCommand(

            @Qualifier("judicialDecisionSegmenter")
            Segmenter segmenter,

            JudicialFileParser legalFileParser,

            SegmentEnhancer segmentEnhancer,

            EmbeddingService embeddingService,

            SegmentDao segmentDao,

            @Value("${judicial-indexer.directory:fallos}")
            String directory,

            @Value("${judicial-indexer.segment-size:1500}")
            int segmentSize,

            @Value("${judicial-indexer.segment-overlap:0}")
            int overlap,

            @Value("${judicial-indexer.embedding-batch-size:32}")
            int embeddingBatchSize,

            @Value("${judicial-indexer.commit-every-documents:100}")
            int commitEveryDocuments,
            @Value("${kbee.rag.enrichment-batch-size:4}")
            int enrichmentBatchSize) {

        this.segmenter =
                segmenter;

        this.legalFileParser =
                legalFileParser;

        this.segmentEnhancer =
                segmentEnhancer;

        this.embeddingService =
                embeddingService;

        this.segmentDao =
                segmentDao;

        this.directory =
                Path.of(
                        directory
                );

        this.segmentSize =
                segmentSize;

        this.overlap =
                overlap;

        this.embeddingBatchSize =
                embeddingBatchSize;

        this.commitEveryDocuments =
                commitEveryDocuments;
        
        this.enrichmentBatchSize = 
        		enrichmentBatchSize;

    }

    /*
     * =================================================
     * RUN
     * =================================================
     */

    @Override
    public void run(
            String... args) {

        indexAll()
                .doOnSubscribe(subscription ->
                        System.out.println(
                                "Iniciando indexación desde: "
                                        + directory.toAbsolutePath()
                        )
                )
                .doOnSuccess(unused ->
                        System.out.println(
                                "Indexación terminada."
                        )
                )
                .block();
    }

    /*
     * =================================================
     * INDEXACIÓN GENERAL
     * =================================================
     */

    public Mono<Void> indexAll() {

        return loadFiles()

                /*
                 * Agrupamos archivos físicos.
                 */
                .buffer(
                        commitEveryDocuments
                )

                .concatMap(
                        this::processDocumentBatch
                )

                .then();
    }

    /*
     * =================================================
     * BATCH DE ARCHIVOS
     * =================================================
     */

    private Mono<Void> processDocumentBatch(
            List<PathFile> files) {

        if (files == null
                || files.isEmpty()) {

            return Mono.empty();
        }

        return Flux.fromIterable(
                        files
                )

                /*
                 * Un archivo físico por vez.
                 */
                .concatMap(
                        this::processFile
                )

                /*
                 * Commit después del batch.
                 */
                .then(
                        segmentDao.commit()
                )

                .doOnSuccess(unused ->
                        System.out.println(
                                "COMMIT después de "
                                        + files.size()
                                        + " archivos"
                        )
                );
    }

    /*
     * =================================================
     * LISTADO DE ARCHIVOS
     * =================================================
     */

    private Flux<PathFile> loadFiles() {

        return Mono.fromCallable(() -> {

                    if (!Files.exists(
                            directory
                    )) {

                        throw new IllegalStateException(
                                "No existe el directorio: "
                                        + directory.toAbsolutePath()
                        );
                    }

                    if (!Files.isDirectory(
                            directory
                    )) {

                        throw new IllegalStateException(
                                "La ruta no es un directorio: "
                                        + directory.toAbsolutePath()
                        );
                    }

                    try (var stream =
                            Files.list(
                                    directory
                            )) {

                        return stream
                                .filter(
                                        Files::isRegularFile
                                )
                                .filter(path ->
                                        path.getFileName()
                                                .toString()
                                                .toLowerCase()
                                                .endsWith(".txt")
                                )
                                .sorted(
                                        Comparator.comparing(
                                                path ->
                                                        path.getFileName()
                                                            .toString()
                                        )
                                )
                                .map(
                                        PathFile::new
                                )
                                .toList();
                    }
                })

                .subscribeOn(
                        Schedulers.boundedElastic()
                )

                .flatMapMany(
                        Flux::fromIterable
                );
    }
    
    /*
     * =================================================
     * ARCHIVO FÍSICO
     * =================================================
     */

    private Mono<Void> processFile(
            ApiFile apiFile) {


        return legalFileParser
                .parse(
                        apiFile
                )

                /*
                 * Un archivo puede generar:
                 *
                 * fallo
                 * sumario-xxx-1
                 * sumario-xxx-2
                 * ...
                 */
                .concatMap(
                        this::processTextFile
                )

                .then()

                /*
                 * Un archivo con error no detiene
                 * la indexación completa.
                 */
                .onErrorResume(error -> {

                    System.err.println(
                            apiFile.name()
                                    + " -> ERROR: "
                                    + error.getMessage()
                    );

                    error.printStackTrace();

                    return Mono.empty();
                });
    }

    /*
     * =================================================
     * TEXT FILE
     * =================================================
     */

    private Mono<Void> processTextFile(
            TextFile file) {

        //if (file == null || "sumario".equals(file.type())) {
        if (file == null) {
            return Mono.empty();
        }
        
        if ("fallo".equals(file.type())) {
        	List<SegmentSearchResult> segments =
                segmentDao
                        .findSegments(file.id(), 0, 1)
                        .collectList()
                        .blockOptional()
                        .orElseGet(List::of);
        	if (!segments.isEmpty()) {
                return Mono.empty();
        	}
        	else {
        		System.out.println("Fallo faltante");
        	}
        }
        
        long start =
                System.nanoTime();

        return segmentDao.deleteByDocumentId(
                file.id()
        )

        /*
         * Segmentación.
         */
        .thenMany(
                segmenter.split(
                        file,
                        segmentSize,
                        overlap
                )
        )

        /*
         * =========================================
         * Batch para enriquecimiento jurídico.
         * =========================================
         */
        .buffer(
                enrichmentBatchSize
        )
        .flatMapSequential(
                segmentEnhancer::enhance,
                3
        )

        /*
         * El enhancer devuelve una lista.
         */
        .flatMapIterable(
                enhancedSegments ->
                        enhancedSegments
        )

        /*
         * =========================================
         * Batch para embeddings finales.
         * =========================================
         */
        .buffer(
                embeddingBatchSize
        )

        .concatMap(
                this::embedBatch
        )

        /*
         * Cada EmbeddedSegment se persiste
         * a través del DAO.
         */
        .flatMapIterable(
                embeddedSegments ->
                        embeddedSegments
        )

        .concatMap(
                segmentDao::add
        )

        .then()

        .doOnSuccess(unused -> {

            double elapsedSeconds =
                    (
                            System.nanoTime()
                                    - start
                    )
                            / 1_000_000_000.0;

            System.out.printf(
                    "%s [%s] -> OK - %.2f segundos%n",
                    file.id(),
                    file.type(),
                    elapsedSeconds
            );

        })

        .doOnError(error -> {

            double elapsedSeconds =
                    (
                            System.nanoTime()
                                    - start
                    )
                            / 1_000_000_000.0;

            System.out.printf(
                    "%s [%s] -> ERROR después de %.2f segundos: %s%n",
                    file.id(),
                    file.type(),
                    elapsedSeconds,
                    error.getMessage()
            );

        });
    }

    /*
     * =================================================
     * EMBEDDINGS
     * =================================================
     */

    private Mono<List<EmbeddedSegment>> embedBatch(
            List<TextSegment> segments) {

        if (segments == null
                || segments.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        return Mono.fromCallable(() ->
                embedSegments(
                        segments
                )
        )
        .subscribeOn(
                Schedulers.boundedElastic()
        );
    }

    private List<EmbeddedSegment> embedSegments(
            List<TextSegment> segments) {

        if (segments == null
                || segments.isEmpty()) {

            return List.of();
        }

        /*
         * =================================================
         * TEXTOS A EMBEDDER
         * =================================================
         */

        List<String> texts =
                new ArrayList<>();

        /*
         * Índice dentro de texts para cada embedding original.
         */
        List<Integer> originalIndexes =
                new ArrayList<>(
                        segments.size()
                );

        /*
         * Índice dentro de texts para cada embedding legal.
         *
         * null significa que ese segmento no tiene
         * embeddingText.
         */
        List<Integer> legalIndexes =
                new ArrayList<>(
                        segments.size()
                );

        for (TextSegment segment :
                segments) {

            /*
             * ORIGINAL
             */

            originalIndexes.add(
                    texts.size()
            );

            texts.add(
                    segment.text()
            );

            /*
             * LEGAL
             */

            String legalText =
                    segment.embeddingText();

            if (legalText == null
                    || legalText.isBlank()) {

                legalIndexes.add(
                        null
                );

            } else {

                legalIndexes.add(
                        texts.size()
                );

                texts.add(
                        legalText
                );
            }
        }

        /*
         * =================================================
         * EMBEDDINGS
         * =================================================
         */

        List<List<Float>> embeddings =
                embeddingService.embed(
                        texts
                );

        if (embeddings.size()
                != texts.size()) {

            throw new IllegalStateException(
                    "Cantidad de embeddings distinta "
                            + "de textos: "
                            + embeddings.size()
                            + " != "
                            + texts.size()
            );
        }

        /*
         * =================================================
         * RESULTADO
         * =================================================
         */

        List<EmbeddedSegment> result =
                new ArrayList<>(
                        segments.size()
                );

        for (int i = 0;
                i < segments.size();
                i++) {

            List<Float> embedding =
                    embeddings.get(
                            originalIndexes.get(
                                    i
                            )
                    );

            Integer legalIndex =
                    legalIndexes.get(
                            i
                    );

            List<Float> legalEmbedding =
                    legalIndex == null
                            ? null
                            : embeddings.get(
                                    legalIndex
                            );

            result.add(
                    new EmbeddedSegment(
                            segments.get(
                                    i
                            ),
                            embedding,
                            legalEmbedding
                    )
            );
        }

        return List.copyOf(
                result
        );
    }
   


}