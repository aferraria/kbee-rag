package kbee.rag.segment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kbee.rag.audit.Logger;
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

	
	static private Logger logger = Logger.getLogger(JudicialDecisionIndexerCommand.class.getName());
	
	
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

    /**
     * Rango de ids a indexar (inclusive).
     * -1 significa sin límite.
     */
    private final long fromId;

    private final long toId;

    private final IndexCheckpointStore checkpointStore;

    /*
     * =================================================
     * CONTADORES
     * =================================================
     */

    private final AtomicLong indexedCount = new AtomicLong();

    private final AtomicLong skippedCount = new AtomicLong();

    private final AtomicLong failedCount = new AtomicLong();

    private final AtomicLong totalFilesInRange = new AtomicLong();

    private static final Pattern FILE_ID_PATTERN =
            Pattern.compile("fallo-(\\d+)\\.txt", Pattern.CASE_INSENSITIVE);

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
            int enrichmentBatchSize,

            @Value("${judicial-indexer.from-id:-1}")
            long fromId,

            @Value("${judicial-indexer.to-id:-1}")
            long toId,

            @Value("${judicial-indexer.index-directory:index}")
            String indexDirectory) {

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

        this.fromId =
                fromId;

        this.toId =
                toId;

        this.checkpointStore =
                new IndexCheckpointStore(
                        Path.of(indexDirectory)
                );

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
                        logger.info(
                                "Iniciando indexación desde: "
                                        + directory.toAbsolutePath()
                                        + " | rango: ["
                                        + (fromId < 0 ? "-" : fromId)
                                        + ", "
                                        + (toId < 0 ? "-" : toId)
                                        + "]"
                        )
                )
                .doOnSuccess(unused ->
                        logSummary()
                )
                .block();
    }

    private void logSummary() {

        long total = totalFilesInRange.get();
        long indexed = indexedCount.get();
        long skipped = skippedCount.get();
        long failed = failedCount.get();
        long pending = total - indexed - skipped - failed;

        logger.info(
                String.format(
                        "Indexación terminada. "
                                + "Total en rango: %d | "
                                + "Indexados: %d | "
                                + "Salteados (checkpoint): %d | "
                                + "Fallidos: %d | "
                                + "Pendientes: %d | "
                                + "Checkpoints acumulados: %d",
                        total,
                        indexed,
                        skipped,
                        failed,
                        pending,
                        checkpointStore.countIndexed()
                )
        );
    }

    /*
     * =================================================
     * INDEXACIÓN GENERAL
     * =================================================
     */

    public Mono<Void> indexAll() {

        return loadFiles()

                /*
                 * Un archivo por vez, con commit
                 * y checkpoint por archivo.
                 */
                .concatMap(
                        this::processAndCheckpointFile
                )

                .then();
    }

    /*
     * =================================================
     * ARCHIVO + COMMIT + CHECKPOINT
     * =================================================
     */

    /**
     * Procesa un archivo, commitea en Solr y
     * recién entonces escribe el checkpoint.
     *
     * El costo del commit por archivo es
     * despreciable frente al tiempo de
     * enriquecimiento/embeddings (~minutos),
     * y garantiza que un shutdown pierda a lo
     * sumo el archivo en curso.
     */
    private Mono<Void> processAndCheckpointFile(
            PathFile file) {

        return processFile(
                        file
                )

                .flatMap(succeeded ->

                        segmentDao.commit()

                                .then(
                                        Mono.fromRunnable(() -> {

                                            checkpointStore.markIndexed(
                                                    stripExtension(
                                                            succeeded.name()
                                                    )
                                            );

                                            indexedCount
                                                    .incrementAndGet();

                                            logger.info(
                                                    succeeded.name()
                                                            + " -> COMMIT + checkpoint | "
                                                            + "acumulado indexados: "
                                                            + indexedCount.get()
                                                            + " / "
                                                            + totalFilesInRange.get()
                                            );
                                        })
                                )
                )

                .then();
    }

    private static String stripExtension(
            String name) {

        int dot =
                name.lastIndexOf('.');

        return dot < 0
                ? name
                : name.substring(0, dot);
        
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

                                /*
                                 * Sólo archivos fallo-<id>.txt
                                 * dentro del rango pedido.
                                 */
                                .filter(path ->
                                        isInRange(
                                                path
                                        )
                                )

                                /*
                                 * Orden numérico por id.
                                 */
                                .sorted(
                                        Comparator.comparingLong(
                                                path ->
                                                        extractId(path)
                                                                .orElse(Long.MAX_VALUE)
                                        )
                                )

                                .peek(path ->
                                        totalFilesInRange
                                                .incrementAndGet()
                                )

                                /*
                                 * Salteamos los ya indexados
                                 * (checkpoint en el directorio
                                 * "index").
                                 */
                                .filter(path -> {

                                    boolean alreadyIndexed =
                                            checkpointStore.isIndexed(
                                                    baseName(path)
                                            );

                                    if (alreadyIndexed) {

                                        skippedCount
                                                .incrementAndGet();

                                        logger.debug(
                                                path.getFileName()
                                                        + " -> ya indexado, se saltea"
                                        );
                                    }

                                    return !alreadyIndexed;
                                })

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
     * RANGO E IDS
     * =================================================
     */

    private boolean isInRange(
            Path path) {

        OptionalLong id =
                extractId(
                        path
                );

        if (id.isEmpty()) {
            return false;
        }

        long value =
                id.getAsLong();

        if (fromId >= 0
                && value < fromId) {
            return false;
        }

        if (toId >= 0
                && value > toId) {
            return false;
        }

        return true;
    }

    private static OptionalLong extractId(
            Path path) {

        Matcher matcher =
                FILE_ID_PATTERN.matcher(
                        path.getFileName()
                                .toString()
                );

        if (!matcher.matches()) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(
                Long.parseLong(
                        matcher.group(1)
                )
        );
    }

    /**
     * Nombre base sin extensión.
     * fallo-10002.txt -> fallo-10002
     */
    private static String baseName(
            Path path) {

        String name =
                path.getFileName()
                        .toString();

        int dot =
                name.lastIndexOf('.');

        return dot < 0
                ? name
                : name.substring(0, dot);
    }
    
    /*
     * =================================================
     * ARCHIVO FÍSICO
     * =================================================
     */

    /**
     * Procesa un archivo físico.
     *
     * Emite el archivo si se procesó sin errores,
     * o vacío si falló (para no cortar la indexación
     * y no escribir su checkpoint).
     */
    private Mono<PathFile> processFile(
            PathFile apiFile) {


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

                .then(
                        Mono.just(
                                apiFile
                        )
                )

                /*
                 * Un archivo con error no detiene
                 * la indexación completa.
                 */
                .onErrorResume(error -> {

                    failedCount
                            .incrementAndGet();

                    logger.error(
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

        if (file == null || "sumario".equals(file.type())) {
        //if (file == null) {
            return Mono.empty();
        }
        
//        if ("fallo".equals(file.type())) {
//        	List<SegmentSearchResult> segments =
//                segmentDao
//                        .findSegments(file.id(), 0, 1)
//                        .collectList()
//                        .blockOptional()
//                        .orElseGet(List::of);
//        	if (!segments.isEmpty()) {
//                return Mono.empty();
//        	}
//        	else {
//        		logger.info("Fallo faltante");
//        	}
//        }
        
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

            String message = String.format(
					"%s [%s] -> OK - %.2f segundos",
					file.id(),
					file.type(),
					elapsedSeconds
			);
            
            logger.info(message);
            
        })

        .doOnError(error -> {

            double elapsedSeconds =
                    (
                            System.nanoTime()
                                    - start
                    )
                            / 1_000_000_000.0;

            String message = String.format(
            							"%s [%s] -> ERROR después de %.2f segundos: %s",
            							file.id(),
            							file.type(),
            							elapsedSeconds,
            							error.getMessage());

            logger.error(message);
            
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