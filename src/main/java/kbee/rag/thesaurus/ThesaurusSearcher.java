package kbee.rag.thesaurus;

import java.util.List;

public interface ThesaurusSearcher {

    List<Concept> findCandidates(
            String text
    );
}