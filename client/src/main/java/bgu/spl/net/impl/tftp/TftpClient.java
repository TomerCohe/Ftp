package bgu.spl.net.impl.tftp;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import java.io.IOException;

import java.net.Socket;

public class TftpClient {
    public static Object keyboardLock = new Object();
    public static boolean shouldTerminate = false;  
    
    public static void main(String[] args) {
        shouldTerminate = false;
        System.out.println("started");
        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());){   
        System.out.println("connected to server");
        TftpEncoderDecoder encdec = new TftpEncoderDecoder();           
        Thread keyboardThread = new Thread(new TftpKeyboardThread(out,encdec));
        Thread listeningThread = new Thread(new TftpListeningThread(in,out,encdec,new TftpClientProtocol()));
        
        listeningThread.start();
        keyboardThread.start();
        
        
        try{
            keyboardThread.join();
            listeningThread.join();
        }catch(InterruptedException ignored){}
        System.out.println("closing program");
        
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
    }
}
    

