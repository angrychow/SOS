package sos.kernel.interrupts;

import java.util.HashMap;
import java.util.Map;

public class SharedMemory {
    public int Owner = 0;
    public int[] Memory;
    public int SharedMemoryID;
    public SharedMemory(int id) {
        Memory = new int[1024];
        SharedMemoryID = id;
        Owner = 0;
    }
}
