module peaceKeeper {
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires java.desktop;
	requires javafx.media;
	requires javafx.base;
	requires jdom;
	opens peaceKeeper to javafx.fxml;
	exports peaceKeeper;
}