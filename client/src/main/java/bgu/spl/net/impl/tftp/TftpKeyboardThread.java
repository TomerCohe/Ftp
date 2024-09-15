
package bgu.spl.net.impl.tftp;
import java.util.Scanner;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;

public class TftpKeyboardThread implements Runnable{
    public static boolean loggedIn = false;
    public static String lastCommand = "";
    public static String writingFileName = "";
    
    private BufferedOutputStream out;
    private Scanner scanner;
    private TftpEncoderDecoder encdec;

    public TftpKeyboardThread(BufferedOutputStream out, TftpEncoderDecoder encdec){
        this.out = out;
        this.encdec = encdec;
        this.scanner = new Scanner(System.in);
    }
    public void run(){
        while(!TftpClient.shouldTerminate){//need to run until we do a successful disconnect 
               String command = scanner.next();
               String rest = scanner.nextLine();
               if(rest.length() > 1)
                    rest = rest.substring(1);
            if (command.equals("LOGRQ"))
                            LOGRQ(command , rest);
            else if (command.equals("DELRQ"))
                            DELRQ(command , rest);
            else if (command.equals("RRQ")) 
                            RRQ(command , rest);
            else if (command.equals("WRQ"))  
                            WRQ(command , rest);
            else if (command.equals("DIRQ")) 
                            DIRQ(command , rest);
            else if (command.equals("DISC"))                 
                            DISC(command , rest);
            else {
                System.out.println("INVALID COMMAND , TRY AGAIN ");
            }                

                
                
            
        }
    }


    private void LOGRQ(String command , String rest){
        byte[] toServer;
        byte[]  byteRest;
        
        if (!rest.isEmpty() && !rest.contains("0") && !rest.contains(" ")){
            toServer = new byte[3 + rest.length()];
            byteRest = rest.getBytes(StandardCharsets.UTF_8);
            toServer[0]= 0;
            toServer[1]= 7;
            toServer[toServer.length-1]=0;


            for ( int i = 0 ; i < byteRest.length  ; i++ ){
                    toServer[i+2] = byteRest[i];
            }
            lastCommand = "LOGRQ";
            sendAndWait(toServer);
       }
       else  System.out.println("INVALID COMMAND , TRY AGAIN1 ");
    }
    
    private void DELRQ(String command , String rest ){
        byte[] toServer;
        byte[]  byteRest;
        if (!rest.isEmpty() && !rest.contains("0") ){
            toServer = new byte[3 + rest.length()];
            byteRest = rest.getBytes(StandardCharsets.UTF_8);
            toServer[0]= 0;
            toServer[1]= 8;
            toServer[toServer.length-1]=0;


            for ( int i = 0 ; i < byteRest.length  ; i++ ){
                toServer[i+2] = byteRest[i];
            }
            lastCommand = "DELRQ";
            sendAndWait(toServer);
        }
        else  System.out.println("INVALID COMMAND , TRY AGAIN2 ");
    }
    private void RRQ(String command , String rest ){
              
        byte[] toServer;
        byte[]  byteRest;
        if (!rest.isEmpty() && !rest.contains("0") ){
            File file = new File("../client",rest);
            if(!file.exists()){
                toServer = new byte[3 + rest.length()];
                byteRest = rest.getBytes(StandardCharsets.UTF_8);
                toServer[0]= 0;
                toServer[1]= 1;
                toServer[toServer.length-1] = 0;
                for ( int i = 0 ; i < byteRest.length  ; i++ ){
                    toServer[i+2] = byteRest[i];
                }
                lastCommand = "RRQ";
                writingFileName = rest;
                sendAndWait(toServer);
            }
            else{
                System.out.println("FILE ALREADY EXISTS IN LOCAL DIRECTORY");
            }
        }
        else  System.out.println("INVALID COMMAND , TRY AGAIN3 ");
    }
    private void WRQ(String command , String rest ){
        byte[] toServer;
        byte[]  byteRest;
        if (!rest.isEmpty() && !rest.contains("0") ){
            File file = new File("../client",rest);
            if(file.exists()){
                toServer = new byte[3 + rest.length()];
                byteRest = rest.getBytes(StandardCharsets.UTF_8);
                toServer[0]= 0;
                toServer[1]= 2;
                toServer[toServer.length-1]=0;
                for ( int i = 0 ; i < byteRest.length  ; i++ ){
                    toServer[i+2] = byteRest[i];
                }
                lastCommand = "WRQ";
               
                byte[] fileData = fileToByteArray(file);
                arrayToDataPackets(fileData);
                sendAndWait(toServer);
            }
            else{
                System.out.println("FILE DOES NOT EXIST");
            }
            
        }
        else  System.out.println("INVALID COMMAND , TRY AGAIN4 ");

    }
    private void DIRQ(String command , String rest ){
        byte[] toServer;
        
        if (rest.isEmpty()){
            toServer= new byte[]{0 , 6};
            lastCommand = "DIRQ";
            sendAndWait(toServer);
            
        }
        else  System.out.println("INVALID COMMAND , TRY AGAIN 5");
    }
    private void DISC(String command , String rest ){
        byte[] toServer;
        
        if (rest.isEmpty()){
            toServer= new byte[]{0 , 10};
            lastCommand = "DISC";
            if(loggedIn)
                sendAndWait(toServer);
            else{
                try{
                    out.write(encdec.encode(toServer));
                    out.flush();
                } catch (IOException e){};
                TftpClient.shouldTerminate = true;
            }   

        }
        else  System.out.println("INVALID COMMAND , TRY AGAIN6 ");
    }

    private void sendAndWait(byte[] toServer){
        try{
            out.write(encdec.encode(toServer));
            out.flush();
        } catch (IOException e){};
        synchronized(TftpClient.keyboardLock){
            try{
                TftpClient.keyboardLock.wait();
            }catch (InterruptedException ignored){}
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
            TftpClientProtocol.dataQueue.add(chunk);
        }
    }

    public static byte[] shortToByte(short num){
        byte [] a_bytes = new byte []{( byte ) ( num >> 8) , ( byte ) ( num & 0xff ) };
        return a_bytes;   
    }
}


     
        




      

    
