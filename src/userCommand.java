import java.net.Socket;
import java.io.*;

public class userCommand {

    public static void putFile (String[] cmdParts, int filePortNumber) {

        if (cmdParts.length != 3) {
            System.out.println("Unsupported command format!");
            System.out.println("To put a file into the SDFS");
            System.out.println("Please enter \"put srcFileName tgtFileName\"");
            return;
        }
        String srcFileName = cmdParts[1];
        String tgtFileName = cmdParts[2];
        // int hashValue = Hash.hashing(tgtFileName, 8);
        String fileServer = Hash.getServer(Hash.hashing(tgtFileName, 8)).split("#")[1];
        System.out.println("Send file to " + fileServer);

        File file = new File(srcFileName);
        if (!file.exists()) {
            System.out.println("Local file not exist!");
        } else {
            try {
                Socket socket = new Socket(fileServer, filePortNumber);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                dos.writeUTF("put");
                dos.writeUTF(tgtFileName);
                String response = in.readUTF();
                System.out.println("Server response " + response);
                if (response.equals("Accept")) {
                    FilesOP.sendFile(file, tgtFileName, socket);
                } else if (response.equals("Confirm")) {

                    System.out.println("Are you sure to send the file? (y/n)");
                    BufferedReader StdIn = new BufferedReader(new InputStreamReader(System.in));

                    long startTime = System.currentTimeMillis();
                    while (((System.currentTimeMillis() - startTime) < 30000) && !StdIn.ready()) {
                        try {
                            Thread.sleep(200);
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    if (StdIn.ready()) {
                        boolean repeat = true;
                        while (repeat) {
                            String cmd = StdIn.readLine().toLowerCase();
                            switch (cmd) {
                                case "y":
                                    FilesOP.sendFile(file, tgtFileName, socket);
                                    repeat = false;
                                    break;
                                case "n":
                                    repeat = false;
                                    // do nothing
                                    break;
                                default:
                                    System.out.println("Unsupported command!");
                                    System.out.println("Are you sure to send the file? (y/n)");
                            }
                        }
                    } else {
                        System.out.println("No response! Update aborted!");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void listFile(String sdfsFileName, int filePortNumber) {
        String fileServer = Hash.getServer(Hash.hashing(sdfsFileName, 8)).split("#")[1];
        try {
            Socket socket = new Socket(fileServer, filePortNumber);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeUTF("ls");
            out.writeUTF(sdfsFileName);

            socket.setSoTimeout(2000);
            String response = in.readUTF(); 

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
