import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.lang.Math;

public class Hash {
    public static void main(String[] args){

        try {
            String a = InetAddress.getLocalHost().toString();
            System.out.println(a);
            String [] strings = {"AA", "BB", "CC", "DE"};

            for (int i = 0 ; i < strings.length; i++) {
                System.out.println(hashing(strings[i], 7));
            }
            // System.out.println(-2 % 32 + 32);
            //System.out.println(hashValue);

            // System.out.println((int)(Math.pow(2, 10)));
        } catch (Exception e){
            e.printStackTrace();
        }

    }
    public static int hashing(String inputString, int numOfBits) {

        long hashValue = 0;
        long modulus = (long) Math.pow(2, numOfBits);

        for (int i = 0; i < inputString.length(); i++) {
            hashValue = hashValue * 31 + inputString.charAt(i);
        }

        hashValue = hashValue % modulus;
        // in case that hashValue is negative
        if (hashValue < 0) hashValue += modulus;

        return (int)hashValue;
    }
}
