package sos.kernel;

import sos.kernel.interrupts.PageFault;
import sos.kernel.interrupts.SyscallHandler;
import sos.kernel.interrupts.Timer;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.sasm.Interpreter;
import sos.kernel.scheduler.Scheduler;

import java.util.ArrayList;
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

    // Check all Interrupt Event is finished or not, called every Tick ends.
    public static void CheckAllInterrupt() throws Exception {
        for(var timer : SyscallHandler.Timers) {
            if(timer.WakeUpCPUTime <= cputick) {
                Timer.TimerInterruptService(timer.RelativeProcess);
                SyscallHandler.Timers.remove(timer);
                return;
            }
        }
        for(var pageFault : SyscallHandler.PageFaults) {
            PageFault.PageFaultServices(pageFault.RelativeProcess, ++cputick);
        }
        SyscallHandler.PageFaults.clear();
    }

    // Called to Create A New Task
    // TODO: Apply A Better PCBID Allocation Algorithm
    public static PCB createProcess(String[] scripts, int CPUTick) throws Exception {
        PCB newProcess = new PCB(PCBCounter++);
        newProcess.RegisterCache[Constants.CR] = (newProcess.PCBID - 1) * virAddrSize / pageSize;
//        newProcess
        Tasks.add(newProcess);
        for(var i = 0 ; i < scripts.length; i ++) {
            var success = controller.MemoryWrite(newProcess, i, scripts[i], CPUTick);
            if(!success) {
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

    // Main Function.
    public static void main(String[] args) throws Exception {


        // Bootstrapping. Fill the Necessary Arguments.
        System.out.println("SOS Bootstrapping ...");
        MMUController mmu = new MMUController(Tasks, Memory, pageSize, virAddrSize, interruptVector);
        controller = mmu;
        interpret = new Interpreter(mmu);
        scheduler = new Scheduler(Tasks);
        SyscallHandler.Tasks = Tasks;
        SyscallHandler.Timers = new ArrayList<>();
        SyscallHandler.PageFaults = new ArrayList<>();
        PageFault.controller = mmu;

        // Fetch Programs in src/resources/script.txt
        var is = Main.class.getClassLoader().getResourceAsStream("script.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");

        // create process.
        createProcess(scripts, 0);
        createProcess(scripts, 0);
        var p = scheduler.Schedule();
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
                p = scheduler.Schedule();
                if(p != null) {
                    p.ProcessState = PCB.State.RUNNING;
                } else {
                    while(p == null) {
                        p = scheduler.Schedule();
                        cputick ++;
                        System.out.printf("[IDLE] CPU Tick:%d\n", cputick);
                        if(Tasks.isEmpty()) break;
                        CheckAllInterrupt(); // when idle, only cpu tick++ and check interrupt. corner cases.
                    }
                }
            }
            CheckAllInterrupt(); // Interrupt Cycle.
        }
    }
}