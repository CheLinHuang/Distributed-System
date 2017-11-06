import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FilesOP {

    public static List<String> listFiles(String dirName) {

        List<String> result = new ArrayList<>();
        File curDir = new File(dirName);
        listFilesHelper(curDir, "", result);

        return result;
    }

    private static void listFilesHelper(File file, String dirName, List<String> result) {

        File[] fileList = file.listFiles();
        if (fileList != null) {
            for (File f : fileList) {
                if (f.isDirectory())
                    listFilesHelper(f, dirName + f.getName() + "/", result);
                if (f.isFile()) {
                    result.add(dirName + f.getName());
                }
            }
        }
    }

    public static boolean deleteFile(String fileName) {
        File file = new File(fileName);
        return file.delete();
    }

    public static Thread sendFile(File file, String fileName, Socket socket) {

        return new SendFileThread(file, socket, fileName);
    }

    static class SendFileThread extends Thread {
        File file;
        Socket socket;
        String fileName;

        public SendFileThread(File file, Socket socket, String fileName) {
            this.file = file;
            this.socket = socket;
            this.fileName = fileName;
        }

        @Override
        public void run() {

            byte[] buffer = new byte[4096];
            //Send file
            try (
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    DataInputStream sktResponse = new DataInputStream(socket.getInputStream())
            ) {

                //Sending file size to the server
                dos.writeLong(file.length());

                //Sending file data to the server
                int read;
                while ((read = dis.read(buffer)) > 0)
                    dos.write(buffer, 0, read);

                // wait for the server to response
                String res = sktResponse.readUTF();
                if (res.equals("Received")) {
                    System.out.println("Put the file successfully");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
