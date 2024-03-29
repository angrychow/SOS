package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.models.PCB;

import java.util.ArrayList;

public class SyscallHandler {

    public static ArrayList<PCB> Tasks;
    public static ArrayList<Timer> Timers;
    public static ArrayList<PageFault> PageFaults;
    static public boolean Syscall(int number, PCB process, int CPUTick) {
//        return true;
        switch(number) {
            case 10 -> {

                process.ProcessState = PCB.State.WAITING;
                Timers.add(new Timer(process, CPUTick + process.RegisterCache[Constants.R1]));
                return false;
            }
            default -> {
                return true;
            }
        }
    }
}
