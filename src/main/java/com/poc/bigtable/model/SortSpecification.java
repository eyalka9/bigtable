package com.poc.bigtable.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SortSpecification {
    private final String column;
    private final SortDirection direction;
    private final int priority;

    @JsonCreator
    public SortSpecification(
            @JsonProperty("column") String column,
            @JsonProperty("direction") SortDirection direction,
            @JsonProperty("priority") int priority) {
        this.column = column;
        this.direction = direction != null ? direction : SortDirection.ASC;
        this.priority = priority;
    }

    public String getColumn() { return column; }
    public SortDirection getDirection() { return direction; }
    public int getPriority() { return priority; }
}