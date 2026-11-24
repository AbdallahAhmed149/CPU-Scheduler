package cloudclusterscheduler;

import java.io.*;
import java.util.*;

public class ReportData {

    static List<Process> tc(int t) {
        List<Process> ps = new ArrayList<>();
        if (t == 1) {
            ps.add(new Process("P1", 0, 4, 2));
            ps.add(new Process("P2", 1, 7, 3));
            ps.add(new Process("P3", 3, 2, 1));
        } else {
            ps.add(new Process("P1", 0, 5, 4));
            ps.add(new Process("P2", 1, 6, 2));
            ps.add(new Process("P3", 4, 4, 3));
        }
        return ps;
    }

    // runs one config silently and returns {avgWT, avgTAT, avgRT, makespan, utilization%, contextSwitches}
    static double[] run(String algo, int t, int q, int cs) {
        List<Process> ps = tc(t);
        PrintStream orig = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
        if (algo.equals("SJF")) new SJFScheduler().execute(ps, cs);
        else new ACSScheduler().execute(ps, q, cs);
        System.setOut(orig);

        int switches = 0;
        for (String line : buf.toString().split("\n"))
            if (line.startsWith("Time") && line.contains("Context Switch")) switches++;

        double wt = 0, tat = 0, rt = 0, burst = 0;
        int end = 0;
        for (Process p : ps) {
            wt += p.waitingTime; tat += p.turnaroundTime; rt += p.responseTime;
            burst += p.burstTime; end = Math.max(end, p.finishTime);
        }
        int n = ps.size();
        return new double[]{wt / n, tat / n, rt / n, end, 100.0 * burst / end, switches};
    }

    public static void main(String[] args) {
        System.out.println("algo,tc,Q,CS,avgWT,avgTAT,avgRT,makespan,util,switches");
        for (int t = 1; t <= 2; t++) {
            for (int cs = 0; cs <= 2; cs++) {
                double[] r = run("SJF", t, 0, cs);
                System.out.printf(Locale.US, "SJF,%d,-,%d,%.4f,%.4f,%.4f,%.0f,%.2f,%.0f%n", t, cs, r[0], r[1], r[2], r[3], r[4], r[5]);
            }
            for (int q = 1; q <= 4; q++)
                for (int cs = 0; cs <= 2; cs++) {
                    double[] r = run("ACS", t, q, cs);
                    System.out.printf(Locale.US, "ACS,%d,%d,%d,%.4f,%.4f,%.4f,%.0f,%.2f,%.0f%n", t, q, cs, r[0], r[1], r[2], r[3], r[4], r[5]);
                }
        }
    }
}
