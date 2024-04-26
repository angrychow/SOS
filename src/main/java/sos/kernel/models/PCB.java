package sos.kernel.models;

import sos.kernel.Constants;

import java.util.Arrays;

import static sos.kernel.Main.controller;
import static sos.kernel.Main.cputick;

public class PCB {
    public static enum State {
        RUNNING,
        WAITING,
        READY,
        TERMINATED
    }
    public static final int HIGH=20;
    public static final int MEDIUM=50;
    public static final int LOW=80;
    public State ProcessState;
    public int PCBID;
    public int[] RegisterCache;
    public boolean IntPageFault = false;
    public int IntVirAddr = 0;
    public int Priority = HIGH;
    public int LastSchedule = 0;
    public String ProcessName;
    public int WaitingTick=0;

    public boolean isTerminated() throws Exception {
        Object temp=controller.MemoryRead(this,RegisterCache[Constants.SP],cputick);
        if(this.IntPageFault)
        {
            controller.PageReenter(this,this.IntVirAddr,cputick);
            this.IntPageFault=false;
        }
        temp = controller.MemoryRead(this,RegisterCache[Constants.SP],cputick);
        if(temp.getClass().equals(String.class)) {
            if(((String)temp).equals("exit"))
            {
                return true;
            }
        }
        return false;
    }
    @Override
    public String toString() {
        return "PCB{" +
                "ProcessState=" + ProcessState +
                ", PCBID=" + PCBID +
                ", RegisterCache=" + Arrays.toString(RegisterCache) +
                ", IntPageFault=" + IntPageFault +
                ", IntVirAddr=" + IntVirAddr +
                '}';
    }

    public PCB(int PCBID, String pName){
        this.PCBID = PCBID;
        this.ProcessName = pName;
        this.ProcessState = State.WAITING; // 创建时全为 WAITING，直到代码全部被拉入内存才切换
        this.RegisterCache = new int[32]; // [0]: CR; [1]: PC
    }
}
