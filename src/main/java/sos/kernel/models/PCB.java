package sos.kernel.models;

import java.util.Arrays;

public class PCB {
    public static enum State {
        RUNNING,
        WAITING,
        READY,
        TERMINATED
    }
    public State ProcessState;
    public int PCBID;
    public int[] RegisterCache;
    public boolean IntPageFault = false;
    public int IntVirAddr = 0;
    public int Priority = 1;
    public int LastSchedule = 0;

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

    public PCB(int PCBID){
        this.PCBID = PCBID;
        this.ProcessState = State.WAITING; // 创建时全为 WAITING，直到代码全部被拉入内存才切换
        this.RegisterCache = new int[32]; // [0]: CR; [1]: PC
    }
}
