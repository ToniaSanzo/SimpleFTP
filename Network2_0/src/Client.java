import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.exit;

public class Client {

    // Static constants                                   opcode  operation
    private static final byte RRQ = 1;             //        1     Read request (RRQ)
    private static final byte WRQ = 2;             //        2     Write request (WRQ)
    private static final byte DATA = 3;            //        3     Data (DATA)
    private static final byte ACK = 4;             //        4     Acknowledgment (ACK)
    private static final byte ERROR = 5;           //        5     Error (ERROR)
    private static final byte OACK = 6;            //        6     Optional ack (OACK)
    private static final byte SLD = 7;             //        7     TCP sliding window (SLD)
    private static final byte DRP = 8;        //        8     Packet dropped error code(DRP)
    private static final int udpPort = 2720;
    private static final int tcpPort = 2721;
    private static final int PACKET_SIZE = 516;


    // Static variables
    private static final String piv6 = "fe80::225:90ff:fe4d:f030";
    private static final String piv4 ="129.3.20.26";
    private static String fileName;
    private static String mode = "octet";
    private static InetAddress ipAddress;
    private static DatagramPacket bufferPacket;
    private static boolean ackSuccess;
    private static byte [] byteBuffer;
    private static DatagramSocket socket = null;
    private static Socket tcpSocket = null;
    private static DataInputStream piIn;
    private static DataOutputStream piOut;

    public static void main(String [] args) throws IOException
    {
        List<File> fileList = getAllFiles("./images/");

        for (File file : fileList) {
            fileName = file.getName();


            socket = new DatagramSocket();

            if (args.length > 0) {
                if (args[0].equals("4")) {
                    System.out.println("IPv4 selected:");
                    ipAddress = InetAddress.getByName(piv4);
                }
                if (args[0].equals("6")) {
                    System.out.println("IPv6 selected:");
                    ipAddress = InetAddress.getByName(piv6);
                }

                if (args[0].equals("drop")) {

                    System.out.println("Drop selected:");
                    ipAddress = InetAddress.getByName(piv4);
                    // Send Write Request
                    sendRequest(WRQ);

                    // Wait for the Ack from the Server
                    ackSuccess = waitForAck((byte) 0, (byte) 0);
                    if (!ackSuccess)
                        exit(0); // Close the program if the Ack is unsuccessful


                    // Convert the file into a byte array
                    byte[] fileArray = null;
                    FileInputStream fileInputStream = null;
                    try {

                        fileArray = new byte[(int) file.length()];
                        fileInputStream = new FileInputStream(file);
                        fileInputStream.read(fileArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (fileArray == null) {
                        System.out.println("5318008: NULL POINTER EXCEPTION");
                        System.exit(0);
                    }

                    // Write the File to the Server
                    transferFileSeqDrop(fileArray);
                    continue;
                }
                if (args[0].equals("slide")) {
                    System.out.println("TCP Sliding window:");
                    ipAddress = InetAddress.getByName(piv4);
                    sendRequest(SLD);


                    ackSuccess = waitForOAck((byte) 0, (byte) 0);
                    System.out.println("OACK received");

                    if (!ackSuccess)
                        exit(0);


                    System.out.println("OACK Successful");


                    // Convert the file into a byte array
                    byte[] fileArray = null;
                    FileInputStream fileInputStream = null;
                    try {

                        fileArray = new byte[(int) file.length()];
                        fileInputStream = new FileInputStream(file);
                        fileInputStream.read(fileArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (fileArray == null) {
                        System.out.println("5318008: NULL POINTER EXCEPTION");
                        System.exit(0);
                    }
                    System.out.println("File Created");

                    //File successfully created
                    sendFileSld(fileArray);

                    continue;
                }
            } else
                ipAddress = InetAddress.getByName(piv4);


            // Send Write Request
            sendRequest(WRQ);

            // Wait for the Ack from the Server
            ackSuccess = waitForAck((byte) 0, (byte) 0);
            if (!ackSuccess)
                exit(0); // Close the program if the Ack is unsuccessful


            // Convert the file into a byte array
            byte[] fileArray = null;
            FileInputStream fileInputStream = null;
            try {

                fileArray = new byte[(int) file.length()];
                fileInputStream = new FileInputStream(file);
                fileInputStream.read(fileArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fileArray == null) {
                System.out.println("5318008: NULL POINTER EXCEPTION");
                System.exit(0);
            }

            // Write the File to the Server
            transferFileSeq(fileArray);
        }

    }


    private static void sendRequest(byte opCode) throws IOException {
        byte zeroByte = 0;
        int headerLength = 2 + fileName.length() + 1 + mode.length() + 1;
        byte [] byteBuffer = new byte[headerLength];

        int position = 0;
        byteBuffer[position] = zeroByte;
        position++;
        byteBuffer[position] = opCode;
        position++;
        for(int i = 0; i < fileName.length();i++){
            byteBuffer[position] = (byte)fileName.charAt(i);
            position++;
        }
        byteBuffer[position] = zeroByte;
        position++;
        for(int i = 0; i < mode.length(); i++){
            byteBuffer[position] = (byte)mode.charAt(i);
            position++;
        }
        byteBuffer[position] = zeroByte;
        bufferPacket = new DatagramPacket(byteBuffer,byteBuffer.length,ipAddress,udpPort);
        socket.send(bufferPacket);
    }

    private static boolean waitForAck(byte b1, byte b2) throws IOException
    {
        byte [] ackArray = new byte [4];
        DatagramPacket ackPack =
                new DatagramPacket(ackArray,ackArray.length, ipAddress, udpPort);
        socket.receive(ackPack);

        if(b1 == ackArray[2] && b2 == ackArray[3])
            return true;

        return false;
    }

    private static boolean waitForOAck(byte b1, byte b2) throws IOException
    {
        byte [] ackArray = new byte [4];
        DatagramPacket ackPack =
                new DatagramPacket(ackArray,ackArray.length, ipAddress, udpPort);
        socket.receive(ackPack);

        if(b1 == ackArray[2] && b2 == ackArray[3])
            return true;

        return false;
    }

    /*private static boolean waitForOACK() throws IOException{
        int incomingOpc;

        tcpSocket = new Socket("pi.cs.oswego.edu",2721);
        piIn = new DataInputStream(tcpSocket.getInputStream());
        piOut = new DataOutputStream(tcpSocket.getOutputStream());


        // receive the OACK from the Server's TCP socket
        incomingOpc = piIn.readByte();


        // If you receive a OACK you know you can begin sliding window
        if(incomingOpc == OACK)
            return true;

        // OAC was not receiveed so you have to start over
        return false;
    }*/

    private static void transferFileSeqDrop(byte[] byteArray) throws IOException {
        byte zeroByte = 0;
        DatagramPacket dataPacket;// = new DatagramPacket(specificByteArr, specificByteArr.length,ipAddress,port);
        int j, block = 1;
        int hundredthDrop = 1;
        boolean successfulAck;


        // Send the majority of the packets
        while (block * 512 <= byteArray.length) {

            System.out.println("TFTP Packet count: " + block);

            // This block creates the DATA TFTP packet
            byteBuffer = new byte[PACKET_SIZE];
            // sets the header of the DATA TFTP packet
            byteBuffer[0] = zeroByte;
            byteBuffer[1] = DATA;
            byteBuffer[2] = (byte) (block >> 8);
            byteBuffer[3] = (byte) block;
            // sets the data or "payload" of the DATA TFTP packet
            j = 4;
            for (int i = (block - 1) * 512; i < block * 512; i++) {
                byteBuffer[j] = byteArray[i];
                j++;
            }

            // turn the DATA TFTP packet into a datagram packet
            dataPacket = new DatagramPacket(byteBuffer, byteBuffer.length, ipAddress, udpPort);
            // send the packet to the client
            if(hundredthDrop % 100 != 0) {
                socket.send(dataPacket);
            }

            if(hundredthDrop % 100 == 0) {
                System.out.println(block +" ---------------> Packet Dropped\n");
            }
            hundredthDrop++;
            // wait for the ack
            successfulAck = waitForAck(byteBuffer[2], byteBuffer[3]);

            if (successfulAck)
                block++;
        }

        successfulAck = false;

        // implement the final DATA TFTP packet
        byteBuffer = new byte[(4 + (byteArray.length - ((block - 1) * 512)))];
        // sets the header of the DATA TFTP packet
        byteBuffer[0] = zeroByte;
        byteBuffer[1] = DATA;
        byteBuffer[2] = (byte) (block >> 8);
        byteBuffer[3] = (byte) block;
        // sets the data or "payload" of the DATA TFTP packet
        j = 4;
        for (int i = (block - 1) * 512; i < byteArray.length; i++) {
            byteBuffer[j] = byteArray[i];
            j++;
        }

        dataPacket = new DatagramPacket(byteBuffer,byteBuffer.length,ipAddress, udpPort);
        System.out.println("final packet size " + dataPacket.getLength());

        while(!successfulAck) {
            System.out.println("Final Loop");
            socket.send(dataPacket);
            successfulAck = waitForAck(byteBuffer[2],byteBuffer[3]);
        }
    }

    private static void transferFileSeq(byte[] byteArray) throws IOException
    {
        byte zeroByte = 0;
        DatagramPacket dataPacket;// = new DatagramPacket(specificByteArr, specificByteArr.length,ipAddress,port);
        int j, block = 1;
        boolean successfulAck;


        // Send the majority of the packets
        while(block*512 <= byteArray.length){



            // This block creates the DATA TFTP packet
            byteBuffer = new byte[PACKET_SIZE];
            // sets the header of the DATA TFTP packet
            byteBuffer[0] = zeroByte;
            byteBuffer[1] = DATA;
            byteBuffer[2] =(byte)(block >> 8);
            byteBuffer[3] =(byte)block;
            // sets the data or "payload" of the DATA TFTP packet
            j = 4;
            for(int i = (block - 1)*512; i < block *512; i++){
                byteBuffer[j] = byteArray[i];
                j++;
            }

            // turn the DATA TFTP packet into a datagram packet
            dataPacket = new DatagramPacket(byteBuffer,byteBuffer.length,ipAddress, udpPort);
            // send the packet to the client
            socket.send(dataPacket);
            // wait for the ack
            successfulAck = waitForAck(byteBuffer[2],byteBuffer[3]);
            System.out.println("TFTP Packet count: " + block);
            if(successfulAck)
                block++;
        }

        successfulAck = false;

        // implement the final DATA TFTP packet
        byteBuffer = new byte[(4 + (byteArray.length - ((block-1) * 512)))];
        // sets the header of the DATA TFTP packet
        byteBuffer[0] = zeroByte;
        byteBuffer[1] = DATA;
        byteBuffer[2] =(byte)(block >> 8);
        byteBuffer[3] =(byte)block;
        // sets the data or "payload" of the DATA TFTP packet
        j = 4;
        for(int i = (block - 1)*512; i < byteArray.length; i++){
            byteBuffer[j] = byteArray[i];
            j++;
        }

        dataPacket = new DatagramPacket(byteBuffer,byteBuffer.length,ipAddress, udpPort);
        System.out.println("final packet size " + dataPacket.getLength());

        while(!successfulAck) {
            System.out.println("Final Loop");
            socket.send(dataPacket);
            successfulAck = waitForAck(byteBuffer[2],byteBuffer[3]);
        }
    }

    private static void sendFileSld(byte [] byteArray) throws IOException {
        int windowHead = 1, windowSize = 7, ackNumb;
        byte zeroByte = 0;
        int j, pendingMessage, block = 1;
        byte[] ackArray = new byte[4];
        DatagramPacket ackPack;
        DatagramPacket dataPacket;

        socket.setSoTimeout(300);

        while (block * 512 <= byteArray.length) {

            //sends the window size within this block
            while (block < windowSize + windowHead && block * 512 <= byteArray.length) {
                // This block creates the DATA TFTP packet
                byteBuffer = new byte[PACKET_SIZE];
                // sets the header of the DATA TFTP packet
                byteBuffer[0] = zeroByte;
                byteBuffer[1] = DATA;
                byteBuffer[2] = (byte) (block >> 8);
                byteBuffer[3] = (byte) block;
                // sets the data or "payload" of the DATA TFTP packet
                j = 4;
                for (int u = (block - 1) * 512; u < block * 512; u++) {
                    byteBuffer[j] = byteArray[u];
                    j++;
                }

                dataPacket = new DatagramPacket(byteBuffer, byteBuffer.length, ipAddress, udpPort);
                socket.send(dataPacket);
                System.out.println(block + " PACKET SENT");

                block++;
            }



            ackArray = new byte[4];
            ackPack = new DatagramPacket(ackArray, ackArray.length, ipAddress, udpPort);

            try {
                socket.receive(ackPack);
            } catch (SocketTimeoutException e) {
                continue;
            }

            // Convert the block number from the received ack into an integer and checks this number against the current window head
            //ackNumb = (int) ackArray[2];
            //ackNumb = ackNumb << 8;
            //ackNumb += (int) ackArray[3];
            byte[] arr = {(byte)0,(byte) 0,ackArray[2],ackArray[3]};
            ByteBuffer wrapped = ByteBuffer.wrap(arr);
            ackNumb = wrapped.getInt();
            wrapped.clear();
            System.out.println("Ack Number: " + ackNumb);

            if (ackNumb == windowHead) {
                windowHead++;
            }

        }


        // implement the final DATA TFTP packet
        byteBuffer = new byte[(4 + (byteArray.length - ((block - 1) * 512)))];
        // sets the header of the DATA TFTP packet
        byteBuffer[0] = zeroByte;
        byteBuffer[1] = DATA;
        byteBuffer[2] = (byte) (block >> 8);
        byteBuffer[3] = (byte) block;
        // sets the data or "payload" of the DATA TFTP packet
        j = 4;
        for (int u = (block - 1) * 512; u < byteArray.length; u++) {
            byteBuffer[j] = byteArray[u];
            j++;
        }

        dataPacket = new DatagramPacket(byteBuffer, byteBuffer.length, ipAddress, udpPort);
        socket.send(dataPacket);
        System.out.println(block + " PACKET SENT: FINAL");


        while(windowHead <= block) {
            ackArray = new byte[4];
            ackPack = new DatagramPacket(ackArray, ackArray.length, ipAddress, udpPort);

            try {
                socket.receive(ackPack);
            } catch (SocketTimeoutException e) {
                continue;
            }

            // Convert the block number from the received ack into an integer and checks this number against the current window head
            byte[] arr = {(byte)0,(byte) 0,ackArray[2],ackArray[3]};
            ByteBuffer wrapped = ByteBuffer.wrap(arr);
            ackNumb = wrapped.getInt();
            wrapped.clear();
            System.out.println("Ack Number: " + ackNumb);

            if (ackNumb == windowHead) {
                windowHead++;
            }
        }
    }



    /**
     * Using a Directory name this will return a ArrayList of Files
     * @author Alexander Lawrence
     * @param dir The name of the Directory that will be recursively searched through
     * @return returns an ArrayList of files
     * @throws IOException
     */
    public static ArrayList<File> getAllFiles(String dir) throws IOException{
        ArrayList<File> list = new ArrayList<>();
        Files.walk(Paths.get(dir)).filter(Files::isRegularFile).forEach((n)->list.add(n.toFile()));
        return list;
    }
}


