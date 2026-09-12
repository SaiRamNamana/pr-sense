package com.sairam.pr_sense.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewComment(
        @JsonProperty("path")     String path,
        @JsonProperty("line")     int line,
        @JsonProperty("category") String category,   // BUG | SECURITY | STYLE | EDGE_CASE
        @JsonProperty("severity") String severity,   // high | medium | low
        @JsonProperty("comment")  String comment) {}