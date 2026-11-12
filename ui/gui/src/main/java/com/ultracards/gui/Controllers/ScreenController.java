package com.ultracards.gui.Controllers;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gui.ScreenNavigation;

public interface ScreenController {
    void setGateway(GatewayAppClient gateway);

    void setNavigation(ScreenNavigation navigation);

    default void setRoute(String route) {
    }
}
