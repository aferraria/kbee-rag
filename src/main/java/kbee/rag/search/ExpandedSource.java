package kbee.rag.search;

import java.util.List;


public record ExpandedSource(
        SegmentSearchResult selected,
        List<SegmentSearchResult> contextSegments
) {


}