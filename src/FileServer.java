import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private final static int bufferSize = 1024;

    public static void main(String[] args) {

        while (true) {
            try (
                    ServerSocket serverSocket = new ServerSocket(11111);
                    Socket socket = serverSocket.accept();
                    DataInputStream clientData = new DataInputStream(socket.getInputStream());
                    // Read filename from clientData.readUTF()
                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                            new FileOutputStream("../SDFS/" + clientData.readUTF()));
            ) {

                long fileSize = clientData.readLong();
                byte[] buffer = new byte[bufferSize];
                int bytes;
                while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(bufferSize, fileSize))) != -1) {
                    fileOutputStream.write(buffer, 0, bytes);
                    fileSize -= bytes;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
