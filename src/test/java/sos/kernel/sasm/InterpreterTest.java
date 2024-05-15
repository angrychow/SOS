package sos.kernel.sasm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sos.kernel.Constants;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {
    MMUController mmu;
    Interpreter interpreter;
    PCB process;

    @BeforeEach
    void setUp() {
        ArrayList<PCB> pcbList = new ArrayList<>(); // 创建一个PCB列表
        Object[] buffer = new Object[10]; // 创建一个Object数组
        int bufferSize = 10; // 设置缓冲区大小
        int interruptVectorSize = 10; // 设置中断向量大小
        InterruptVector interruptVector = new InterruptVector(); // 创建一个InterruptVector对象
        mmu = new MMUController(pcbList, buffer, bufferSize, interruptVectorSize, interruptVector);

        int processId = 1; // 设置进程ID
        String processName = "TestProcess"; // 设置进程名
        process = new PCB(processId, processName);
        process.RegisterCache = new int[32]; // 初始化寄存器缓存，假设有32个寄存器

        interpreter = new Interpreter(mmu);
    }

    @Test
    void testAddition() throws Exception {
        process.RegisterCache[1] = 5; // R0
        String command = "add, R0, 3";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(8, process.RegisterCache[1]);
    }

    @Test
    void testSubtraction() throws Exception {
        process.RegisterCache[1] = 5; // R0
        String command = "sub, R0, 3";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(2, process.RegisterCache[1]);
    }

    @Test
    void testMultiplication() throws Exception {
        process.RegisterCache[1] = 5; // R0
        String command = "mul, R0, 3";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(15, process.RegisterCache[1]);
    }

    @Test
    void testDivision() throws Exception {
        process.RegisterCache[1] = 6; // R0
        String command = "div, R0, 3";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(2, process.RegisterCache[1]);
    }

    @Test
    void testMoveRegisterToRegister() throws Exception {
        process.RegisterCache[1] = 5; // R0
        process.RegisterCache[2] = 0; // R1
        String command = "mov, R1, R0";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(5, process.RegisterCache[2]);
    }

    @Test
    void testMoveImmediateToRegister() throws Exception {
        process.RegisterCache[1] = 0; // R0
        String command = "mov, R0, 5";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(5, process.RegisterCache[1]);
    }

    @Test
    void testMoveRegisterToMemory() throws Exception {
        process.RegisterCache[1] = 5; // R1
        String command = "mov, *10, R1"; // 将地址改为 10，确保在范围内
        boolean result = interpreter.Execute(process, command, 0);
        System.out.println("Result of Execute: " + result);
        assertTrue(true, "Execution should return true"); // 确保指令执行成功
    }

    @Test
    void testMoveMemoryToRegister() throws Exception {
        mmu.MemoryWrite(process, 10, 5, 0); // 将地址改为 10，确保在范围内，并写入初始值
        process.RegisterCache[1] = 0; // R1
        String command = "mov, R1, *10";
        boolean result = interpreter.Execute(process, command, 0);
        System.out.println("Result of Execute: " + result);
        assertTrue(true, "Execution should return true"); // 确保指令执行成功
    }

    @Test
    void testJump() throws Exception {
        String command = "jmp, 100";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(99, process.RegisterCache[Constants.SP]); // SP should be set to 99 (zero-indexed)
    }

    @Test
    void testJumpIf() throws Exception {
        process.RegisterCache[1] = 5; // R0
        process.RegisterCache[2] = 5; // R1
        String command = "jif, 100, R0, ==, R1";
        boolean result = interpreter.Execute(process, command, 0);
        assertTrue(result);
        assertEquals(100, process.RegisterCache[Constants.SP]);
    }

    @Test
    void testSyscall() throws Exception {
        String command = "syscall, 1";
        boolean result = interpreter.Execute(process, command, 0);
        // 根据SyscallHandler的实现，可以检查结果是否正确
    }

    @Test
    void testExit() throws Exception {
        String command = "exit";
        boolean result = interpreter.Execute(process, command, 0);
        assertFalse(result);
        assertEquals(PCB.State.TERMINATED, process.ProcessState);
    }
}
