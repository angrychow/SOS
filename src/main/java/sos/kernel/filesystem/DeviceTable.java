package sos.kernel.filesystem;

import sos.kernel.Constants;
import sos.kernel.interrupts.SyscallHandler;
import sos.kernel.models.PCB;


import java.util.Scanner;
import java.io.*;
import java.lang.Thread;

import static sos.kernel.Main.cputick;

public class DeviceTable extends  Thread{
    public static Object[] DeviceBuffer;
    public static final int DeviceBufferSize = 64;
    private static int count=0;
    private static int head=0,tail=0;
    private static PrintWriter writer;
    public static PCB process;
    public static void PrintToSTDOUT(String content) {
        writer.print(content);
        writer.flush();
    }
    //TODO: STDIN 异步实现
    public boolean isNewObject(){
        return count>0;
    }
    public static Object GetFromSTDIN() {
        if(count==0){
            return null;
        }else{
            count--;
            Object temp= DeviceBuffer[head++];
            head%=DeviceBufferSize;
            return temp;
        }
    }
    public void run() {
        Scanner scanner = new Scanner(System.in);
        try {
            writer = new PrintWriter("output.txt");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        while(true) {
            if(count<DeviceBufferSize && scanner.hasNext()){
                DeviceBuffer[tail++] = scanner.next();
                tail%=DeviceBufferSize;
                count++;
                process.RegisterCache[Constants.SP]=7;
                process.ProcessState=PCB.State.READY;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
