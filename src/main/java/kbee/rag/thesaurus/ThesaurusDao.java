package kbee.rag.thesaurus;

import java.util.List;

public interface ThesaurusDao {

    List<ConceptRecord> getConcepts();
}