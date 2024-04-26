package sos.kernel;

import sos.kernel.filesystem.FileTree;
import sos.kernel.interrupts.*;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.*;
import sos.kernel.sasm.Interpreter;
import sos.kernel.scheduler.Scheduler;
import sos.kernel.filesystem.DeviceTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;

public class Main {

    //    public static Object[] Memory = new Object[1024 * 50]; // SOS Mock MemoryWrite
    public static Object[] Memory = new Object[1024 * 15]; // Display Page Demanding
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
    public static DeviceTable deviceTable;

    public static PCB CurrentProcess = null;
    public static String GetPhysicalMemory() {
        var ret = new StringBuilder();
        for (var i = 0; i < Memory.length; i++) {
            if (Memory[i] != null) {
                ret.append(String.format("[Physical Memory] Address: %d, Content: %s\n", i, Memory[i].toString()));
            }
        }
        return ret.toString();
    }
    public static SOSInfo GetSOSInfo() {
        var ret = new SOSInfo();
        ret.pcbList = Tasks;
        ret.nowProcess = CurrentProcess;
        ret.pages = new HashMap<>();
        for (var task : Tasks) {
            var m = new HashMap<String, PageEntry>();
            ret.pages.put(String.valueOf(task.PCBID), m);
            final int PAGE_ENTRY_SIZE = virAddrSize / pageSize;
            for (var i = 0; i < PAGE_ENTRY_SIZE; i++) {
                if (
                        Memory[task.RegisterCache[Constants.CR] + i] != null &&
                                Memory[task.RegisterCache[Constants.CR] + i] instanceof PageEntry
                ) {
                    m.put(String.valueOf(i), (PageEntry) Memory[task.RegisterCache[Constants.CR] + i]);
                }
            }
        }
        return ret;
    }

    public static boolean CreateFile(
            String filename,
            String filetype,
            String deviceName,
            String content,
            String path
    ) {
        var f = new FileTreeNode();
        f.Name = filename;
        if (filetype.equals("FILE")) {
            f.Type = FileTreeNode.FileType.FILE;
        } else if (filetype.equals("DIRECTORY")) {
            f.Type = FileTreeNode.FileType.DIRECTORY;
        } else {
            f.Type = FileTreeNode.FileType.DEVICES;
        }
        f.DeviceName = deviceName;
        f.contents = content;
        return FS.CreateFile(path, f);
    }

    public static boolean DeleteFile(String path) {
        return FS.DeleteFile(path);
    }

    public static FileTreeNode FoundFile(String path) {
        return FS.FoundFile(path);
    }

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
        for (var pageFault : SyscallHandler.PageFaults) {
            PageFault.PageFaultServices(pageFault.RelativeProcess, ++cputick);
        }
        SyscallHandler.PageFaults.clear();
        if (interruptVector.SharedMemoryRelease) {
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
        if (!interruptVector.RWQueue.isEmpty()) {
            Iterator<RWInterrupt> interruptIterator = interruptVector.RWQueue.iterator();
            while (interruptIterator.hasNext()) {
                var rwi = interruptIterator.next();
                System.out.println(rwi);
                IO.IOService(rwi, controller, cputick);
                interruptIterator.remove();
            }
        }
    }

    // Called to Create A New Task
    // TODO: Apply A Better PCBID Allocation Algorithm
    public static PCB createProcess(String[] scripts, int CPUTick, String pName) throws Exception {
        var PCBID = -1;
        for (var i = 1; i < Constants.PAGE_TABLE_NUMBER; i++) {//1到PAGE_TABLE_NUMBER?
            int finalI = i;
            var found = Tasks.stream().filter(element -> element.PCBID == finalI).findFirst();
            if (found.isEmpty()) {
                PCBID = i;
                break;
            }
        }
        if (PCBID == -1) {
            System.out.println("[Create Process Failed] Current Running Processes Up To Limit!");
            return null;
        }
        PCB newProcess = new PCB(PCBID, pName);
        newProcess.RegisterCache[Constants.CR] = (newProcess.PCBID - 1) * virAddrSize / pageSize; //页表的起始地址
//        newProcess
        Tasks.add(newProcess);
        for (var i = 0; i < scripts.length; i++) {
            controller.MemoryWrite(newProcess, i, scripts[i], CPUTick);
            if (newProcess.IntPageFault) {
                controller.PageReenter(newProcess, newProcess.IntVirAddr, CPUTick);
                controller.MemoryWrite(newProcess, i, scripts[i], CPUTick);
                newProcess.IntPageFault = false;
            }
        }
        newProcess.ProcessState = PCB.State.READY;
        return newProcess;
    }

    public static boolean CreateProcess(String script, String pname) throws Exception {
        var p = createProcess(script.split("\n"), cputick, pname);
        return p != null;
    }

    // SOS's Next Tick. Return True If There Is No Interrupt.
    public static boolean nextTick(PCB p) throws Exception {

        // Fetch Instruction. Handle Page Fault Manually.

        var instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
        if (!instruction.getClass().equals(String.class)) {
//            throw new Exception("Error While Reading Instruction");
            p.ProcessState = PCB.State.WAITING;
            return true;
        } else {
            if (p.IntPageFault) {
                p.IntPageFault = false;
                controller.PageReenter(p, p.IntVirAddr, cputick++);
                instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
            }

            // Output Instruction

            System.out.printf(
                    "[RUNNING] CPU Tick: %d, PCBID: %d, PRIORITY: %d, Instruction: %s \n",
                    cputick,
                    p.PCBID,
                    p.Priority,
                    instruction
            );

            interruptVector.LastExecCommand = String.format("[RUNNING] CPU Tick: %d, PCBID: %d, PRIORITY: %d, Instruction: %s \n",
                    cputick,
                    p.PCBID,
                    p.Priority,
                    instruction);

            // Pass the instruction 2 interpreter
            // if Interpreter Return false, means Interrupt Occurs.
            var result = interpret.Execute(p, (String) instruction, cputick++);
            if (!result) {
                if (p.ProcessState == PCB.State.TERMINATED) {
                    Tasks.remove(p);
                    for (var key : SyscallHandler.SharedMemoryMap.keySet()) {
                        var sharedMemory = SyscallHandler.SharedMemoryMap.get(key);
                        if (sharedMemory.Owner == p.PCBID) {
                            interruptVector.SharedMemoryReleaseRelativeBlockID.add(sharedMemory.SharedMemoryID);
                        }
                    }
                    if (!interruptVector.SharedMemoryReleaseRelativeBlockID.isEmpty())
                        interruptVector.SharedMemoryRelease = true;
                    var fdIterator = FS.FDTable.iterator();
                    while (fdIterator.hasNext()) {
                        var fd = fdIterator.next();
                        if (fd.PCBID == p.PCBID) {
                            fd.FileNode.Link = null;
                            fdIterator.remove();
                            System.out.println(fd);
                        }
                    }
                    controller.ClearPageTable(p);
                    return false;
                }
                if (p.IntPageFault) {
                    p.ProcessState = PCB.State.WAITING;
                    SyscallHandler.PageFaults.add(new PageFault(p));
                }
                if (p.ProcessState == PCB.State.WAITING) {
                    return false;
                }
            }

            // SP += 1, only when no interrupt happens.
            p.RegisterCache[Constants.SP]++;
            return true;
        }
    }

    public static void Bootstrap() throws Exception {
        interruptVector.RWQueue = new ArrayList<>();
        MMUController mmu = new MMUController(Tasks, Memory, pageSize, virAddrSize, interruptVector);//目前15个页
        FS = new FileTree(interruptVector);
        controller = mmu;
        interpret = new Interpreter(mmu);
        scheduler = new Scheduler(Tasks);
        deviceTable = new DeviceTable();
        DeviceTable.DeviceBuffer = new Object[DeviceTable.DeviceBufferSize];
        SyscallHandler.Tasks = Tasks;
        SyscallHandler.Timers = new ArrayList<>();
        SyscallHandler.PageFaults = new ArrayList<>();
        SyscallHandler.SharedMemoryMap = new HashMap<>();
        SyscallHandler.SharedMemoryBlocks = new ArrayList<>();
        SyscallHandler.interruptVector = interruptVector;
        SyscallHandler.FS = FS;
        SyscallHandler.MMU = controller;
        PageFault.controller = mmu;

//
//        var is = Main.class.getClassLoader().getResourceAsStream("script2.txt");
//        var buffer = is.readAllBytes();
//        is.close();
//        var scriptsRaw = new String(buffer);
//        var scripts = scriptsRaw.split("\n");
//        createProcess(scripts, 0, "Process1");
//        is = Main.class.getClassLoader().getResourceAsStream("script3.txt");
//        buffer = is.readAllBytes();
//        is.close();
//        scriptsRaw = new String(buffer);
//        scripts = scriptsRaw.split("\n");
//        createProcess(scripts, 0, "Process2");
        var is = Main.class.getClassLoader().getResourceAsStream("keyboard.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");
        DeviceTable.process = createProcess(scripts, 0, "IOProcess");
        is = Main.class.getClassLoader().getResourceAsStream("script2.txt");
        buffer = is.readAllBytes();
        is.close();
        scriptsRaw = new String(buffer);
        scripts = scriptsRaw.split("\n");
        createProcess(scripts, 0, "Process1");
        is = Main.class.getClassLoader().getResourceAsStream("script3.txt");
        buffer = is.readAllBytes();
        is.close();
        scriptsRaw = new String(buffer);
        scripts = scriptsRaw.split("\n");
        createProcess(scripts, 0, "Process2");
        cputick = 1;
    }

    public static String NextTick() throws Exception {
        if (Tasks.isEmpty()) return "[IDLE] No Task In Tasks Queue";
        var p = CurrentProcess;
        if (p == null || p.ProcessState != PCB.State.RUNNING) {
            p = scheduler.Schedule(cputick);
            CurrentProcess = p;
        }
        if (p != null) {
            p.ProcessState = PCB.State.RUNNING;
            int startCPUTick = cputick;
            var interrupted = !nextTick(p);//蕴含了cputick++
            RRNowTick += cputick - startCPUTick;
            if (interrupted || RRNowTick > p.Priority) { // RR time up or interrupt, change Tasks.
                if (RRNowTick > p.Priority && !interrupted) {
                    p.ProcessState = PCB.State.READY;
                    if (!p.isTerminated()) {
                        if (p.Priority == PCB.HIGH) {
                            System.out.printf("[Priority Downgrade] PCBID: %d, Priority: %d -> %d\n", p.PCBID, p.Priority, PCB.MEDIUM);
                            p.Priority = PCB.MEDIUM;
                        } else if (p.Priority == PCB.MEDIUM) {
                            System.out.printf("[Priority Downgrade] PCBID: %d, Priority: %d -> %d\n", p.PCBID, p.Priority, PCB.LOW);
                            p.Priority = PCB.LOW;
                        }
                    }//采用三级反馈队列
                }
                RRNowTick = 0;
                p = scheduler.Schedule(cputick);
                if (p != null) {
                    p.ProcessState = PCB.State.RUNNING;
                    CurrentProcess = p;
                } else {
                    while (p == null) {
                        p = scheduler.Schedule(cputick);
                        cputick++;
//                        System.out.printf("[IDLE] CPU Tick:%d\n", cputick);
                        if (Tasks.isEmpty()) {
                            break;
                        }
                        CheckAllInterrupt(); // when idle, only cpu tick++ and check interrupt. corner cases.
                    }
                    if (p != null) {
                        CurrentProcess = p;
                        p.ProcessState = PCB.State.RUNNING;
                    }
                }
            }
            if (!interrupted) CheckAllInterrupt(); // Interrupt Cycle.
            return interruptVector.LastExecCommand;
        }
        cputick++;
//        System.out.printf("[IDLE] CPU Tick:%d\n", cputick);
        CheckAllInterrupt();// when idle, only cpu tick++ and check interrupt. corner cases.
        return "[IDLE] No Ready Task";
    }

    // Main Function.
    public static void main(String[] args) throws Exception {
        // Bootstrapping. Fill the Necessary Arguments.
        System.out.println("SOS Bootstrapping ...");
        Bootstrap();
        deviceTable.start();
        // Fetch Programs in src/resources/script.txt


        // if we want to execute commands by steps, use 'blocked'
//        var blocked = new Scanner(System.in);
        while (!Tasks.isEmpty()) {
            NextTick();
        }
//        System.out.println(FS.FoundFile("root/home"));
    }
}