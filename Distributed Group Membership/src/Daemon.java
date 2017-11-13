import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Daemon {

    static String ID;
    static int joinPortNumber;
    static int packetPortNumber;
    static final Set<String> neighbors = new HashSet<>();
    static final TreeMap<String, long[]> membershipList = new TreeMap<>();
    private static PrintWriter fileOutput;
    private String[] hostNames;

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

            // assign daemon process an ID
            ID = LocalDateTime.now().toString() + "#" +
                    InetAddress.getLocalHost().toString().split("/")[1];

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
                neighbors.clear();
                // get the predecessors
                String currentKey = ID;
                for (int i = 0; i < 2; i++) {
                    currentKey = membershipList.lowerKey(currentKey);
                    if (currentKey == null) {
                        currentKey = membershipList.lastKey();
                    }
                    if (!currentKey.equals(ID)) {
                        try {
                            neighbors.add(currentKey);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                // get the successors
                currentKey = ID;
                for (int i = 0; i < 2; i++) {
                    currentKey = membershipList.higherKey(currentKey);
                    if (currentKey == null) {
                        currentKey = membershipList.firstKey();
                    }

                    if (!currentKey.equals(ID)) {
                        try {
                            neighbors.add(currentKey);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
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

            // process the membership list that the first introducer response and ignore the rest
            String[] members = response.split("%");
            for (String member : members) {
                String[] memberDetail = member.split("/");
                long[] memberStatus = {Long.parseLong(memberDetail[1]), System.currentTimeMillis()};
                membershipList.put(memberDetail[0], memberStatus);
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
                switch (cmd) {
                    case "JOIN":
                        if (membershipList.size() == 0) {
                            daemon.joinGroup(isIntroducer);
                            ExecutorService mPool = Executors.newFixedThreadPool(3 + ((isIntroducer) ? 1 : 0));
                            if (isIntroducer) {
                                mPool.execute(new IntroducerThread());
                            }
                            mPool.execute(new ListeningThread());
                            mPool.execute(new HeartbeatThread(900));
                            mPool.execute(new MonitorThread());
                        }
                        break;

                    case "MEMBER":
                        System.out.println("===============================");
                        System.out.println("Membership List:");
                        for (String key : membershipList.keySet()) {
                            System.out.println(key);
                        }
                        System.out.println("===============================");
                        break;

                    case "ID":
                        System.out.println(ID);
                        break;

                    case "LEAVE":
                        if (membershipList.size() != 0) {
                            Protocol.sendGossip(ID, "Leave", membershipList.get(ID)[0] + 10,
                                    3, 4, new DatagramSocket());
                        }
                        fileOutput.println(LocalDateTime.now().toString() + " \"LEAVE!!\" " + ID);
                        fileOutput.close();
                        System.exit(0);

                    default:
                        System.out.println("Unsupported command!");
                }
                displayPrompt();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
