package sos.kernel.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import sos.kernel.models.PCB;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerTest {

    private ArrayList<PCB> tasks;
    private Scheduler scheduler;

    @BeforeEach
    public void setup() {
        tasks = new ArrayList<>();
        scheduler = new Scheduler(tasks);
    }

    @Test
    public void testScheduleNoTasks() {
        PCB result = scheduler.Schedule(0);
        assertNull(result);
    }

    @Test
    public void testScheduleSingleTask() {
        PCB task = new PCB(1, "Task1");
        task.ProcessState = PCB.State.READY;
        task.Priority = PCB.MEDIUM;
        tasks.add(task);

        PCB result = scheduler.Schedule(0);
        assertNotNull(result);
        assertEquals(task, result);
    }

    @Test
    public void testScheduleMultipleTasksDifferentPriority() {
        PCB task1 = new PCB(1, "Task1");
        task1.ProcessState = PCB.State.READY;
        task1.Priority = PCB.HIGH;
        PCB task2 = new PCB(2, "Task2");
        task2.ProcessState = PCB.State.READY;
        task2.Priority = PCB.LOW;
        tasks.add(task1);
        tasks.add(task2);

        PCB result = scheduler.Schedule(0);
        assertNotNull(result);
        assertEquals(task1, result); // High priority task should be scheduled first
    }

    @Test
    public void testScheduleMultipleTasksSamePriority() {
        PCB task1 = new PCB(1, "Task1");
        task1.ProcessState = PCB.State.READY;
        task1.Priority = PCB.MEDIUM;
        task1.LastSchedule = 5;
        PCB task2 = new PCB(2, "Task2");
        task2.ProcessState = PCB.State.READY;
        task2.Priority = PCB.MEDIUM;
        task2.LastSchedule = 3;
        tasks.add(task1);
        tasks.add(task2);

        PCB result = scheduler.Schedule(10);
        assertNotNull(result);
        assertEquals(task2, result); // Task with the longest time since last schedule should be scheduled first
    }

    @Test
    public void testScheduleTaskNotReady() {
        PCB task = new PCB(1, "Task1");
        task.ProcessState = PCB.State.WAITING;
        task.Priority = PCB.MEDIUM;
        tasks.add(task);

        PCB result = scheduler.Schedule(0);
        assertNull(result);
    }

    @Test
    public void testScheduleUpdatesLastSchedule() {
        PCB task = new PCB(1, "Task1");
        task.ProcessState = PCB.State.READY;
        task.Priority = PCB.MEDIUM;
        task.LastSchedule = 5;
        tasks.add(task);

        int currentTick = 10;
        PCB result = scheduler.Schedule(currentTick);
        assertNotNull(result);
        assertEquals(currentTick, result.LastSchedule);
    }
}
