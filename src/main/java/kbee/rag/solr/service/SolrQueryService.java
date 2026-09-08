package kbee.rag.solr.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kbee.rag.model.SolrQueryRequest;
import kbee.rag.model.SolrQueryResponse;

@Service
public class SolrQueryService {
    private final SolrClient solrClient; private final String core;
    public SolrQueryService(SolrClient solrClient,@Value("${solr.source.core}") String core){this.solrClient=solrClient;this.core=core;}
    public SolrQueryResponse query(SolrQueryRequest request){
        SolrQuery q=new SolrQuery(request.getQuery()); q.setStart(request.getStart()); q.setRows(request.getRows());
        if(StringUtils.hasText(request.getSort())) q.setParam("sort",request.getSort());
        if(!request.getFilterQueries().isEmpty()) q.addFilterQuery(request.getFilterQueries().toArray(String[]::new));
        if(!request.getFields().isEmpty()) q.setFields(request.getFields().toArray(String[]::new));
        try{QueryResponse r=solrClient.query(core,q); SolrDocumentList docs=r.getResults();
            return new SolrQueryResponse(docs.getNumFound(),docs.getStart(),r.getElapsedTime(),convert(docs));
        }catch(SolrServerException|IOException e){throw new SolrQueryException("No fue posible ejecutar la consulta en Solr",e);}
    }
    public SolrQueryResponse rawQuery(String query){SolrQueryRequest r=new SolrQueryRequest();r.setQuery(query);return query(r);}
    private List<Map<String,Object>> convert(SolrDocumentList docs){
        List<Map<String,Object>> out=new ArrayList<>(docs.size());
        for(SolrDocument d:docs){Map<String,Object> m=new LinkedHashMap<>(); for(String f:d.getFieldNames())m.put(f,d.getFieldValue(f)); out.add(m);}
        return out;
    }
}
