package kbee.rag.reranker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BatchRerankRequest
        implements RerankRequest {

    private String question;

    private List<ExpandedSource> sources;

    private int topK;

    private int batchSize;
    
    @Override
    public String question() {
        return question;
    }

    @Override
    public List<ExpandedSource> sources() {
        return sources;
    }

    @Override
    public Mono<List<ExpandedSource>> execute(
            RerankerService reranker) {

        if (sources == null || sources.isEmpty()) {
            return Mono.just(List.of());
        }
        
        printSources();
        
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
                .buffer(batchSize)
                .concatMap(batch ->
                        rerankBatch(
                                reranker,
                                batch
                        )
                )
                .flatMapIterable(Function.identity())
                .map(IndexedSource::source)
                .sort(
                        Comparator.comparingDouble(
                                (ExpandedSource source) ->
                                        source.selected().score()
                        ).reversed()
                )
                .collectList();
     }
    
    
    private Mono<List<IndexedSource>> rerankBatch(
            RerankerService reranker,
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

        RerankRequest batchRequest =
                new SimpleRerankRequest(
                        question(),
                        batchSources
                );

        return reranker.rerank(
                batchRequest,
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
    
    private void printSources() {
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
    }
    

		public static RerankRequestBuilder builder() {
		
		    return new Builder();
		}
		
		public static class Builder
		        implements RerankRequestBuilder {
		
		    private String question;
		
		    private List<ExpandedSource> sources;
		
		    private int topK;
		
		    @Override
		    public RerankRequestBuilder question(
		            String question) {
		
		        this.question = question;
		
		        return this;
		    }
		
		    @Override
		    public RerankRequestBuilder sources(
		            List<ExpandedSource> sources) {
		
		        this.sources = sources;
		
		        return this;
		    }
		
		    @Override
		    public RerankRequestBuilder topK(
		            int topK) {
		
		        this.topK = topK;
		
		        return this;
		    }
		
		    @Override
		    public RerankRequest build() {
		
		        BatchRerankRequest request =
		                new BatchRerankRequest();
		
		        request.question = question;
		        request.sources = sources;
		        request.topK = topK;
		
		        return request;
		    }
		}

    
    private record IndexedSource(
            int index,
            ExpandedSource source
    ) {
    }
}