package kbee.rag.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import kbee.rag.audit.AuditContext;
import kbee.rag.audit.AuditRecord;
import kbee.rag.audit.ReactorAuditPublisher;
import kbee.rag.config.InstructionProvider;
import kbee.rag.document.DocumentDao;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.reranker.LlmRerankerService;
import kbee.rag.reranker.LocalQwenRerankerService;
import kbee.rag.reranker.RerankRequest;
import kbee.rag.reranker.RerankRequestBuilder;
import kbee.rag.reranker.RerankResult;
import kbee.rag.reranker.RerankerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RagService {

    private final SegmentSearchService segmentSearchService;

    private final DocumentDao documentDao;

    private final RerankRequestBuilder rerankRequestBuilder;
    
    private final LlmRequestBuilder llmRequestBuilder;

    private final LocalQwenRerankerService rerankerService;

    private final LlmService llmService;
    
    private final LlmRerankerService llmRerankerService; // LLM final
    
    private final FilterQueryBuilder filterBuilder;
    
    private final InstructionProvider instructionProvider;

    public RagService(
            SegmentSearchService segmentSearchService,
            DocumentDao documentDao,
            RerankRequestBuilder rerankRequestBuilder,
            LocalQwenRerankerService rerankerService,
            LlmRequestBuilder llmRequestBuilder,
            LlmService llmService,
            LlmRerankerService llmRerankerService,
            FilterQueryBuilder filterBuilder,
            InstructionProvider instructionProvider) {

        this.segmentSearchService =
                segmentSearchService;

        this.documentDao =
                documentDao;

        this.rerankRequestBuilder =
                rerankRequestBuilder;

        this.rerankerService =
                rerankerService;
        
        this.llmRerankerService = llmRerankerService;
        
        this.llmRequestBuilder = llmRequestBuilder;

        this.llmService =
                llmService;
        
        this.filterBuilder = filterBuilder;
        
        this.instructionProvider = instructionProvider;

    }
    
    public Mono<RagResponse> answer(
            RagRequest request) {

        String question =
                request.question();

        int topK =
                request.topK() == null
                        ? 5
                        : request.topK();

        return segmentSearchService.search(
                question,
                getFilters(request.parameters()),
                topK
        )
        .collectList()
        .flatMap(results ->
                documentDao.getSources(
                        results,
                        question
                )
        )
        .flatMap(sources ->
                rerank(
                        question,
                        sources,
                        topK
                )
        ) 
        .flatMap(rerankedSources ->
                generateResponse(
                        question,
                        rerankedSources
                )
        );
    }
    
    public Mono<SourcesResponse> rankSources(
            RagRequest request) {
    	
        AuditContext auditContext =
                new AuditContext(
                        request.question(),
                        request.topK()
                );

        return doRankSources(request)
                .flatMap(sources -> {

                	AuditRecord record =
                	        auditContext.finish();

                	long totalMillis =
                	        Duration.between(
                	                record.startedAt(),
                	                record.finishedAt()
                	        ).toMillis();

                	System.out.println(
                	        "===== AUDIT RECORD ====="
                	);

                	System.out.println(record);

                	System.out.printf(
                	        "===== TOTAL: %.2f s =====%n",
                	        totalMillis / 1000.0
                	);

                    return Mono.just(
                            sources
                    );
                })
                .contextWrite(context ->
                        context.put(
                                ReactorAuditPublisher.AUDIT_CONTEXT_KEY,
                                auditContext
                        )
                );
    }

    
    public Mono<SourcesResponse> doRankSources(
            RagRequest request) {

        String question =
                request.question();

        int topK =
                request.topK() == null
                        ? 5
                        : request.topK();

        return segmentSearchService
                .search(
                        question,
                        getFilters(request.parameters()),
                        topK
                )
                .collectList()
                .flatMap(results ->
                        documentDao.getSources(
                                results,
                                question
                        )
                )
                .flatMap(sources ->
                        rerank(
                                question,
                                sources,
                                topK
                        )
                )
                .flatMap(rerankedSources ->
                        generateSourcesResponse(
                                question,
                                rerankedSources
                        )
                )
                .doOnNext(response -> {

                    System.out.println(
                            "===== RERANKED SOURCES ====="
                    );

                    for (Source source : response.sources()) {

                        System.out.println(
                                source.documentId()
                                        + " "
                                        + source.documentTitle()
                        );
                    }
                })
                .doOnError(error -> {

                    System.err.println(
                            "===== RANK SOURCES ERROR ====="
                    );

                    error.printStackTrace();
                });
    }
    
    public Mono<DocumentAnalysisResponse> analyzeDocument(
            String documentId,
            String question) {

        return documentDao
                .getDocument(
                        documentId
                )
                .switchIfEmpty(
                        Mono.error(
                                new IllegalArgumentException(
                                        "Document not found: "
                                                + documentId
                                )
                        )
                )
                .flatMap(document -> {

                    String context = """
                            Pregunta del usuario:

                            %s

                            Documento a analizar:

                            %s
                            """.formatted(
                                    question,
                                    document.text()
                            );

                    String instructions =
                            instructionProvider.get(
                                    "document-analysis"
                            );

                    LlmRequest request =
                            new LlmRequest(
                                    instructions,
                                    context,
                                    null
                            );

                    return llmService
                            .generate(
                                    request
                            )
                            .map(answer ->
                                    new DocumentAnalysisResponse(
                                            document.documentId(),
                                            document.documentTitle(),
                                            answer
                                    )
                            );
                });
    }
    
    private static final int RERANK_BATCH_SIZE = 3;
    

    private Mono<List<ExpandedSource>> rerank(
            String question,
            List<ExpandedSource> sources,
            int topK) {

        if (sources == null || sources.isEmpty()) {
            return Mono.just(List.of());
        }
        
        System.out.println();
        System.out.println(
                "===== SOURCES BEFORE RERANK ====="
        );

        for (int i = 0; i < sources.size(); i++) {

            ExpandedSource source =
                    sources.get(i);

            SegmentSearchResult selected =
                    source.selected();

            System.out.printf(
                    "%d | %s | %s%n",
                    i,
                    selected.documentId(),
                    selected.id()
            );
        }

        System.out.println(
                "================================="
        );
        System.out.println();

        int rerankSize =
                Math.min(
                        topK,
                        sources.size()
                );

        List<IndexedSource> indexedSources =
                IntStream.range(0, rerankSize)
                        .mapToObj(index ->
                                new IndexedSource(
                                        index,
                                        sources.get(index)
                                )
                        )
                        .toList();
        
        return Flux.fromIterable(indexedSources)
                .buffer(RERANK_BATCH_SIZE)
                .concatMap(batch ->
                        rerankBatch(
                                question,
                                batch
                        )
                )
                .flatMapIterable(Function.identity())
                .collectList()
                .flatMap(finalists -> {

                	List<ExpandedSource> finalSources =
                	        finalists.stream()
                	                .map(IndexedSource::source)
                	                .sorted(
                	                        Comparator.comparingDouble(
                	                                (ExpandedSource source) ->
                	                                        source.selected().score()
                	                        ).reversed()
                	                )
                	                .limit(25)
                	                .collect(
                	                        Collectors.toCollection(
                	                                ArrayList::new
                	                        )
                	                );
                	

							System.out.println();
							System.out.println(
							        "===== QWEN FINALISTS SORTED ====="
							);
							
							int position = 1;
							
							for (ExpandedSource source : finalSources) {
							
							    SegmentSearchResult selected =
							            source.selected();
							
							    System.out.printf(
							            "%2d | score=%.2f | %s | %s%n",
							            position++,
							            selected.score(),
							            selected.documentId(),
							            selected.id()
							    );
							}
							
							System.out.println(
							        "================================="
							);

                	//Collections.shuffle(finalSources);

                    
                    RerankRequest finalRequest =
                            rerankRequestBuilder.buildFinal(
                                    question,
                                    finalSources
                            );

                    return llmRerankerService.rerankFinal(
                            finalRequest,
                            Math.min(
                                    topK,
                                    finalSources.size()
                            )
                    );
                });
    }
    

    private static final int RERANK_BATCH_DROP_LAST = 2;

    private static final double RERANK_BATCH_MIN_SCORE = 0.40;


    private Mono<List<IndexedSource>> rerankBatch(
            String question,
            List<IndexedSource> batch) {

        if (batch == null || batch.isEmpty()) {
            return Mono.just(List.of());
        }

        List<ExpandedSource> batchSources =
                batch.stream()
                        .map(IndexedSource::source)
                        .toList();

        Map<String, IndexedSource> indexedByKey =
                batch.stream()
                        .collect(
                                Collectors.toMap(
                                        indexed ->
                                                sourceKey(
                                                        indexed.source()
                                                ),
                                        Function.identity()
                                )
                        );

        RerankRequest request =
                rerankRequestBuilder.build(
                        question,
                        batchSources
                );

        return rerankerService.rerank(
                request,
                batchSources.size()
        )
        .map(reranked -> {

            System.out.println();
            System.out.println(
                    "===== RERANK BATCH FINALISTS ====="
            );

            if (reranked == null
                    || reranked.isEmpty()) {

                System.out.printf(
                        "KEEP 0 / %d | DROP %d%n",
                        batch.size(),
                        batch.size()
                );

                System.out.println(
                        "=================================="
                );

                return List.<IndexedSource>of();
            }

            List<IndexedSource> finalists =
                    new ArrayList<>();

            for (ExpandedSource source : reranked) {

                SegmentSearchResult selected =
                        source.selected();

                double score =
                        selected.score();

//                if (score < RERANK_BATCH_MIN_SCORE) {
//
//                    System.out.printf(
//                            "DROP | score=%.2f | %s | %s%n",
//                            score,
//                            selected.documentId(),
//                            selected.id()
//                    );
//
//                    continue;
//                }

                String key =
                        sourceKey(source);

                IndexedSource indexed =
                        indexedByKey.get(key);

                if (indexed == null) {

                    throw new IllegalStateException(
                            "No se pudo encontrar la fuente rerankeada "
                                    + "dentro del batch. "
                                    + "documentId="
                                    + selected.documentId()
                                    + ", id="
                                    + selected.id()
                    );
                }

                finalists.add(
                        new IndexedSource(
                                indexed.index(),
                                source
                        )
                );

                System.out.printf(
                        "KEEP | score=%.2f | global=%2d | %s | %s%n",
                        score,
                        indexed.index(),
                        selected.documentId(),
                        selected.id()
                );
            }

            System.out.printf(
                    "KEEP %d / %d | DROP %d%n",
                    finalists.size(),
                    batch.size(),
                    batch.size() - finalists.size()
            );

            System.out.println(
                    "=================================="
            );

            return finalists;
        });
    }
    private String sourceKey(
            ExpandedSource source) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "ExpandedSource no puede ser null"
            );
        }

        SegmentSearchResult selected =
                source.selected();

        if (selected == null) {
            throw new IllegalStateException(
                    "ExpandedSource.selected() no puede ser null"
            );
        }

        return selected.documentId()
                + "|"
                + selected.id();
    }

    private record IndexedSource(
            int index,
            ExpandedSource source
    ) {
    }

    private record RankedSource(
            int index,
            double score
    ) {
    }

    private Mono<List<ExpandedSource>> rerank2(
            String question,
            List<ExpandedSource> sources,
            int topK) {

        if (sources == null
                || sources.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        System.out.println();
        System.out.println(
                "===== SOURCES BEFORE RERANK ====="
        );

        for (int i = 0; i < sources.size(); i++) {

            ExpandedSource source =
                    sources.get(i);

            SegmentSearchResult selected =
                    source.selected();

            System.out.printf(
                    "%d | %s | %s%n",
                    i,
                    selected.documentId(),
                    selected.id()
            );
        }

        System.out.println(
                "================================="
        );
        System.out.println();

        RerankRequest request =
                rerankRequestBuilder.build(
                        question,
                        sources
                );

        return rerankerService.rerank(
                request,
                topK
        );
    }
    
    private Mono<RagResponse> generateResponse(
            String question,
            List<ExpandedSource> sources) {

        if (sources == null
                || sources.isEmpty()) {

            return Mono.just(
                    new RagResponse(
                            question,
                            "No se encontraron fuentes relevantes.",
                            List.of()
                    )
            );
        }

        LlmRequest request =
                llmRequestBuilder.build(
                        question,
                        sources
                );

        return llmService.generate(new LlmRequest(
        		request.instructions(),
                request.input(),
                null))
        
        .map(answer ->
                new RagResponse(
                        question,
                        answer,
                        sources
                )
        );
    }
    
    private Mono<SourcesResponse> generateSourcesResponse(
            String question,
            List<ExpandedSource> expandedSources) {

        Map<String, Source> sourcesByDocumentId =
                new LinkedHashMap<>();

        for (ExpandedSource expandedSource : expandedSources) {

            SegmentSearchResult result =
                    expandedSource.selected();

            String documentId =
                    documentDao.getDocumentId(
                            expandedSource
                    );

            sourcesByDocumentId.putIfAbsent(
                    documentId,
                    new Source(
                            documentId,
                            result.documentTitle(),
                            result.documentDate(),
                            result.score()
                    )
            );
        }

        List<Source> sources =
                new ArrayList<>(
                        sourcesByDocumentId.values()
                );

        SourcesResponse response =
                new SourcesResponse(
                        question,
                        sources
                );

        return Mono.just(response);
    }
    
    private List<String> getFilters(Map<String, String> parameters) {
    	return filterBuilder.build(parameters);
    }
}