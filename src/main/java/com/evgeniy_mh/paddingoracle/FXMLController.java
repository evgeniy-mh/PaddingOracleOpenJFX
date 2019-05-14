package main.java.com.evgeniy_mh.paddingoracle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sample.Main;

public class FXMLController {

  final int AES_BLOCK_SIZE = 16;

  private Main mainApp;
  private FileChooser fileChooser = new FileChooser();
  private Stage stage;

  private File encryptedFile;
  private File resultFile;
  private DecodeInfo decodeInfo;

  @FXML
  public Label blocksCountLabel;
  @FXML
  public Label currentBlockLabel;
  @FXML
  public Label currentByteLabel;
  @FXML
  public TextField encFilePathTextField;
  @FXML
  public TextField resultFilePathTextField;
  @FXML
  public Button openEncFileButton;
  @FXML
  public Button startDecodeButton;
  @FXML
  public Button openResultFile;
  @FXML
  public Button createResultFile;
  @FXML
  public ProgressBar decodeProgressBar;

  public void setMainApp(Main mainApp) {
    this.mainApp = mainApp;
  }

  public void initialize() {
    decodeInfo = new DecodeInfo();
    updateDecryptInfo();

    try {
      fileChooser.setInitialDirectory(new File(
          MainApp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())
          .getParentFile());
    } catch (URISyntaxException ex) {
      Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
    }

    openEncFileButton.setOnAction(event -> {
      encryptedFile = openFile("Выбрать зашифрованный файл");
      updateFileInfo();
    });

    openResultFile.setOnAction(event -> {
      resultFile = openFile("Выбрать файл результата");
      updateFileInfo();
    });

    createResultFile.setOnAction(event -> {
      resultFile = createNewFile("Создать файл для сохранения результата");
      updateFileInfo();
    });

    startDecodeButton.setOnAction(event -> {
      startDecode();
    });
  }

  private void startDecode() {

    if (encryptedFile == null || resultFile == null) {
      return;
    }

    if (resultFile.length() != 0) {
      try {
        clearFile(resultFile);
      } catch (IOException ex) {
        Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    Task<Boolean> bruteforce = new AES_CBCBruteforcer(decodeProgressBar, decodeInfo)
        .Bruteforce(encryptedFile, resultFile);

    bruteforce.setOnSucceeded(value -> {
      Alert alert;
      if (bruteforce.getValue()) {
        alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Файл расшифрован");
        alert.setHeaderText("Файл успешно расшифрован!");
      } else {
        alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Файл не расшифрован!");
        alert.setHeaderText("При расшифровке произошла ошибка!");
      }
      alert.showAndWait();

      decodeInfo.currentBlock.set(0);
      decodeInfo.currentByte.set(0);
      decodeProgressBar.setProgress(0);
    });

    final LongProperty lastUpdate = new SimpleLongProperty();
    final long minUpdateInterval = 0;
    AnimationTimer timer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        if (now - lastUpdate.get() > minUpdateInterval) {
          updateDecryptInfo();
          lastUpdate.set(now);
        }
      }
    };
    timer.start();

    Thread t = new Thread(bruteforce);
    t.start();
  }

  private void updateFileInfo() {
    if (encryptedFile != null) {
      encFilePathTextField.setText(encryptedFile.getAbsolutePath());
    }
    if (resultFile != null) {
      resultFilePathTextField.setText(resultFile.getAbsolutePath());
    }
  }

  private void updateDecryptInfo() {
    blocksCountLabel.setText(String.valueOf(decodeInfo.blocksCount.get()));
    currentBlockLabel.setText(decodeInfo.currentBlock.get() + " / " + decodeInfo.blocksCount.get());
    currentByteLabel.setText(decodeInfo.currentByte.get() + " / " + AES_BLOCK_SIZE);
  }

  private File openFile(String dialogTitle) {
    fileChooser.setTitle(dialogTitle);
    File file = fileChooser.showOpenDialog(stage);
    return file;
  }

  private void clearFile(File file) throws IOException {
    RandomAccessFile ras = new RandomAccessFile(file, "rw");
    ras.setLength(0);
    ras.close();
  }

  private File createNewFile(String dialogTitle) {
    fileChooser.setTitle(dialogTitle);
    File file = fileChooser.showSaveDialog(stage);
    if (file != null) {
      try {
        file.createNewFile();
      } catch (IOException ex) {
        Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return file;
  }

  static public void debugPrintByteArray(String mes, byte[] array) {
    System.out.println(mes);
    for (int i = 0; i < array.length; i++) {
      System.out.print(String.format("0x%08X", array[i]) + " ");
    }
    System.out.println();
  }
}
