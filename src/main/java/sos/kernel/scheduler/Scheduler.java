package sos.kernel.scheduler;

import sos.kernel.models.PCB;

import java.util.ArrayList;

public class Scheduler {
    public ArrayList<PCB> Tasks;
    public Scheduler(ArrayList<PCB> tasks) {
        Tasks = tasks;
    }

    public PCB Schedule() {
        for(var task: Tasks) {
            if(task.ProcessState == PCB.State.READY)
                return task;
        }
        return null;
    }
}
