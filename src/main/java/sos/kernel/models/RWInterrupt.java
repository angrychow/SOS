package sos.kernel.models;

public class RWInterrupt {
    public PCB Process;
    public FileTreeNode Node;
    public enum RWType {
        READ,
        WRITE
    }
    public int cursor;
    public int size;
    public int ReadAddr;
    public int WriteAddr;
    public RWType Type;

    public RWInterrupt(PCB process, FileTreeNode node, int cursor, int size, int WriteAddr, RWType type, int ReadAddr) {
        Process = process;
        Node = node;
        this.cursor = cursor;
        this.size = size;
        this.WriteAddr = WriteAddr;
        Type = type;
        this.ReadAddr = ReadAddr;
    }

    @Override
    public String toString() {
        return "RWInterrupt{" +
                "Process=" + Process.PCBID +
                ", Node=" + Node.Name +
                ", cursor=" + cursor +
                ", size=" + size +
                ", ReadAddr=" + ReadAddr +
                ", WriteAddr=" + WriteAddr +
                ", Type=" + Type +
                '}';
    }
}
