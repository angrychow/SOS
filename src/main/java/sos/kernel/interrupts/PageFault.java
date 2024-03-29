package sos.kernel.interrupts;

import sos.kernel.mmu.MMUController;
import sos.kernel.models.PCB;

public class PageFault {
    public PCB RelativeProcess;
    public static MMUController controller;

    public PageFault(PCB pcb) {
        RelativeProcess = pcb;
    }
    public static void PageFaultServices(PCB relativeProcess, int cputick) throws Exception {
        relativeProcess.IntPageFault = false;
        controller.PageReenter(relativeProcess, relativeProcess.IntVirAddr, cputick);
        relativeProcess.ProcessState = PCB.State.READY;
    }
}
