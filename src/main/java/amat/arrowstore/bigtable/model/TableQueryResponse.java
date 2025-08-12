package amat.arrowstore.bigtable.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class TableQueryResponse {
    private final List<Map<String, Object>> data;
    private final long totalElements;
    private final int totalPages;
    private final int currentPage;
    private final int pageSize;
    private final long queryTimeMs;
    private final String implementation;

    @JsonCreator
    public TableQueryResponse(
            @JsonProperty("data") List<Map<String, Object>> data,
            @JsonProperty("totalElements") long totalElements,
            @JsonProperty("totalPages") int totalPages,
            @JsonProperty("currentPage") int currentPage,
            @JsonProperty("pageSize") int pageSize,
            @JsonProperty("queryTimeMs") long queryTimeMs,
            @JsonProperty("implementation") String implementation) {
        this.data = data;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.queryTimeMs = queryTimeMs;
        this.implementation = implementation;
    }

    public List<Map<String, Object>> getData() { return data; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int getCurrentPage() { return currentPage; }
    public int getPageSize() { return pageSize; }
    public long getQueryTimeMs() { return queryTimeMs; }
    public String getImplementation() { return implementation; }
}