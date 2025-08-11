package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameService {
    private final RestTemplate restTemplate;
    private final String serverBaseUrl;

    @Autowired
    // You still have to initiate it if you are not using Spring
    // If you are using Spring you can initiate the serverBaseUrl bean
    public GameService(RestTemplate restTemplate,
                       @Qualifier("serverBaseUrl") String serverBaseUrl) {
        this.restTemplate = restTemplate;
        this.serverBaseUrl = serverBaseUrl;
    }

    /**
     * Creates HTTP headers with the Authorization header set to "Bearer {token}".
     *
     * @param token The JWT token
     * @return The HTTP headers
     */
    private HttpHeaders createAuthHeaders(String token) {
        var headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * Create a new game.
     *
     * @param gameName The name of the game
     * @param gameType The type of game to create
     * @param playerIds The IDs of the players in the game
     * @param gameOptions Additional options for the game
     * @param token The JWT token for authentication
     * @return The created game
     */
    public GameResponseDTO createGame(String gameName, String gameType, List<Long> playerIds, Long creatorId, Map<String, Object> gameOptions, String token) {
        var requestDTO = new GameCreationRequestDTO(gameName, gameType, creatorId, playerIds, gameOptions);
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<>(requestDTO, headers);

        return restTemplate.exchange(
                serverBaseUrl + "/api/games",
                HttpMethod.POST,
                entity,
                GameResponseDTO.class).getBody();
    }

    /**
     * Get a game by ID.
     *
     * @param gameId The ID of the game to get
     * @param token The JWT token for authentication
     * @return The game
     */
    public GameResponseDTO getGame(String gameId, String token) {
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<Void>(headers);

        return restTemplate.exchange(
                serverBaseUrl + "/api/games/" + gameId,
                HttpMethod.GET,
                entity,
                GameResponseDTO.class).getBody();
    }

    /**
     * Update a game with an action.
     *
     * @param gameId The ID of the game to update
     * @param playerId The ID of the player performing the action
     * @param actionType The type of action to perform
     * @param token The JWT token for authentication
     * @return The updated game
     */
    public GameResponseDTO updateGame(String gameId, Long playerId, GameAction actionType, String token) {
        var requestDTO = new GameActionRequestDTO(playerId, actionType);
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<>(requestDTO, headers);

        return restTemplate.exchange(
                serverBaseUrl + "/api/games/" + gameId,
                HttpMethod.PUT,
                entity,
                GameResponseDTO.class).getBody();
    }

    /**
     * List all games.
     *
     * @param token The JWT token for authentication
     * @return A list of all games
     */
    public List<GameSummaryDTO> listGames(String token) {
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(
                serverBaseUrl + "/api/games",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<GameSummaryDTO>>() {});
        return response.getBody() != null ? response.getBody() : Collections.emptyList();
    }

    /**
     * List games by status.
     *
     * @param status The status to filter by
     * @param token The JWT token for authentication
     * @return A list of games with the specified status
     */
    public List<GameSummaryDTO> listGamesByStatus(String status, String token) {
        var url = UriComponentsBuilder.fromUri(
                    URI.create(serverBaseUrl + "/api/games")
                ).queryParam("status", status)
                .toUriString();

        return getGameSummaryDTOS(token, url);
    }

    /**
     * List games by game type.
     *
     * @param gameType The game type to filter by
     * @param token The JWT token for authentication
     * @return A list of games with the specified game type
     */
    public List<GameSummaryDTO> listGamesByType(String gameType, String token) {
        var url = UriComponentsBuilder.fromUri(URI.create(serverBaseUrl + "/api/games"))
                .queryParam("gameType", gameType)
                .toUriString();

        return getGameSummaryDTOS(token, url);
    }

    /**
     * List games by player.
     *
     * @param playerId The player ID to filter by
     * @param token The JWT token for authentication
     * @return A list of games that include the specified player
     */
    public List<GameSummaryDTO> listGamesByPlayer(Long playerId, String token) {
        var url = UriComponentsBuilder.fromUri(URI.create(serverBaseUrl + "/api/games"))
                .queryParam("playerId", playerId)
                .toUriString();

        return getGameSummaryDTOS(token, url);
    }

    public void stopGame(GameSummaryDTO game, String token) {

        var url = UriComponentsBuilder
                .fromUri(URI.create(serverBaseUrl + "/api/games/" + game.getGameId()))
                .toUriString();
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                new ParameterizedTypeReference<Void>() {}
        );
//        TODO: Add Admin role to the web-ui and make it automatically create a user.
//        Wtf did i even want?????
    }

    /**
     * Removes games by player
     *
     * @param playerId The player ID whose games to delete
     * @param token The JWT token for authentication
     */
    public void stopGamesByPlayer(Long playerId, String token) {
        var games = listGamesByPlayer(playerId, token);
        for (var game : games) {
            stopGame(game, token);
        }
    }

    public void leaveGame() {

    }

    private List<GameSummaryDTO> getGameSummaryDTOS(String token, String url) {
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<Void>(headers);

        var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<GameSummaryDTO>>() {});
        return response.getBody() != null ? response.getBody() : Collections.emptyList();
    }
}
