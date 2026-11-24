package cloudclusterscheduler;

import java.util.ArrayList;
import java.util.List;

public class CloudClusterScheduler {

    // Test Case 1 assignment
    private static List<Process> testCase1() {
        List<Process> ps = new ArrayList<>();
        ps.add(new Process("P1", 0, 4, 2));
        ps.add(new Process("P2", 1, 7, 3));
        ps.add(new Process("P3", 3, 2, 1));
        return ps;
    }

    // Test Case 2 assignment
    private static List<Process> testCase2() {
        List<Process> ps = new ArrayList<>();
        ps.add(new Process("P1", 0, 5, 4));
        ps.add(new Process("P2", 1, 6, 2));
        ps.add(new Process("P3", 4, 4, 3));
        return ps;
    }

    private static List<Process> freshCopy(List<Process> src) {
        List<Process> copy = new ArrayList<>();
        for (Process p : src) {
            copy.add(new Process(p.name, p.arrivalTime, p.burstTime, p.priority));
        }
        return copy;
    }

    public static void main(String[] args) {

        // ===== SJF (Preemptive SRTF) =====
        System.out.println("========== SJF — TEST CASE 1 (CS=0) ==========");
        new SJFScheduler().execute(freshCopy(testCase1()), 0);

        System.out.println("\n========== SJF — TEST CASE 2 (CS=1) ==========");
        new SJFScheduler().execute(freshCopy(testCase2()), 1);

        // ===== ACS =====
        System.out.println("\n\n========== ACS — TEST CASE 1: Q=3, CS=0 ==========");
        new ACSScheduler().execute(freshCopy(testCase1()), 3, 0);

        System.out.println("\n========== ACS — TEST CASE 2: Q=3, CS=1 ==========");
        new ACSScheduler().execute(freshCopy(testCase2()), 3, 1);

        // ===== Q / CS sweep for the report =====
        System.out.println("\n\n########## ACS Q / CS SWEEP ##########");
        int[] qs = {1, 2, 3, 4};
        int[] css = {0, 1, 2};

        for (int test = 1; test <= 2; test++) {
            System.out.println("\n----- Test Case " + test + " -----");
            for (int q : qs) {
                for (int cs : css) {
                    List<Process> ps = (test == 1) ? freshCopy(testCase1())
                                                : freshCopy(testCase2());
                    System.out.println("\n>>> TC" + test + "  Q=" + q + "  CS=" + cs);
                    new ACSScheduler().execute(ps, q, cs);
                }
            }
        }
    }
}