package kbee.rag.search;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import kbee.rag.audit.AuditContext;
import kbee.rag.audit.AuditRecord;
import kbee.rag.audit.ReactorAuditPublisher;
import kbee.rag.config.InstructionProvider;
import kbee.rag.document.DocumentDao;
import kbee.rag.document.DocumentText;
import kbee.rag.llm.LlmResponseRequest;
import kbee.rag.llm.LlmAnalysisRequest;
import kbee.rag.llm.LlmRequestFactory;
import kbee.rag.reranker.RerankRequestBuilderFactory;
import kbee.rag.reranker.RerankerService;
import reactor.core.publisher.Mono;

@Service
public class RagService {
	
    private static final Logger log =
            LoggerFactory.getLogger(RagService.class);

    private final SegmentSearchService segmentSearchService;

    private final DocumentDao documentDao;

    private final RerankRequestBuilderFactory rerankRequestFactory;
    
    private final LlmRequestFactory llmRequestFactory;

    private final RerankerService rerankerService;

    private final FilterQueryBuilder filterBuilder;

    public RagService(
            SegmentSearchService segmentSearchService,
            DocumentDao documentDao,
            RerankRequestBuilderFactory rerankRequestFactory,
            RerankerService rerankerService,
            LlmRequestFactory llmRequestFactory,
            FilterQueryBuilder filterBuilder,
            InstructionProvider instructionProvider) {

        this.segmentSearchService =
                segmentSearchService;

        this.documentDao =
                documentDao;

        this.rerankRequestFactory =
                rerankRequestFactory;

        this.rerankerService =
                rerankerService;
        
        this.filterBuilder = filterBuilder;
        
        this.llmRequestFactory = llmRequestFactory;

    }
    
    public Mono<RagResponse> answer(
            RagRequest request) {

        /**
         * server side id of the query. The query information and results will be
         * stored on disk or cache under this id (to be implemented).
         */
        String queryId = java.util.UUID.randomUUID().toString();

        AuditContext auditContext =
                new AuditContext(
                        request.question(),
                        request.topK()
                );

        return doAnswer(request)
                .map(response -> response.withQueryId(queryId))
                .doOnNext(response -> {

                    AuditRecord record =
                            auditContext.finish();

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Total time: {}",
                                record.totalMillis()
                        );
                    }
                })
                .contextWrite(context ->
                        context
                                .put(
                                        "llm",
                                        request.llm()
                                )
                                .put(
                                        ReactorAuditPublisher.AUDIT_CONTEXT_KEY,
                                        auditContext
                                )
                );
    }
    
    
    public Mono<RagResponse> doAnswer(
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
    
    
    public Mono<DocumentAnalysisResponse> analyze(
    		DocumentAnalysisRequest request) {
    	
        AuditContext auditContext =
                new AuditContext(
                		request.documentId(),
                		0
                );

        return doAnalyze(request)
                .doOnNext(response -> {

                    AuditRecord record =
                            auditContext.finish();

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Total time: {}",
                                record.totalMillis()
                        );
                    }
                })
                .contextWrite(context ->
                        context
                                .put(
                                        "llm",
                                        request.llm()
                                )
                                .put(
                                        ReactorAuditPublisher.AUDIT_CONTEXT_KEY,
                                        auditContext
                                )
                );
    }

    /**
     * Global analysis of a query previously executed and stored by the server.
     * The query is identified by the server's query id.
     *
     * TODO stub implementation: query storage on the server is not implemented
     * yet, so a placeholder analysis is returned.
     */
    public Mono<QueryAnalysis> queryAnalysis(QueryAnalysisRequest request) {

        long start = System.currentTimeMillis();

        String analysis =
                "Análisis general (stub) para la consulta " + request.queryId()
                        + ". El almacenamiento de consultas en el servidor todavía no está implementado.";

        return Mono.just(
                new QueryAnalysis(
                        request.queryId(),
                        request.llm(),
                        java.time.OffsetDateTime.now(),
                        System.currentTimeMillis() - start,
                        analysis
                )
        );
    }

    public Mono<DocumentAnalysisResponse> doAnalyze(DocumentAnalysisRequest request) {

        return documentDao
                .getDocument(
                        request.documentId()
                )
                .switchIfEmpty(
                        Mono.error(
                                new IllegalArgumentException(
                                        "Document not found: "
                                                + request.documentId()
                                )
                        )
                )
                .flatMap(document -> {
                    return generateAnalysis(request.question(), document);
                });
    }
    
    private Mono<List<ExpandedSource>> rerank(
            String question,
            List<ExpandedSource> sources,
            int topK) {

        return rerankRequestFactory
                .builder()
                .question(question)
                .sources(sources)
                .topK(topK)
                .build()
                .execute(rerankerService);
    }
    
    
    private Mono<RagResponse> generateResponse(
            String question,
            List<ExpandedSource> sources) {

    	    return llmRequestFactory.execute(
    	            LlmResponseRequest.class,
    	            builder ->
    	                    builder
    	                            .input(question)
    	                            .input(sources)
    	    );
    	}
    
    private Mono<DocumentAnalysisResponse> generateAnalysis(
            String question,
            DocumentText document) {
    	
	    return llmRequestFactory.execute(
	    		LlmAnalysisRequest.class,
	            builder ->
	                    builder
                        .input(question)
                        .input(document)
	    );
    }
    
    private List<String> getFilters(Map<String, String> parameters) {
    	return filterBuilder.build(parameters);
    }
}