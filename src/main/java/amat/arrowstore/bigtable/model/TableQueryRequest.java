package amat.arrowstore.bigtable.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.ArrayList;

public class TableQueryRequest {
    @NotBlank
    private final String sessionId;
    private final List<FilterCriteria> filters;
    private final List<SortSpecification> sorts;
    private final String searchTerm;
    @Min(0)
    private final int page;
    @Min(1)
    private final int pageSize;

    @JsonCreator
    public TableQueryRequest(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("filters") List<FilterCriteria> filters,
            @JsonProperty("sorts") List<SortSpecification> sorts,
            @JsonProperty("searchTerm") String searchTerm,
            @JsonProperty("page") Integer page,
            @JsonProperty("pageSize") Integer pageSize) {
        this.sessionId = sessionId;
        this.filters = filters != null ? filters : new ArrayList<>();
        this.sorts = sorts != null ? sorts : new ArrayList<>();
        this.searchTerm = searchTerm;
        this.page = page != null ? page : 0;
        this.pageSize = pageSize != null ? pageSize : 100;
    }

    public String getSessionId() { return sessionId; }
    public List<FilterCriteria> getFilters() { return filters; }
    public List<SortSpecification> getSorts() { return sorts; }
    public String getSearchTerm() { return searchTerm; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}