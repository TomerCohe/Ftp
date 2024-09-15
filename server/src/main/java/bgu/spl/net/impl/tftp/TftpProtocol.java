package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;
import java.util.Map;


class holder{
    public static ConcurrentHashMap<Integer,String> logged_in = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String,Object> files = new ConcurrentHashMap<>();
    public static void initiallizeFiles(){
        File folder = new File("../server" , "Files");
        if (folder.exists() && folder.isDirectory()) {
            System.out.println("good start");
            File[] fileList = folder.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile()) {
                        files.put(file.getName(), new Object());
                    }
                }
            } else {
                System.out.println("No files found in the folder.");
            }
        } else {
            System.out.println("Folder does not exist or is not a directory.");
        }
    }
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>{
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean shouldTerminate = false;
    private ConcurrentLinkedQueue<byte[]> dataQueue;
    private ConcurrentLinkedQueue<byte[]> incomingDataQueue;
    private String writingFileName;
    public static Object checkingFile = new Object();
    
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        holder.logged_in.put(connectionId,"");
        this.connections = connections;
        this.connectionId = connectionId;
        this.dataQueue = new ConcurrentLinkedQueue<byte[]>();
        this.incomingDataQueue = new ConcurrentLinkedQueue<byte[]>();
        
        
    }

    @Override
    public void process(byte[] message) {
        byte [] byteNum = new byte[2];
        byteNum[0] = message[0];
        byteNum[1] = message[1];
        short requestType = byteToShort(byteNum);
        if(requestType == 7 || requestType == 10){
            if(requestType == 7)
                loginRequest(message);
            else if(requestType == 10)
                disc();
        }
        else{
            if(holder.logged_in.get(connectionId)!=""){
                if(requestType == 1){
                    readRequest(message);
                }
                else if(requestType == 2){
                    writeRequest(message);
                }
                else if(requestType == 3){
                    data(message);
                }
                else if(requestType == 4){
                    ack();
                }
                else if(requestType == 6){
                    dirq();
                }
                else if(requestType == 8){
                    deleteRequest(message);
                }
                
            }
            else{
                String errorMsg = "User not logged in";
                byte[] errorMsgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
                byte [] response = new byte[errorMsgBytes.length+5];
                response[0] = 0;
                response[1] = 5;
                response[2] = 0;
                response[3] = 6;
                for(int i = 0 ; i < errorMsgBytes.length;i++){
                    response[i+4] = errorMsgBytes[i];
                }
                response[response.length-1] = 0;
                System.out.println("sending not logged in error");
                connections.send(connectionId, response);
            
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        if(shouldTerminate){
            this.connections.disconnect(connectionId);
            holder.logged_in.remove(connectionId);   
        }
        return shouldTerminate;
        
    } 

    public static short byteToShort(byte[] bytes){
        short b_short = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        return b_short;
    } 

    public static byte[] shortToByte(short num){
        byte [] a_bytes = new byte []{( byte ) ( num >> 8) , ( byte ) ( num & 0xff ) };
        return a_bytes;
    
        
    }
    private void arrayToDataPackets(byte[] data){
        int numChunks = (int)Math.ceil(((double)data.length + 1)/512);
        for (int i = 0; i < numChunks; i++) {
            int startIdx = i * 512;
            int endIdx = Math.min(startIdx + 512, data.length);
            int length = endIdx - startIdx;

            byte[] chunk = new byte[length+6];
            chunk[0] = 0;
            chunk[1] = 3;
                
            byte[] packetSize = shortToByte((short)length);
            chunk[2] = packetSize[0];
            chunk[3] = packetSize[1];

            byte[] blockNumber = shortToByte((short)(i + 1));
            chunk[4] = blockNumber[0];
            chunk[5] = blockNumber[1];

            if(length!=0){
                for(int j = startIdx,t = 6; j < endIdx; j++,t++){
                    chunk[t] = data[j];
                }
            }
            dataQueue.add(chunk);
        }
    }
    
    private byte[] fileToByteArray(File file){
        byte [] bytes = null;
        try{
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }

    private void readRequest(byte[] message){
        String fileName = new String(message,2,(message.length-3),StandardCharsets.UTF_8);
        String filePath = "Files";
        File file = new File(filePath,fileName);
        byte [] fileData = null;
        if(file.exists()){
            synchronized(holder.files.get(fileName)){
                if(file.exists()){
                    fileData = fileToByteArray(file);
                    arrayToDataPackets(fileData);
                    connections.send(connectionId, dataQueue.remove());
                }
            }
        }
        else{
            String errorMsg = "File not found";
            byte[] errorMsgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
            byte[] response = new byte[errorMsgBytes.length+5];
            response[0] = 0;
            response[1] = 5;
            response[2] = 0;
            response[3] = 1;
            for(int i = 0 ; i < errorMsgBytes.length;i++){
                response[i+4] = errorMsgBytes[i];
            }
            response[response.length-1] = 0;
            connections.send(connectionId, response);
        }
    }

    private void writeRequest(byte[] message){
        String fileName = new String(message,2,(message.length-3),StandardCharsets.UTF_8);
        byte[] response = null;
        boolean fileExists = false;
        synchronized(checkingFile){
            fileExists = holder.files.containsKey(fileName);
            if(!fileExists)
                holder.files.put(fileName, new Object());
        }
        if(fileExists){
            String errorMsg = "File already exists";
            byte[] errorMsgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
            response = new byte[errorMsgBytes.length+5];
            response[0] = 0;
            response[1] = 5;
            response[2] = 0;
            response[3] = 5;
            for(int i = 0 ; i < errorMsgBytes.length;i++){
                response[i+4] = errorMsgBytes[i];
            }
            response[response.length-1] = 0;
            
        }
        else{
            writingFileName = fileName;
            response = new byte[]{0,4,0,0};
        }
        connections.send(connectionId, response);
    }

    private void loginRequest(byte[] message){
        String userName = new String(message,2,(message.length-3),StandardCharsets.UTF_8);
        byte[] response;
        if(holder.logged_in.contains(userName)){
            
            String errorMsg = "User already logged in";
            byte[] errorMsgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
            response = new byte[errorMsgBytes.length+5];
            response[0] = 0;
            response[1] = 5;
            response[2] = 0;
            response[3] = 7;
            for(int i = 0 ; i < errorMsgBytes.length;i++){
                response[i+4] = errorMsgBytes[i];
            }
            response[response.length-1] = 0;
            
            
        }
        else{
            holder.logged_in.put(connectionId,userName);
            response = new byte[]{0,4,0,0};
        }
        
        connections.send(connectionId, response);
        
    }

    private void bCast(String fileName,boolean deleted){
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[fileNameBytes.length+4];
        packet[0] = 0;
        packet[1] = 9;
        if(deleted)
            packet[2] = 0;
        else
            packet[2] = 1;
        for(int i = 0 ; i < fileNameBytes.length ; i++){
            packet[i+3] = fileNameBytes[i];
        }
        packet[packet.length-1] = 0;
        for(Map.Entry<Integer, String> entry : holder.logged_in.entrySet()){
            if(entry.getValue()!=null){
                connections.send(entry.getKey(), packet);
            }
        }
    }

    private void ack(){
        if(!dataQueue.isEmpty())
            connections.send(connectionId,dataQueue.remove());
    }

    private void data(byte[] message){
        boolean fileRecived = false;
        byte[] blockNumberBytes = new byte[2];
        blockNumberBytes[0] = message[4];
        blockNumberBytes[1] = message[5];
        byte[] data = Arrays.copyOfRange(message, 6,message.length);
        incomingDataQueue.add(data);
        if(data.length < 512){
            fileRecived = true;
            Path newFile = Paths.get("Files", writingFileName);
            try (FileOutputStream fos = new FileOutputStream(newFile.toString())) {
                while(!incomingDataQueue.isEmpty()){
                    fos.write(incomingDataQueue.remove());
                }
            
            }catch(IOException ignored){}
            
            
        }
        byte [] ack = new byte[]{0,4,blockNumberBytes[0],blockNumberBytes[1]};
        connections.send(connectionId,ack);
        if(fileRecived)
            bCast(writingFileName, false);
        
    }
    private void dirq(){
        byte[] response = new byte[0];
        byte[] fileName = null;
        String filePath = "Files";
        for(Map.Entry<String, Object> entry : holder.files.entrySet()){
            fileName = entry.getKey().getBytes(StandardCharsets.UTF_8);
            File file = new File(filePath,entry.getKey());
            if(file.exists()){
                synchronized(holder.files.get(entry.getKey())){
                    if(file.exists()){
                        if(response.length!=0)
                            response = arrayAppend(response, new byte[]{0});
                        response = arrayAppend(response, fileName);
                    }
                }
            }
        }
        arrayToDataPackets(response);
        System.out.println("sending first dirq packet");
        connections.send(connectionId, dataQueue.remove());
    }

    public static byte[] arrayAppend(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    private void deleteRequest(byte[] message){
        String fileName = new String(message,2,(message.length-3),StandardCharsets.UTF_8);
        String filePath = "Files";
        File file = new File(filePath,fileName);
       
        if(file.exists()){
            synchronized(holder.files.get(fileName)){
                if(file.exists())
                    file.delete(); 
            }

            holder.files.remove(fileName);
            byte[] ack = new byte[]{0,4,0,0};
            connections.send(connectionId,ack);
            bCast(fileName, true);
        }
        else{
            String errorMsg = "File not found";
            byte[] errorMsgBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
            byte [] response = new byte[errorMsgBytes.length+5];
            response[0] = 0;
            response[1] = 5;
            response[2] = 0;
            response[3] = 1;
            for(int i = 0 ; i < errorMsgBytes.length;i++){
                response[i+4] = errorMsgBytes[i];
            }
            response[response.length-1] = 0;    
        }
    }
    
    private void disc(){
        shouldTerminate = true;
        byte[] ack = new byte[]{0,4,0,0};
        connections.send(connectionId,ack);
    }
}
