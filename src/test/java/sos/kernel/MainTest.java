package sos.kernel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sos.kernel.device.StdDevice;
import sos.kernel.filesystem.FileTree;
import sos.kernel.interrupts.PageFault;
import sos.kernel.interrupts.SyscallHandler;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.sasm.Interpreter;
import sos.kernel.scheduler.Scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
public class MainTest {

    @BeforeEach
    public void setup() throws Exception {
        // 初始化Main类的静态变量
        Main.interruptVector = new InterruptVector();
        Main.Memory = new Object[1024 * 15];
        Main.Tasks = new ArrayList<>();
        Main.DeviceTable = new ArrayList<>();
        Main.PCBCounter = 1;
        Main.cputick = 0;
        Main.virAddrSize = 1 << 20;
        Main.pageSize = 1 << 10;
        Main.RRNowTick = 0;
        Main.RRMaxTick = 2;
        Main.FS = new FileTree(Main.interruptVector);
        Main.controller = new MMUController(Main.Tasks, Main.Memory, Main.pageSize, Main.virAddrSize, Main.interruptVector);
        Main.interpret = new Interpreter(Main.controller);
        Main.scheduler = new Scheduler(Main.Tasks);
        Main.stdDevice = new StdDevice();
        Main.DeviceTable.add(Main.stdDevice);
        Main.stdDevice.DeviceName = "std";
        Main.stdDevice.DeviceBuffer = new Object[Main.stdDevice.DeviceBufferSize];
        Main.stdDevice.LoadDriver();
        SyscallHandler.Tasks = Main.Tasks;
        SyscallHandler.Timers = new ArrayList<>();
        SyscallHandler.PageFaults = new ArrayList<>();
        SyscallHandler.SharedMemoryMap = new HashMap<>();
        SyscallHandler.SharedMemoryBlocks = new ArrayList<>();
        SyscallHandler.interruptVector = Main.interruptVector;
        SyscallHandler.FS = Main.FS;
        SyscallHandler.MMU = Main.controller;
        PageFault.controller = Main.controller;
        Main.cputick = 1;
    }

    @Test
    public void testCreateFile() {
        boolean result = Main.CreateFile("testfile", "FILE", "std", "Hello World", "/");
        assertTrue(result);
    }

    @Test
    public void testDeleteFile() {
        Main.CreateFile("testfile", "FILE", "std", "Hello World", "/");
        boolean result = Main.DeleteFile("/testfile");
        assertTrue(true);
    }

    @Test
    public void testFoundFile() {
        Main.CreateFile("testfile", "FILE", "std", "Hello World", "/");
        var file = Main.FS.FoundFile("/testfile");
        assertNotNull(true);
    }

    @Test
    public void testBootstrapAndProcessCreation() throws IOException, Exception {
        Main.Bootstrap();
        String script = new String(Files.readAllBytes(Paths.get("src/test/resources/keyboard.txt")));
        PCB process = Main.createProcess(script.split("\n"), Main.cputick, "TestProcess");
        assertNotNull(process);
        assertEquals("TestProcess", process.ProcessName);
        assertEquals(PCB.State.READY, process.ProcessState);
    }

    @Test
    public void testNextTick() throws Exception {
        Main.Bootstrap();
        String script = new String(Files.readAllBytes(Paths.get("src/test/resources/keyboard.txt")));
        Main.createProcess(script.split("\n"), Main.cputick, "TestProcess");
        String result = Main.NextTick();
        assertNotNull(result);
        assertTrue(result.contains("[RUNNING]"));
    }

    @Test
    public void testHttpInput() {
        boolean result = Main.HttpInput("std", "input data");
        assertTrue(result);
        assertEquals("input data", Main.stdDevice.node.readContents());
    }
}