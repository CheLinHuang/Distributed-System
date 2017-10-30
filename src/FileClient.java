import java.io.*;
import java.net.*;

public class FileClient {
    public static void main(String[] args) {


        File file = new File("DelayedFlights.csv");
        // TODO: handle FileNotFoundException

        // Support file < 2GB with int casting
        byte[] byteArray = new byte[(int) file.length()];

        //Send file
        try (
            Socket socket = new Socket("wirelessprv-10-193-58-171.near.illinois.edu", 11111);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            OutputStream os = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(os);
        ){

            //Sending file name and file size to the server
            //dis.readFully(byteArray, 0, byteArray.length);
            dis.readFully(byteArray);
            dos.writeUTF(file.getName());
            dos.writeLong(byteArray.length);

            //Sending file data to the server
            //dos.write(byteArray, 0, byteArray.length);
            dos.write(byteArray);
            dos.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
