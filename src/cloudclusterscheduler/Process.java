package cloudclusterscheduler;

public class Process {
    String name;
    int arrivalTime;
    int burstTime;
    int priority;
    int remainingTime;
    int startTime;
    int finishTime;
    int waitingTime;
    int turnaroundTime;
    int responseTime;
    
    public Process(String name, int arrivalTime, int burstTime, int priority) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.remainingTime = burstTime;
        this.startTime = -1;
    }
    
}
