import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.HashSet;
import java.util.List;

public class FileServerThread extends Thread {

    Socket socket;

    public FileServerThread(Socket socket) {
        this.socket = socket;
    }

    private final static int bufferSize = 1024;

    @Override
    public void run() {

        //while (true) {
        try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                DataInputStream clientData = new DataInputStream(socket.getInputStream())
        ) {

            System.out.println("FileServerThread established");

            String operation = clientData.readUTF();

            switch (operation) {
                case "put": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();

                    // TODO check time stamp
                    out.println("Accept");

                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                            new FileOutputStream("../SDFS/" + sdfsfilename));

                    long fileSize = clientData.readLong();
                    byte[] buffer = new byte[bufferSize];
                    int bytes;
                    while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(bufferSize, fileSize))) != -1) {
                        fileOutputStream.write(buffer, 0, bytes);
                        fileSize -= bytes;
                    }

                    File file = new File("../SDFS/" + sdfsfilename);
                    if (file.length() == fileSize) {
                        //out.println("Received");
                        System.out.println("File received");
                        System.out.println("File name:" + sdfsfilename);

                    } else {
                        System.out.println("Fail to receive file");
                        out.println("Resend");
                        FilesOP.deleteFile(sdfsfilename);
                    }

                    fileOutputStream.close();

                    // TODO send replica
                    int index = Daemon.neighbors.size() - 1;
                    for (int i = 0; index >= 0 && i < 2; i++) {
                        Socket replicaSocket = new Socket(Daemon.neighbors.get(index), Daemon.filePortNumber);
                        PrintWriter outPrint = new PrintWriter(replicaSocket.getOutputStream(), true);
                        outPrint.println("replica");
                        FilesOP.sendFile(file, sdfsfilename, replicaSocket);
                        index--;
                    }

                    out.println("Put Success");
                    break;
                }
                case "replica": {
                    String sdfsfilename = clientData.readUTF();

                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                            new FileOutputStream("../SDFS/" + sdfsfilename));

                    long fileSize = clientData.readLong();
                    byte[] buffer = new byte[bufferSize];
                    int bytes;
                    while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(bufferSize, fileSize))) != -1) {
                        fileOutputStream.write(buffer, 0, bytes);
                        fileSize -= bytes;
                    }

                    File file = new File("../SDFS/" + sdfsfilename);
                    if (file.length() == fileSize) {
                        out.println("Replica Received");
                        System.out.println("Replica File received");
                        System.out.println("Replica File name:" + sdfsfilename);

                    } else {
                        System.out.println("Fail to receive file");
                        out.println("Resend");
                        FilesOP.deleteFile(sdfsfilename);
                    }

                    fileOutputStream.close();
                    break;
                }
                case "fail replica": {
                    String sdfsfilename = clientData.readUTF();

                    if (!new File("../SDFS/" + sdfsfilename).exists()) {
                        out.println("Ready to receive");
                        BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                                new FileOutputStream("../SDFS/" + sdfsfilename));

                        long fileSize = clientData.readLong();
                        byte[] buffer = new byte[bufferSize];
                        int bytes;
                        while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(bufferSize, fileSize))) != -1) {
                            fileOutputStream.write(buffer, 0, bytes);
                            fileSize -= bytes;
                        }

                        fileOutputStream.close();
                    } else {
                        out.println("Replica Exist");
                    }
                    break;
                }
                case "get": {
                    String sdfsfilename = clientData.readUTF();
                    String localfilename = clientData.readUTF();

                    File file = new File("../SDFS/" + sdfsfilename);
                    if (!file.exists()) {
                        out.println("File Not Exist");
                    } else {
                        out.println("File Exist");
                        FilesOP.sendFile(file, localfilename, socket);
                    }
                    break;
                }
                case "delete": {
                    String sdfsfilename = clientData.readUTF();
                    FilesOP.deleteFile("../SDFS/" + sdfsfilename);

                    // TODO delete replica
                    int index = Daemon.neighbors.size() - 1;
                    for (int i = 0; index >= 0 && i < 2; i++) {
                        Socket replicaSocket = new Socket(Daemon.neighbors.get(index), 123);
                        PrintWriter outPrint = new PrintWriter(replicaSocket.getOutputStream(), true);
                        outPrint.println("delete replica");
                        outPrint.println(sdfsfilename);
                        replicaSocket.close();
                        index--;
                    }

                    out.println("Delete Success");
                    break;
                }
                case "delete replica": {
                    String sdfsfilename = clientData.readUTF();
                    FilesOP.deleteFile("../SDFS/" + sdfsfilename);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


        //}
    }
}
