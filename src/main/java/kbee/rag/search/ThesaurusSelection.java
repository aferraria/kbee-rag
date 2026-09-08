package kbee.rag.search;

import java.util.List;

public record ThesaurusSelection(
        List<ThesaurusTermBoost> terms) {
}