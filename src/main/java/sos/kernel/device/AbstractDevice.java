package sos.kernel.device;

import sos.kernel.models.PCB;

import java.io.IOException;
import java.io.PrintWriter;

public abstract class AbstractDevice extends Thread{
    public String DeviceName;
    public DeviceStatus Status=DeviceStatus.UNAVAILABLE;
    public  Object[] DeviceBuffer;
    public  int DeviceBufferSize = 64;
    public int count=0;
    public  int head=0;
    public int tail=0;
    public int intEntry;
    public PrintWriter writer;
    public  PCB process;
    public  abstract  void PrintToOUT(String content) ;
    public  Object GetFromIN() {
        if(count==0){
            return null;
        }else{
            count--;
            Object temp= DeviceBuffer[head++];
            head%=DeviceBufferSize;
            return temp;
        }
    }
    public abstract void LoadDriver() throws Exception;

}
