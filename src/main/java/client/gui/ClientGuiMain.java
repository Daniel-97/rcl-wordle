package client.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientGuiMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Crea un'istanza della classe LoginInterface
        LoginInterface loginInterface = new LoginInterface();
        
        // Chiama il metodo start della classe LoginInterface per creare la scena di login
        Scene loginScene = loginInterface.createScene();
        
        // Imposta la scena di login sullo stage principale
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("Client GUI");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
