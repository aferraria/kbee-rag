package kbee.rag.search;

import java.util.List;
import java.util.Map;

public interface FilterQueryBuilder {

    List<String> build(
            Map<String, String> parameters
    );
}