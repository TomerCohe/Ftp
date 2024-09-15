package bgu.spl.net.impl.tftp;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import bgu.spl.net.api.MessagingProtocol;

public class TftpClientProtocol implements MessagingProtocol<byte[]>{

    public static ConcurrentLinkedQueue<byte[]> dataQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<byte[]> incomingDataQueue = new ConcurrentLinkedQueue<>();

    public static short byteToShort(byte[] bytes){
        short b_short = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        return b_short;
    } 

    public boolean shouldTerminate(){
        return TftpClient.shouldTerminate;
    }

    public byte[] process(byte[] msg){
        byte [] byteNum = new byte[2];
        byteNum[0] = msg[0];
        byteNum[1] = msg[1];
        short requestType = byteToShort(byteNum);
        byte [] response = null;
        if(requestType == 9){
            response = bcast(msg);
        }
        else if(requestType == 5){
            response = error(msg);
        }
        else if(requestType == 3){
            response = data(msg);
        }
        else if(requestType == 4){
            response = ack(msg);
        }
        else{
            System.out.println("got illegal packet");
            
        }
        return response;
    }

    private byte[] bcast(byte[] msg){
        String answer = "BCAST ";
        if(msg[2] == 1)
            answer += "add ";
        else
            answer += "delete ";
        String fileName = new String(msg,3,(msg.length-4),StandardCharsets.UTF_8);
        answer += fileName;
        System.out.println(answer);
        return null;
    }

    private byte[] error(byte[] msg){
        if(TftpKeyboardThread.lastCommand == "RRQ"){
            incomingDataQueue.clear();
        }
        else if(TftpKeyboardThread.lastCommand == "WRQ"){
            dataQueue.clear();
        }
        else if(TftpKeyboardThread.lastCommand == "DISC"){
            TftpKeyboardThread.loggedIn = false;
        }
        
        String answer = "ERROR ";
        byte[] errorCodeBytes = new byte[2];
        errorCodeBytes[0] = msg[2];
        errorCodeBytes[1] = msg[3];
        answer += byteToShort(msg) + " ";
        String error = new String(msg,4,(msg.length-5),StandardCharsets.UTF_8);
        answer += error;
        System.out.println(answer);
        synchronized(TftpClient.keyboardLock){
            TftpClient.keyboardLock.notifyAll();
        }
        return null;
    }

    private byte[] data(byte[] msg){
        if(TftpKeyboardThread.lastCommand == "RRQ"){
            byte[] blockNumberBytes = new byte[2];
            blockNumberBytes[0] = msg[4];
            blockNumberBytes[1] = msg[5];
            byte[] data = Arrays.copyOfRange(msg, 6,msg.length);
            incomingDataQueue.add(data);
            if(data.length < 512){
                Path newFile = Paths.get(TftpKeyboardThread.writingFileName);
                try (FileOutputStream fos = new FileOutputStream(newFile.toString())) {
                    while(!incomingDataQueue.isEmpty()){
                        fos.write(incomingDataQueue.remove());
                    }
            
                }catch(IOException ignored){}
                synchronized(TftpClient.keyboardLock){
                    TftpClient.keyboardLock.notifyAll();
                }
            
            }
            byte [] ack = new byte[]{0,4,blockNumberBytes[0],blockNumberBytes[1]};
            return ack;
        }
        else if(TftpKeyboardThread.lastCommand == "DIRQ"){
            byte[] blockNumberBytes = new byte[2];
            blockNumberBytes[0] = msg[4];
            blockNumberBytes[1] = msg[5];
            byte[] data = Arrays.copyOfRange(msg, 6,msg.length);
            incomingDataQueue.add(data);
            if(data.length < 512){
                StringBuilder result = new StringBuilder();
                while(!incomingDataQueue.isEmpty()){
                    result.append(new String(incomingDataQueue.remove(),StandardCharsets.UTF_8));
                }
                
                String resultString = result.toString();
                String [] fileNames = resultString.split(String.valueOf("\0"));
                
                for(String file:fileNames)
                    System.out.println(file);
                synchronized(TftpClient.keyboardLock){
                    TftpClient.keyboardLock.notifyAll();
                }
            
            }
            byte [] ack = new byte[]{0,4,blockNumberBytes[0],blockNumberBytes[1]};
            return ack;
        }
        return null;
    }

    private byte[] ack(byte[] msg){
        byte[] response = null;
        if(TftpKeyboardThread.lastCommand.equals("LOGRQ")){
            TftpKeyboardThread.loggedIn = true;
            synchronized(TftpClient.keyboardLock){
                TftpClient.keyboardLock.notifyAll();
            }
        }
        else if(TftpKeyboardThread.lastCommand.equals("DELRQ")){
            synchronized(TftpClient.keyboardLock){
                TftpClient.keyboardLock.notifyAll();
            }
        }
        else if(TftpKeyboardThread.lastCommand.equals("WRQ")){
            if(dataQueue.isEmpty()){
                synchronized(TftpClient.keyboardLock){
                    TftpClient.keyboardLock.notifyAll();
                }
            }
            else{
                response = dataQueue.remove();
            }
        }

        else if(TftpKeyboardThread.lastCommand.equals("DISC")){
            System.out.println("we in here");
            TftpClient.shouldTerminate = true;
            synchronized(TftpClient.keyboardLock){
                TftpClient.keyboardLock.notifyAll();
            }
        }

        byte [] byteShort = new byte[2];
        byteShort[0] = msg[2];
        byteShort[1] = msg[3];
        short blockNumber = byteToShort(byteShort);
        System.out.println("ACK " + blockNumber);
        return response;
    }

    
}