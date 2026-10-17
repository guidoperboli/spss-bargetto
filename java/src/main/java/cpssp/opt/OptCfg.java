package cpssp.opt;

import java.io.*;
import java.util.Properties;

/**
 * Optimization configuration container mirroring C++ optcfg.
 */
public class OptCfg {

    public enum Algo {
        MILP,
        BAB
    }

    // 5-digit code: [KPI][BFF][PRT][PRG][LP]
    // Default 40110: KPI=4 (UB/RALB+IS), BFF=0 (no enum), PRT=1 (priorities), PRG=1 (RALB), LP=0 (auto)
    public int alg_mode = 40110;

    public Algo opt_algo = Algo.MILP;
    public double maxmem = 1024.0;
    public int threads2use = 1;
    public double timelimsec = 3600.0;
    public double maxgap = 600.0;       // mapped to timelim2sec for scenario subproblems

    // Progressive Hedging parameters
    public int phIterations = 30;
    public double phRho0 = 1.0;
    public double phAlpha = 1.05;
    public double phRhoMax = 1000.0;
    public double phTolerance = 1e-4;

    public OptCfg() {
    }

    public OptCfg(int algMode) {
        this.alg_mode = algMode;
    }

    /**
     * Attempts to read a config file either as key-value properties or whitespace tokens.
     */
    public static OptCfg loadFromFile(String path) {
        OptCfg cfg = new OptCfg();
        File f = new File(path);
        if (!f.exists()) {
            return cfg;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim().toLowerCase();
                    String val = parts[1].trim();
                    switch (key) {
                        case "alg_mode":
                            cfg.alg_mode = Integer.parseInt(val);
                            break;
                        case "threads2use":
                            cfg.threads2use = Integer.parseInt(val);
                            break;
                        case "timelimsec":
                            cfg.timelimsec = Double.parseDouble(val);
                            break;
                        case "maxgap":
                        case "timelim2sec":
                            cfg.maxgap = Double.parseDouble(val);
                            break;
                        case "maxmem":
                            cfg.maxmem = Double.parseDouble(val);
                            break;
                        case "ph_iterations":
                        case "phiterations":
                            cfg.phIterations = Integer.parseInt(val);
                            break;
                        case "ph_rho0":
                        case "phrho":
                        case "rho0":
                            cfg.phRho0 = Double.parseDouble(val);
                            break;
                        case "ph_alpha":
                        case "phalpha":
                        case "alpha":
                            cfg.phAlpha = Double.parseDouble(val);
                            break;
                        case "ph_rhomax":
                        case "rhomax":
                            cfg.phRhoMax = Double.parseDouble(val);
                            break;
                        case "ph_tol":
                        case "phtolerance":
                            cfg.phTolerance = Double.parseDouble(val);
                            break;
                    }
                } else {
                    // Try parsing as single alg_mode integer
                    try {
                        cfg.alg_mode = Integer.parseInt(line);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: Could not parse config file " + path + ": " + e.getMessage());
        }
        return cfg;
    }
}
