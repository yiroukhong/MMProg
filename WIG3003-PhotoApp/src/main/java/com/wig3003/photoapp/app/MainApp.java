package com.wig3003.photoapp.app;

import javafx.application.Application;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

public class MainApp extends Application {

    @Override
    public void init() {
        OpenCV.loadLocally(); // loads native libs from openpnp JAR
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("WIG3003 PhotoApp");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}