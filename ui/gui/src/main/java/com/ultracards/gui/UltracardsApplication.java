package com.ultracards.gui;

import com.ultracards.gateway.app.GatewayAppClient;
import javafx.application.Application;
import javafx.stage.Stage;

public class UltracardsApplication extends Application {
    private GatewayAppClient gateway;

    @Override
    public void start(Stage stage) throws Exception {
        String serverUrl = System.getenv().getOrDefault("ULTRACARDS_SERVER_URL", "https://ultracards.en1y.qzz.io/");
        String wsUrl = serverUrl.replaceFirst("^http", "ws");
        gateway = new GatewayAppClient(serverUrl, wsUrl);
        var navigation = new ScreenNavigation(stage, gateway);
        stage.setTitle("ULTRACARDS");
        stage.setMinWidth(640);
        stage.setMinHeight(520);
        stage.setResizable(true);
        stage.setFullScreen(true);
        navigation.show("Home.fxml");
        stage.show();
    }

    @Override
    public void stop() {
        if (gateway != null) {
            gateway.close();
        }
    }
}
