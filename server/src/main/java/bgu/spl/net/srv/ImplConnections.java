package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ImplConnections implements Connections<byte[]> {
    
    private ConcurrentHashMap<Integer,BlockingConnectionHandler<byte[]>> connections = new ConcurrentHashMap<>();
    
    public void connect(int connectionId, BlockingConnectionHandler<byte[]> handler){
        connections.put(connectionId,handler);
    }

    public boolean send(int connectionId, byte[] msg){
        if(connections.get(connectionId)!=null){
            BlockingConnectionHandler<byte[]> handler = connections.get(connectionId);
            handler.send(msg);
            return true;
        }
        return false;
        
    }

    public void disconnect(int connectionId){
        connections.remove(connectionId);
    }
}

