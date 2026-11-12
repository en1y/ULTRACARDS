package com.ultracards.gui.Controllers;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.gui.ScreenNavigation;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;

import java.util.List;

public class LobbiesController implements ScreenController {
    @FXML
    private ListView<GameLobbyDTO> lobbyList;

    @FXML
    private TextField codeInput;

    @FXML
    private Label lobbyCountLabel;

    @FXML
    private Label statusLabel;

    private GatewayAppClient gateway;
    private ScreenNavigation navigation;

    @FXML
    private void initialize() {
        lobbyList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(GameLobbyDTO lobby, boolean empty) {
                super.updateItem(lobby, empty);
                if (empty || lobby == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                int playerCount = lobby.getPlayers() == null ? 0 : lobby.getPlayers().size();
                setText(lobby.getName() + "     ·     " + lobby.getGameType() + "     ·     "
                        + playerCount + "/" + lobby.getMaxPlayers()
                        + (Boolean.TRUE.equals(lobby.getIsPublic()) ? "     ·     PUBLIC" : "     ·     PRIVATE"));
            }
        });
        lobbyList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                joinSelected();
            }
        });
        codeInput.textProperty().addListener((observable, oldValue, newValue) -> {
            String normalized = newValue.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (!normalized.equals(newValue)) {
                codeInput.setText(normalized);
            }
            if (normalized.length() > 6) {
                codeInput.setText(normalized.substring(0, 6));
            }
        });
    }

    @Override
    public void setGateway(GatewayAppClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public void setNavigation(ScreenNavigation navigation) {
        this.navigation = navigation;
    }

    @Override
    public void setRoute(String route) {
        refreshLobbies();
        if ("create".equals(route)) {
            createLobby(null);
        } else if ("join".equals(route)) {
            codeInput.requestFocus();
        } else if (route != null && route.startsWith("create:")) {
            createLobby(route.substring("create:".length()));
        }
    }

    @FXML
    private void refreshLobbies() {
        try {
            var lobbies = gateway.lobby().getLobbies();
            lobbyList.getItems().setAll(lobbies == null ? List.of() : lobbies);
            int count = lobbyList.getItems().size();
            lobbyCountLabel.setText(count + " OPEN");
            statusLabel.setText(count == 0 ? "No active lobbies right now. Create one and invite your friends." : "Select a lobby to join.");
        } catch (RuntimeException exception) {
            lobbyList.getItems().clear();
            lobbyCountLabel.setText("UNAVAILABLE");
            statusLabel.setText("Could not load lobbies: " + errorMessage(exception));
        }
    }

    @FXML
    private void createLobby() {
        createLobby(null);
    }

    private void createLobby(String requestedGame) {

        if (AuthDialogs.notSignedIn(gateway)) {
            return;
        }

        var gameNames = List.of("Briskula", "Durak", "Treseta");
        String defaultGame = gameNames.contains(requestedGame) ? requestedGame : "Briskula";
        var gameDialog = new ChoiceDialog<>(defaultGame, gameNames);
        gameDialog.setTitle("Create lobby");
        gameDialog.setHeaderText("Choose a game");
        gameDialog.setContentText("Game:");
        var selectedGame = gameDialog.showAndWait();
        if (selectedGame.isEmpty()) {
            return;
        }

        var playerCounts = switch (selectedGame.get()) {
            case "Durak" -> List.of(2, 3, 4, 5, 6);
            case "Treseta", "Briskula" -> List.of(2, 3, 4);
            default -> List.of(2);
        };
        var playerDialog = new ChoiceDialog<>(2, playerCounts);
        playerDialog.setTitle("Lobby settings");
        playerDialog.setHeaderText("Choose the player count");
        playerDialog.setContentText("Players:");
        var players = playerDialog.showAndWait();
        if (players.isEmpty()) {
            return;
        }

        var nameDialog = new TextInputDialog("ULTRAlobby");
        nameDialog.setTitle("Lobby name");
        nameDialog.setHeaderText("Name your lobby");
        nameDialog.setContentText("Lobby name:");
        var lobbyName = nameDialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
        if (lobbyName.isEmpty()) {
            return;
        }

        try {
            var request = buildCreateRequest(selectedGame.get(), players.get(), lobbyName.get());
            var created = gateway.lobby().createLobby(request);
            String code = created == null ? "" : created.getLobbyCode();
            showInfo("Lobby created", "Your lobby is ready. Share code " + code + " with friends.");
            refreshLobbies();
        } catch (RuntimeException exception) {
            showError("Could not create lobby", errorMessage(exception));
        }
    }

    private GameLobbyDTO buildCreateRequest(String game, int players, String name) {
        GameConfigDTO config;
        GameTypeDTO gameType;
        switch (game) {
            case "Durak" -> {
                gameType = GameTypeDTO.Durak;
                config = new DurakGameConfigDTO(players, 36, false, DurakThrowInPolicyDTO.NEIGHBORS_ONLY, true, null);
            }
            case "Treseta" -> {
                gameType = GameTypeDTO.Treseta;
                config = new TresetaGameConfigDTO(players, players == 3 ? 13 : 10, false, false, null);
            }
            default -> {
                gameType = GameTypeDTO.Briskula;
                config = new BriskulaGameConfigDTO(players, 3, false, null);
            }
        }

        var lobby = new GameLobbyDTO();
        lobby.setName(name);
        lobby.setMinPlayers(players);
        lobby.setMaxPlayers(players);
        lobby.setGameType(gameType);
        lobby.setIsPublic(true);
        lobby.setGameConfig(config);
        lobby.setWager(WagerConfigDTO.disabled());
        return lobby;
    }

    @FXML
    private void joinByCode() {
        if (AuthDialogs.notSignedIn(gateway)) {
            return;
        }
        String code = codeInput.getText().trim();
        if (code.length() != 6) {
            showError("Invalid invite code", "Enter a six-character code using letters and numbers.");
            return;
        }
        joinWithCode(code);
    }

    private void joinWithCode(String code) {
        try {
            gateway.lobby().joinLobby(code);
            var lobby = gateway.lobby().getLobby();
            showInfo("Joined lobby", lobby.getName() + " is ready. Lobby code: " + lobby.getLobbyCode());
            statusLabel.setText("Joined " + lobby.getName());
        } catch (RuntimeException exception) {
            showError("Could not join lobby", errorMessage(exception));
        }
    }

    @FXML
    private void joinSelected() {
        var lobby = lobbyList.getSelectionModel().getSelectedItem();
        if (lobby == null) {
            showError("Choose a lobby", "Select a lobby from the list first.");
            return;
        }
        if (AuthDialogs.notSignedIn(gateway)) {
            return;
        }
        try {
            gateway.lobby().joinLobby(lobby.getId());
            var joined = gateway.lobby().getLobby();
            showInfo("Joined lobby", joined.getName() + " is ready. Lobby code: " + joined.getLobbyCode());
            statusLabel.setText("Joined " + joined.getName());
        } catch (RuntimeException exception) {
            showError("Could not join lobby", errorMessage(exception));
        }
    }

    @FXML
    private void goHome() {
        navigation.show("Home.fxml");
    }

    @FXML
    private void openGames() {
        navigation.show("Games.fxml");
    }

    @FXML
    private void openGuides() {
        showInfo("How to play", "Durak: attack with cards and defend with a higher card or trump.\n\n"
                + "Briskula: follow the trump suit when you can and collect high-value cards.\n\n"
                + "Treseta: win tricks with a strong card, and take the final trick for a bonus point.");
    }

    @FXML
    private void openLeaderboards() {
        try {
            var page = gateway.leaderboards().get(com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO.WINS,
                    null, null, 0, 10);
            var rows = new java.util.ArrayList<String>();
            for (var entry : page.items()) {
                rows.add(entry.position() + ".  " + entry.username() + "  ·  " + entry.wins() + " wins");
            }
            showInfo("Leaderboards", rows.isEmpty() ? "No leaderboard entries yet." : String.join("\n", rows));
        } catch (RuntimeException exception) {
            showError("Could not load leaderboards", errorMessage(exception));
        }
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The server request failed. Check your connection and try again."
                : exception.getMessage();
    }

    private void showInfo(String title, String message) {
        var alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
