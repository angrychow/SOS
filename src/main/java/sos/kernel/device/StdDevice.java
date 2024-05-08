package sos.kernel.device;

import sos.kernel.Constants;
import sos.kernel.Main;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.PCB;


import java.util.Scanner;
import java.io.*;
import java.lang.Thread;

import static sos.kernel.Main.*;

public class StdDevice extends AbstractDevice {
    @Override
    public   void PrintToOUT(String content) {
        writer.print(content);
        writer.flush();
    }
    @Override
    public void LoadDriver() throws Exception {
        var is = Main.class.getClassLoader().getResourceAsStream("keyboard.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");
        process = createProcess(scripts, cputick, "IOProcess");
        Status= DeviceStatus.AVAILABLE;
        intEntry=7;
        DeviceBufferSize=64;
        DeviceBuffer=new Object[DeviceBufferSize];
        var Std = new FileTreeNode();
        Std.DeviceName = "std";
        Std.Name = "std";
        Std.Type = FileTreeNode.FileType.DEVICES;
        FS.CreateFile("root/dev",Std);
        DeviceTable.add(this);
    }
    @Override
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
                process.RegisterCache[Constants.SP]=intEntry;
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
