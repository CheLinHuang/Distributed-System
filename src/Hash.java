import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.lang.Math;
import java.util.*;

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
    public static String getServer(int hashValue) {

        int size = Daemon.hashValues.navigableKeySet().size();
        Integer[] keySet = new Integer[size];
        Daemon.hashValues.navigableKeySet().toArray(keySet);

        int min = keySet[0].intValue();
        int max = keySet[size-1].intValue();

        System.out.println(hashValue);
        System.out.println(min);
        System.out.println(max);

        if (hashValue > max) {
            return Daemon.hashValues.get(keySet[0]);
        }
        if (hashValue < min) {
            return Daemon.hashValues.get(keySet[size - 1]);
        }
        System.out.println(hashValue);
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            System.out.println(keySet[i].intValue());
            if (keySet[i].intValue() >= hashValue && keySet[i - 1].intValue() < hashValue) {
                targetIndex = i;
            }
        }
        return Daemon.hashValues.get(keySet[targetIndex]);
        /*
        int lower = 0;
        int higher = size - 1;

        while (lower <= higher) {
            int mid = (lower + higher) / 2;

            // if (mid == 0) return Daemon.hashValues.get(keySet[0]);

            if (keySet[mid].intValue() >= hashValue && keySet[mid - 1].intValue() < hashValue) {
                return Daemon.hashValues.get(keySet[mid]);
            }
            if (keySet[mid].intValue() < hashValue) {
                lower = mid;
            }
            else if (keySet[mid - 1].intValue() == hashValue) {
                return Daemon.hashValues.get(keySet[mid - 1]);
            } else if (keySet[mid - 1].intValue() > hashValue) {
                higher = mid;
            }
        }
        return "None";
        */
    }

}
