package kbee.rag.search;

import java.util.List;

public class ExtendedSegmentSearchRequest
        extends SegmentSearchRequest {

    private final String extendedQuery;

    private final List<Concept> thesaurusTerms;

    private final List<String> propositions;

    public ExtendedSegmentSearchRequest(
            SegmentSearchRequest request,
            String extendedQuery,
            List<Concept> thesaurusTerms,
            List<String> propositions) {

        super(
                request.query(),
                request.filters(),
                request.topK()
        );

        this.extendedQuery = extendedQuery;
        this.thesaurusTerms = thesaurusTerms;
        this.propositions = propositions;
    }

    public String extendedQuery() {
        return extendedQuery;
    }

    public List<Concept> thesaurusTerms() {
        return thesaurusTerms;
    }

    public List<String> propositions() {
        return propositions;
    }
}