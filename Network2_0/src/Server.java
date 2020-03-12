import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

import static java.lang.System.exit;

public class Server {
    // Static constants                              opcode  operation
    private static final byte RRQ = 1;        //        1     Read request (RRQ)
    private static final byte WRQ = 2;        //        2     Write request (WRQ)
    private static final byte DATA = 3;       //        3     Data (DATA)
    private static final byte ACK = 4;        //        4     Acknowledgment (ACK)
    private static final byte ERROR = 5;      //        5     Error (ERROR)
    private static final byte OACK = 6;       //        6     Optional ack (OACK)
    private static final byte SLD = 7;        //        7     TCP sliding window (SLD)
    private static final byte DRP = 8;        //        8     Packet dropped error code(DRP)
    private static final int  PACKET_SIZE = 516;

    // Static variables
    private static DatagramSocket serverSocket = null;
    private static ServerSocket tcpServer = null;
    private static DatagramPacket packetBuffer = null;
    private static Socket tcpSocket = null;
    private static DataInputStream in = null;
    private static DataOutputStream out = null;
    private static int port;
    private static InetAddress ipAddress;
    private static String fileName;
    private static String mode;
    private static byte opc;
    private static byte [] byteBuffer;
    private static long time;


    public static void main(String [] args) throws IOException {
        serverSocket = new DatagramSocket(2720);
        tcpServer = new ServerSocket(2721);

        while(true){
        // No Timeout is Set
        serverSocket.setSoTimeout(0);

        // Receive the Header from the Client
        receiveOpCode();

        if(opc == WRQ) {
            // Send the initial Ack to the Client
            byteBuffer = new byte[2];
            byteBuffer[0] = 0;
            byteBuffer[1] = 0;
            sendAck(byteBuffer);

            time = System.nanoTime();
            readFile(fileName);
            time = System.nanoTime() - time;
            System.out.println("File Name: " + fileName + "Time in nanoseconds: " + time);
        }

        if(opc == SLD) {
            // Send the initial OAck to the Client
            byteBuffer = new byte[2];
            byteBuffer[0] = 0;
            byteBuffer[1] = 0;
            sendOAck(byteBuffer);

            time = System.nanoTime();
            ByteArrayOutputStream byteOutOS = receiveFileSLD();
            // Step 4: write the file to your local disk
            writeFile(byteOutOS, fileName);
            time = System.nanoTime() - time;
            System.out.println("File Name: " + fileName + "Time in nanoseconds: " + time);
        }

        }
    }


    /*private static ByteArrayOutputStream receiveFileSLD() throws IOException {
        ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOutOS);
        int pendingMessage, packetSize = 516;

        //tcpSocket = tcpServer.accept();
        //DataInputStream in = new DataInputStream(tcpSocket.getInputStream());
        //DataOutputStream out = new DataOutputStream(tcpSocket.getOutputStream());

        int currentBlock;
        //out.writeByte(OACK);

        try {
            Thread.sleep(35);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        do{
            //System.out.println("TFTP/SLD Packet count: " + currentBlock);
            pendingMessage = in.available();

            // If the server sent a data packet to the client
            if(pendingMessage != 0){
                // reads the size of the packetSize
                packetSize = in.readInt();
                byteBuffer = new byte[packetSize];
                in.read(byteBuffer, 0, packetSize);
                // convert the block number from bytes into an integer
                //currentBlock = (()byteBuffer[2] << 8) + (int)byteBuffer[3];
                currentBlock = in.readInt();
                System.out.println("current ack being sent: " + currentBlock);
                out.writeInt(currentBlock);
            }

            if(byteBuffer != null) {
                if (byteBuffer[1] == ERROR) {
                    System.out.println("ERROR PACKET BEING RECEIVED START PROGRAM OVER");
                    exit(0);
                } else if (byteBuffer[1] == DATA) {
                    dos.write(byteBuffer, 4, byteBuffer.length - 4);
                    byteBuffer = null;
                }
            }

        }while(packetSize == 516);
        return byteOutOS;
    }*/

    private static ByteArrayOutputStream receiveFileSLD() throws IOException {
        ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOutOS);
        serverSocket.setSoTimeout(300);
        int block = 1;

        do{
            System.out.println("TFTP/SLide Packet count: " + block);

            byteBuffer = new byte[PACKET_SIZE];
            packetBuffer = new DatagramPacket(byteBuffer, byteBuffer.length);

            // Step 1: receive packet from TFTP server
            try {
                serverSocket.receive(packetBuffer);
            } catch (SocketTimeoutException e){
                System.out.println( block + " ------------> SOCKET TIMEOUT");
                //byte [] dropByteArray = {0,DRP,0,0};
                //DatagramPacket dropPacket = new DatagramPacket(dropByteArray,4,ipAddress,port);
                //serverSocket.send(dropPacket);

                // Quick pause so the client can get itself together
                //for(int i = 0; i < 300; i++){
                //    for(int j = 3; j <1000; j++);
                //}
                continue;
            }
            // Getting the first 4 characters from the TFTP packet
            byte[] opcode = {byteBuffer[0], byteBuffer[1]};

            if(opcode[1] == ERROR){
                System.out.println("ERROR PACKET BEING RECEIVED START PROGRAM OVER");
                exit(0);
            } else if(opcode[1] == DATA) {
                // check for the TFTP packets block number
                byte[] blockNumber = {byteBuffer[2], byteBuffer[3]};
                dos.write(packetBuffer.getData(), 4, packetBuffer.getLength() - 4);
                block++;

                // Step 2: send Ack to TFTP server for received packet
                sendAck(blockNumber);
            }

        }while(notTheLastPacket(packetBuffer));
        return byteOutOS;
    }


    private static void readFile(String fileName) throws IOException {

        ByteArrayOutputStream byteOutOS = receiveFile();

        // Step 4: write the file to your local disk
        writeFile(byteOutOS,fileName);
    }



    private static void writeFile(ByteArrayOutputStream baoStream, String fileName){
        try {
            OutputStream opStream = new FileOutputStream(fileName);
            baoStream.writeTo(opStream);
        } catch (IOException e){
            e.printStackTrace();
        }
    }


    private static ByteArrayOutputStream receiveFile() throws IOException {
        ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOutOS);
        serverSocket.setSoTimeout(300);
        int block = 1;

        do{
            System.out.println("TFTP Packet count: " + block);

            byteBuffer = new byte[PACKET_SIZE];
            packetBuffer = new DatagramPacket(byteBuffer, byteBuffer.length);

            // Step 1: receive packet from TFTP server
            try {
                serverSocket.receive(packetBuffer);
            } catch (SocketTimeoutException e){
                System.out.println( block + " ------------> PACKET DROPPED SOCKET TIMEOUT");
                byte [] dropByteArray = {0,DRP,0,0};
                DatagramPacket dropPacket = new DatagramPacket(dropByteArray,4,ipAddress,port);
                serverSocket.send(dropPacket);

                // Quick pause so the client can get itself together
                for(int i = 0; i < 300; i++){
                    for(int j = 3; j <1000; j++);
                }
                continue;
            }
            // Getting the first 4 characters from the TFTP packet
            byte[] opcode = {byteBuffer[0], byteBuffer[1]};

            if(opcode[1] == ERROR){
                System.out.println("ERROR PACKET BEING RECEIVED START PROGRAM OVER");
                exit(0);
            } else if(opcode[1] == DATA) {
                // check for the TFTP packets block number
                byte[] blockNumber = {byteBuffer[2], byteBuffer[3]};
                dos.write(packetBuffer.getData(), 4, packetBuffer.getLength() - 4);
                block++;

                // Step 2: send Ack to TFTP server for received packet
                sendAck(blockNumber);
            }

        }while(notTheLastPacket(packetBuffer));
        return byteOutOS;
    }


    private static boolean notTheLastPacket(DatagramPacket packet) {
        if(packet.getLength() < 516) {
            System.out.println("final packet size " + packet.getLength());
            return false;
        } else
            return true;
    }


    private static void receiveOpCode() throws IOException
    {
        int index = 2;
        byte [] bArray;
        fileName = "./";
        mode = "";

        // Step 1: receive the header from the client server
        byte [] byteBuffer = new byte[128];

        DatagramPacket receiveHeader =
                new DatagramPacket(byteBuffer, byteBuffer.length);
        serverSocket.receive(receiveHeader);

        // Step 2: convert the request header into a byte array, record the ip address and port number
        ByteBuffer requestInBytes = ByteBuffer.wrap(receiveHeader.getData());
        port = receiveHeader.getPort();
        ipAddress = receiveHeader.getAddress();

        // Step 3: convert the byte buffer into a byte array
        bArray = new byte[requestInBytes.remaining()];
        requestInBytes.get(bArray);

        //Step 4: determine and record the op code, file name, and mode from the byte buffer.
        opc = bArray[1];

        while( bArray[index] != ((byte)0) ) {
            fileName += (char)bArray[index];
            index++;
        }

        index++;

        while( bArray[index] != ((byte) 0)) {
            mode += (char)bArray[index];
            index++;
        }
    }

    private static void sendAck(byte[] blockNumber) {
        byte[] ack = {0, ACK, blockNumber[0],blockNumber[1]};

        DatagramPacket ackPacket = new DatagramPacket(ack,ack.length,ipAddress,port);

        try {
            serverSocket.send(ackPacket);
        } catch(IOException e){
            e.printStackTrace();
        }
    }


    private static void sendOAck(byte[] blockNumber) {
        byte[] ack = {0, OACK, blockNumber[0],blockNumber[1]};

        DatagramPacket ackPacket = new DatagramPacket(ack,ack.length,ipAddress,port);

        try {
            serverSocket.send(ackPacket);
        } catch(IOException e){
            e.printStackTrace();
        }
    }

}
