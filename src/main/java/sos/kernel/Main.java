package sos.kernel;

import sos.kernel.filesystem.FileTree;
import sos.kernel.interrupts.*;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.models.RWInterrupt;
import sos.kernel.sasm.Interpreter;
import sos.kernel.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;

public class Main {

    public static Object[] Memory = new Object[1024 * 50]; // SOS Mock Memory
    public static ArrayList<PCB> Tasks = new ArrayList<PCB>(); // Tasks Array
    public static InterruptVector interruptVector = new InterruptVector(); // Interrupt Vector, not been used yet
    public static int PCBCounter = 1; // PCB Counter, used to allocate PCBID, should be modified to a better version.
    public static MMUController controller; // MMU Controller
    public static int cputick = 0; // CPU Tick
    public static int virAddrSize = 1 << 20; // Virtual Address Size
    public static int pageSize = 1 << 10; // Page Size
    public static int RRNowTick = 0; // Round Robin Now Tick
    public static int RRMaxTick = 2; // Round Robin Max Tick
    public static Scheduler scheduler; // Task Scheduler
    public static Interpreter interpret; // SOS Assembly Interpreter, or Software CPU
    public static FileTree FS;

    // Check all Interrupt Event is finished or not, called every Tick ends.
    public static void CheckAllInterrupt() throws Exception {
        Iterator<Timer> iteratorTimer = SyscallHandler.Timers.iterator();
        while (iteratorTimer.hasNext()) {
            Timer timer = iteratorTimer.next();
            if (timer.WakeUpCPUTime <= cputick) {
                Timer.TimerInterruptService(timer.RelativeProcess);
                iteratorTimer.remove(); // 使用迭代器的 remove 方法删除元素
            }
        }
        for(var pageFault : SyscallHandler.PageFaults) {
            PageFault.PageFaultServices(pageFault.RelativeProcess, ++cputick);
        }
        SyscallHandler.PageFaults.clear();
        if(interruptVector.SharedMemoryRelease) {
            Iterator<SharedMemoryBlocked> iteratorBlock = SyscallHandler.SharedMemoryBlocks.iterator();
            while (iteratorBlock.hasNext()) {
                var smb = iteratorBlock.next();
                if (interruptVector.SharedMemoryReleaseRelativeBlockID.contains(smb.RelativeSharedMemory.SharedMemoryID)) {
                    SharedMemoryBlocked.SharedMemoryReleaseService(smb.RelativeProcess);
                    iteratorBlock.remove(); // 使用迭代器的 remove 方法删除元素
                }
            }
            interruptVector.SharedMemoryRelease = false;
            interruptVector.SharedMemoryReleaseRelativeBlockID = new ArrayList<>();
        }
        if(!interruptVector.RWQueue.isEmpty()) {
            Iterator<RWInterrupt> interruptIterator = interruptVector.RWQueue.iterator();
            while(interruptIterator.hasNext()) {
                var rwi = interruptIterator.next();
                System.out.println(rwi);
                IO.IOService(rwi, controller, cputick);
                interruptIterator.remove();
            }
        }
    }

    // Called to Create A New Task
    // TODO: Apply A Better PCBID Allocation Algorithm
    public static PCB createProcess(String[] scripts, int CPUTick) throws Exception {
        PCB newProcess = new PCB(PCBCounter++);
        newProcess.RegisterCache[Constants.CR] = (newProcess.PCBID - 1) * virAddrSize / pageSize;
//        newProcess
        Tasks.add(newProcess);
        for(var i = 0 ; i < scripts.length; i ++) {
            controller.MemoryWrite(newProcess, i, scripts[i], CPUTick);
            if(newProcess.IntPageFault) {
                controller.PageReenter(newProcess, newProcess.IntVirAddr, CPUTick);
                controller.MemoryWrite(newProcess, i, scripts[i], CPUTick);
                newProcess.IntPageFault = false;
            }
        }
        newProcess.ProcessState = PCB.State.READY;
        return newProcess;
    }

    // SOS's Next Tick. Return True If There Is No Interrupt.
    public static boolean nextTick(PCB p) throws Exception {

        // Fetch Instruction. Handle Page Fault Manually.

        var instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
        if(instruction == null) {
            throw new Exception("Error While Reading Instruction");
        }
        if(p.IntPageFault) {
            p.IntPageFault = false;
            controller.PageReenter(p, p.IntVirAddr, cputick++);
            instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
        }

        // Output Instruction

        System.out.printf (
                "[RUNNING] CPU Tick: %d, PCBID: %d, Instruction: %s \n",
                cputick,
                p.PCBID,
                instruction
        );

        // Pass the instruction 2 interpreter
        // if Interpreter Return false, means Interrupt Occurs.
        var result = interpret.Execute(p, (String) instruction, cputick++);
        if(!result) {
            if(p.ProcessState == PCB.State.TERMINATED) {
                Tasks.remove(p);
                for(var key : SyscallHandler.SharedMemoryMap.keySet()) {
                    var sharedMemory = SyscallHandler.SharedMemoryMap.get(key);
                    if(sharedMemory.Owner == p.PCBID) {
                        interruptVector.SharedMemoryReleaseRelativeBlockID.add(sharedMemory.SharedMemoryID);
                    }
                }
                if(!interruptVector.SharedMemoryReleaseRelativeBlockID.isEmpty()) interruptVector.SharedMemoryRelease = true;
                var fdIterator = FS.FDTable.iterator();
                while(fdIterator.hasNext()) {
                    var fd = fdIterator.next();
                    if(fd.PCBID == p.PCBID) {
                        fd.FileNode.Link = null;
                        fdIterator.remove();
                        System.out.println(fd);
                    }
                }
                return false;
            }
            if(p.IntPageFault) {
                SyscallHandler.PageFaults.add(new PageFault(p));
            }
            if(p.ProcessState == PCB.State.WAITING) {
                return false;
            }
        }

        // SP += 1, only when no interrupt happens.
        p.RegisterCache[Constants.SP] ++;
        return true;
    }

    public static void Bootstrap()  {
        interruptVector.RWQueue = new ArrayList<>();
        MMUController mmu = new MMUController(Tasks, Memory, pageSize, virAddrSize, interruptVector);
        FS = new FileTree(interruptVector);
        controller = mmu;
        interpret = new Interpreter(mmu);
        scheduler = new Scheduler(Tasks);
        SyscallHandler.Tasks = Tasks;
        SyscallHandler.Timers = new ArrayList<>();
        SyscallHandler.PageFaults = new ArrayList<>();
        SyscallHandler.SharedMemoryMap = new HashMap<>();
        SyscallHandler.SharedMemoryBlocks = new ArrayList<>();
        SyscallHandler.interruptVector = interruptVector;
        SyscallHandler.FS = FS;
        SyscallHandler.MMU = controller;
        PageFault.controller = mmu;
    }

    // Main Function.
    public static void main(String[] args) throws Exception {
        // Bootstrapping. Fill the Necessary Arguments.
        System.out.println("SOS Bootstrapping ...");
        Bootstrap();

        // Fetch Programs in src/resources/script.txt
        var is = Main.class.getClassLoader().getResourceAsStream("scriptIO.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");
        createProcess(scripts, 0);
        is = Main.class.getClassLoader().getResourceAsStream("script3.txt");
        buffer = is.readAllBytes();
        is.close();
        scriptsRaw = new String(buffer);
        scripts = scriptsRaw.split("\n");
        createProcess(scripts, 0);
        var p = scheduler.Schedule(cputick);
        cputick = 1;

        // if we want to execute commands by steps, use 'blocked'
//        var blocked = new Scanner(System.in);
        while(!Tasks.isEmpty()) {
            int startCPUTick = cputick;
//            var words = blocked.nextLine();
//            if(words.contains("exit")) {
//                break;
//            }
            var interrupted = !nextTick(p);
            RRNowTick += cputick - startCPUTick;
            if(interrupted || RRNowTick > RRMaxTick) { // RR time up or interrupt, change Tasks.
                if(RRNowTick > RRMaxTick && !interrupted) {
                    p.ProcessState = PCB.State.READY;
                }
                RRNowTick = 0;
                p = scheduler.Schedule(cputick);
                if(p != null) {
                    p.ProcessState = PCB.State.RUNNING;
                } else {
                    while(p == null) {
                        p = scheduler.Schedule(cputick);
                        cputick ++;
//                        System.out.printf("[IDLE] CPU Tick:%d\n", cputick);
                        if(Tasks.isEmpty()) break;
                        CheckAllInterrupt(); // when idle, only cpu tick++ and check interrupt. corner cases.
                    }
                }
            }
            CheckAllInterrupt(); // Interrupt Cycle.
        }
//        System.out.println(FS.FoundFile("root/home"));
    }
}