package cloudclusterscheduler;

import java.util.List;

public class SJFScheduler {

    public void execute(List<Process> processes, int contextSwitch) {
        int currentTime = 0;
        int completed = 0;
        int n = processes.size();
        Process currentProcess = null;

        System.out.println("Preemptive SJF");

        while (completed < n) {
            Process shortest = null;
            int minRemaining = Integer.MAX_VALUE;
            for (Process p : processes) {
                if (p.arrivalTime <= currentTime && p.remainingTime > 0) {
                    if (p.remainingTime < minRemaining) {
                        minRemaining = p.remainingTime;
                        shortest = p;
                    }
                    else if (p.remainingTime == minRemaining) {
                        if (shortest != null && p.arrivalTime < shortest.arrivalTime) {
                            shortest = p;
                        }
                    }
                }
            }

            if (shortest == null) {
                currentTime++;
                continue;
            }

            if (currentProcess != null && currentProcess != shortest && contextSwitch > 0) {
                System.out.println("Time " + currentTime + " to " + (currentTime + contextSwitch) + ": Context Switch");
                currentTime += contextSwitch;
                currentProcess = null; 
                continue;
            }

            if (shortest.startTime == -1) {
                shortest.startTime = currentTime;
            }

            System.out.println("Time " + currentTime + ": " + shortest.name + " is running");
            shortest.remainingTime--;
            currentTime++;
            currentProcess = shortest;

            if (shortest.remainingTime == 0) {
                completed++;
                shortest.finishTime = currentTime;
                shortest.turnaroundTime = shortest.finishTime - shortest.arrivalTime;
                shortest.waitingTime = shortest.turnaroundTime - shortest.burstTime;
                shortest.responseTime = shortest.startTime - shortest.arrivalTime;
            }
        }

        printResults(processes);
    }

    public static void printResults(List<Process> processes) {
        double totalWT = 0;
        double totalTAT = 0;

        System.out.println("\nProcess | Arrival | Burst | Finish | Turnaround | Waiting | Response");
        System.out.println("--------------------------------------------------------------------");
        for (Process p : processes) {
            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
            System.out.printf("%-7s | %-7d | %-5d | %-6d | %-10d | %-7d | %-8d\n",
                    p.name, p.arrivalTime, p.burstTime, p.finishTime, p.turnaroundTime, p.waitingTime, p.responseTime);
        }

        System.out.println("--------------------------------------------------------------------");
        System.out.printf("Average Turnaround Time: %.2f\n", (totalTAT / processes.size()));
        System.out.printf("Average Waiting Time: %.2f\n", (totalWT / processes.size()));
    }
}