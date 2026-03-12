package com.ultracards.gateway.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class ServerService {

    private static final Log log = LogFactory.getLog(ServerService.class);

    private final RestTemplate restTemplate;
    private final String serverUrl;

    @Autowired
    public ServerService(RestTemplate restTemplate,
                         @Qualifier("serverUrl")  String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
    }

    public ServerConnectionStatus isTheServerUp() {

        var url = URI.create(serverUrl + "/active").toString();

        try {
            var res = restTemplate.exchange(url, HttpMethod.GET, null, Void.class);
            if (res.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Server is active!");
                return ServerConnectionStatus.CONNECTED;
            } else {
                log.error("Server had a connection issue.");
                return ServerConnectionStatus.REFUSED;
            }
        } catch (Exception e) {
            log.error("Could not connect to the server! Check if the server is turned on first.");
            return ServerConnectionStatus.REFUSED;
        }
    }

    public enum ServerConnectionStatus{
        CONNECTED, REFUSED, ERROR
    }
}
