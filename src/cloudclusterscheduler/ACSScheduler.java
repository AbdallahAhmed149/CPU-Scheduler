package cloudclusterscheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ACSScheduler {

    public static final int Aging_threshold = 4;

    private Map<Process, Integer> tier2EntryTime = new HashMap<>();

    public void execute(List<Process> processes, int quantum, int contextSwitch) {

    Queue<Process> tier1 = new LinkedList<>();
    List<Process> tier2 = new ArrayList<>();

    List<Process> allProcesses = new ArrayList<>(processes);
    tier2EntryTime.clear();

    int currentTime = 0;
    int completed = 0;
    int n = processes.size();

    Process currentProcess = null;

    while (completed < n) {

        for (Process p : allProcesses) {
            if (p.arrivalTime <= currentTime
                    && p.remainingTime > 0
                    && !tier1.contains(p)
                    && !tier2.contains(p)) {
                tier1.add(p);
            }
        }

        Process selected = null;
        boolean fromTier2 = false;

        int selectedEffPri = -1;

        if (!tier1.isEmpty()) {
            selected = tier1.peek();
        } else if (!tier2.isEmpty()) {
            selected = getHighestPriority(tier2, currentTime);
            selectedEffPri = getEffectivePriority(selected, currentTime);  
            fromTier2 = true;
        }

        if (selected == null) {
            printDemotionMatrix(currentTime, tier1, tier2, null, quantum, contextSwitch);
            currentTime++;
            currentProcess = null;  
            continue;
        }

                if (currentProcess != null
                && currentProcess != selected
                && contextSwitch > 0) {

            System.out.println("Time " + currentTime
                    + " to " + (currentTime + contextSwitch)
                    + ": Context Switch");

            for (int t = 0; t < contextSwitch; t++) {
                printDemotionMatrix(currentTime, tier1, tier2, null, quantum, contextSwitch);
                currentTime++;

                for (Process p : allProcesses) {
                    if (p.arrivalTime <= currentTime
                            && p.remainingTime > 0
                            && !tier1.contains(p)
                            && !tier2.contains(p)
                            && p != selected) {
                        tier1.add(p);
                    }
                }
            }
        }

        if (fromTier2) {
            tier2.remove(selected);
            tier2EntryTime.remove(selected);
        } else {
            tier1.remove(selected);
        }

        if (selected.startTime == -1) {
            selected.startTime = currentTime;
        }

        if (fromTier2) {

            System.out.println("Time " + currentTime
                    + ": " + selected.name + " is running in Tier 2"
                    + " (effPri=" + selectedEffPri + ")");

            int runTime = selected.remainingTime;

            for (int i = 0; i < runTime; i++) {
                // print BEFORE the tick: state at this exact time unit
                printDemotionMatrix(currentTime, tier1, tier2, selected, quantum, contextSwitch);

                selected.remainingTime--;
                currentTime++;

                for (Process p : allProcesses) {
                    if (p.arrivalTime <= currentTime
                            && p.remainingTime > 0
                            && !tier1.contains(p)
                            && !tier2.contains(p)
                            && p != selected) {
                        tier1.add(p);
                    }
                }
            }

            completed++;
            selected.finishTime = currentTime;
            selected.turnaroundTime = selected.finishTime - selected.arrivalTime;
            selected.waitingTime = selected.turnaroundTime - selected.burstTime;
            selected.responseTime = selected.startTime - selected.arrivalTime;

            System.out.println(selected.name + " finished at " + currentTime);

        } else {

            System.out.println("Time " + currentTime
                    + ": " + selected.name + " is running in Tier 1");

            int runTime = Math.min(quantum, selected.remainingTime);

            for (int i = 0; i < runTime; i++) {
                // print BEFORE the tick
                printDemotionMatrix(currentTime, tier1, tier2, selected, quantum, contextSwitch);

                selected.remainingTime--;
                currentTime++;

                for (Process p : allProcesses) {
                    if (p.arrivalTime <= currentTime
                            && p.remainingTime > 0
                            && !tier1.contains(p)
                            && !tier2.contains(p)
                            && p != selected) {
                        tier1.add(p);
                    }
                }
            }

            if (selected.remainingTime == 0) {
                completed++;
                selected.finishTime = currentTime;
                selected.turnaroundTime = selected.finishTime - selected.arrivalTime;
                selected.waitingTime = selected.turnaroundTime - selected.burstTime;
                selected.responseTime = selected.startTime - selected.arrivalTime;

                System.out.println(selected.name + " finished at " + currentTime);
            } else {
                tier2.add(selected);
                tier2EntryTime.put(selected, currentTime);

                System.out.println(selected.name
                        + " moved to Tier 2, Remaining Time = " + selected.remainingTime);
            }
        }

        currentProcess = selected;
    }

    printResults(processes);
}
    private Process getHighestPriority(List<Process> tier2, int currentTime) {
        Process best = tier2.get(0);
        for (Process p : tier2) {
            int pEff = getEffectivePriority(p, currentTime);
            int bestEff = getEffectivePriority(best, currentTime);
            if (pEff < bestEff) {
                best = p;
            } else if (pEff == bestEff && p.arrivalTime < best.arrivalTime) {
                best = p;
            }
        }
        return best;
    }

    public int getEffectivePriority(Process p, int currentTime) {
        Integer entry = tier2EntryTime.get(p);
        int waited = (entry == null) ? 0 : (currentTime - entry);
        int Priority_dec = waited / Aging_threshold;
        return p.priority - Priority_dec;
    }

    private void printDemotionMatrix(int time,
                                 Queue<Process> tier1,
                                 List<Process> tier2,
                                 Process running,
                                 int quantum,
                                 int cs) {

    StringBuilder sb = new StringBuilder();
    sb.append("T=").append(time).append(" | ");
    sb.append("Running: ")
      .append(running == null ? "-" : running.name)
      .append(" | ");

    sb.append("Tier1=[");
    boolean first = true;
    for (Process p : tier1) {
        if (!first) sb.append(", ");
        sb.append(p.name).append("(rem:").append(p.remainingTime)
          .append(",pri:").append(p.priority).append(")");
        first = false;
    }
    sb.append("] | ");

    sb.append("Tier2=[");
    first = true;
    for (Process p : tier2) {
        if (!first) sb.append(", ");
        sb.append(p.name).append("(rem:").append(p.remainingTime)
          .append(",effPri:").append(getEffectivePriority(p, time))
          .append(",basePri:").append(p.priority).append(")");
        first = false;
    }
    sb.append("]  (Q=").append(quantum).append(", CS=").append(cs).append(")");

    System.out.println(sb.toString());
}

    public static void printResults(List<Process> processes) {
        double totalWT = 0;
        double totalTAT = 0;
        double totalRT = 0;

        System.out.println("\nProcess | Arrival | Burst | Finish | Turnaround | Waiting | Response");
        System.out.println("--------------------------------------------------------------------");
        for (Process p : processes) {
            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
            totalRT += p.responseTime;
            System.out.printf("%-7s | %-7d | %-5d | %-6d | %-10d | %-7d | %-8d\n",
                    p.name, p.arrivalTime, p.burstTime, p.finishTime, p.turnaroundTime, p.waitingTime, p.responseTime);
        }

        System.out.println("--------------------------------------------------------------------");
        System.out.printf("Average Turnaround Time: %.2f\n", (totalTAT / processes.size()));
        System.out.printf("Average Waiting Time: %.2f\n", (totalWT / processes.size()));
        System.out.printf("Average Response Time: %.2f\n", (totalRT / processes.size()));
    }
}