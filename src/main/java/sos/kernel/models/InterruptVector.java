package sos.kernel.models;

public class InterruptVector {
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
    }
}
