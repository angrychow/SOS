package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.filesystem.DeviceTable;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.PCB;
import sos.kernel.models.RWInterrupt;

public class IO {
    public static void IOService(RWInterrupt rwInterrupt, MMUController controller, int CPUTick) throws Exception {
        if (rwInterrupt.Type == RWInterrupt.RWType.READ) {//Read From File, And then Write to Memory or Device
            var Content = "";
            Content = rwInterrupt.Node.contents.substring(rwInterrupt.cursor, rwInterrupt.cursor + rwInterrupt.size);
            if (!rwInterrupt.Node.DeviceName.equals("std")) {
                var success = controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
                if (rwInterrupt.Process.IntPageFault) {
                    controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                    controller.MemoryWrite(rwInterrupt.Process, rwInterrupt.ReadAddr, Content, CPUTick);
                    rwInterrupt.Process.IntPageFault = false;
                }
            } else {
                DeviceTable.PrintToSTDOUT(Content);
            }
        } else {//Write to File, Read from Memory or Device
            Object Content = null;
            if (rwInterrupt.Node.DeviceName.equals("std")) {
                Content = DeviceTable.GetFromSTDIN();
            } else {
                Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.WriteAddr, CPUTick);
                if (rwInterrupt.Process.IntPageFault) {
                    controller.PageReenter(rwInterrupt.Process, rwInterrupt.Process.IntVirAddr, CPUTick);
                    Content = controller.MemoryRead(rwInterrupt.Process, rwInterrupt.ReadAddr, CPUTick);
                    rwInterrupt.Process.IntPageFault = false;
                }
            }
            if(Content!=null){
                StringBuilder stringBuilder = new StringBuilder(rwInterrupt.Node.contents);
                stringBuilder.insert(rwInterrupt.cursor, Content);
                rwInterrupt.Node.Link.cursor += Content.toString().length();
                rwInterrupt.Node.contents = stringBuilder.toString();
                if(rwInterrupt.Node.DeviceName.equals("std")) {
                    DeviceTable.PrintToSTDOUT(Content.toString());
                }
            }
            System.out.println(rwInterrupt.Node);
        }
        rwInterrupt.Process.ProcessState = PCB.State.READY;
        rwInterrupt.Process.RegisterCache[Constants.SP]++;
    }
}
