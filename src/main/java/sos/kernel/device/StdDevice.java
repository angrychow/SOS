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
        System.out.println("[Message Received]: " + content);
        writer.print(content);
        writer.flush();
    }
    @Override
    public void LoadDriver() throws Exception {
//        var is = Main.class.getClassLoader().getResourceAsStream("keyboard.txt");
//        var buffer = is.readAllBytes();
//        is.close();
//        var scriptsRaw = new String(buffer);
//        var scripts = scriptsRaw.split("\n");
        process = null;
        Status= DeviceStatus.AVAILABLE;
//        intEntry=7;
        DeviceBufferSize=64;
        DeviceBuffer=new Object[DeviceBufferSize];
        this.node = new FileTreeNode();
        this.node.DeviceName = "std";
        this.node.Name = "std";
        this.node.Type = FileTreeNode.FileType.DEVICES;
        FS.CreateFile("root/dev",this.node);
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
            if(scanner.hasNext()) {
                node.appendContents(scanner.next());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
