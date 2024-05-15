package sos.kernel.mmu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sos.kernel.Constants;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.models.PageEntry;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class MMUControllerTest {

    private MMUController mmu;
    private InterruptVector interruptVector;
    private ArrayList<PCB> tasks;
    private Object[] memory;
    private int pageSize = 4;
    private int virAddrSize = 16;

    @BeforeEach
    public void setUp() {
        interruptVector = new InterruptVector();
        tasks = new ArrayList<>();
        memory = new Object[64]; // 假设我们有64个单元的内存
        mmu = new MMUController(tasks, memory, pageSize, virAddrSize, interruptVector);
    }

    @Test
    public void testMemoryWriteAndRead() throws Exception {
        PCB process = new PCB(1, "TestProcess");
        process.RegisterCache = new int[10];
        process.RegisterCache[Constants.CR] = 0;
        tasks.add(process);

        int virtualAddress = 0;
        String content = "TestContent";

        assertFalse(mmu.MemoryWrite(process, virtualAddress, content, 0)); // Expect page fault
        mmu.PageReenter(process, virtualAddress, 0);
        assertTrue(mmu.MemoryWrite(process, virtualAddress, content, 0)); // After handling page fault, write should succeed

        Object readContent = mmu.MemoryRead(process, virtualAddress, 0);
        assertEquals(content, readContent);
    }

    @Test
    public void testMemoryWritePageFault() throws Exception {
        PCB process = new PCB(1, "TestProcess");
        process.RegisterCache = new int[10];
        process.RegisterCache[Constants.CR] = 0;
        tasks.add(process);

        int virtualAddress = 0;
        String content = "TestContent";

        // 模拟引发页面错误的写操作
        boolean writeResult = mmu.MemoryWrite(process, virtualAddress, content, 0);
        assertFalse(writeResult);
        assertTrue(process.IntPageFault);
    }

    @Test
    public void testPageReenter() throws Exception {
        PCB process = new PCB(1, "TestProcess");
        process.RegisterCache = new int[10];
        process.RegisterCache[Constants.CR] = 0;
        tasks.add(process);

        int virtualAddress = 0;
        mmu.PageReenter(process, virtualAddress, 0);

        PageEntry pageEntry = (PageEntry) mmu.Memory[process.RegisterCache[Constants.CR]];
        assertNotNull(pageEntry);
        assertTrue(pageEntry.Valid);
    }

    @Test
    public void testClearPageTable() {
        PCB process = new PCB(1, "TestProcess");
        process.RegisterCache = new int[10];
        process.RegisterCache[Constants.CR] = 0;
        tasks.add(process);

        // 设置一个有效的页表条目和页面位图
        mmu.Memory[0] = new PageEntry(true, false, Constants.PAGE_TABLE_NUMBER);
        mmu.pageBitmap[Constants.PAGE_TABLE_NUMBER] = process.PCBID;

        System.out.println("Before ClearPageTable:");
        for (int i = 0; i < mmu.pageBitmap.length; i++) {
            System.out.printf("pageBitmap[%d] = %d\n", i, mmu.pageBitmap[i]);
        }

        mmu.ClearPageTable(process);

        System.out.println("After ClearPageTable:");
        for (int i = 0; i < mmu.pageBitmap.length; i++) {
            System.out.printf("pageBitmap[%d] = %d\n", i, mmu.pageBitmap[i]);
        }

        assertNull(mmu.Memory[0]);
        assertEquals(0, mmu.pageBitmap[Constants.PAGE_TABLE_NUMBER]);
    }

    @Test
    public void testMemoryUsage() {
        mmu.Memory[0] = "Test";
        mmu.Memory[1] = "Test";
        mmu.Memory[2] = "Test";

        double usage = mmu.MemoryUsage();
        assertEquals(3.0 / mmu.Memory.length, usage);
    }
}
