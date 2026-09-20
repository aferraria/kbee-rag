package kbee.rag.thesaurus;

import java.util.List;

public record ThesaurusSelection(
        List<ThesaurusTermBoost> terms) {
}