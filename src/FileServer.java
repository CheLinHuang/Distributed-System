import java.io.IOException;
import java.net.ServerSocket;

public class FileServer extends Thread {

    public void run() {
        boolean listening = true;

        try (ServerSocket serverSocket = new ServerSocket(Daemon.filePortNumber)) {

            while (listening)
                new FileServerThread(serverSocket.accept()).start();

        } catch (IOException e) {
            System.err.println("Could not listen to port " + Daemon.filePortNumber);
            System.exit(-1);
        }
    }
}
