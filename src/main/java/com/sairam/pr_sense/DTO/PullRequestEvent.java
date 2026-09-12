package com.sairam.pr_sense.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestEvent {

    private String action;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Installation installation;

    @JsonProperty("repository")
    private Repository repository;

    // getters and setters
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public PullRequest getPullRequest() { return pullRequest; }
    public void setPullRequest(PullRequest pullRequest) { this.pullRequest = pullRequest; }
    public Installation getInstallation() { return installation; }
    public void setInstallation(Installation installation) { this.installation = installation; }
    public Repository getRepository() { return repository; }
    public void setRepository(Repository repository) { this.repository = repository; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        private int number;
        private String title;

        @JsonProperty("diff_url")
        private String diffUrl;

        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDiffUrl() { return diffUrl; }
        public void setDiffUrl(String diffUrl) { this.diffUrl = diffUrl; }

        @JsonProperty("head")
        private Head head;

        public Head getHead() { return head; }
        public void setHead(Head head) { this.head = head; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Head {
            private String sha;
            private String ref;

            public String getSha() { return sha; }
            public void setSha(String sha) { this.sha = sha; }
            public String getRef() { return ref; }
            public void setRef(String ref) { this.ref = ref; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Installation {
        private long id;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        @JsonProperty("full_name")
        private String fullName;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }
}