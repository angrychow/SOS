package sos.kernel.interrupts;

import sos.kernel.models.PCB;

public class SharedMemoryBlocked {
    public PCB RelativeProcess;
    public SharedMemory RelativeSharedMemory;
    public static void SharedMemoryReleaseService(PCB relativeProcess) {
        relativeProcess.ProcessState = PCB.State.READY;
    }
    public SharedMemoryBlocked(PCB relativeProcess, SharedMemory relativeSharedMemory) {
        RelativeProcess = relativeProcess;
        RelativeSharedMemory = relativeSharedMemory;
    }
}
