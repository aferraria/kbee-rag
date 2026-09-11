package kbee.rag.document;

import java.util.List;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Mono;

public interface DocumentDao {

    Mono<List<ExpandedSource>> getSources(
            List<SegmentSearchResult> results,
            String question
    );
    
    String getDocumentId(ExpandedSource source);

        Mono<DocumentText> getDocument(
                String documentId
        );
}