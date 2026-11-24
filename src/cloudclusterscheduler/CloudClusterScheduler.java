package cloudclusterscheduler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    private static final boolean VERBOSE_SWEEP = false;

    private static void runConfig(String algo, int test, int q, int cs) {
        List<Process> ps = (test == 1) ? freshCopy(testCase1()) : freshCopy(testCase2());
        boolean sjf = algo.equals("SJF");
        PrintStream console = System.out;

        if (VERBOSE_SWEEP) {
            System.out.println("\n>>> " + algo + "  TC" + test
                    + (sjf ? "" : "  Q=" + q) + "  CS=" + cs);
        } else {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
        }
        if (sjf) new SJFScheduler().execute(ps, cs);
        else new ACSScheduler().execute(ps, q, cs);
        System.setOut(console);

        double wt = 0, tat = 0, rt = 0;
        for (Process p : ps) {
            wt += p.waitingTime;
            tat += p.turnaroundTime;
            rt += p.responseTime;
        }
        int n = ps.size();
        System.out.printf("%-4s  %-2s %-2d | %7.2f %7.2f %7.2f%n",
                algo, sjf ? "-" : String.valueOf(q), cs, wt / n, tat / n, rt / n);
    }

    public static void main(String[] args) {

        // ===== SJF (Preemptive SRTF) =====
        System.out.println("========== SJF - TEST CASE 1 (CS=0) ==========");
        new SJFScheduler().execute(freshCopy(testCase1()), 0);

        System.out.println("\n========== SJF - TEST CASE 2 (CS=1) ==========");
        new SJFScheduler().execute(freshCopy(testCase2()), 1);

        // ===== ACS =====
        System.out.println("\n\n========== ACS - TEST CASE 1: Q=3, CS=0 ==========");
        new ACSScheduler().execute(freshCopy(testCase1()), 3, 0);

        System.out.println("\n========== ACS - TEST CASE 2: Q=3, CS=1 ==========");
        new ACSScheduler().execute(freshCopy(testCase2()), 3, 1);

        // ===== Q / CS sweep for the report =====
        System.out.println("\n\n########## Q / CS SWEEP (summary) ##########");
        int[] qs = {1, 2, 3, 4};
        int[] css = {0, 1, 2};

        for (int test = 1; test <= 2; test++) {
            System.out.println("\n----- Test Case " + test + " -----");
            System.out.println("Algo  Q  CS |  avgWT  avgTAT   avgRT");
            for (int cs : css) {
                runConfig("SJF", test, 0, cs);
            }
            for (int q : qs) {
                for (int cs : css) {
                    runConfig("ACS", test, q, cs);
                }
            }
        }
    }
}