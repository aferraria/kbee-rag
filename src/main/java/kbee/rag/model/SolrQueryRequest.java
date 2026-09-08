package kbee.rag.model;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class SolrQueryRequest {
    @NotBlank private String query;
    @Min(0) private int start = 0;
    @Min(1) @Max(1000) private int rows = 10;
    private String sort;
    private List<String> filterQueries = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    public String getQuery(){return query;} public void setQuery(String query){this.query=query;}
    public int getStart(){return start;} public void setStart(int start){this.start=start;}
    public int getRows(){return rows;} public void setRows(int rows){this.rows=rows;}
    public String getSort(){return sort;} public void setSort(String sort){this.sort=sort;}
    public List<String> getFilterQueries(){return filterQueries;}
    public void setFilterQueries(List<String> v){this.filterQueries=v==null?new ArrayList<>():v;}
    public List<String> getFields(){return fields;}
    public void setFields(List<String> v){this.fields=v==null?new ArrayList<>():v;}
}
