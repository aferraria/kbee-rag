package kbee.rag.solr.service;

public class SolrQueryException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SolrQueryException(String message, Throwable cause) {
		super(message, cause);
	}
}
