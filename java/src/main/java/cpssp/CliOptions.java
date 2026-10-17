package cpssp;

import cpssp.opt.OptCfg;
import cpssp.opt.ProgType;

import java.io.File;
import java.util.*;

/**
 * Human-friendly command-line options parser for the CPSSP solver.
 * 
 * Supports both named options (--instance, --mode, --time-limit, etc.)
 * and positional/legacy arguments for full backward compatibility.
 */
public class CliOptions {

    public String instancePath = null;
    public String modeName = "ub";              // default: ub (RALB + heuristic recourse)
    public Integer legacyAlgMode = null;        // explicit 5-digit code if provided
    public String configPath = null;
    public double timeLimit = 3600.0;
    public double scenarioTimeLimit = 600.0;
    public int threads = 1;
    public double memory = 1024.0;

    public ProgType formulation = null;         // FDE, RALB (or null to deduce from mode)
    public Boolean enumSuppliers = null;        // combinatorial enumeration flag
    public Boolean priorities = true;           // hierarchical priorities (default true)
    public Integer lpAlgo = 0;                  // 0: auto, 1: primal, 2: dual, 3: barrier
    public String outputSolutionFile = null;    // file to export .sspsol

    public boolean verbose = false;
    public boolean quiet = false;
    public boolean helpRequested = false;

    // Progressive Hedging parameters
    public int phIterations = 30;
    public double phRho0 = 1.0;
    public double phAlpha = 1.05;
    public double phRhoMax = 1000.0;
    public double phTolerance = 1e-4;

    // Supported human-readable mode names mapped to standard KPI digits
    public static final Map<String, Integer> MODE_TO_KPI = new LinkedHashMap<>();
    public static final Map<Integer, String> KPI_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        // Mode mappings
        MODE_TO_KPI.put("ub", 4);
        MODE_TO_KPI.put("heuristic", 4);
        MODE_TO_KPI.put("ralb-is", 4);

        MODE_TO_KPI.put("all", 5);
        MODE_TO_KPI.put("stochastic", 5);
        MODE_TO_KPI.put("so", 5);

        MODE_TO_KPI.put("ws", 2);
        MODE_TO_KPI.put("wait-and-see", 2);
        MODE_TO_KPI.put("perfect-info", 2);

        MODE_TO_KPI.put("ev-eev", 3);
        MODE_TO_KPI.put("ev", 3);
        MODE_TO_KPI.put("eev", 3);

        MODE_TO_KPI.put("fde", 1);
        MODE_TO_KPI.put("exact", 1);
        MODE_TO_KPI.put("deterministic-equivalent", 1);

        MODE_TO_KPI.put("ralb", 1);
        MODE_TO_KPI.put("lower-bound", 1);
        MODE_TO_KPI.put("lb", 1);

        MODE_TO_KPI.put("saa", 6);
        MODE_TO_KPI.put("error", 6);
        MODE_TO_KPI.put("sampling", 6);

        MODE_TO_KPI.put("feasibility", 0);
        MODE_TO_KPI.put("check", 0);

        MODE_TO_KPI.put("determ", 7);
        MODE_TO_KPI.put("export", 7);

        MODE_TO_KPI.put("ph", 8);
        MODE_TO_KPI.put("progressive-hedging", 8);
        MODE_TO_KPI.put("hedging", 8);

        // Descriptions
        KPI_DESCRIPTIONS.put(4, "Row Aggregation Lower Bound + Heuristic Recourse Upper Bound (RALB + FDEE-IS) [Default]");
        KPI_DESCRIPTIONS.put(5, "Complete Stochastic Optimization Analysis (EV, WS, RP, EEV, EVPI, VSS)");
        KPI_DESCRIPTIONS.put(2, "Wait-and-See (WS) / Perfect Information Lower Bound");
        KPI_DESCRIPTIONS.put(3, "Expected Value Problem (EV) & Expected Result of EV Policy (EEV)");
        KPI_DESCRIPTIONS.put(1, "Direct MILP Solve (FDE with packing or RALB volume surrogate)");
        KPI_DESCRIPTIONS.put(6, "Sample Average Approximation (SAA) Error & 95% Confidence Intervals");
        KPI_DESCRIPTIONS.put(0, "Feasibility Test (iteratively expands bin pool if infeasible)");
        KPI_DESCRIPTIONS.put(7, "Deterministic Model Solve & Export to .sspsol file");
        KPI_DESCRIPTIONS.put(8, "Progressive Hedging (Augmented Lagrangian Scenario Decomposition)");
    }

    /**
     * Parses command line arguments.
     */
    public static CliOptions parse(String[] args) {
        CliOptions opts = new CliOptions();
        if (args == null || args.length == 0) {
            opts.helpRequested = true;
            return opts;
        }

        List<String> positionalArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.isEmpty()) continue;

            if (arg.equals("-h") || arg.equals("--help")) {
                opts.helpRequested = true;
                return opts;
            } else if (arg.equals("-v") || arg.equals("--verbose")) {
                opts.verbose = true;
            } else if (arg.equals("-q") || arg.equals("--quiet")) {
                opts.quiet = true;
            } else if (arg.equals("-i") || arg.equals("--instance")) {
                opts.instancePath = requireNext(args, ++i, "--instance");
            } else if (arg.startsWith("--instance=")) {
                opts.instancePath = arg.substring("--instance=".length());
            } else if (arg.equals("-m") || arg.equals("--mode")) {
                opts.modeName = requireNext(args, ++i, "--mode").toLowerCase();
            } else if (arg.startsWith("--mode=")) {
                opts.modeName = arg.substring("--mode=".length()).toLowerCase();
            } else if (arg.equals("--alg-mode")) {
                String val = requireNext(args, ++i, "--alg-mode");
                opts.legacyAlgMode = Integer.parseInt(val);
            } else if (arg.startsWith("--alg-mode=")) {
                opts.legacyAlgMode = Integer.parseInt(arg.substring("--alg-mode=".length()));
            } else if (arg.equals("-c") || arg.equals("--config")) {
                opts.configPath = requireNext(args, ++i, "--config");
            } else if (arg.startsWith("--config=")) {
                opts.configPath = arg.substring("--config=".length());
            } else if (arg.equals("-t") || arg.equals("--time-limit")) {
                opts.timeLimit = Double.parseDouble(requireNext(args, ++i, "--time-limit"));
            } else if (arg.startsWith("--time-limit=")) {
                opts.timeLimit = Double.parseDouble(arg.substring("--time-limit=".length()));
            } else if (arg.equals("--scenario-time-limit") || arg.equals("--sub-time-limit")) {
                opts.scenarioTimeLimit = Double.parseDouble(requireNext(args, ++i, "--scenario-time-limit"));
            } else if (arg.startsWith("--scenario-time-limit=")) {
                opts.scenarioTimeLimit = Double.parseDouble(arg.substring("--scenario-time-limit=".length()));
            } else if (arg.equals("--threads")) {
                opts.threads = Integer.parseInt(requireNext(args, ++i, "--threads"));
            } else if (arg.startsWith("--threads=")) {
                opts.threads = Integer.parseInt(arg.substring("--threads=".length()));
            } else if (arg.equals("--memory")) {
                opts.memory = Double.parseDouble(requireNext(args, ++i, "--memory"));
            } else if (arg.startsWith("--memory=")) {
                opts.memory = Double.parseDouble(arg.substring("--memory=".length()));
            } else if (arg.equals("-f") || arg.equals("--formulation")) {
                String val = requireNext(args, ++i, "--formulation").toUpperCase();
                opts.formulation = ProgType.valueOf(val);
            } else if (arg.startsWith("--formulation=")) {
                opts.formulation = ProgType.valueOf(arg.substring("--formulation=".length()).toUpperCase());
            } else if (arg.equals("-e") || arg.equals("--enum-suppliers") || arg.equals("--enumerate")) {
                opts.enumSuppliers = true;
            } else if (arg.equals("--no-enum")) {
                opts.enumSuppliers = false;
            } else if (arg.equals("-p") || arg.equals("--priorities")) {
                opts.priorities = true;
            } else if (arg.equals("--no-priorities")) {
                opts.priorities = false;
            } else if (arg.equals("--lp-algo")) {
                opts.lpAlgo = parseLpAlgo(requireNext(args, ++i, "--lp-algo"));
            } else if (arg.startsWith("--lp-algo=")) {
                opts.lpAlgo = parseLpAlgo(arg.substring("--lp-algo=".length()));
            } else if (arg.equals("-o") || arg.equals("--output-sol")) {
                opts.outputSolutionFile = requireNext(args, ++i, "--output-sol");
            } else if (arg.startsWith("--output-sol=")) {
                opts.outputSolutionFile = arg.substring("--output-sol=".length());
            } else if (arg.equals("--ph-iterations") || arg.equals("--ph-iter")) {
                opts.phIterations = Integer.parseInt(requireNext(args, ++i, "--ph-iterations"));
            } else if (arg.startsWith("--ph-iterations=")) {
                opts.phIterations = Integer.parseInt(arg.substring("--ph-iterations=".length()));
            } else if (arg.equals("--ph-rho") || arg.equals("--rho0")) {
                opts.phRho0 = Double.parseDouble(requireNext(args, ++i, "--ph-rho"));
            } else if (arg.startsWith("--ph-rho=")) {
                opts.phRho0 = Double.parseDouble(arg.substring("--ph-rho=".length()));
            } else if (arg.startsWith("--rho0=")) {
                opts.phRho0 = Double.parseDouble(arg.substring("--rho0=".length()));
            } else if (arg.equals("--ph-alpha") || arg.equals("--alpha")) {
                opts.phAlpha = Double.parseDouble(requireNext(args, ++i, "--ph-alpha"));
            } else if (arg.startsWith("--ph-alpha=")) {
                opts.phAlpha = Double.parseDouble(arg.substring("--ph-alpha=".length()));
            } else if (arg.equals("--ph-rho-max")) {
                opts.phRhoMax = Double.parseDouble(requireNext(args, ++i, "--ph-rho-max"));
            } else if (arg.startsWith("--ph-rho-max=")) {
                opts.phRhoMax = Double.parseDouble(arg.substring("--ph-rho-max=".length()));
            } else if (arg.equals("--ph-tol") || arg.equals("--ph-tolerance")) {
                opts.phTolerance = Double.parseDouble(requireNext(args, ++i, "--ph-tol"));
            } else if (arg.startsWith("--ph-tol=")) {
                opts.phTolerance = Double.parseDouble(arg.substring("--ph-tol=".length()));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown command line option: " + arg + ". Run with --help for usage.");
            } else {
                positionalArgs.add(arg);
            }
        }

        // Process positional arguments for backward compatibility
        if (opts.instancePath == null && !positionalArgs.isEmpty()) {
            opts.instancePath = positionalArgs.remove(0);
        }

        if (!positionalArgs.isEmpty()) {
            String second = positionalArgs.remove(0);
            File f = new File(second);
            if (f.exists()) {
                opts.configPath = second;
            } else if (second.matches("\\d{5}")) {
                opts.legacyAlgMode = Integer.parseInt(second);
            } else if (MODE_TO_KPI.containsKey(second.toLowerCase())) {
                opts.modeName = second.toLowerCase();
            } else {
                // If numeric, might be alg_mode
                try {
                    opts.legacyAlgMode = Integer.parseInt(second);
                } catch (NumberFormatException ignored) {
                    opts.modeName = second.toLowerCase();
                }
            }
        }

        if (!positionalArgs.isEmpty()) {
            String third = positionalArgs.remove(0);
            if (third.equalsIgnoreCase("true") || third.equalsIgnoreCase("verbose") || third.equals("1")) {
                opts.verbose = true;
            }
        }

        // Validate instance path
        if (opts.instancePath == null) {
            throw new IllegalArgumentException("Missing required instance file. Specify with -i <path> or as the first argument.");
        }

        return opts;
    }

    private static String requireNext(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + option + " requires an argument.");
        }
        return args[index].trim();
    }

    private static int parseLpAlgo(String val) {
        val = val.trim().toLowerCase();
        switch (val) {
            case "auto":
            case "default":
            case "0":
                return 0;
            case "primal":
            case "1":
                return 1;
            case "dual":
            case "2":
                return 2;
            case "barrier":
            case "3":
                return 3;
            case "sifting":
            case "4":
                return 4;
            default:
                try {
                    return Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    System.err.println("Unrecognized LP algorithm '" + val + "', defaulting to auto (0)");
                    return 0;
                }
        }
    }

    /**
     * Converts the parsed options into an OptCfg instance, calculating the 5-digit alg_mode.
     */
    public OptCfg toOptCfg() {
        OptCfg cfg = new OptCfg();

        // Load config file first if specified
        if (configPath != null) {
            cfg = OptCfg.loadFromFile(configPath);
        }

        // If explicit 5-digit mode was supplied, use it
        if (legacyAlgMode != null) {
            cfg.alg_mode = legacyAlgMode;
        } else {
            // Compute 5-digit mode from human-readable flags
            int kpi = MODE_TO_KPI.getOrDefault(modeName.toLowerCase(), 4);
            int bff = (enumSuppliers != null && enumSuppliers) ? 1 : 0;
            int prt = (priorities != null && priorities) ? 1 : 0;

            int prg;
            if (formulation != null) {
                prg = (formulation == ProgType.RALB) ? 1 : 0;
            } else {
                // If mode is specifically 'fde', default to FDE (0), otherwise RALB (1)
                if (modeName.equalsIgnoreCase("fde") || modeName.equalsIgnoreCase("exact")) {
                    prg = 0;
                } else {
                    prg = 1;
                }
            }

            int lp = (lpAlgo != null) ? lpAlgo : 0;

            cfg.alg_mode = kpi * 10000 + bff * 1000 + prt * 100 + prg * 10 + lp;
        }

        cfg.timelimsec = timeLimit;
        cfg.maxgap = scenarioTimeLimit;
        cfg.threads2use = threads;
        cfg.maxmem = memory;

        cfg.phIterations = phIterations;
        cfg.phRho0 = phRho0;
        cfg.phAlpha = phAlpha;
        cfg.phRhoMax = phRhoMax;
        cfg.phTolerance = phTolerance;

        return cfg;
    }

    public int getKpi() {
        if (legacyAlgMode != null) {
            return (legacyAlgMode / 10000) % 10;
        }
        return MODE_TO_KPI.getOrDefault(modeName.toLowerCase(), 4);
    }

    public String getKpiDescription() {
        int kpi = getKpi();
        return KPI_DESCRIPTIONS.getOrDefault(kpi, "Custom / Unknown KPI");
    }

    /**
     * Prints clean, formatted, human-friendly help text.
     */
    public static void printHelp() {
        System.out.println("==========================================================================================");
        System.out.println("CPSSP SOLVER — Capacity Planning with Supplier Selection (Java CLI)");
        System.out.println("Stochastic Variable Cost & Size Bin Packing with Capacity Loss (SVCSBPP-CL-SS)");
        System.out.println("==========================================================================================");
        System.out.println("\nUSAGE:");
        System.out.println("  java -jar cpssp-solver.jar [OPTIONS] <instance_file>");
        System.out.println("  java -cp ... cpssp.Main [OPTIONS] -i <instance_file>");
        System.out.println("\nMAIN OPTIONS:");
        System.out.println("  -i, --instance <file>         Path to instance text file (required, or 1st argument).");
        System.out.println("  -m, --mode <name>             Optimization method/mode to run (default: 'ub').");
        System.out.println("  -t, --time-limit <sec>        Global solver time limit in seconds (default: 3600).");
        System.out.println("      --scenario-time-limit <s> Time limit per scenario subproblem (default: 600).");
        System.out.println("  -o, --output-sol <file>       Export solution to .sspsol file for Python analysis.");
        System.out.println("  -v, --verbose                 Enable detailed solver logging and Gurobi output.");
        System.out.println("  -q, --quiet                   Quiet mode: suppresses banners, prints only results.");
        System.out.println("  -h, --help                    Display this help message and exit.");
        System.out.println("\nALGORITHM TUNING OPTIONS:");
        System.out.println("  -f, --formulation <fde|ralb>  Model type: 'fde' (exact packing) or 'ralb' (volume surrogate).");
        System.out.println("  -e, --enum-suppliers          Enumerate supplier combinations C(M, k) instead of MIP branching.");
        System.out.println("      --no-enum                 Disable supplier enumeration (default).");
        System.out.println("  -p, --priorities              Enable hierarchical branching priorities (default).");
        System.out.println("      --no-priorities           Disable branching priorities.");
        System.out.println("      --threads <n>             Number of solver threads (default: 1).");
        System.out.println("      --memory <mb>             Solver memory limit in megabytes (default: 1024).");
        System.out.println("      --lp-algo <name>          LP algorithm: auto (0), primal (1), dual (2), barrier (3).");
        System.out.println("      --ph-iterations <n>       Progressive Hedging maximum iterations (default: 30).");
        System.out.println("      --ph-rho <val>            Progressive Hedging initial penalty rho_0 (default: 1.0).");
        System.out.println("      --ph-alpha <val>          Progressive Hedging penalty growth rate (default: 1.05).");
        System.out.println("      --ph-tol <val>            Progressive Hedging consensus tolerance (default: 1e-4).");
        System.out.println("  -c, --config <file>           Load parameters from key=value configuration file.");
        System.out.println("      --alg-mode <code>         Direct 5-digit legacy mode code (e.g. 40110, 50110).");

        System.out.println("\nAVAILABLE MODES (-m, --mode):");
        System.out.printf("  %-16s %s%n", "ub, heuristic", "Row Aggregation Lower Bound + Heuristic Recourse (Default, Fastest)");
        System.out.printf("  %-16s %s%n", "ph, hedging", "Progressive Hedging (Augmented Lagrangian scenario decomposition)");
        System.out.printf("  %-16s %s%n", "all, stochastic", "All Stochastic Measures: EV, WS, RP, EEV, EVPI, and VSS");
        System.out.printf("  %-16s %s%n", "ws", "Wait-and-See Lower Bound (Perfect Information across scenarios)");
        System.out.printf("  %-16s %s%n", "ev-eev", "Expected Value of Mean Scenario and EEV Recourse Evaluation");
        System.out.printf("  %-16s %s%n", "fde, exact", "Full Deterministic Equivalent with explicit item packing");
        System.out.printf("  %-16s %s%n", "ralb, lb", "Row Aggregation Lower Bound formulation directly");
        System.out.printf("  %-16s %s%n", "saa, error", "Sample Average Approximation statistical stability & 95% CI");
        System.out.printf("  %-16s %s%n", "determ", "Solve deterministic model and write solution to .sspsol");
        System.out.printf("  %-16s %s%n", "feasibility", "Test instance feasibility (expands bin pool if needed)");

        System.out.println("\nEXAMPLES:");
        System.out.println("  1. Fast standard solve (Row Aggregation + Heuristic Recourse):");
        System.out.println("     java -jar cpssp.jar instance.txt");
        System.out.println("\n  2. Progressive Hedging scenario decomposition:");
        System.out.println("     java -jar cpssp.jar instance.txt -m ph --ph-iterations 40 --ph-rho 2.0 -v");
        System.out.println("\n  3. Compute all stochastic optimization measures (EV, WS, RP, EEV, EVPI, VSS):");
        System.out.println("     java -jar cpssp.jar instance.txt -m all");
        System.out.println("\n  4. Solve with 60s time limit, supplier enumeration, and solution export:");
        System.out.println("     java -jar cpssp.jar instance.txt -m ub -e -t 60 -o solution.sspsol");
        System.out.println("\n  5. Exact Full Deterministic Equivalent with verbose output:");
        System.out.println("     java -jar cpssp.jar instance.txt -m fde -v --threads 4");
        System.out.println("\n  6. Legacy positional syntax (fully supported):");
        System.out.println("     java -jar cpssp.jar instance.txt 40110 true");
        System.out.println("==========================================================================================");
    }
}
