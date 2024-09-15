package bgu.spl.net.impl.tftp;


import bgu.spl.net.srv.ImplConnections;
import bgu.spl.net.srv.Server;




public class TftpServer {
   public static void main(String[] args) {
        ImplConnections connections = new ImplConnections();
        holder.initiallizeFiles();
        // you can use any server... 
        Server.threadPerClient(
                Integer.parseInt(args[0]), //port
                TftpProtocol::new, //protocol factory
                TftpEncoderDecoder::new, //message encoder decoder factory
                connections
                 
        ).serve();
        
    }
}
