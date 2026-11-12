module game.gateway {
    requires java.net.http;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires jakarta.validation;
    requires static lombok;
    requires spring.beans;
    requires spring.context;
    requires spring.core;
    requires org.apache.commons.logging;
    requires spring.messaging;
    requires spring.web;
    requires spring.websocket;

    requires transitive cards.template;
    requires italian.cards;
    requires poker.cards;

    opens com.ultracards.gateway.dto to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.admin to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.auth to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.friends to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.chat to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.games to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.games.briskula to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.games.durak to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.games.treseta to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.games.lobby to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.leaderboard to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.notifications to com.fasterxml.jackson.databind;
    opens com.ultracards.gateway.dto.points to com.fasterxml.jackson.databind;

    exports com.ultracards.gateway.app;
    exports com.ultracards.gateway.dto;
    exports com.ultracards.gateway.dto.admin;
    exports com.ultracards.gateway.dto.auth;
    exports com.ultracards.gateway.dto.friends;
    exports com.ultracards.gateway.dto.games;
    exports com.ultracards.gateway.dto.games.chat;
    exports com.ultracards.gateway.dto.games.games;
    exports com.ultracards.gateway.dto.games.games.briskula;
    exports com.ultracards.gateway.dto.games.games.durak;
    exports com.ultracards.gateway.dto.games.games.treseta;
    exports com.ultracards.gateway.dto.games.lobby;
    exports com.ultracards.gateway.dto.leaderboard;
    exports com.ultracards.gateway.dto.notifications;
    exports com.ultracards.gateway.dto.points;
    exports com.ultracards.gateway.service;
}
