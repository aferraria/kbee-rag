package kbee.rag.segment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "segment-indexer")
public class SegmentIndexerProperties {

    private boolean enabled;

    private String sourceQuery = "*:*";
    private String sourceIdField = "id";
    private String sourceTextField = "text";
    private String sourceTitleField = "title";

    private int sourceStart = 0;
    private int sourceMaxIds = 100;
    private int sourcePageSize = 100;

    private int segmentSize = 1000;
    private int segmentOverlap = 150;

    private int embeddingBatchSize = 20;
    private int indexingBatchSize = 100;

    private int commitEveryDocuments = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceQuery() {
        return sourceQuery;
    }

    public void setSourceQuery(String sourceQuery) {
        this.sourceQuery = sourceQuery;
    }

    public String getSourceIdField() {
        return sourceIdField;
    }

    public void setSourceIdField(String sourceIdField) {
        this.sourceIdField = sourceIdField;
    }

    public String getSourceTextField() {
        return sourceTextField;
    }

    public void setSourceTextField(String sourceTextField) {
        this.sourceTextField = sourceTextField;
    }
    
    public String getSourceTitleField() {
        return sourceTitleField;
    }

    public void setSourceTitleField(String sourceTitleField) {
        this.sourceTitleField = sourceTitleField;
    }


    public int getSourceStart() {
        return sourceStart;
    }

    public void setSourceStart(int sourceStart) {
        this.sourceStart = sourceStart;
    }

    public int getSourceMaxIds() {
        return sourceMaxIds;
    }

    public void setSourceMaxIds(int sourceMaxIds) {
        this.sourceMaxIds = sourceMaxIds;
    }

    public int getSourcePageSize() {
        return sourcePageSize;
    }

    public void setSourcePageSize(int sourcePageSize) {
        this.sourcePageSize = sourcePageSize;
    }

    public int getSegmentSize() {
        return segmentSize;
    }

    public void setSegmentSize(int segmentSize) {
        this.segmentSize = segmentSize;
    }

    public int getSegmentOverlap() {
        return segmentOverlap;
    }

    public void setSegmentOverlap(int segmentOverlap) {
        this.segmentOverlap = segmentOverlap;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public int getIndexingBatchSize() {
        return indexingBatchSize;
    }

    public void setIndexingBatchSize(int indexingBatchSize) {
        this.indexingBatchSize = indexingBatchSize;
    }

    public int getCommitEveryDocuments() {
        return commitEveryDocuments;
    }

    public void setCommitEveryDocuments(int commitEveryDocuments) {
        this.commitEveryDocuments = commitEveryDocuments;
    }
}