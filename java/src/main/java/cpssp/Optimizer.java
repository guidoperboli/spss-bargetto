package cpssp;

import cpssp.algo.CpsspAlgoILP;
import cpssp.algo.ProgressiveHedging;
import cpssp.ilp.CpsspILP;
import cpssp.model.CpsspInstance;
import cpssp.opt.ILPparams;
import cpssp.opt.OptCfg;
import cpssp.opt.OptResult;
import cpssp.opt.ProgType;

import gurobi.GRBEnv;
import gurobi.GRBException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * High-level optimization driver decoding configuration modes (alg_mode)
 * and triggering execution of the appropriate stochastic or deterministic algorithm.
 */
public class Optimizer {

    private static final List<String> ALG_NAMES = Arrays.asList(
            "test feasibility",
            "solve deterministic ILP",
            "WS (Wait-and-See)",
            "EV-EEV",
            "UB (Row Aggregation + Heuristic Recourse)",
            "all SO measures (EV, WS, RP, EEV, EVPI, VSS)",
            "RP error (Sample Average Approximation stability)",
            "Deterministic ILP Model",
            "Progressive Hedging (Augmented Lagrangian Decomposition)"
    );

    private boolean isVerbose = false;

    public void setVerbose(boolean verbose) {
        this.isVerbose = verbose;
    }

    public OptResult optimize(CpsspInstance inst, OptCfg ocfg) throws GRBException, IOException {
        return optimize(inst, ocfg, null);
    }

    /**
     * Executes optimization for a loaded instance, configuration, and optional solution export.
     */
    public OptResult optimize(CpsspInstance inst, OptCfg ocfg, String outputSolFile) throws GRBException, IOException {
        if (isVerbose) {
            System.out.println("Instance: " + inst.getBaseName() + " (" + inst.ifname + ")");
            for (Map.Entry<String, String> entry : inst.getBasicInfo()) {
                System.out.println("Cpssp instance ::: " + entry.getKey() + " " + entry.getValue());
            }
        }

        // Decode 5-digit alg_mode: [KPI][BFF][PRT][PRG][LP]
        int mode = ocfg.alg_mode;
        int ilpKpi = (mode / 10000) % 10;
        int ilpBff = (mode / 1000) % 10;
        int ilpPrt = (mode / 100) % 10;
        int ilpPrg = (mode / 10) % 10;
        int lpAlgo = (mode / 1) % 10;

        String kpiName = (ilpKpi >= 0 && ilpKpi < ALG_NAMES.size()) ? ALG_NAMES.get(ilpKpi) : "Unknown KPI";
        if (isVerbose) {
            System.out.println("Cpssp optimizer ::: Algorithm: " + kpiName);
            System.out.println("Cpssp optimizer ::: Enumerate supplier selection: " + (ilpBff != 0));
            System.out.println("Cpssp optimizer ::: Variable priorities: " + (ilpPrt != 0));
            System.out.println("Cpssp optimizer ::: ILP formulation: " + (ilpPrg != 0 ? ProgType.RALB : ProgType.FDE));
            System.out.println("Cpssp optimizer ::: LP algorithm: " + lpAlgo);
        }

        if (ilpPrt != 0) {
            inst.computePriorities();
        }

        ILPparams ilpps = new ILPparams();
        ilpps.progtype = (ilpPrg != 0) ? ProgType.RALB : ProgType.FDE;
        ilpps.algo = ilpBff;
        ilpps.lpAlgo = (lpAlgo == 0) ? -1 : (lpAlgo - 1);
        ilpps.priorities = (ilpPrt != 0);
        ilpps.presolve = true;
        ilpps.maxmem = ocfg.maxmem;
        ilpps.threads2use = ocfg.threads2use;
        ilpps.timelimsec = ocfg.timelimsec;
        ilpps.timelim2sec = ocfg.maxgap;
        ilpps.sifting = 2;

        CpsspAlgoILP algo = new CpsspAlgoILP(inst, ilpps);
        algo.setVerbose(isVerbose);

        OptResult optr;
        switch (ilpKpi) {
            case 0:
                // Test feasibility (iteratively improve capacity if needed)
                do {
                    optr = algo.testProgFeasibility(ilpps);
                    if (!optr.integerValues.isEmpty() && optr.integerValues.get(0) == 3) {
                        System.out.println("Instance infeasible, augmenting supplier bin pool...");
                        inst.improveFeasibility();
                    }
                } while (!optr.integerValues.isEmpty() && optr.integerValues.get(0) == 3);
                break;

            case 1:
                // Solve deterministic ILP
                optr = algo.solveProg(ilpps);
                break;

            case 2:
                // Wait-and-See / Perfect Information
                optr = algo.computePerfectInfoBound(ilpps);
                break;

            case 3:
                // Expected Value and EEV
                optr = algo.computeAveScenarioBound(ilpps);
                break;

            case 4:
                // Row Aggregation + Upper Bound via FDEE-IS
                optr = algo.computeFullProblemUB(ilpps, null, true);
                break;

            case 5:
                // All Stochastic Optimization measures
                optr = algo.computeRootEvaluation(ilpps);
                break;

            case 6:
                // RP SAA error evaluation
                int nrOfSamples = 120;
                int sampleSize = Math.max(1, inst.nrOfScenarios / nrOfSamples);
                optr = algo.computeFullProblemUBerr(ilpps, sampleSize);
                break;

            case 7:
                // Deterministic model solve and export to .sspsol
                String targetFile = (outputSolFile != null) ? outputSolFile : (inst.getBaseName() + ".sspsol");
                optr = algo.solveProgDeterm(ilpps);
                break;

            case 8:
                // Progressive Hedging scenario decomposition
                ProgressiveHedging ph = new ProgressiveHedging(inst, algo.getGrbEnv(), ilpps);
                ph.maxIterations = ocfg.phIterations;
                ph.rho0 = ocfg.phRho0;
                ph.alpha = ocfg.phAlpha;
                ph.rhoMax = ocfg.phRhoMax;
                ph.consensusTolerance = ocfg.phTolerance;
                ph.timeLimitSec = ilpps.timelimsec;
                ph.verbose = isVerbose;
                optr = ph.solve();
                break;

            default:
                throw new IllegalArgumentException("Unknown KPI mode: " + ilpKpi);
        }

        if (isVerbose) {
            System.out.println("Result: " + optr);
        }
        return optr;
    }
}
