module gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires game.gateway;
    requires static lombok;

    opens com.ultracards.gui to javafx.fxml;
    opens com.ultracards.gui.Controllers to javafx.fxml;
    exports com.ultracards.gui;
}
