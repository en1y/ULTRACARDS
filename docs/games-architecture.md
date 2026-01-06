# Games Architecture Overview

This document outlines the game-related components added to the ULTRACARDS server and game-gateway modules: entities, repositories, services, controllers, DTOs, and the WebSocket pipeline for continuous communication and player presence.

## Goals

- Create and manage a lobby for any `GameType` with customizable rules (e.g., BriskulaGameConfig variations).
- Start a game from a lobby and persist a `GameEntity` with players and state.
- Broadcast in-progress game events over WebSockets.
- Update `UserGamesStats` when a game starts (played++) and when finished (wins++ for winners).
- Provide DTOs and client-side services (REST + WebSocket) in `game-gateway` for easy integration from other Java projects.

---

## Server Module

### Entities

- `server/src/main/java/com/ultracards/server/entity/games/GameEntity.java`
  - Persists a game instance: `id`, `type` (`GameType`), `gameName`, `creator`, `players`, `gameStateJson` (jsonb), timestamps, and `active`.
  - `gameStateJson` stores game-specific state/config at start and can be updated during or after the game.

- `server/src/main/java/com/ultracards/server/entity/games/GameLobby.java`
  - In-memory lobby representation used by `LobbyService`. Holds `id`, `gameType`, `createdAt`, `players`, `owner`.

- `server/src/main/java/com/ultracards/server/entity/games/UserGamesStats.java`
  - Existing entity used to track per-user played/won counts by `GameType`.

### Repositories

- `server/src/main/java/com/ultracards/server/repositories/games/GameEntityRepository.java`
  - Standard JPA repository for `GameEntity`.

### Services

- `server/src/main/java/com/ultracards/server/service/games/LobbyService.java`
  - In-memory lobby lifecycle:
    - `createLobby(owner, LobbyCreateRequest)` — creates a lobby (parses `gameType` string to `GameType`).
    - `listLobbies()` — lists active lobbies.
    - `joinLobby(user, lobbyId)` — adds a player (idempotent); owner leaving disbands lobby.
    - `disbandLobby(lobbyId)` — removes lobby when game starts or owner leaves.

- `server/src/main/java/com/ultracards/server/service/games/GameService.java`
  - `startGame(lobby, configJson)` — persists a new `GameEntity`, increments `played` for all lobby players, and publishes a `STARTED` WebSocket event.
  - `finishGame(GameResultDTO, GameType)` — marks the game inactive, optionally stores final state, increments `wins` for winners, and publishes a `FINISHED` event.
  - `getGame(id)` and `toDto(entity)` helpers.

- `server/src/main/java/com/ultracards/server/service/games/GameEventPublisher.java`
  - Wraps `SimpMessagingTemplate` to publish `GameEventDTO` to `/topic/games/{gameId}/events`.

- Existing: `UserGamesStatsService` updates stats on start/finish.

### WebSocket

- `server/src/main/java/com/ultracards/server/config/WebSocketConfig.java`
  - STOMP endpoint: `/ws` (CORS allowed origins `*`).
  - Application prefix: `/app`, Broker prefix: `/topic`.

- `server/src/main/java/com/ultracards/server/controllers/games/GameWsController.java`
  - `@MessageMapping("/games/{gameId}/heartbeat")` — accepts client heartbeat (`HeartbeatDTO`), re-broadcasts `HEARTBEAT` event on the game topic.
  - `@MessageMapping("/games/{gameId}/action")` — accepts `PlayerActionDTO` and broadcasts the corresponding event. Hook in actual game engines here.

Security: WebSocket handshake uses the same cookie (`refreshToken`) to authenticate; Spring Security filter rotates token and authenticates requests.

### REST Controllers

- `server/src/main/java/com/ultracards/server/controllers/games/GameLobbyController.java`
  - `POST /api/games/lobbies` — create lobby (owner = caller). Uses `LobbyCreateRequest`.
  - `GET /api/games/lobbies` — list lobbies.
  - `POST /api/games/lobbies/join` — join an existing lobby.
  - `POST /api/games/start` — owner-only; starts game from lobby and disbands lobby. Increments played for all players.

- `server/src/main/java/com/ultracards/server/controllers/games/GameController.java`
  - `GET /api/games/{id}` — fetch game details.
  - `POST /api/games/finish?type=BRISKULA` — finishes game and updates winners using `GameResultDTO`.

Notes:
- For Briskula (or other games), pass variations via `configJson` (e.g., serialized `BriskulaGameConfig`).
- Hook your game engine to consume actions and update `gameStateJson`, broadcasting `STATE` events on changes.

---

## game-gateway Module (Integration API)

### DTOs (Strings used for `gameType` to avoid cross-module dependency)

- `LobbyCreateRequest` — `gameType`, `lobbyName`, `configJson`, `maxPlayers`.
- `LobbyDTO` — lobby fields + `playerIds`.
- `StartGameRequest` — `lobbyId`, optional `configJson`.
- `GameDTO` — `id`, `gameName`, `gameType` (String), `playerIds`, `stateJson`, `active`, timestamps.
- `GameEventDTO` — `gameId`, `eventType` (STATE, PLAYER_JOINED, etc.), `payloadJson`, `createdAt`.
- `PlayerActionDTO` — `gameId`, `actionType`, `payloadJson`.
- `HeartbeatDTO` — `gameId`, `sentAt`.
- `GameResultDTO` — `gameId`, `winnerUserIds`, `finalStateJson`.

### Services

- `GamesService` — REST client using `RestTemplate`:
  - `createLobby(token, request)`
  - `listLobbies(token)`
  - `joinLobby(token, lobbyId)`
  - `startGame(token, request)`
  - `getGame(token, gameId)`
  - `finishGame(token, result)`
  - Reuses cookie-based token from `ClientTokenHolder` (same approach as existing AuthService).

- `GameWsClient` — STOMP client:
  - `connect(tokenHolder, onConnected, onError)` → connects to `/ws` with cookie `refreshToken`.
  - `subscribeToGame(gameId, handler)` → subscribes to `/topic/games/{id}/events`.
  - `sendHeartbeat(gameId)` and `sendAction(action)` to `/app/games/{id}/...`.

---

## Flow Summary

1) Create Lobby: Client calls `POST /api/games/lobbies` with `gameType` and optional `configJson`. Owner is current user.
2) Join Lobby: Players call join. Lobby exists in-memory.
3) Start Game: Owner calls `POST /api/games/start` with `lobbyId`. Server persists `GameEntity`, increments played for all players, publishes `STARTED` event, disbands lobby.
4) Live Updates: Clients connect to `/ws`, subscribe to `/topic/games/{id}/events`. Actions sent to `/app/games/{id}/action`, heartbeats to `/app/games/{id}/heartbeat`.
5) Finish Game: Game engine (or host) posts `POST /api/games/finish?type=...` with winners. Server updates `wins` for winners and publishes `FINISHED`.

---

## Next Steps & Hooks

- Plug in your per-game engine to process `PlayerActionDTO`, mutate the state, and call `GameEventPublisher.publish(..., "STATE", updatedStateJson)`.
- Parse/validate `configJson` (e.g., into `BriskulaGameConfig`) on start to initialize engine/state.
- Add presence tracking with timeouts using a scheduled task to mark `PLAYER_LEFT` if no heartbeat in N seconds.
- Add persistence for lobbies if desired, or migrate to Redis for clustered deployments.

