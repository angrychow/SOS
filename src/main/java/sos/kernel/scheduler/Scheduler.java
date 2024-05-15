package sos.kernel.scheduler;

import sos.kernel.models.PCB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Scheduler {
    public ArrayList<PCB> Tasks;
    public Scheduler(ArrayList<PCB> tasks) {
        Tasks = tasks;
    }
    public PCB Schedule(int CPUTick) {
        ArrayList<PCB> Tasks_ = new ArrayList<>(Tasks);
        Tasks_.sort((task1, task2) -> {
            if (task1.Priority != task2.Priority) return task1.Priority - task2.Priority;
            return task1.LastSchedule - task2.LastSchedule;
        });//优先级+最长不使用优先调度
        Tasks_.removeIf(item -> item.ProcessState != PCB.State.READY);
        if(Tasks_.isEmpty()) return null;
        var p = Tasks_.getFirst();
        p.LastSchedule = CPUTick;
        return p;
    }
}
