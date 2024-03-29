package sos.kernel;

import sos.kernel.interrupts.SyscallHandler;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.sasm.Interpreter;
import sos.kernel.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    public static Object[] Memory = new Object[1024 * 50];
    public static ArrayList<PCB> Tasks = new ArrayList<PCB>();
    public static InterruptVector interruptVector = new InterruptVector();
    public static int PCBCounter = 1;
    public static MMUController controller;

    public static int cputick = 0;
    public static int virAddrSize = 1 << 20;
    public static int pageSize = 1 << 10;
    public static int RRNowTick = 0;
    public static int RRMaxTick = 2;
    public static Scheduler scheduler;
    public static Interpreter interpret;

    public static boolean CheckAllInterrupt() {
        for(var timer : SyscallHandler.Timers) {
            if(timer.WakeUpCPUTime <= cputick) {
                timer.RelativeProcess.ProcessState = PCB.State.READY;
                timer.RelativeProcess.RegisterCache[Constants.SP] ++;
                SyscallHandler.Timers.remove(timer);
                return true;
            }
        }
        return false;
    }

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

    public static boolean nextTick(PCB p) throws Exception {
        var instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
        if(instruction == null) {
            throw new Exception("Error While Reading Instruction");
        }
        if(p.IntPageFault) {
            p.IntPageFault = false;
            controller.PageReenter(p, p.IntVirAddr, cputick++);
            instruction = controller.MemoryRead(p, p.RegisterCache[Constants.SP], cputick++);
        }
        // output instruction
        System.out.printf (
                "[RUNNING] CPU Tick: %d, PCBID: %d, Instruction: %s \n",
                cputick,
                p.PCBID,
                instruction
        );
        var result = interpret.Execute(p, (String) instruction, cputick++);
        if(!result) {
            if(p.ProcessState == PCB.State.TERMINATED) {
                Tasks.remove(p);
                return false;
            }
            if(p.IntPageFault) {
                p.IntPageFault = false;
                controller.PageReenter(p, p.IntVirAddr, cputick++);
                interpret.Execute(p, (String) instruction, cputick++);
            }
            if(p.ProcessState == PCB.State.WAITING) {
                return false;
            }
        }
        p.RegisterCache[Constants.SP] ++;
        return true;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("SOS Bootstrapping ...");
        MMUController mmu = new MMUController(Tasks, Memory, pageSize, virAddrSize, interruptVector);
        controller = mmu;
        interpret = new Interpreter(mmu);
        scheduler = new Scheduler(Tasks);
        SyscallHandler.Tasks = Tasks;
        SyscallHandler.Timers = new ArrayList<>();

//        PCB p = new PCB(1);
        var is = Main.class.getClassLoader().getResourceAsStream("script.txt");
        var buffer = is.readAllBytes();
        is.close();
        var scriptsRaw = new String(buffer);
        var scripts = scriptsRaw.split("\n");

        createProcess(scripts, 0);
        createProcess(scripts, 0);
        var p = scheduler.Schedule();
        cputick = 1;
//        var blocked = new Scanner(System.in);
        while(!Tasks.isEmpty()) {
            int startCPUTick = cputick;
//            var words = blocked.nextLine();
//            if(words.contains("exit")) {
//                break;
//            }
            var interrupted = !nextTick(p);
            RRNowTick += cputick - startCPUTick;
            if(interrupted || RRNowTick > RRMaxTick) {
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
                        CheckAllInterrupt();
                    }
                }
            }
            CheckAllInterrupt();
        }
    }
}