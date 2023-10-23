package client.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class LoginInterface {

    public Scene createScene() {

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Text scenetitle = new Text("Benvenuto in Wordle!");
        scenetitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(scenetitle, 0, 0, 2, 1);

        Label userName = new Label("Username:");
        grid.add(userName, 0, 1);

        TextField userTextField = new TextField();
        grid.add(userTextField, 1, 1);

        Label pw = new Label("Password:");
        grid.add(pw, 0, 2);

        PasswordField pwBox = new PasswordField();
        grid.add(pwBox, 1, 2);

        Button signInBtn = new Button("Sign in");
        grid.add(signInBtn, 0, 3);

        Button signUpBtn = new Button("Sign up");
        grid.add(signUpBtn, 1, 3);

        Text actiontarget = new Text();
        grid.add(actiontarget, 1, 4);

        signInBtn.setOnAction(e -> {
            actiontarget.setText("Sign in button pressed");

        });

        signUpBtn.setOnAction(e -> {
            actiontarget.setText("Sign up button pressed");
            
        });

        Scene scene = new Scene(grid, 600, 500);
        return scene;
    }
}