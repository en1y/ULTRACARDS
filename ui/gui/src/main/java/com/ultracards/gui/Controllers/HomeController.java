package com.ultracards.gui.Controllers;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO;
import com.ultracards.gui.ScreenNavigation;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;

import java.util.ArrayList;

public class HomeController implements ScreenController {

    @FXML
    private Label statusLabel;

    @FXML
    private TilePane gameGrid;

    @FXML private AuthController authController;

    private GatewayAppClient gateway;

    private ScreenNavigation navigation;

    @Override
    public void setGateway(GatewayAppClient gateway) {
        this.gateway = gateway;
        authController.configure(gateway, this::showStatus);
    }

    @Override
    public void setNavigation(ScreenNavigation navigation) {
        this.navigation = navigation;
    }

    @Override
    public void setRoute(String route) {
        if ("signin".equals(route)) {
            Platform.runLater(this::signIn);
        }
    }

    @FXML
    private void initialize() {
        gameGrid.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            int columns = newWidth.doubleValue() >= 1010 ? 3 : newWidth.doubleValue() >= 650 ? 2 : 1;
            gameGrid.setPrefColumns(columns);
        });
    }

    @FXML
    private void createGame() {
        navigation.show("Lobbies.fxml", "create");
    }

    @FXML
    private void browseGames() {
        navigation.show("Lobbies.fxml");
    }

    @FXML
    private void joinByCode() {
        navigation.show("Lobbies.fxml", "join");
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
            var page = gateway.leaderboards().get(LeaderboardMetricDTO.WINS, null, null, 0, 10);
            var rows = new ArrayList<String>();
            for (var entry : page.items()) {
                rows.add(entry.position() + ".  " + entry.username() + "  ·  " + entry.wins() + " wins");
            }
            showInfo("Leaderboards", rows.isEmpty() ? "No leaderboard entries yet." : String.join("\n", rows));
        } catch (RuntimeException exception) {
            showError("Could not load leaderboards", exception.getMessage());
        }
    }

    @FXML
    private void signIn() {
        authController.open();
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
    }

    private void showInfo(String title, String message) {
        var alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR, message == null || message.isBlank() ? title : message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
