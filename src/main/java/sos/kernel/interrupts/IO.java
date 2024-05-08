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
    public static void IOService(RWInterrupt rwInterrupt, MMUController controller, int CPUTick) throws Exception {
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
            var Content = "";
            Content = rwInterrupt.Node.contents.substring(rwInterrupt.cursor, rwInterrupt.cursor + rwInterrupt.size);
            if (rwInterrupt.Node.Type != FileTreeNode.FileType.DEVICES) {
                var success = controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
                if (rwInterrupt.Process.IntPageFault) {
                    controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                    controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
                    rwInterrupt.Process.IntPageFault = false;
                }
            } else {
//                stdDevice.PrintToOUT(Content);
                device.PrintToOUT(Content);
            }
        } else {//Write to File, Read from Memory or Device
            Object Content = null;
            if (rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES) {
                Content = device.GetFromIN();
            } else {
                Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.WriteAddr, CPUTick);
                if (rwInterrupt.Process.IntPageFault) {
                    controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                    Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.ReadAddr, CPUTick);
                    rwInterrupt.Process.IntPageFault = false;
                }
            }
            if (Content != null) {
                StringBuilder stringBuilder = new StringBuilder(rwInterrupt.Node.contents);
                stringBuilder.insert(rwInterrupt.cursor, Content);
                rwInterrupt.Node.Link.cursor += Content.toString().length();
                rwInterrupt.Node.contents = stringBuilder.toString();
                if (rwInterrupt.Node.Type == FileTreeNode.FileType.DEVICES) {
                    device.PrintToOUT(Content.toString());
                }
            }
            System.out.println(rwInterrupt.Node);
        }
        rwInterrupt.Process.ProcessState = PCB.State.READY;
        rwInterrupt.Process.RegisterCache[Constants.SP]++;
    }
}
