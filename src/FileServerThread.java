import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;

public class FileServerThread extends Thread {

    Socket socket;
    private static int numOfReplica = 3;

    public FileServerThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try (
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream clientData = new DataInputStream(socket.getInputStream())
        ) {

            Daemon.writeLog("FileServerThread established", socket.getRemoteSocketAddress().toString());
            String operation = clientData.readUTF();
            Daemon.writeLog(operation + " request", socket.getRemoteSocketAddress().toString());

            switch (operation) {
                case "put": {

                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");

                    SocketAddress socketAddress = socket.getRemoteSocketAddress();
                    FileServer.putQueue.add(socketAddress);

                    while (!(FileServer.lock.tryLock() && FileServer.putQueue.peek() == socketAddress)) {
                        if (FileServer.lock.isHeldByCurrentThread()) {
                            FileServer.lock.unlock();
                            Thread.sleep(50);
                        }
                    }

                    // Open the file in SDFS
                    File sdfsfile = new File("../SDFS/" + sdfsfilename);

                    if (sdfsfile.exists() && System.currentTimeMillis() - sdfsfile.lastModified() < 60000) {
                        // Require confirmation to put file
                        out.writeUTF("Confirm");
                        String clientConfirmation = clientData.readUTF();
                        if (clientConfirmation.equals("N")) {
                            FileServer.putQueue.poll();
                            FileServer.lock.unlock();
                            break;
                        }
                    } else {
                        out.writeUTF("Accept");
                    }

                    int count = 0;
                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                            new FileOutputStream("../SDFS/" + sdfsfilename));

                    long fileSize = clientData.readLong();

                    Daemon.writeLog("file size", Long.toString(fileSize));

                    byte[] buffer = new byte[Daemon.bufferSize];
                    int bytes;
                    while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(Daemon.bufferSize, fileSize))) != -1) {
                        fileOutputStream.write(buffer, 0, bytes);
                        fileSize -= bytes;
                    }
                    fileOutputStream.close();

                    File file = new File("../SDFS/" + sdfsfilename);
                    count++;
                    Daemon.writeLog("receive file size", Long.toString(file.length()));

                    // Send replica to other nodes
                    int index = Daemon.neighbors.size() - 1;
                    Thread[] threads = new Thread[2];

                    for (int i = 0; index >= 0 && i < 2; i++) {
                        Socket replicaSocket = new Socket(Daemon.neighbors.get(index).split("#")[1], Daemon.filePortNumber);
                        DataOutputStream outPrint = new DataOutputStream(replicaSocket.getOutputStream());
                        outPrint.writeUTF("replica");
                        outPrint.writeUTF(sdfsfilename);
                        threads[i] = FilesOP.sendFile(file, replicaSocket);
                        threads[i].start();
                        index--;
                    }

                    for (Thread t : threads)
                        if (t != null) {
                            t.join();
                            count++;
                            // quorum write
                            if (count >= Math.ceil((double) numOfReplica / 2) || count == Daemon.membershipList.size()) {
                                out.writeUTF("Received");
                            }
                        }
                    FileServer.putQueue.poll();
                    FileServer.lock.unlock();
                    break;
                }
                case "replica": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");

                    // Replica request write immediately
                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                            new FileOutputStream("../SDFS/" + sdfsfilename));

                    long fileSize = clientData.readLong();
                    Daemon.writeLog("file size", Long.toString(fileSize));
                    byte[] buffer = new byte[Daemon.bufferSize];
                    int bytes;
                    while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(Daemon.bufferSize, fileSize))) != -1) {
                        fileOutputStream.write(buffer, 0, bytes);
                        fileSize -= bytes;
                    }
                    fileOutputStream.close();

                    File file = new File("../SDFS/" + sdfsfilename);
                    out.writeUTF("Received");
                    Daemon.writeLog("receive file size", Long.toString(file.length()));

                    break;
                }
                case "fail replica": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");

                    if (!new File("../SDFS/" + sdfsfilename).exists()) {

                        // If no replica, receive the replica immediately
                        out.writeUTF("Ready to receive");
                        BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                                new FileOutputStream("../SDFS/" + sdfsfilename));

                        long fileSize = clientData.readLong();
                        byte[] buffer = new byte[Daemon.bufferSize];
                        int bytes;
                        while (fileSize > 0 && (bytes = clientData.read(buffer, 0, (int) Math.min(Daemon.bufferSize, fileSize))) != -1) {
                            fileOutputStream.write(buffer, 0, bytes);
                            fileSize -= bytes;
                        }
                        fileOutputStream.close();
                        out.writeUTF("Received");
                        Daemon.writeLog("Receive Replica", "");

                    } else {

                        // Replica exist, no need to overwrite
                        Daemon.writeLog("Replica Exist", "");
                        out.writeUTF("Replica Exist");
                    }
                    break;
                }
                case "get": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");

                    // Open the file in SDFS
                    File file = new File("../SDFS/" + sdfsfilename);
                    if (!file.exists()) {
                        Daemon.writeLog("File Not Exist", "");
                        out.writeUTF("File Not Exist");
                    } else {
                        Daemon.writeLog("File Exist", "");
                        out.writeUTF("File Exist");
                        Thread t = FilesOP.sendFile(file, socket);
                        t.start();
                        t.join();
                    }
                    Daemon.writeLog("get complete", sdfsfilename);
                    break;
                }
                case "delete": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");

                    FilesOP.deleteFile("../SDFS/" + sdfsfilename);

                    // Delete replica
                    int index = Daemon.neighbors.size() - 1;
                    for (int i = 0; index >= 0 && i < 2; i++) {
                        Socket replicaSocket = new Socket(Daemon.neighbors.get(index).split("#")[1], Daemon.filePortNumber);
                        DataOutputStream outPrint = new DataOutputStream(replicaSocket.getOutputStream());
                        outPrint.writeUTF("delete replica");
                        outPrint.writeUTF(sdfsfilename);
                        replicaSocket.close();
                        index--;
                    }
                    break;
                }
                case "delete replica": {
                    // Read filename from clientData.readUTF()
                    String sdfsfilename = clientData.readUTF();
                    Daemon.writeLog(sdfsfilename, "");
                    FilesOP.deleteFile("../SDFS/" + sdfsfilename);
                    break;
                }
                case "ls": {
                    // Read filename from clientData.readUTF()
                    String sdfsFileName = clientData.readUTF();
                    Daemon.writeLog(sdfsFileName, "");

                    String queryResult = "";
                    // query the file locally on the coordinator
                    if (new File("../SDFS/" + sdfsFileName).exists()) {
                        queryResult += Daemon.ID.split("#")[1] + "#";
                    }

                    // query the file on the neighbors of the coordinator
                    int j = Daemon.neighbors.size() - 1;
                    while (j >= 0) {
                        String tgtNode = Daemon.neighbors.get(j--).split("#")[1];
                        try {
                            // Connect to server
                            Socket lsSocket = new Socket(tgtNode, Daemon.filePortNumber);
                            DataOutputStream lsOut = new DataOutputStream(lsSocket.getOutputStream());
                            DataInputStream lsIn = new DataInputStream(lsSocket.getInputStream());

                            lsOut.writeUTF("ls replica");
                            lsOut.writeUTF(sdfsFileName);

                            lsSocket.setSoTimeout(1000);
                            String result = lsIn.readUTF();
                            if (!result.equals("Empty")) {
                                queryResult += result + "#";
                            }

                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                    }
                    if (queryResult.isEmpty()) {
                        out.writeUTF("Empty");
                    } else {
                        queryResult = queryResult.substring(0, queryResult.length() - 1);
                        out.writeUTF(queryResult);
                    }
                    break;
                }
                case "ls replica": {
                    // check if the query file exists on the replica node
                    String sdfsFileName = clientData.readUTF();
                    Daemon.writeLog(sdfsFileName, "");

                    if (new File("../SDFS/" + sdfsFileName).exists()) {
                        out.writeUTF(Daemon.ID.split("#")[1]);
                    } else {
                        out.writeUTF("Empty");
                    }
                    break;
                }
                case "get replica": {
                    String targetNode = clientData.readUTF();
                    List<String> fileList = FilesOP.listFiles("../SDFS/");
                    if (fileList.size() == 0) {
                        out.writeUTF("Empty");
                    } else {
                        for (String file : fileList) {
                            if (targetNode.equals(Hash.getServer(Hash.hashing(file, 8)))) {
                                out.writeUTF(file);
                                Thread t = FilesOP.sendFile(new File("../SDFS/" + file), socket);
                                t.start();
                                t.join();
                            }
                        }
                        out.writeUTF("Empty");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (FileServer.lock.isHeldByCurrentThread())
                FileServer.lock.unlock();
        }
    }
}
