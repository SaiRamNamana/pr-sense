package com.sairam.pr_sense.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InstallationEvent {

    private String action;

    private Installation installation;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Installation getInstallation() { return installation; }
    public void setInstallation(Installation installation) { this.installation = installation; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Installation {
        private long id;
        private Account account;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Account getAccount() { return account; }
        public void setAccount(Account account) { this.account = account; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private String login;
        private String type;

        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}