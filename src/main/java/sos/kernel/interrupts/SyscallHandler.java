package sos.kernel.interrupts;

import sos.kernel.Constants;
import sos.kernel.filesystem.FileTree;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.FileDescriptor;
import sos.kernel.models.FileTreeNode;
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
    public static FileTree FS;
    public static MMUController MMU;
    static public boolean Syscall(int number, PCB process, int CPUTick) throws Exception {
//        return true;
        switch(number) {
            case 3 -> { // syscall open. R1: address where storage file path. R2: Open mode. R3==-1: append, else cursor R4: fd numbers.
                var path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                }
                if(path instanceof String) {
                    var node = FS.FoundFile((String)path);
                    if(node == null) {
                        process.RegisterCache[Constants.R4] = -1;
                        return true;
                    }
                    var readable = (process.RegisterCache[Constants.R2] & 1) == 1;
                    var writable = (process.RegisterCache[Constants.R2] & 2) == 2;
                    var cursor = process.RegisterCache[Constants.R3] >= 0 ? Math.min(process.RegisterCache[Constants.R3], node.contents.length() )  : node.contents.length();
                    process.RegisterCache[Constants.R4] = FS.OpenFile((String)path, process, readable, writable, cursor).FDID;
                } else {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                }
                return true;
            }
            case 4 -> { // syscall close. R1: fd number
                FileDescriptor found = null;
                for(var fd : FS.FDTable) {
                    if(fd.FDID == process.RegisterCache[Constants.R1]) {
                        found = fd;
                    }
                }
                if(found == null) {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                } else {
                    if(FS.CloseFile(found)) {
                        process.RegisterCache[Constants.R4] = 1; // Success;
                    } else {
                        process.RegisterCache[Constants.R4] = -1; // Failed;
                    }
                }
                return true;
            }
            case 5 -> { // syscall read. R1: fd number. R2: size, R3: buffer addr.
                FileDescriptor found = null;
                for(var fd : FS.FDTable) {
                    if(fd.FDID == process.RegisterCache[Constants.R1]) {
                        found = fd;
                    }
                }
                if(found == null) {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                    return true;
                }
                FS.ReadFile(found, Math.min(found.FileNode.contents.length(), process.RegisterCache[Constants.R2]), process.RegisterCache[Constants.R3], process);
                return false;
            }
            case 6 -> { // syscall writes. R1: fd number. R2: content addr
                FileDescriptor found = null;
                for(var fd : FS.FDTable) {
                    if(fd.FDID == process.RegisterCache[Constants.R1]) {
                        found = fd;
                    }
                }
                if(found == null) {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                    return true;
                }
                FS.WriteFile(found, process.RegisterCache[Constants.R2], process);
                return false;
            }
            case 7 -> { // syscall delete. R1: file path string addr
                var path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                }
                if(path instanceof String) {
                    var success = FS.DeleteFile((String) path);
                    if(success) {
                        process.RegisterCache[Constants.R4] = 1; // Success;
                    } else {
                        process.RegisterCache[Constants.R4] = -1; // Failed;
                    }
                } else {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                }
                return true;
            }
            case 8 -> { // syscall mkdir. R1: file path string addr. R2: dirname.
                var path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                }
                var name = MMU.MemoryRead(process, process.RegisterCache[Constants.R2], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    name = MMU.MemoryRead(process, process.RegisterCache[Constants.R2], CPUTick);
                }
                if(path instanceof String && name instanceof String) {
                    var node = new FileTreeNode();
                    node.Name = (String)name;
                    node.Type = FileTreeNode.FileType.DIRECTORY;
                    var success = FS.CreateFile((String)path, node);
                    if(success) {
                        process.RegisterCache[Constants.R4] = 1; // Success;
                    } else {
                        process.RegisterCache[Constants.R4] = -1; // Failed;
                    }
                } else {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                }
                return true;
            }
            case 9 -> { // syscall create. R1: file path string addr. R2: filename.
                var path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    path = MMU.MemoryRead(process, process.RegisterCache[Constants.R1], CPUTick);
                }
                var name = MMU.MemoryRead(process, process.RegisterCache[Constants.R2], CPUTick);
                if(process.IntPageFault) {
                    process.IntPageFault = false;
                    MMU.PageReenter(process, process.IntVirAddr, CPUTick);
                    name = MMU.MemoryRead(process, process.RegisterCache[Constants.R2], CPUTick);
                }
                if(path instanceof String && name instanceof String) {
                    var node = new FileTreeNode();
                    node.Name = (String)name;
                    node.Type = FileTreeNode.FileType.FILE;
                    var success = FS.CreateFile((String)path, node);
                    if(success) {
                        process.RegisterCache[Constants.R4] = 1; // Success;
                    } else {
                        process.RegisterCache[Constants.R4] = -1; // Failed;
                    }
                } else {
                    process.RegisterCache[Constants.R4] = -1; // Failed;
                }
                return true;
            }
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
                    interruptVector.SharedMemoryReleaseRelativeBlockID.add(sharedNumber);
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
