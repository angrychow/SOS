package sos.kernel.models;

import sos.kernel.interrupts.SharedMemoryBlocked;

public class InterruptVector {
    public boolean SharedMemoryRelease;
    public int SharedMemoryReleaseRelativeBlockID;
    public boolean PageInterrupt;
    public int PageInterruptRelatedPCBID;
    public boolean TimerInterrupt;
    public int TimerInterruptRelatedPCBID;
    public int TimerInterruptRelatedTimerID;
    public boolean IOInterrupt;
    public int IOInterruptRelatedDeviceID;
    public int IOInterruptRelatedPCBID;
    public InterruptVector() {
        PageInterrupt = false;
        TimerInterrupt = false;
        IOInterrupt = false;
        SharedMemoryRelease = false;
    }
}
