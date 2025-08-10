package com.poc.bigtable.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ColumnDefinition {
    private final String name;
    private final DataType type;
    private final boolean sortable;
    private final boolean filterable;
    private final boolean searchable;
    private final int width;

    @JsonCreator
    public ColumnDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("type") DataType type,
            @JsonProperty("sortable") boolean sortable,
            @JsonProperty("filterable") boolean filterable,
            @JsonProperty("searchable") boolean searchable,
            @JsonProperty("width") Integer width) {
        this.name = name;
        this.type = type;
        this.sortable = sortable;
        this.filterable = filterable;
        this.searchable = searchable;
        this.width = width != null ? width : getDefaultWidth(type);
    }

    public String getName() { return name; }
    public DataType getType() { return type; }
    public boolean isSortable() { return sortable; }
    public boolean isFilterable() { return filterable; }
    public boolean isSearchable() { return searchable; }
    public int getWidth() { return width; }
    public boolean hasWidth() { return width > 0; }
    
    private static int getDefaultWidth(DataType type) {
        switch (type) {
            case STRING: return 80;
            case BINARY: return 1024;
            default: return -1; // No width for other types
        }
    }
}