package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;

import java.util.ArrayList;
import java.util.HashMap;

public class SyscallHandler {

    public static ArrayList<PCB> Tasks;
    public static ArrayList<Timer> Timers;
    public static ArrayList<PageFault> PageFaults;
    public static HashMap<Integer, SharedMemory> SharedMemoryMap;
    public static ArrayList<SharedMemoryBlocked> SharedMemoryBlocks;
    public static InterruptVector interruptVector;
    static public boolean Syscall(int number, PCB process, int CPUTick) {
//        return true;
        switch(number) {
            case 10 -> {
                process.ProcessState = PCB.State.WAITING;
                Timers.add(new Timer(process, CPUTick + process.RegisterCache[Constants.R1]));
                return false;
            }
            case 11 -> { // read shared memory
                var sharedNumber = process.RegisterCache[Constants.R1];
                var sharedNumberAddress = process.RegisterCache[Constants.R2];
                var sharedMemory = SharedMemoryMap.computeIfAbsent(sharedNumber, k -> new SharedMemory(sharedNumber));
                if(sharedMemory.Owner == 0 || sharedMemory.Owner == process.PCBID) {
                    sharedMemory.Owner = process.PCBID;
                    System.out.printf("[Shared Memory %d] Address %d: %d\n",sharedMemory.SharedMemoryID, sharedNumberAddress, sharedMemory.Memory[sharedNumberAddress]);
                    process.RegisterCache[Constants.R4] = sharedMemory.Memory[sharedNumberAddress];
                    return true;
                } else {
                    process.ProcessState = PCB.State.WAITING;
                    SharedMemoryBlocks.add(new SharedMemoryBlocked(process, sharedMemory));
                    return false;
                }
            }
            case 12 -> { // write shared memory
                var sharedNumber = process.RegisterCache[Constants.R1];
                var sharedNumberAddress = process.RegisterCache[Constants.R2];
                var sharedContent = process.RegisterCache[Constants.R3];
                var sharedMemory = SharedMemoryMap.computeIfAbsent(sharedNumber, k -> new SharedMemory(sharedNumber));
                if(sharedMemory.Owner == 0 || sharedMemory.Owner == process.PCBID) {
                    sharedMemory.Owner = process.PCBID;
                    sharedMemory.Memory[sharedNumberAddress] = sharedContent;
                    return true;
                } else {
                    process.ProcessState = PCB.State.WAITING;
                    SharedMemoryBlocks.add(new SharedMemoryBlocked(process, sharedMemory));
                    return false;
                }
            }
            case 13 -> { // 释放信号量
                var sharedNumber = process.RegisterCache[Constants.R1];
                var sharedMemory = SharedMemoryMap.computeIfAbsent(sharedNumber, k -> new SharedMemory(sharedNumber));
                if(sharedMemory.Owner == 0 || sharedMemory.Owner == process.PCBID) {
                    interruptVector.SharedMemoryRelease = true;
                    interruptVector.SharedMemoryReleaseRelativeBlockID = sharedNumber;
                    sharedMemory.Owner = 0;
                } else {
                    process.RegisterCache[Constants.R4] = -1;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }
}
