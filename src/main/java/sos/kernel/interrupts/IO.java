package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.device.AbstractDevice;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.PCB;
import sos.kernel.models.RWInterrupt;

import static sos.kernel.Main.DeviceTable;
import static sos.kernel.Main.stdDevice;

public class IO {
    public static boolean IOService(RWInterrupt rwInterrupt, MMUController controller, int CPUTick) throws Exception {
        AbstractDevice device = null;
        if (rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES) {
            for (var device1 : DeviceTable) {
                if (device1.DeviceName.equals(rwInterrupt.Node.DeviceName)) {
                    device = device1;
                    break;
                }
            }
        } else {
            device = null;
        }
        if (rwInterrupt.Type == RWInterrupt.RWType.READ) {//Read From File, And then Write to Memory or Device
//            System.out.println(rwInterrupt.Node);
//            System.out.println("read");
            if(rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES && rwInterrupt.Node.readContents().isEmpty()) {
                return false;
            }
            var Content = "";
            if(rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES) {
                Content = rwInterrupt.Node.readContents();
                rwInterrupt.Node.writeContents("");
            } else {
                Content = rwInterrupt.Node.readContents().substring(rwInterrupt.cursor, rwInterrupt.cursor + rwInterrupt.size);
            }
            var success = controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
            if (rwInterrupt.Process.IntPageFault) {
                controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
                rwInterrupt.Process.IntPageFault = false;
            }
        } else {//Write to File, Read from Memory or Device
            Object Content = null;

            Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.WriteAddr, CPUTick);
            if (rwInterrupt.Process.IntPageFault) {
                controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.ReadAddr, CPUTick);
                rwInterrupt.Process.IntPageFault = false;
            }
            if (Content != null) {
                if(rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES) {
                    if(device != null) {
                        device.PrintToOUT(Content.toString());
                    }
                } else {
                    StringBuilder stringBuilder = new StringBuilder(rwInterrupt.Node.readContents());
                    stringBuilder.insert(rwInterrupt.cursor, Content);
                    rwInterrupt.Node.Link.cursor += Content.toString().length();
                    rwInterrupt.Node.writeContents(stringBuilder.toString());
                }
            }
            System.out.println(rwInterrupt.Node);
        }
        rwInterrupt.Process.ProcessState = PCB.State.READY;
        rwInterrupt.Process.RegisterCache[Constants.SP]++;
        return true;
    }
}
