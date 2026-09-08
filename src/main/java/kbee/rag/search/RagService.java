package kbee.rag.search;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import kbee.rag.audit.AuditContext;
import kbee.rag.audit.AuditRecord;
import kbee.rag.audit.ReactorAuditPublisher;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.reranker.RerankRequest;
import kbee.rag.reranker.RerankRequestBuilder;
import kbee.rag.reranker.RerankerService;
import reactor.core.publisher.Mono;

@Service
public class RagService {

    private final SegmentSearchService segmentSearchService;

    private final DocumentDao documentDao;

    private final RerankRequestBuilder rerankRequestBuilder;
    
    private final LlmRequestBuilder llmRequestBuilder;

    private final RerankerService rerankerService;

    private final LlmService llmService;
    
    private final FilterQueryBuilder filterBuilder;

    public RagService(
            SegmentSearchService segmentSearchService,
            DocumentDao documentDao,
            RerankRequestBuilder rerankRequestBuilder,
            RerankerService rerankerService,
            LlmRequestBuilder llmRequestBuilder,
            LlmService llmService,
            FilterQueryBuilder filterBuilder) {

        this.segmentSearchService =
                segmentSearchService;

        this.documentDao =
                documentDao;

        this.rerankRequestBuilder =
                rerankRequestBuilder;

        this.rerankerService =
                rerankerService;
        
        this.llmRequestBuilder = llmRequestBuilder;

        this.llmService =
                llmService;
        
        this.filterBuilder = filterBuilder;
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
    
    public Mono<List<ExpandedSource>> rankSources(
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

                    System.out.println(
                            "===== AUDIT RECORD ====="
                    );

                    System.out.println(
                            record
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

    
    public Mono<List<ExpandedSource>> doRankSources(
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

                .doOnNext(sources -> {

                    System.out.println(
                            "===== RERANKED SOURCES ====="
                    );

                    for (ExpandedSource source : sources) {

                        System.out.println(
                                source.selected().documentId()
                                        + " "
                                        + source.selected().documentTitle()
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
    
    public DocumentAnalysisResponse analyzeDocument(
            String documentId,
            String question) {

//        List<SegmentSearchResult> segments =
//                vectorSearchService.findDocumentSegments(
//                        resolveFalloId(documentId)
//                );
//
//        if (segments.isEmpty()) {
//            throw new IllegalArgumentException(
//                    "Document not found: " + documentId
//            );
//        }
//
//        SegmentSearchResult firstSegment = segments.get(0);
//
//        String documentText =
//                rebuildDocument(
//                        firstSegment,
//                        segments
//                );
//
//        String context = """
//                Pregunta del usuario:
//
//                %s
//
//                Documento a analizar:
//
//                %s
//                """.formatted(
//                        question,
//                        documentText
//                );
//
//        String answer =
//                llmService.generate(
//                        buildDocumentAnalysisInstructions(),
//                        context
//                );
//
//        return new DocumentAnalysisResponse(
//                documentId,
//                firstSegment.documentTitle(),
//                answer
//        );
    	
    	return null;
    }
    
    private Mono<List<ExpandedSource>> rerank(
            String question,
            List<ExpandedSource> sources,
            int topK) {

        if (sources == null
                || sources.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

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
    
    private List<String> getFilters(Map<String, String> parameters) {
    	return filterBuilder.build(parameters);
    }
}