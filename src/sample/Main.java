package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import main.java.com.evgeniy_mh.paddingoracle.FXMLController;

public class Main extends Application {

  @Override
  public void start(Stage primaryStage) throws Exception {
    FXMLLoader loader = new FXMLLoader();
    loader.setLocation(getClass().getResource("/fxml/mainOverview.fxml"));
    AnchorPane rootOverview = loader.load();

    Scene scene = new Scene(rootOverview);
    primaryStage.setTitle("Padding Oracle");
    primaryStage.setScene(scene);
    primaryStage.show();

    FXMLController mc = loader.getController();
    mc.setMainApp(this);
  }


  public static void main(String[] args) {
    launch(args);
  }
}
