package com.poc.bigtable.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class FilterCriteria {
    private final String column;
    private final FilterOperation operation;
    private final List<Object> values;
    private final LogicalOperator logicalOperator;

    @JsonCreator
    public FilterCriteria(
            @JsonProperty("column") String column,
            @JsonProperty("operation") FilterOperation operation,
            @JsonProperty("values") List<Object> values,
            @JsonProperty("logicalOperator") LogicalOperator logicalOperator) {
        this.column = column;
        this.operation = operation;
        this.values = values;
        this.logicalOperator = logicalOperator != null ? logicalOperator : LogicalOperator.AND;
    }

    public String getColumn() { return column; }
    public FilterOperation getOperation() { return operation; }
    public List<Object> getValues() { return values; }
    public LogicalOperator getLogicalOperator() { return logicalOperator; }
}