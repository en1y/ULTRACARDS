package com.ultracards.gui;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gui.Controllers.ScreenController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ScreenNavigation {
    private static final double DEFAULT_WIDTH = 1120;
    private static final double DEFAULT_HEIGHT = 780;

    private final Stage stage;
    private final GatewayAppClient gateway;

    public ScreenNavigation(Stage stage, GatewayAppClient gateway) {
        this.stage = stage;
        this.gateway = gateway;
    }

    public void show(String fxmlFile) {
        show(fxmlFile, null);
    }

    public void show(String fxmlFile, String route) {
        try {
            var loader = new FXMLLoader(ScreenNavigation.class.getResource(fxmlFile));
            Parent view = loader.load();
            double width = stage.getScene() == null ? DEFAULT_WIDTH : stage.getScene().getWidth();
            double height = stage.getScene() == null ? DEFAULT_HEIGHT : stage.getScene().getHeight();
            var scene = new Scene(view, width, height);
            scene.getStylesheets().add(ScreenNavigation.class.getResource("home.css").toExternalForm());
            stage.setScene(scene);

            Object controller = loader.getController();
            if (controller instanceof ScreenController screenController) {
                screenController.setGateway(gateway);
                screenController.setNavigation(this);
                screenController.setRoute(route);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + fxmlFile, exception);
        }
    }
}
