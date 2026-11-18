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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

    @FXML private StackPane createOverlay;
    @FXML private TextField createNameInput;
    @FXML private FlowPane gameChoicePane;
    @FXML private VBox gameSettingsBox;
    @FXML private CheckBox publicCheck;
    @FXML private Label visibilityValue;
    @FXML private CheckBox wagerEnabledCheck;
    @FXML private VBox wagerEditor;
    @FXML private Slider wagerSlider;
    @FXML private Label wagerAmountLabel;
    @FXML private FlowPane wagerPresets;
    @FXML private Label createStatusLabel;
    @FXML private Button createSubmitButton;

    private GatewayAppClient gateway;
    private ScreenNavigation navigation;
    private String selectedGame;
    private String selectedSetting;
    private int selectedPlayers;
    private int durakDeckSize = 36;
    private boolean durakJokers;
    private DurakThrowInPolicyDTO durakThrowInPolicy = DurakThrowInPolicyDTO.NEIGHBORS_ONLY;
    private boolean durakPassing = true;
    private boolean tresetaDeclarations;
    private final ToggleGroup gameGroup = new ToggleGroup();
    private static final long[] WAGER_STEPS = createWagerSteps();

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
        initializeCreateLobbyOverlay();
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

        if (requireSignIn()) {
            return;
        }

        resetCreateForm(requestedGame);
        createOverlay.setManaged(true);
        createOverlay.setVisible(true);
        createNameInput.requestFocus();
    }

    private void initializeCreateLobbyOverlay() {
        for (String game : List.of("Briskula", "Durak", "Poker", "Treseta")) {
            ToggleButton button = choiceButton(game, game, gameGroup, "create-game-choice");
            button.setOnAction(event -> selectGame(game));
            gameChoicePane.getChildren().add(button);
        }
        publicCheck.selectedProperty().addListener((observable, oldValue, value) ->
                visibilityValue.setText(value ? "Public lobby" : "Private lobby"));
        wagerEnabledCheck.selectedProperty().addListener((observable, oldValue, value) -> {
            wagerEditor.setDisable(!value);
            wagerEditor.setOpacity(value ? 1 : 0.45);
        });
        wagerSlider.valueProperty().addListener((observable, oldValue, value) ->
                wagerAmountLabel.setText(String.format("%,d P", WAGER_STEPS[(int) Math.round(value.doubleValue())])));
        for (long amount : new long[]{100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000}) {
            Button preset = new Button(String.format("%,d", amount));
            preset.getStyleClass().add("wager-preset");
            preset.setOnAction(event -> wagerSlider.setValue(wagerIndex(amount)));
            wagerPresets.getChildren().add(preset);
        }
    }

    private static long[] createWagerSteps() {
        long[] steps = new long[37];
        int index = 0;
        for (long decade : new long[]{100, 1_000, 10_000, 100_000}) {
            for (int offset = 1; offset <= 9; offset++) steps[index++] = decade * offset;
        }
        steps[index] = 1_000_000;
        return steps;
    }

    private int wagerIndex(long amount) {
        int best = 0;
        for (int i = 1; i < WAGER_STEPS.length; i++) {
            if (Math.abs(WAGER_STEPS[i] - amount) < Math.abs(WAGER_STEPS[best] - amount)) best = i;
        }
        return best;
    }

    private void resetCreateForm(String requestedGame) {
        selectedGame = null;
        selectedSetting = null;
        selectedPlayers = 0;
        durakDeckSize = 36;
        durakJokers = false;
        durakThrowInPolicy = DurakThrowInPolicyDTO.NEIGHBORS_ONLY;
        durakPassing = true;
        tresetaDeclarations = false;
        createNameInput.clear();
        publicCheck.setSelected(true);
        wagerEnabledCheck.setSelected(true);
        wagerSlider.setValue(0);
        gameSettingsBox.getChildren().clear();
        gameSettingsBox.setManaged(false);
        gameSettingsBox.setVisible(false);
        createStatusLabel.setText("Pick a game to continue.");
        createStatusLabel.getStyleClass().remove("create-error");
        gameGroup.selectToggle(null);
        if (requestedGame != null) {
            gameGroup.getToggles().stream().filter(toggle -> requestedGame.equals(toggle.getUserData()))
                    .findFirst().ifPresent(gameGroup::selectToggle);
            if (gameGroup.getSelectedToggle() != null) selectGame(requestedGame);
        }
        createSubmitButton.setDisable(true);
    }

    private ToggleButton choiceButton(String label, String value, ToggleGroup group, String styleClass) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setUserData(value);
        button.getStyleClass().add(styleClass);
        return button;
    }

    private void selectGame(String game) {
        selectedGame = game;
        selectedSetting = null;
        selectedPlayers = 0;
        gameSettingsBox.getChildren().clear();
        gameSettingsBox.setVisible(true);
        gameSettingsBox.setManaged(true);
        if ("Poker".equals(game)) {
            gameSettingsBox.getChildren().add(new Label("Lobby creation is not available for Poker yet."));
            setCreateStatus("Poker lobby creation is not available yet.", true);
            createSubmitButton.setDisable(true);
            return;
        }
        if ("Briskula".equals(game)) {
            addChoiceRow("Game configuration", new String[][]{
                    {"1 vs 1 · 3 cards", "p2"}, {"1 vs 1 · 4 cards", "p2c4"}, {"3 players", "p3"},
                    {"4 players", "p4"}, {"2 vs 2", "p4teams"}
            }, value -> {
                selectedSetting = value;
                selectedPlayers = value.startsWith("p2") ? 2 : value.equals("p3") ? 3 : 4;
                syncCreateState();
            });
        } else if ("Treseta".equals(game)) {
            addChoiceRow("Game configuration", new String[][]{
                    {"1 vs 1", "p2"}, {"3 players", "p3"}, {"2 vs 2", "p4teams"}, {"4 players", "p4"}
            }, value -> {
                selectedSetting = value;
                selectedPlayers = value.equals("p2") ? 2 : value.equals("p3") ? 3 : 4;
                syncCreateState();
            });
            CheckBox declarations = new CheckBox("Enable declarations");
            declarations.setOnAction(event -> tresetaDeclarations = declarations.isSelected());
            gameSettingsBox.getChildren().add(declarations);
        } else {
            addChoiceRow("Players", new String[][]{
                    {"2", "2"}, {"3", "3"}, {"4", "4"}, {"5", "5"}, {"6", "6"}
            }, value -> {
                selectedPlayers = Integer.parseInt(value);
                selectedSetting = "p" + value;
                syncCreateState();
            });
            addChoiceRow("Deck", new String[][]{{"36 cards", "36"}, {"24 cards", "24"}, {"54 cards", "54"}},
                    value -> { durakDeckSize = Integer.parseInt(value); if (durakDeckSize != 54) durakJokers = false; });
            addChoiceRow("Throw-in", new String[][]{{"Neighbors only", "NEIGHBORS_ONLY"}, {"Everyone", "EVERYONE"}},
                    value -> durakThrowInPolicy = "EVERYONE".equals(value)
                            ? DurakThrowInPolicyDTO.EVERYONE : DurakThrowInPolicyDTO.NEIGHBORS_ONLY);
            CheckBox jokers = new CheckBox("Use jokers (54-card deck only)");
            jokers.setOnAction(event -> durakJokers = jokers.isSelected() && durakDeckSize == 54);
            CheckBox passing = new CheckBox("Allow passing");
            passing.setSelected(true);
            passing.setOnAction(event -> durakPassing = passing.isSelected());
            gameSettingsBox.getChildren().addAll(jokers, passing);
        }
        syncCreateState();
    }

    @FunctionalInterface
    private interface ChoiceHandler { void selected(String value); }

    private void addChoiceRow(String title, String[][] options, ChoiceHandler handler) {
        VBox row = new VBox(7);
        Label label = new Label(title);
        label.getStyleClass().add("create-label");
        FlowPane choices = new FlowPane(7, 7);
        ToggleGroup group = new ToggleGroup();
        boolean first = true;
        for (String[] option : options) {
            ToggleButton button = choiceButton(option[0], option[1], group, "create-option");
            button.setOnAction(event -> handler.selected(option[1]));
            if (first) {
                button.setSelected(true);
                handler.selected(option[1]);
                first = false;
            }
            choices.getChildren().add(button);
        }
        row.getChildren().addAll(label, choices);
        gameSettingsBox.getChildren().add(row);
    }

    private void syncCreateState() {
        boolean ready = selectedGame != null && selectedSetting != null && selectedPlayers >= 2;
        createSubmitButton.setDisable(!ready);
        setCreateStatus(ready ? "Your lobby is ready to create." : "Choose a game configuration to continue.", false);
    }

    private void setCreateStatus(String text, boolean error) {
        createStatusLabel.setText(text);
        createStatusLabel.getStyleClass().remove("create-error");
        if (error) createStatusLabel.getStyleClass().add("create-error");
    }

    @FXML
    private void closeCreateLobby() {
        createOverlay.setVisible(false);
        createOverlay.setManaged(false);
    }

    @FXML
    private void closeCreateOnBackdrop(MouseEvent event) {
        if (event.getTarget() == createOverlay) closeCreateLobby();
    }

    @FXML
    private void submitCreateLobby() {
        if (selectedGame == null || selectedSetting == null) return;
        createSubmitButton.setDisable(true);
        setCreateStatus("Creating lobby…", false);
        try {
            String name = createNameInput.getText() == null ? "" : createNameInput.getText().trim();
            if (name.isBlank()) name = "ULTRAlobby";
            var request = buildCreateRequest(selectedGame, selectedSetting, selectedPlayers, name);
            var created = gateway.lobby().createLobby(request);
            String code = created == null ? "" : created.getLobbyCode();
            closeCreateLobby();
            showInfo("Lobby created", "Your lobby is ready. Share code " + code + " with friends.");
            refreshLobbies();
        } catch (RuntimeException exception) {
            setCreateStatus(errorMessage(exception), true);
            createSubmitButton.setDisable(false);
        }
    }

    private GameLobbyDTO buildCreateRequest(String game, String setting, int players, String name) {
        GameConfigDTO config;
        GameTypeDTO gameType;
        switch (game) {
            case "Durak" -> {
                gameType = GameTypeDTO.Durak;
                int deck = durakDeckSize == 24 && players > 4 ? 36 : durakDeckSize;
                config = new DurakGameConfigDTO(players, deck, deck == 54 && durakJokers,
                        durakThrowInPolicy, durakPassing, null);
            }
            case "Treseta" -> {
                gameType = GameTypeDTO.Treseta;
                config = new TresetaGameConfigDTO(players, players == 3 ? 13 : 10,
                        "p4teams".equals(setting), tresetaDeclarations, null);
            }
            default -> {
                gameType = GameTypeDTO.Briskula;
                config = new BriskulaGameConfigDTO(players, "p2c4".equals(setting) ? 4 : 3,
                        "p4teams".equals(setting), null);
            }
        }

        var lobby = new GameLobbyDTO();
        lobby.setName(name);
        lobby.setMinPlayers(players);
        lobby.setMaxPlayers(players);
        lobby.setGameType(gameType);
        lobby.setIsPublic(publicCheck.isSelected());
        lobby.setGameConfig(config);
        long stake = WAGER_STEPS[(int) Math.round(wagerSlider.getValue())];
        lobby.setWager(wagerEnabledCheck.isSelected()
                ? new WagerConfigDTO(true, stake) : WagerConfigDTO.disabled());
        return lobby;
    }

    @FXML
    private void joinByCode() {
        if (requireSignIn()) {
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
        if (requireSignIn()) {
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

    private boolean requireSignIn() {
        if (gateway.tokenHolder().getToken() != null) {
            return false;
        }
        navigation.show("Home.fxml", "signin");
        return true;
    }

    @FXML
    private void openGames() {
        navigation.show("Games.fxml");
    }

    @FXML
    private void openGuides() {
        showInfo("How to play", """
                Durak: attack with cards and defend with a higher card or trump.
                
                Briskula: follow the trump suit when you can and collect high-value cards.
                
                Treseta: win tricks with a strong card, and take the final trick for a bonus point.""");
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
