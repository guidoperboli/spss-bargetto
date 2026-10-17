package cpssp;

import cpssp.model.CpsspInstance;
import cpssp.opt.OptCfg;
import cpssp.opt.OptResult;
import cpssp.opt.ProgType;

import java.io.File;
import java.util.Locale;

/**
 * Human-friendly command-line interface for the CPSSP / SVCSBPP-CL-SS solver.
 */
public class Main {

    public static void main(String[] args) {
        CliOptions opts;
        try {
            opts = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("\n[ERROR] " + e.getMessage());
            System.err.println("Run with -h or --help for complete usage instructions.\n");
            System.exit(1);
            return;
        }

        if (opts.helpRequested) {
            CliOptions.printHelp();
            return;
        }

        // Validate instance file existence
        File instFile = new File(opts.instancePath);
        if (!instFile.exists() || !instFile.isFile()) {
            System.err.println("\n[ERROR] Instance file not found: " + opts.instancePath);
            System.err.println("Please check that the file path is correct.\n");
            System.exit(1);
            return;
        }

        OptCfg cfg = opts.toOptCfg();

        if (!opts.quiet) {
            printBanner(opts, cfg);
        }

        try {
            long loadStart = System.currentTimeMillis();
            CpsspInstance instance = new CpsspInstance(opts.instancePath);
            double loadTime = (System.currentTimeMillis() - loadStart) / 1000.0;

            if (!opts.quiet) {
                printInstanceSummary(instance, loadTime);
            }

            Optimizer optimizer = new Optimizer();
            optimizer.setVerbose(opts.verbose);

            long solveStart = System.currentTimeMillis();
            OptResult result = optimizer.optimize(instance, cfg, opts.outputSolutionFile);
            double totalSolveTime = (System.currentTimeMillis() - solveStart) / 1000.0;

            // Solution export notification if requested
            if (opts.outputSolutionFile != null) {
                System.out.println("\n[INFO] Exported solution to: " + opts.outputSolutionFile);
            }

            printResults(opts, result, totalSolveTime);

        } catch (Exception e) {
            System.err.println("\n[ERROR] Optimization failed with exception:");
            System.err.println(e.getMessage());
            if (opts.verbose) {
                e.printStackTrace();
            } else {
                System.err.println("Tip: Run with --verbose for complete stack trace.");
            }
            System.exit(2);
        }
    }

    private static void printBanner(CliOptions opts, OptCfg cfg) {
        int kpi = (cfg.alg_mode / 10000) % 10;
        int bff = (cfg.alg_mode / 1000) % 10;
        int prt = (cfg.alg_mode / 100) % 10;
        int prg = (cfg.alg_mode / 10) % 10;
        int lp = cfg.alg_mode % 10;

        System.out.println("==========================================================================================");
        System.out.println("CPSSP Solver — Stochastic Capacity Planning with Supplier Selection");
        System.out.println("==========================================================================================");
        System.out.printf("  Instance:         %s%n", opts.instancePath);
        System.out.printf("  Mode:             %s (KPI %d: %s)%n", opts.modeName, kpi, opts.getKpiDescription());
        System.out.printf("  Formulation:      %s%n", (prg != 0 ? ProgType.RALB : ProgType.FDE));
        System.out.printf("  Supplier Enum:    %s%n", (bff != 0 ? "Enabled (Combinatorial C(M,k))" : "Disabled (MIP Branching)"));
        System.out.printf("  Priorities:       %s%n", (prt != 0 ? "Enabled (w -> y0 -> z -> x)" : "Disabled"));
        System.out.printf("  Time Limit:       %.1f s (Scenario subproblems: %.1f s)%n", cfg.timelimsec, cfg.maxgap);
        System.out.printf("  Threads / Memory: %d thread(s) / %.0f MB%n", cfg.threads2use, cfg.maxmem);
        System.out.printf("  Internal Mode:    %05d%n", cfg.alg_mode);
        System.out.println("==========================================================================================");
    }

    private static void printInstanceSummary(CpsspInstance inst, double loadTime) {
        System.out.printf("Instance loaded in %.3fs: %d suppliers (min %d), %d 1st-stage bins, %d scenarios (max %d items/scen)%n",
                loadTime, inst.nrOfSuppliers, inst.minNrOfSuppliers, inst.nrOfStage1Bins,
                inst.nrOfScenarios, inst.maxNrOfScenarioItems);
        System.out.println("------------------------------------------------------------------------------------------");
    }

    private static void printResults(CliOptions opts, OptResult res, double wallTime) {
        System.out.println("\n==========================================================================================");
        System.out.println("OPTIMIZATION RESULTS");
        System.out.println("==========================================================================================");
        System.out.printf(Locale.US, "  Status:               %s%n", res.getStatus());
        System.out.printf(Locale.US, "  Objective Value:      %.2f%n", res.objValue);
        System.out.printf(Locale.US, "  Best Bound:           %.2f%n", res.bestBound);
        System.out.printf(Locale.US, "  Optimality Gap:       %.2f%%%n", res.optGap * 100.0);
        System.out.printf(Locale.US, "  Total Runtime:        %.3f s%n", res.runTime > 0 ? res.runTime : wallTime);

        if (res.integerValues != null && res.integerValues.size() > 1) {
            System.out.printf(Locale.US, "  Selected Suppliers:   %d%n", res.integerValues.get(1));
        }

        // Detailed stochastic analysis table if mode is "all" / "stochastic" (KPI=5)
        int kpi = opts.getKpi();
        if (kpi == 5 && res.doubleValues != null && res.doubleValues.size() >= 6) {
            double ev = res.doubleValues.get(0);
            double ws = res.doubleValues.get(1);
            double rp = res.doubleValues.get(2);
            double eev = res.doubleValues.get(3);
            double evpi = res.doubleValues.get(4);
            double vss = res.doubleValues.get(5);

            System.out.println("------------------------------------------------------------------------------------------");
            System.out.println("STOCHASTIC OPTIMIZATION MEASURES");
            System.out.println("------------------------------------------------------------------------------------------");
            System.out.printf(Locale.US, "  Expected Value Problem (EV):                   %10.2f%n", ev);
            System.out.printf(Locale.US, "  Wait-and-See Lower Bound (WS):                 %10.2f%n", ws);
            System.out.printf(Locale.US, "  Recourse Problem / Upper Bound (RP):           %10.2f%n", rp);
            System.out.printf(Locale.US, "  Expected Result of EV Policy (EEV):            %10.2f%n", eev);
            System.out.printf(Locale.US, "  Stochastic Ordering:                           EV <= WS <= RP <= EEV (%s)%n",
                    (ev <= ws + 1e-4 && ws <= rp + 1e-4 && rp <= eev + 1e-4 ? "VERIFIED" : "TOLERANCE"));
            System.out.println("------------------------------------------------------------------------------------------");
            System.out.printf(Locale.US, "  Expected Value of Perfect Info (EVPI = RP - WS): %8.2f  (%.2f%%)%n",
                    evpi, (ws > 0 ? 100.0 * evpi / ws : 0.0));
            System.out.printf(Locale.US, "  Value of Stochastic Solution (VSS = EEV - RP):   %8.2f  (%.2f%%)%n",
                    vss, (rp > 0 ? 100.0 * vss / rp : 0.0));
        }

        // Progressive Hedging metrics (KPI=8)
        if (kpi == 8 && res.doubleValues != null && res.doubleValues.size() >= 3) {
            int iters = (res.integerValues != null && res.integerValues.size() > 2) ? res.integerValues.get(2) : 0;
            double finalDelta = res.doubleValues.get(2);
            System.out.println("------------------------------------------------------------------------------------------");
            System.out.println("PROGRESSIVE HEDGING DECOMPOSITION DETAILS");
            System.out.println("------------------------------------------------------------------------------------------");
            System.out.printf(Locale.US, "  Iterations Completed:                          %d%n", iters);
            System.out.printf(Locale.US, "  Final Consensus Distance (Delta):              %.6f%n", finalDelta);
            System.out.printf(Locale.US, "  Best Upper Bound (Feasible integer recourse):  %10.2f%n", res.objValue);
            System.out.printf(Locale.US, "  Best Lower Bound (Lagrangian relaxation):      %10.2f%n", res.bestBound);
        }

        System.out.println("==========================================================================================");
    }
}
