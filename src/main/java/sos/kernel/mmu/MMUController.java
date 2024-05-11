package sos.kernel.mmu;

import sos.kernel.Constants;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.PCB;
import sos.kernel.models.PageEntry;
import sos.kernel.models.SwappedOutPage;

import java.util.ArrayList;
import java.util.Arrays;

public class MMUController {
    public Object[] Memory;
    public ArrayList<PCB> Tasks;
    public int[] pageBitmap; // indicate who(PCBID) holds this page
    int[] pageLastVisit;
    int pageSize;
    int pageTableSize;
    int phyPageSize;
    int pagesStart; // 页表项在这之上，页表内容在这之下
    InterruptVector interruptVector;
    ArrayList<SwappedOutPage> swappedPages;

    public MMUController(ArrayList<PCB> Tasks, Object[] m, int pageSize, int virAddrSize, InterruptVector vector) {
        this.Memory = m;
        this.Tasks = Tasks;
        pageBitmap = new int[m.length / pageSize];
        pageLastVisit = new int[m.length / pageSize];
        this.pageSize = pageSize;
        this.pageTableSize = virAddrSize / pageSize;
        phyPageSize = m.length / pageSize; // phyPageSize指明了物理内存有多少页
        swappedPages = new ArrayList<>(); // 用于存放被换出的页面
        pagesStart = pageTableSize * Constants.PAGE_TABLE_NUMBER; //页表项最多能有PAGE_TABLE_NUMBER个进程
        interruptVector = vector;
        System.out.printf("[System Page Size]: %d pages \n", phyPageSize);
    }

    private int foundEmptyPage(PCB nowPCB, int CPUTick) throws Exception {
        // 首先查看有没有空白页
        for(int i = Constants.PAGE_TABLE_NUMBER; i < phyPageSize; i++) {
            if(0 == pageBitmap[i]) {
                pageBitmap[i] = nowPCB.PCBID;
                return i; // 有的话直接返回
            }
        }
        // 使用 LRU 策略，选择一个页面换出
        int swappedIndex = 0, minVisTime = 0x7fffffff; // LRU Strategy
        int swappedVirPage = 0;
        for(int i = Constants.PAGE_TABLE_NUMBER; i < phyPageSize; i++) {
            if(pageLastVisit[i] < minVisTime) {
                minVisTime = pageLastVisit[i];
                swappedIndex = i;
            }
        }
        // 拷贝页面
        Object[] contents = Arrays.copyOfRange(this.Memory, swappedIndex * pageSize, (swappedIndex + 1) * pageSize);
        PCB task = null;
        // 寻找预备换出的物理页面对应的 Task,
        for(var tempTask : Tasks) {
            if(tempTask.PCBID == pageBitmap[swappedIndex]) {
                task = tempTask;
            }
        }
        if(task == null) {
            throw new Exception("task not found, check swapped index -> pageBitmap");
        }
        // 找到该 Task 哪个虚拟页面映射到这个物理页面
        for(int i = 0; i < pageTableSize; i++) {
            if(this.Memory[task.RegisterCache[Constants.CR] + i] == null) continue;
            if(
                    ((PageEntry)(this.Memory[task.RegisterCache[Constants.CR] + i])).PhyPage == swappedIndex &&
                            ((PageEntry)(this.Memory[task.RegisterCache[Constants.CR] + i])).Valid
            ) {
                ((PageEntry)(this.Memory[task.RegisterCache[Constants.CR] + i])).Valid = false;
                swappedVirPage = i;
                break;
            }
        }

        // 更换页面 PCB ownership
        pageBitmap[swappedIndex] = nowPCB.PCBID;
        pageLastVisit[swappedIndex] = CPUTick;
        // 页面加入 Swapped Pages Pool
        swappedPages.add(new SwappedOutPage(pageBitmap[swappedIndex], swappedVirPage, contents));
        return swappedIndex;
    }

    private int page(int addr) { return addr / pageSize; }
    private int offset(int addr) { return addr % pageSize; }

    public Double MemoryUsage() {
        int used=0;
        for (Object o : Memory) {
            if (o != null)
                used++;
        }
        return (double)used/Memory.length;
    }
    public boolean MemoryWrite(PCB process, int virtualAddress, Object content, int CPUTick) throws Exception {
        int virPage = page(virtualAddress);
        int offset = offset(virtualAddress);
        PageEntry phyPageEntry = (PageEntry)(this.Memory[process.RegisterCache[Constants.CR] + virPage]);
        if(phyPageEntry == null || !phyPageEntry.Valid || pageBitmap[phyPageEntry.PhyPage] != process.PCBID) {
            process.IntPageFault = true;
            return false; // 中断
        } else {
            this.Memory[phyPageEntry.PhyPage * pageSize + offset] = content;
            phyPageEntry.Dirty = true;
            pageLastVisit[phyPageEntry.PhyPage] = CPUTick;
            return true;
        }
    }

    public Object MemoryRead(PCB process, int virtualAddress, int CPUTick) throws Exception {
        int virPage = page(virtualAddress);
        int offset = offset(virtualAddress);
        PageEntry phyPageEntry = (PageEntry)(this.Memory[process.RegisterCache[Constants.CR] + virPage]);
        if(phyPageEntry == null || !phyPageEntry.Valid || pageBitmap[phyPageEntry.PhyPage] != process.PCBID) {
            process.IntPageFault = true;
            return false; // 中断
        } else {
//            this.Memory[phyPageEntry.PhyPage * pageSize + offset] = content;
            pageLastVisit[phyPageEntry.PhyPage] = CPUTick;
            return this.Memory[phyPageEntry.PhyPage * pageSize + offset];
        }
    }

    public void ClearPageTable(PCB process) {
        for(int i = 0; i < pageTableSize; i++) {
            Memory[process.RegisterCache[Constants.CR] + i] = null;
        }
        for(int i = Constants.PAGE_TABLE_NUMBER; i < phyPageSize; i++) {
            if(pageBitmap[i] == process.PCBID) {
                pageBitmap[i] = 0;
            }
        }
    }

    public void PageReenter(PCB process, int virtualAddress, int CPUTick) throws Exception {
        var virPage = page(virtualAddress);
        var phyPage = foundEmptyPage(process, CPUTick);
        Object[] contents = null;
        // 首先查看这个页面曾经有没有被换出过
        SwappedOutPage p = null;
        for(var page : swappedPages) {
            if(page.VirPage == virPage && page.PCBID == process.PCBID) {
                contents = page.PageContents;
                p = page;
                break;
            }
        }
        // 如果有加载被换出的页面，如果没有，加载一个空页面
        if(contents == null) {
            contents = new Object[pageSize];
        } else {
            swappedPages.remove(p);
        }

        // 页面换入
        for(var i = 0 ; i < pageSize; i++) {
            var real = i + phyPage * pageSize;//真实的物理地址
            if(contents[i] != null)
                this.Memory[real] = contents[i];
            else
                this.Memory[real] = 0;
        }

        // 新建页表项
        this.Memory[process.RegisterCache[Constants.CR] + virPage] = new PageEntry(true, false, phyPage);
    }
}
