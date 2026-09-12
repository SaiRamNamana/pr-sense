package com.sairam.pr_sense.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewResult(
        @JsonProperty("summary")  String summary,
        @JsonProperty("comments") List<ReviewComment> comments) {

    public ReviewResult {
        comments = (comments == null) ? List.of() : comments;
    }
}