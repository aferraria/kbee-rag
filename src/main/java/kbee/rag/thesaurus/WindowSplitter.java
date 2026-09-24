package kbee.rag.thesaurus;

import java.util.List;

public interface WindowSplitter {

    List<String> split(
            String text
    );
}