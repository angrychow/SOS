package sos.kernel.models;

import sos.kernel.interrupts.SharedMemoryBlocked;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class InterruptVector {
    public boolean SharedMemoryRelease;
    public ArrayList<Integer> SharedMemoryReleaseRelativeBlockID = new ArrayList<Integer>();
    public boolean PageInterrupt;
    public int PageInterruptRelatedPCBID;
    public boolean TimerInterrupt;
    public int TimerInterruptRelatedPCBID;
    public int TimerInterruptRelatedTimerID;
    public boolean IOInterrupt;
    public int IOInterruptRelatedDeviceID;
    public int IOInterruptRelatedPCBID;
    public ArrayList<RWInterrupt> RWQueue;
    public InterruptVector() {
        PageInterrupt = false;
        TimerInterrupt = false;
        IOInterrupt = false;
        SharedMemoryRelease = false;
    }
}
