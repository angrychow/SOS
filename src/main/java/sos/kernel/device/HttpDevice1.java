package sos.kernel.device;

import sos.kernel.Constants;
import sos.kernel.Main;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.PCB;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

import static sos.kernel.Main.*;

public class HttpDevice1 extends AbstractDevice{
    @Override
    public   void PrintToOUT(String content) {
        writer.print(content);
        writer.flush();
    }
    @Override
    public void LoadDriver() throws Exception {
        var is = Main.class.getClassLoader().getResourceAsStream("httpIO.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");
//        process = createProcess(scripts, cputick, "HttpIOProcess");
        Status= DeviceStatus.AVAILABLE;
        DeviceBufferSize=64;
        DeviceBuffer=new Object[DeviceBufferSize];
        intEntry=7;//驱动程序的中断写入口行，驱动程序在resources/httpIO.txt中
        var device = new FileTreeNode();
        device.DeviceName = "httpdevice1";
        device.Name = "httpdevice1";
        device.Type = FileTreeNode.FileType.DEVICES;
        FS.CreateFile("root/dev",device);
        node=device;
        DeviceTable.add(this);
        writer = new PrintWriter("httpdevice1.txt");
    }
}
