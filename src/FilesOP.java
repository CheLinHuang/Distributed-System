import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FilesOP {

    public static void main(String[] args) {
        listFiles("fnm");
        System.out.println(deleteFile("SDFS"));
    }

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

        System.out.println("Called File Thread");
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

            // Support file < 2GB with int casting
            byte[] byteArray = new byte[(int) file.length()];

            System.out.println("File size: " + file.length());

            //Send file
            try (
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    // BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {

                //Sending file name and file size to the server
                dis.readFully(byteArray);
                //dos.writeUTF(fileName);
                dos.writeLong(byteArray.length);

                //Sending file data to the server
                dos.write(byteArray);
                dos.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("Send File Thread finish");
        }
    }
}
