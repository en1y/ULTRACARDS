package com.ultracards.gateway.service;

import com.ultracards.gateway.config.ServerIssueResolver;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class ServerService {

    private static final Log log = LogFactory.getLog(ServerService.class);

    private final RestTemplate restTemplate;

    private ServerIssueResolver serverIssue;
    private ServerIssueResolver serverConnectionIssue;

    @Value("${app.ultracards.server.protocol}")
    private String serverProtocol;
    @Value("${app.ultracards.server.hostname}")
    private String serverHostname;
    @Value("${app.ultracards.server.port}")
    private String serverPort;

    // Should not be used since it should be autowired by Spring
    @Autowired
    public ServerService(RestTemplate restTemplate,
                         @Qualifier("serverIssue") ServerIssueResolver serverIssue,
                         @Qualifier("serverConnectionIssue") ServerIssueResolver serverConnectionIssue) {
        this.restTemplate = restTemplate;
        this.serverIssue = serverIssue;
        this.serverConnectionIssue = serverConnectionIssue;
    }

    public ServerService(RestTemplate restTemplate, String serverProtocol, String serverHostname, String serverPort) {
        this.restTemplate = restTemplate;
        this.serverProtocol = serverProtocol;
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
    }

    public ServerService(RestTemplate restTemplate, String serverProtocol, String serverHostname, String serverPort, ServerIssueResolver serverIssue, ServerIssueResolver serverConnectionIssue) {
        this.restTemplate = restTemplate;
        this.serverProtocol = serverProtocol;
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.serverIssue = serverIssue;
        this.serverConnectionIssue = serverConnectionIssue;
    }

    @PostConstruct
    @Scheduled(fixedRate = 10000) // every 10 seconds
    public void checkIsTheServerUp() {
        var serverBaseUrl = serverProtocol + "://" + serverHostname + ":" + serverPort;
        var url = URI.create(serverBaseUrl + "/active").toString();

        try {
            var res = restTemplate.exchange(url, HttpMethod.GET, null, Void.class);
            if (res.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Server is active!");
            } else {
                log.error("Server had a connection issue.");
                serverIssue();
            }
        } catch (Exception e) {
            log.error("Could not connect to the server! Check if the server is turned on first.");
            serverConnectionIssue();
        }
    }

    // You can override it or create a bean so that it is autowired
    private void serverIssue() {
        if (serverIssue != null) serverIssue.resolve();
    }

    public void serverConnectionIssue() {
        if (serverConnectionIssue != null) serverConnectionIssue.resolve();
    }

}
