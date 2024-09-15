package bgu.spl.net.impl.tftp;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;


public class TftpListeningThread implements Runnable{

   boolean shouldTerminate = false;
    private BufferedOutputStream out;
    private BufferedInputStream in;
    private TftpEncoderDecoder encdec;
    private TftpClientProtocol protocol;
   
    public TftpListeningThread(BufferedInputStream in , BufferedOutputStream out ,TftpEncoderDecoder encdec,
     TftpClientProtocol protocol ){
        this.out = out;
        this.in = in;
        this.encdec = encdec;
        this.protocol = protocol;
       
    }
    public void run(){
        int read;
        byte[] response;
        try{
            while (!TftpClient.shouldTerminate && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if(nextMessage != null) {
                    response = protocol.process(nextMessage);
                    if(response!=null){
                        try{
                            out.write(encdec.encode(response));
                            out.flush();
                        }catch(IOException ex){
                            ex.printStackTrace();
                        }
                    }
                    
                    
                }
                
            }
        }
        catch (IOException ioex){}

        

    

    }

}
            
        



    

