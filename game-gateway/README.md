# game-gateway

DTOs and client services for UI apps that talk to the ULTRACARDS server.

## Quick Start

```java
var client = new GatewayAppClient(
        "http://localhost:8080/",
        "ws://localhost:8080/ws"
);

var profile = client.profile().getProfile();
var lobbies = client.lobby().getLobbies();
```

Service groups:

- REST: `authentication`, `profile`, `lobby`, `game`, `friend`, `chat`, `notification`, `users`, `cards`, `server`, `uiPages`
- WS: `gameSocket`, `lobbySocket`, `chatSocket`, `notificationSocket`

## JavaFX Calls

Keep server calls off the UI thread:

```java
var async = GatewayAsync.cached(Platform::runLater);
var client = new GatewayAppClient(restTemplate, serverUrl, wsUrl, tokenHolder, async);

var request = client.async().call(() -> client.profile().getProfile());
client.async().onUi(request, profile -> {
    usernameLabel.setText(profile.getUsername());
}, error -> {
    errorLabel.setText(error.getMessage());
});
```

## State

Use `GatewayState<T>` for view-model state. `listen` returns a `GatewayListener` cleanup handle.

```java
var lobbyState = new GatewayState<GameLobbyDTO>();
var listener = lobbyState.listen(lobby -> renderLobby(lobby));

lobbyState.set(client.lobby().getLobby());
listener.close();
```

## WebSockets

Track subscriptions with `GatewaySocketHandle`, then close once when the screen closes.

```java
client.gameSocket().thenAccept(gameSocket -> {
    var handle = new GatewaySocketHandle(gameSocket::close);

    handle.track(gameSocket.subscribeToGame(gameId, event -> gameState.set(event.getGameEntity())));
    handle.track(gameSocket.subscribeToCards(cards -> handState.set(cards)));
    gameSocket.playCard(card);

    // later: handle.close();
});
```

## Checks

```bash
mvn -pl game-gateway -am test
java -ea -cp game-gateway/target/test-classes:game-gateway/target/classes com.ultracards.gateway.app.GatewayTest
```
