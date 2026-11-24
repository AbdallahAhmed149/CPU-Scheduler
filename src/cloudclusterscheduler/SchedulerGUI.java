package cloudclusterscheduler;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GUI for the Cloud-Cluster CPU scheduling simulator.
 *
 * The GUI does not re-implement any scheduling logic: it runs the existing SJFScheduler / ACSScheduler
 * and visualises what they print (Gantt chart, demotion matrix for every time step, per-process results).
 */
public class SchedulerGUI extends JPanel {

    // ------------------------------------------------------------------ colours
    private static final Color[] PALETTE = {
        new Color(0x4C78A8), new Color(0xF58518), new Color(0x54A24B), new Color(0xE45756),
        new Color(0x72B7B2), new Color(0xB279A2), new Color(0xFF9DA6), new Color(0x9D755D)
    };
    private static final Color CS_COLOR = new Color(0xBBBBBB);
    private static final Color IDLE_COLOR = new Color(0xF2F2F2);

    // ------------------------------------------------------------------ widgets
    private final DefaultTableModel inputModel =
            new DefaultTableModel(new Object[]{"Process", "Arrival", "Burst", "Priority"}, 0);
    private final JTable inputTable = new JTable(inputModel);
    private final JComboBox<String> algoBox = new JComboBox<>(new String[]{"ACS (Adaptive Container Scheduler)", "Preemptive SJF"});
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    private final JSpinner csSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
    private final GanttPanel gantt = new GanttPanel();
    private final DefaultTableModel matrixModel =
            new DefaultTableModel(new Object[]{"Time", "Running", "Tier 1", "Tier 2"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable matrixTable = new JTable(matrixModel);
    private final DefaultTableModel resultModel =
            new DefaultTableModel(new Object[]{"Process", "Arrival", "Burst", "Finish", "Turnaround", "Waiting", "Response"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable resultTable = new JTable(resultModel);
    private final JLabel summaryLabel = new JLabel(" ");
    private final Map<String, Color> colorOf = new HashMap<>();

    public SchedulerGUI() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ---- top: controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(new JLabel("Algorithm:"));
        controls.add(algoBox);
        controls.add(new JLabel("Quantum (Q):"));
        controls.add(quantumSpinner);
        controls.add(new JLabel("Context switch (CS):"));
        controls.add(csSpinner);
        JButton run = new JButton("Run simulation");
        run.addActionListener(e -> runSimulation());
        controls.add(run);
        algoBox.addActionListener(e -> quantumSpinner.setEnabled(algoBox.getSelectedIndex() == 0));
        add(controls, BorderLayout.NORTH);

        // ---- left: process input
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Processes"));
        left.add(new JScrollPane(inputTable), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
        JButton add = new JButton("Add process");
        add.addActionListener(e -> inputModel.addRow(new Object[]{"P" + (inputModel.getRowCount() + 1), 0, 1, 1}));
        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            if (inputTable.isEditing()) inputTable.getCellEditor().stopCellEditing();
            int r = inputTable.getSelectedRow();
            if (r >= 0) inputModel.removeRow(r);
        });
        JButton tc1 = new JButton("Load Test Case 1");
        tc1.addActionListener(e -> loadPreset(1));
        JButton tc2 = new JButton("Load Test Case 2");
        tc2.addActionListener(e -> loadPreset(2));
        buttons.add(add); buttons.add(remove); buttons.add(tc1); buttons.add(tc2);
        left.add(buttons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(300, 100));
        add(left, BorderLayout.WEST);

        // ---- centre: gantt on top, tabs below
        JScrollPane ganttScroll = new JScrollPane(gantt, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ganttScroll.setBorder(BorderFactory.createTitledBorder("Gantt chart"));
        ganttScroll.setPreferredSize(new Dimension(100, 140));

        matrixTable.setDefaultRenderer(Object.class, new MatrixRenderer());
        matrixTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);          // long rows scroll instead of being cut
        int[] widths = {50, 115, 320, 560};
        for (int i = 0; i < widths.length; i++) matrixTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        matrixTable.getSelectionModel().addListSelectionListener(e -> {
            int r = matrixTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0) {
                gantt.setMarker(Integer.parseInt(matrixModel.getValueAt(r, 0).toString()));
            }
        });
        JPanel matrixPane = new JPanel(new BorderLayout());
        matrixPane.add(new JLabel(" Demotion matrix: the state at the start of every time unit (click a row to mark it on the Gantt chart)."),
                BorderLayout.NORTH);
        matrixPane.add(new JScrollPane(matrixTable), BorderLayout.CENTER);

        JPanel resultPane = new JPanel(new BorderLayout());
        resultPane.add(new JScrollPane(resultTable), BorderLayout.CENTER);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        resultPane.add(summaryLabel, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Demotion matrix", matrixPane);
        tabs.addTab("Results", resultPane);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(ganttScroll, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        loadPreset(2);
        quantumSpinner.setValue(3);
        csSpinner.setValue(1);
    }

    // ------------------------------------------------------------------ presets
    private void loadPreset(int tc) {
        if (inputTable.isEditing()) inputTable.getCellEditor().stopCellEditing();
        inputModel.setRowCount(0);
        if (tc == 1) {
            inputModel.addRow(new Object[]{"P1", 0, 4, 2});
            inputModel.addRow(new Object[]{"P2", 1, 7, 3});
            inputModel.addRow(new Object[]{"P3", 3, 2, 1});
            quantumSpinner.setValue(3);
            csSpinner.setValue(0);
        } else {
            inputModel.addRow(new Object[]{"P1", 0, 5, 4});
            inputModel.addRow(new Object[]{"P2", 1, 6, 2});
            inputModel.addRow(new Object[]{"P3", 4, 4, 3});
            quantumSpinner.setValue(3);
            csSpinner.setValue(1);
        }
    }

    // ------------------------------------------------------------------ running
    /** Reads the inputs, runs the chosen scheduler and refreshes every view. Public so it can be tested. */
    public void runSimulation() {
        if (inputTable.isEditing()) inputTable.getCellEditor().stopCellEditing();

        List<Process> ps = readProcesses();
        if (ps == null) return;
        boolean acs = algoBox.getSelectedIndex() == 0;
        int q = (Integer) quantumSpinner.getValue();
        int cs = (Integer) csSpinner.getValue();

        // run the existing scheduler and capture what it prints
        PrintStream console = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, "UTF-8"));
            if (acs) new ACSScheduler().execute(ps, q, cs);
            else new SJFScheduler().execute(ps, cs);
        } catch (Exception ex) {
            System.setOut(console);
            JOptionPane.showMessageDialog(this, "Simulation failed: " + ex, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            System.setOut(console);
        }
        String out;
        try { out = buf.toString("UTF-8"); } catch (Exception ex) { out = buf.toString(); }

        colorOf.clear();
        for (int i = 0; i < ps.size(); i++) colorOf.put(ps.get(i).name, PALETTE[i % PALETTE.length]);

        show(out, ps, acs, q, cs);
    }

    private List<Process> readProcesses() {
        List<Process> ps = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (inputModel.getRowCount() == 0) {
            error("Add at least one process.");
            return null;
        }
        for (int r = 0; r < inputModel.getRowCount(); r++) {
            try {
                String name = String.valueOf(inputModel.getValueAt(r, 0)).trim();
                int arrival = Integer.parseInt(String.valueOf(inputModel.getValueAt(r, 1)).trim());
                int burst = Integer.parseInt(String.valueOf(inputModel.getValueAt(r, 2)).trim());
                int pri = Integer.parseInt(String.valueOf(inputModel.getValueAt(r, 3)).trim());
                if (name.isEmpty()) throw new IllegalArgumentException("The process name is empty.");
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate process name: " + name);
                if (arrival < 0) throw new IllegalArgumentException(name + ": arrival time cannot be negative.");
                if (burst < 1) throw new IllegalArgumentException(name + ": burst time must be at least 1.");
                ps.add(new Process(name, arrival, burst, pri));
            } catch (NumberFormatException ex) {
                error("Row " + (r + 1) + ": arrival, burst and priority must be whole numbers.");
                return null;
            } catch (IllegalArgumentException ex) {
                error("Row " + (r + 1) + ": " + ex.getMessage());
                return null;
            }
        }
        return ps;
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Invalid input", JOptionPane.WARNING_MESSAGE);
    }

    // ------------------------------------------------------------------ output -> views
    private static final Pattern MATRIX = Pattern.compile(
            "^T=(\\d+) \\| Running: (\\S+) \\| Tier1=\\[(.*?)\\] \\| Tier2=\\[(.*?)\\]  \\(Q");
    private static final Pattern RUN_TIER = Pattern.compile("^Time (\\d+): (\\S+) is running in Tier (\\d)");
    private static final Pattern RUN_SJF = Pattern.compile("^Time (\\d+): (\\S+) is running$");
    private static final Pattern CS_LINE = Pattern.compile("^Time (\\d+) to (\\d+): Context Switch");
    private static final Pattern ITEM = Pattern.compile("(\\S+?)\\(rem:(\\d+),(?:pri|effPri):(-?\\d+)(?:,basePri:(-?\\d+))?\\)");

    private void show(String out, List<Process> ps, boolean acs, int q, int cs) {
        int makespan = 0;
        for (Process p : ps) makespan = Math.max(makespan, p.finishTime);

        String[] unit = new String[makespan];
        int[] tier = new int[makespan];
        Arrays.fill(unit, "IDLE");

        List<int[]> csRanges = new ArrayList<>();
        matrixModel.setRowCount(0);
        int currentTier = 0;

        for (String line : out.split("\\r?\\n")) {
            Matcher m;
            if ((m = CS_LINE.matcher(line)).find()) {
                int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
                csRanges.add(new int[]{a, b});
                for (int t = a; t < b && t < makespan; t++) unit[t] = "CS";
            } else if ((m = RUN_TIER.matcher(line)).find()) {
                currentTier = Integer.parseInt(m.group(3));
            } else if ((m = MATRIX.matcher(line)).find()) {
                int t = Integer.parseInt(m.group(1));
                String running = m.group(2);
                if (!running.equals("-") && t < makespan) { unit[t] = running; tier[t] = currentTier; }
                String label = running;
                if (running.equals("-")) label = isCs(csRanges, t) ? "Context switch" : "idle";
                matrixModel.addRow(new Object[]{t, label, "Tier 1: " + fmt(m.group(3), false), "Tier 2: " + fmt(m.group(4), true)});
            } else if ((m = RUN_SJF.matcher(line)).find()) {
                int t = Integer.parseInt(m.group(1));
                if (t < makespan) unit[t] = m.group(2);
            }
        }
        if (!acs) {
            // SJF prints no matrix: build a simple per-unit timeline instead
            for (int t = 0; t < makespan; t++) {
                String label = unit[t].equals("CS") ? "Context switch" : unit[t].equals("IDLE") ? "idle" : unit[t];
                matrixModel.addRow(new Object[]{t, label, "(SJF has no tiers)", ""});
            }
        }
        // closing row: everything finished
        matrixModel.addRow(new Object[]{makespan, "done", acs ? "Tier 1: [Empty]" : "", acs ? "Tier 2: [Empty]" : ""});

        gantt.setData(unit, tier, colorOf, acs);

        // results
        resultModel.setRowCount(0);
        double wt = 0, tat = 0, rt = 0, burst = 0;
        for (Process p : ps) {
            resultModel.addRow(new Object[]{p.name, p.arrivalTime, p.burstTime, p.finishTime, p.turnaroundTime, p.waitingTime, p.responseTime});
            wt += p.waitingTime; tat += p.turnaroundTime; rt += p.responseTime; burst += p.burstTime;
        }
        int n = ps.size();
        summaryLabel.setText(String.format(Locale.US,
                "<html><b>Average waiting %.2f</b> &nbsp;|&nbsp; <b>Average turnaround %.2f</b> &nbsp;|&nbsp; Average response %.2f"
                        + "<br>Makespan %d &nbsp;|&nbsp; CPU utilization %.2f%% &nbsp;|&nbsp; Context switches %d &nbsp;|&nbsp; %s</html>",
                wt / n, tat / n, rt / n, makespan, 100.0 * burst / makespan, csRanges.size(),
                acs ? "ACS, Q=" + q + ", CS=" + cs : "SJF, CS=" + cs));
    }

    private static boolean isCs(List<int[]> ranges, int t) {
        for (int[] r : ranges) if (t >= r[0] && t < r[1]) return true;
        return false;
    }

    private static String fmt(String s, boolean tier2) {
        Matcher m = ITEM.matcher(s);
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String base = m.group(4);
            if (tier2 && base != null && !base.equals(m.group(3)))
                parts.add(m.group(1) + "(rem:" + m.group(2) + ", pri:" + m.group(3) + ", base:" + base + ")");
            else
                parts.add(m.group(1) + "(rem:" + m.group(2) + ", pri:" + m.group(3) + ")");
        }
        return parts.isEmpty() ? "[Empty]" : "[" + String.join(", ", parts) + "]";
    }

    // ------------------------------------------------------------------ helpers for painting
    private static Color textOn(Color c) {
        double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return lum > 150 ? Color.BLACK : Color.WHITE;
    }

    private class MatrixRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (!sel) {
                String running = String.valueOf(matrixModel.getValueAt(row, 1));
                Color bg = Color.WHITE;
                if (running.equals("Context switch")) bg = new Color(0xE6E6E6);
                else if (colorOf.containsKey(running)) {
                    Color base = colorOf.get(running);
                    bg = new Color((base.getRed() + 255 * 3) / 4, (base.getGreen() + 255 * 3) / 4, (base.getBlue() + 255 * 3) / 4);
                }
                c.setBackground(bg);
                c.setForeground(Color.BLACK);
            }
            return c;
        }
    }

    // ------------------------------------------------------------------ gantt chart
    private static class GanttPanel extends JPanel {
        private static final int UNIT = 30, X0 = 20, Y0 = 28, H = 52;
        private String[] unit = new String[0];
        private int[] tier = new int[0];
        private Map<String, Color> colors = new HashMap<>();
        private boolean showTiers;
        private int marker = -1;

        GanttPanel() { setBackground(Color.WHITE); }

        void setData(String[] unit, int[] tier, Map<String, Color> colors, boolean showTiers) {
            this.unit = unit; this.tier = tier; this.colors = new HashMap<>(colors); this.showTiers = showTiers;
            this.marker = -1;
            setPreferredSize(new Dimension(X0 * 2 + unit.length * UNIT, 120));
            revalidate(); repaint();
        }

        void setMarker(int t) {
            marker = t;
            repaint();
            scrollRectToVisible(new Rectangle(X0 + t * UNIT - UNIT * 2, 0, UNIT * 5, 10));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = unit.length;
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g.getFontMetrics();

            int i = 0;
            while (i < n) {
                int j = i;
                while (j + 1 < n && unit[j + 1].equals(unit[i]) && tier[j + 1] == tier[i]) j++;
                int x = X0 + i * UNIT, w = (j - i + 1) * UNIT;
                String lab = unit[i];
                Color c = lab.equals("CS") ? CS_COLOR : lab.equals("IDLE") ? IDLE_COLOR : colors.getOrDefault(lab, Color.GRAY);
                g.setColor(c);
                g.fillRect(x, Y0, w, H);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, Y0, w, H);
                String text = lab.equals("IDLE") ? "idle" : lab;
                g.setColor(textOn(c));
                g.drawString(text, x + (w - fm.stringWidth(text)) / 2, Y0 + H / 2 + (showTiers && tier[i] > 0 ? 0 : 5));
                if (showTiers && tier[i] > 0) {
                    g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
                    String tt = "Tier " + tier[i];
                    g.drawString(tt, x + (w - g.getFontMetrics().stringWidth(tt)) / 2, Y0 + H / 2 + 14);
                    g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                }
                i = j + 1;
            }

            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g.setColor(Color.BLACK);
            FontMetrics fm2 = g.getFontMetrics();
            for (int t = 0; t <= n; t++) {
                int x = X0 + t * UNIT;
                g.drawLine(x, Y0 + H, x, Y0 + H + 5);
                String s = String.valueOf(t);
                g.drawString(s, x - fm2.stringWidth(s) / 2, Y0 + H + 18);
            }
            if (marker >= 0 && marker <= n) {
                g.setColor(new Color(255, 0, 0, 70));
                g.fillRect(X0 + marker * UNIT, Y0 - 6, UNIT, H + 12);
                g.setColor(Color.RED);
                g.drawRect(X0 + marker * UNIT, Y0 - 6, UNIT, H + 12);
            }
            g.setColor(Color.GRAY);
            g.drawString("grey = context switch, light = idle", X0, 16);
        }
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Cloud-Cluster CPU Scheduling Simulator - CS241 Assignment 2");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            SchedulerGUI gui = new SchedulerGUI();
            f.setContentPane(gui);
            f.setSize(1150, 720);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            gui.runSimulation();
        });
    }
}
