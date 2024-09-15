package bgu.spl.net.impl.tftp;

import java.util.LinkedList;
import java.util.List;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
   
    private List<Byte> bytes = new LinkedList<Byte>();
    private byte[] msg;
    private short requestType = -1;
    private short messageLength = -1;
    boolean finished = false;


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        finished = false;
        bytes.add(nextByte);
        if(bytes.size() <= 2){   
            if(bytes.size() == 2){
                byte [] byteNum = new byte[2];
                byteNum[0] = bytes.get(0);
                byteNum[1] = bytes.get(1);
                requestType = byteToShort(byteNum);
                
            }
        }
        if(requestType!=-1){
            if(requestType == 1 || requestType == 2 || requestType == 5
             || requestType == 7 || requestType == 8 || requestType == 9 ){
                if(bytes.get(bytes.size()-1) == (byte)0){
                    finished = true;
                }
            }
            else if(requestType == 3){
                if(bytes.size() == 4){
                    byte [] byteNum = new byte[2];
                    byteNum[0] = bytes.get(2);
                    byteNum[1] = bytes.get(3);
                    messageLength = byteToShort(byteNum);
                    messageLength+=6;
                }
                if(bytes.size() == messageLength && messageLength!=-1){
                    finished = true;
                }
            }
            else if(requestType == 4){
                if(bytes.size() == 4)
                    finished = true;
            }
            else if(requestType == 6 || requestType == 10){
                finished = true;                    
            }

        }
        if(finished){
            msg = new byte[bytes.size()];
            int i = 0;
            for(byte b:bytes){
                msg[i] = b;
                i++;
            }
            bytes.clear();
            requestType = -1;
            return msg;

        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private short byteToShort(byte[] bytes){
        short b_short = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        return b_short;
    } 
    

    
}