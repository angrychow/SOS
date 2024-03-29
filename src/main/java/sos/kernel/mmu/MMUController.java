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
    int[] pageBitmap; // indicate who(PCBID) holds this page
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
        phyPageSize = m.length / pageSize;
        swappedPages = new ArrayList<>();
        pagesStart = pageTableSize * Constants.PAGE_NUMBER;
        interruptVector = vector;
    }

    private int foundEmptyPage(PCB nowPCB, int CPUTick) throws Exception {
        for(int i = Constants.PAGE_NUMBER; i < phyPageSize; i++) {
            if(0 == pageBitmap[i]) {
                pageBitmap[i] = nowPCB.PCBID;
                return i;
            }
        }
        int swappedIndex = 0, minVisTime = 0x7fffffff; // LRU Strategy
        int swappedVirPage = 0;
        for(int i = Constants.PAGE_NUMBER; i < phyPageSize; i++) {
            if(pageLastVisit[i] < minVisTime) {
                minVisTime = pageLastVisit[i];
                swappedIndex = i;
            }
        }
        Object[] contents = Arrays.copyOfRange(this.Memory, swappedIndex * pageSize, (swappedIndex + 1) * pageSize);
        PCB task = null;
        for(var tempTask : Tasks) {
            if(tempTask.PCBID == pageBitmap[swappedIndex]) {
                task = tempTask;
            }
        }
        if(task == null) {
            throw new Exception("task not found, check swapped index -> pageBitmap");
        }
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
        pageBitmap[swappedIndex] = nowPCB.PCBID;
        pageLastVisit[swappedIndex] = CPUTick;
        swappedPages.add(new SwappedOutPage(pageBitmap[swappedIndex], swappedVirPage, contents));
        return swappedIndex;
    }

    private int page(int addr) { return addr / pageSize; }
    private int offset(int addr) { return addr % pageSize; }

    public boolean MemoryWrite(PCB process, int virtualAddress, Object content, int CPUTick) throws Exception {
        int virPage = page(virtualAddress);
        int offset = offset(virtualAddress);
        PageEntry phyPageEntry = (PageEntry)(this.Memory[process.RegisterCache[Constants.CR] + virPage]);

        if(phyPageEntry == null || !phyPageEntry.Valid || pageBitmap[phyPageEntry.PhyPage] != process.PCBID) {
            return false; // 中断
        } else {
            this.Memory[phyPageEntry.PhyPage * pageSize + offset] = content;
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

    public void PageReenter(PCB process, int virtualAddress, int CPUTick) throws Exception {
        var virPage = page(virtualAddress);
        var phyPage = foundEmptyPage(process, CPUTick);
        Object[] contents = null;
        SwappedOutPage p = null;
        for(var page : swappedPages) {
            if(page.VirPage == virPage && page.PCBID == process.PCBID) {
                contents = page.PageContents;
                p = page;
                break;
            }
        }
        if(contents == null) {
            contents = new Object[pageSize];
        } else {
            swappedPages.remove(p);
        }
        for(var i = 0 ; i < pageSize; i++) {
            var real = i + phyPage * pageSize;
            if(contents[i] != null)
                this.Memory[real] = contents[i];
            else
                this.Memory[real] = 0;
        }
        this.Memory[process.RegisterCache[Constants.CR] + virPage] = new PageEntry(true, false, phyPage);
        interruptVector.PageInterrupt = true;
    }
}
