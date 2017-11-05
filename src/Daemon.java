import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.*;

public class Daemon {

    static String ID;
    static Integer myHashValue;
    static int joinPortNumber;
    static int packetPortNumber;
    static int filePortNumber;
    static final List<String> neighbors = new ArrayList<>();
    static final Map<String, long[]> membershipList = new HashMap<>();
    static final TreeMap<Integer, String> hashValues = new TreeMap<>();
    private static PrintWriter fileOutput;
    private String[] hostNames;
    private final static int bufferSize = 1024;

    public Daemon(String configPath) {

        // check if the configPath is valid
        if (!(new File(configPath).isFile())) {
            System.err.println("No such file!");
            System.exit(1);
        }

        Properties config = new Properties();

        try (InputStream configInput = new FileInputStream(configPath)) {

            // load the configuration
            config.load(configInput);
            hostNames = config.getProperty("hostNames").split(":");
            joinPortNumber = Integer.parseInt(config.getProperty("joinPortNumber"));
            packetPortNumber = Integer.parseInt(config.getProperty("packetPortNumber"));
            filePortNumber = Integer.parseInt(config.getProperty("filePortNumber"));
            String logPath = config.getProperty("logPath");

            // output the configuration setting for double confirmation
            System.out.println("Configuration file loaded!");
            System.out.println("Introducers are:");
            for (int i = 0; i < hostNames.length; i++) {
                String vmIndex = String.format("%02d", (i + 1));
                System.out.println("VM" + vmIndex + ": " + hostNames[i]);
            }
            System.out.println("Join Port Number: " + joinPortNumber);
            System.out.println("Packet Port Number: " + packetPortNumber);

            // assign daemon process an ID: the IP address
            ID = LocalDateTime.now().toString() + "#" +
                    InetAddress.getLocalHost().toString().split("/")[1];
            myHashValue = Integer.valueOf(Hash.hashing(ID, 8));
            // assign appropriate log file path
            File outputDir = new File(logPath);
            if (!outputDir.exists())
                outputDir.mkdirs();
            fileOutput = new PrintWriter(new BufferedWriter(new FileWriter(logPath + "result.log")));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateNeighbors() {

        synchronized (membershipList) {
            synchronized (neighbors) {
                List<String> oldNeighbors = new ArrayList<>(neighbors);

                neighbors.clear();

                Integer currentHash = myHashValue;
                // get the predecessors
                for (int i = 0; i < 2; i++) {
                    currentHash = hashValues.lowerKey(currentHash);
                    // since we are maintaining a virtual ring, if lower key is null,
                    // it means that we are at the end of the list
                    if (currentHash == null) {
                        currentHash = hashValues.lastKey();
                    }
                    if (!currentHash.equals(myHashValue) && !neighbors.contains(hashValues.get(currentHash))) {
                        try {
                            neighbors.add(hashValues.get(currentHash));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                // get the successors
                currentHash = myHashValue;
                for (int i = 0; i < 2; i++) {
                    currentHash = hashValues.higherKey(currentHash);
                    if (currentHash == null) {
                        currentHash = hashValues.firstKey();
                    }

                    if (!currentHash.equals(myHashValue) && !neighbors.contains(hashValues.get(currentHash))) {
                        try {
                            neighbors.add(hashValues.get(currentHash));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                boolean updated = false;
                for (int i = 0; i < oldNeighbors.size() && i < neighbors.size(); i++) {
                    if (!oldNeighbors.get(i).equals(neighbors.get(i))) {
                        updated = true;
                        break;
                    }
                }
                if (updated) {
                    List<String> fileList = FilesOP.listFiles("../SDFS/");
                    for (int i = 0; i < fileList.size(); i++) {
                        String file = fileList.get(i);
                        String targetID = Hash.getServer(Hash.hashing(file, 8));
                        if (targetID.equals(ID)) {
                            // replicate the file to the two successors
                            int j = neighbors.size()- 1;
                            while (j >= 0) {
                                String tgtHostName = neighbors.get(j--).split("#")[1];
                                try {
                                    Socket socket = new Socket(tgtHostName, filePortNumber);
                                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                    out.println("fail replica");
                                    out.println(file);
                                    String returnMsg = in.readLine();
                                    if (returnMsg.equals("Ready to receive")) {
                                        FilesOP.sendFile(new File(file), file, socket);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            try {
                                Socket socket = new Socket(targetID.split("#")[1], filePortNumber);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        } else if (!neighbors.contains(targetID)) {
                            // consider the case that new node is added,
                            // and the target node is no longer in the neighbor list,
                            // this means that this file is longer needed
                            if (FilesOP.deleteFile(file)) {
                                System.out.println(file + "is successfully deleted!");
                                // writeLog();
                            }
                        }
                    }
                }

                System.out.println("print neighbors......");
                for (String neighbor : neighbors) {
                    System.out.println(neighbor);
                }

                // Update timestamp for non-neighbor
                for (String neighbor : neighbors) {
                    long[] memberStatus = {membershipList.get(neighbor)[0], System.currentTimeMillis()};
                    membershipList.put(neighbor, memberStatus);

                }
            }
        }
    }

    public void joinGroup(boolean isIntroducer) {

        for (String s : FilesOP.listFiles("../SDFS/"))
            FilesOP.deleteFile("../SDFS/" + s);

        DatagramSocket clientSocket = null;

        // try until socket create correctly
        while (clientSocket == null) {
            try {
                clientSocket = new DatagramSocket();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        byte[] sendData = ID.getBytes();

        // send join request to each introducer
        for (String hostName : hostNames) {
            try {
                InetAddress IPAddress = InetAddress.getByName(hostName);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, joinPortNumber);
                clientSocket.send(sendPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        // wait for the server's response
        try {
            clientSocket.setSoTimeout(2000);
            clientSocket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println(response);

            // process the membership list that the first introducer response and ignore the rest
            String[] members = response.split("%");
            for (String member : members) {
                String[] memberDetail = member.split("/");
                long[] memberStatus = {Long.parseLong(memberDetail[1]), System.currentTimeMillis()};
                membershipList.put(memberDetail[0], memberStatus);
                hashValues.put(Hash.hashing(memberDetail[0], 8), memberDetail[0]);
            }

            writeLog("JOIN!", ID);
            updateNeighbors();

        } catch (SocketTimeoutException e) {

            if (!isIntroducer) {
                System.err.println("All introducers are down!! Cannot join the group.");
                System.exit(1);
            } else {
                // This node might be the first introducer,
                // keep executing the rest of codes
                System.out.println("You might be first introducer!");

                // put the process itself to the membership list
                long[] memberStatus = {0, System.currentTimeMillis()};
                membershipList.put(ID, memberStatus);
                hashValues.put(Hash.hashing(ID, 8), ID);
                writeLog("JOIN", ID);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void displayPrompt() {
        System.out.println("===============================");
        System.out.println("Please input the commands:.....");
        System.out.println("Enter \"JOIN\" to join to group......");
        System.out.println("Enter \"MEMBER\" to show the membership list");
        System.out.println("Enter \"ID\" to show self's ID");
        System.out.println("Enter \"LEAVE\" to leave the group");
    }

    public static void writeLog(String action, String nodeID) {

        // write logs about action happened to the nodeID into log
        fileOutput.println(LocalDateTime.now().toString() + " \"" + action + "\" " + nodeID);
        if (!action.equals("FAILURE") || !action.equals("MESSAGE") || !action.equals("JOIN")) {
            fileOutput.println("Updated Membership List:");
            for (String key : membershipList.keySet()) {
                fileOutput.println(key);
            }
            fileOutput.println("======================");
        }
        fileOutput.flush();
    }

    public static void main(String[] args) {

        boolean isIntroducer = false;
        String configPath = null;

        // parse the input arguments
        if (args.length == 0 || args.length > 2) {
            System.err.println("Please enter the argument as the following format: <configFilePath> <-i>(optional)");
            System.exit(1);
        }
        if (args.length == 1) {
            configPath = args[0];
        } else if (args.length == 2) {
            configPath = args[0];
            if (args[1].equals("-i")) {
                isIntroducer = true;
                System.out.println("Set this node as an introducer!");
            } else {
                System.err.println("Could not recognize the input argument");
                System.err.println("Please enter the argument as the following format: <configFilePath> <-i>(optional)");
                System.exit(1);
            }
        }

        Daemon daemon = new Daemon(configPath);
        displayPrompt();

        try (BufferedReader StdIn = new BufferedReader(new InputStreamReader(System.in))) {

            // prompt input handling
            String cmd;
            while ((cmd = StdIn.readLine()) != null) {

                String[] cmdParts = cmd.trim().split(" +");

                switch (cmdParts[0]) {
                    case "join":
                        // to deal with the case that users enter "JOIN" command multiple times
                        if (membershipList.size() == 0) {
                            daemon.joinGroup(isIntroducer);
                            ExecutorService mPool = Executors.newFixedThreadPool(4 + ((isIntroducer) ? 1 : 0));
                            if (isIntroducer) {
                                mPool.execute(new IntroducerThread());
                            }

                            mPool.execute(new FileServer());
                            mPool.execute(new ListeningThread());
                            mPool.execute(new HeartbeatThread(900));
                            mPool.execute(new MonitorThread());
                        }
                        break;

                    case "member":
                        System.out.println("===============================");
                        System.out.println("Membership List:");
                        int size = hashValues.size();
                        Integer[] keySet = new Integer[size];
                        hashValues.navigableKeySet().toArray(keySet);

                        for (Integer key: keySet) {
                            System.out.println(hashValues.get(key));
                        }
                        System.out.println("===============================");
                        break;

                    case "id":
                        System.out.println(ID);
                        break;

                    case "leave":
                        if (membershipList.size() != 0) {
                            Protocol.sendGossip(ID, "Leave", membershipList.get(ID)[0] + 10,
                                    3, 4, new DatagramSocket());
                        }
                        fileOutput.println(LocalDateTime.now().toString() + " \"LEAVE!!\" " + ID);
                        fileOutput.close();
                        System.exit(0);

                    case "put": {
                        if (cmdParts.length != 3) {
                            System.out.println("Unsupported command format!");
                            System.out.println("To put a file into the SDFS");
                            System.out.println("Please enter \"put localfilename sdfsfilename\"");
                            break;
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
                            Socket socket = new Socket(fileServer, filePortNumber);
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            DataInputStream in = new DataInputStream(socket.getInputStream());
                            dos.writeUTF("put");
                            dos.writeUTF(tgtFileName);
                            String response = in.readUTF();
                            System.out.println("Server response " + response);
                            if (response.equals("Accept")) {
                                FilesOP.sendFile(file, tgtFileName, socket);
                            }
                        }
                        break;
                    }
                    case "get": {
                        if (cmdParts.length != 3) {
                            System.out.println("Unsupported command format!");
                            System.out.println("To get a file from the SDFS");
                            System.out.println("Please enter \"get sdfsfilename localfilename\"");
                            break;
                        }

                        String sdfsfilename = cmdParts[1];
                        String localfilename = cmdParts[2];
                        // int hashValue = Hash.hashing(tgtFileName, 8);
                        String fileServer = Hash.getServer(Hash.hashing(sdfsfilename, 8)).split("#")[1];
                        System.out.println("Get file from " + fileServer);
                        Socket socket = new Socket(fileServer, filePortNumber);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        out.writeUTF("get");
                        out.writeUTF(sdfsfilename);

                        String response = in.readUTF();
                        System.out.println("Server response " + response);

                        if (response.equals("File Exist")) {
                            BufferedOutputStream fileOutputStream = new BufferedOutputStream(
                                    new FileOutputStream(localfilename));

                            long fileSize = in.readLong();
                            System.out.println("Ture file size:" + fileSize);
                            byte[] buffer = new byte[bufferSize];
                            int bytes;
                            while (fileSize > 0 && (bytes = in.read(buffer, 0, (int) Math.min(bufferSize, fileSize))) != -1) {
                                fileOutputStream.write(buffer, 0, bytes);
                                fileSize -= bytes;
                            }
                            fileOutputStream.flush();
                            fileOutputStream.close();

                        } else {
                            System.out.println("sdfsfilename not exist!");
                        }
                        break;
                    }
                    case "delete": {
                        if (cmdParts.length != 2) {
                            System.out.println("Unsupported command format!");
                            System.out.println("To delete a file on the SDFS");
                            System.out.println("Please enter \"delete sdfsfilename\"");
                            break;
                        }

                        String sdfsfilename = cmdParts[1];
                        String fileServer = Hash.getServer(Hash.hashing(sdfsfilename, 8)).split("#")[1];
                        Socket socket = new Socket(fileServer, filePortNumber);
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        dos.writeUTF("delete");
                        dos.writeUTF(sdfsfilename);
                        break;
                    }
                    case "ls": {
                        if (cmdParts.length != 2) {
                            System.out.println("Unsupported command format!");
                            System.out.println("To list a file on the SDFS");
                            System.out.println("Please enter \"ls sdfsfilename\"");
                        } else {
                            String sdfsfilename = cmdParts[1];
                            int hash = Hash.hashing(sdfsfilename, 8);
                            List<String> list = new ArrayList<>(3);
                            while (list.size() < Math.min(3, membershipList.size())) {
                                Integer num = hashValues.ceilingKey(hash);
                                if (num == null)
                                    num = hashValues.ceilingKey(0);
                                list.add(hashValues.get(num));
                                hash = num + 1;
                            }

                            for (String s : list)
                                System.out.println(s);
                        }
                        break;
                    }
                    case "store":
                        for (String s : FilesOP.listFiles("../SDFS/"))
                            System.out.println(s);
                        break;
                    default:
                        System.out.println("Unsupported command!");
                        displayPrompt();
                }
                // displayPrompt();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
