package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.models.PCB;

public class Timer {
    public PCB RelativeProcess;
    public int WakeUpCPUTime;

    public Timer(PCB relativeProcess, int wakeUpCPUTime) {
        RelativeProcess = relativeProcess;
        WakeUpCPUTime = wakeUpCPUTime;
    }

    public static void TimerInterruptService(PCB process) {
        process.ProcessState = PCB.State.READY;
        process.RegisterCache[Constants.SP] ++;
    }
}
