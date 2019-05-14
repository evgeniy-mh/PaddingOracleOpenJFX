package main.java.com.evgeniy_mh.paddingoracle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

public class FileByteSender implements Callable<Integer> {

  final int serverPort = 55555;
  final String address = "127.0.0.1";
  final byte[] file;

  public FileByteSender(byte[] fileBytes) {
    this.file = fileBytes;
  }

  @Override
  public Integer call() throws Exception {
    int response = 0;
    try (Socket socket = new Socket(address, serverPort)) {
      InputStream sin = socket.getInputStream();
      OutputStream sout = socket.getOutputStream();
      DataInputStream in = new DataInputStream(sin);
      DataOutputStream out = new DataOutputStream(sout);

      out.writeUTF("new file");
      out.flush();

      out.writeLong(file.length);
      out.flush();

      sout.write(file);
      sout.flush();

      //System.out.println(socket.isConnected());
      response = in.readInt();

      out.close();
      in.close();
      sin.close();
      sout.close();
    }
    return response;
  }
}
