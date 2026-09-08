package kbee.rag.search;

import java.util.List;

import reactor.core.publisher.Mono;

public interface DocumentDao {

    Mono<List<ExpandedSource>> getSources(
            List<SegmentSearchResult> results,
            String question
    );
}