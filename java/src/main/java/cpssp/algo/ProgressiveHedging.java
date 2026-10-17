package cpssp.algo;

import cpssp.ilp.CpsspILP;
import cpssp.model.CpsspInstance;
import cpssp.model.Scenario;
import cpssp.opt.ILPparams;
import cpssp.opt.OptResult;
import cpssp.opt.ProgType;

import gurobi.GRBEnv;
import gurobi.GRBException;

import java.util.*;

/**
 * Progressive Hedging (PH) algorithm for the two-stage CPSSP problem.
 * 
 * Based on the Augmented Lagrangian decomposition described in Section 4 of cpss.tex:
 * - Decomposes the two-stage stochastic program by scenario.
 * - Enforces non-anticipativity on 1st-stage decisions (suppliers and bins) via
 *   iterative consensus averaging, Lagrange multiplier updates, and linearized quadratic penalties.
 * - Extracts feasible binary 1st-stage solutions at each iteration and evaluates
 *   them across all scenarios to track valid Upper Bounds.
 */
public class ProgressiveHedging {

    private final CpsspInstance inst;
    private final GRBEnv grbenv;
    private final ILPparams baseParams;

    // PH configuration parameters
    public int maxIterations = 30;
    public double rho0 = 1.0;
    public double alpha = 1.05;         // penalty growth factor: rho_{k+1} = alpha * rho_k
    public double rhoMax = 1000.0;
    public double consensusTolerance = 1e-4;
    public double timeLimitSec = 3600.0;
    public boolean verbose = false;

    public ProgressiveHedging(CpsspInstance inst, GRBEnv env, ILPparams params) {
        this.inst = inst;
        this.grbenv = env;
        this.baseParams = new ILPparams(params);
    }

    /**
     * Executes the Progressive Hedging method.
     */
    public OptResult solve() throws GRBException {
        long startTime = System.currentTimeMillis();
        int S = inst.nrOfScenarios;
        int M = inst.nrOfSuppliers;
        int J = inst.nrOfStage1Bins;

        if (verbose) {
            System.out.println("==========================================================================================");
            System.out.printf("Progressive Hedging: %d scenarios, %d suppliers, %d 1st-stage bins, maxIter=%d, rho0=%.2f%n",
                    S, M, J, maxIterations, rho0);
            System.out.println("==========================================================================================");
        }

        // Multipliers lambda_{m}^s and lambda_{j}^{s}
        double[][] lambdaW = new double[S][M];
        double[][] lambdaY = new double[S][J];

        // Penalties rho_m and rho_j (can be initialized per supplier/bin cost scale)
        double[] rhoW = new double[M];
        Arrays.fill(rhoW, rho0);
        double[] rhoY = new double[J];
        Arrays.fill(rhoY, rho0);

        // Scenario solutions at iteration k: y_{m}^{s} and y_{j}^{s}
        boolean[][] solW = new boolean[S][M];
        boolean[][] solY = new boolean[S][J];

        // Consensus global solutions bar{y}_m and bar{y}_j
        double[] barW = new double[M];
        double[] barY = new double[J];

        // Best integer solution found and best Upper Bound
        boolean[] bestW = new boolean[M];
        boolean[] bestY = new boolean[J];
        double bestUB = Double.MAX_VALUE;
        double bestLB = 0.0;

        // Create persistent scenario subproblem models for fast warm-starts
        List<CpsspILP> subproblems = new ArrayList<>(S);
        ILPparams subParams = new ILPparams(baseParams);
        subParams.algo = 0; // subproblems solved directly with MIP

        for (int s = 0; s < S; s++) {
            CpsspILP ilp = new CpsspILP(inst, grbenv, subParams, Collections.singletonList(s));
            ilp.setVerbose(false);
            subproblems.add(ilp);
        }

        int iter = 0;
        boolean consensusReached = false;
        double finalDelta = Double.MAX_VALUE;

        while (iter < maxIterations) {
            long iterStart = System.currentTimeMillis();

            // Check global time limit
            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            if (timeLimitSec > 0 && elapsed >= timeLimitSec) {
                if (verbose) {
                    System.out.printf("[PH Iter %d] Global time limit reached (%.1fs). Terminating.%n", iter, elapsed);
                }
                break;
            }

            // Step 1: Solve scenario subproblems with modified objective coefficients
            double lagrangianObjSum = 0.0;

            for (int s = 0; s < S; s++) {
                Scenario sc = inst.scenarios.get(s);
                CpsspILP subIlp = subproblems.get(s);

                // Compute modified supplier costs bar{f}_m^s
                double[] modifiedCostW = new double[M];
                for (int m = 0; m < M; m++) {
                    // bar{f}_m^s = f_m + lambda_m^s + rho_m * (0.5 - bar{y}_m)
                    double baseCost = inst.supplierContractCosts[m];
                    modifiedCostW[m] = baseCost + lambdaW[s][m] + rhoW[m] * (0.5 - barW[m]);
                }

                // Compute modified bin costs bar{f}_j^s
                double[] modifiedCostY = new double[J];
                for (int j = 0; j < J; j++) {
                    int btype = inst.stage1BinTypes.get(j);
                    int bsuppl = inst.stage1BinSuppliers.get(j);
                    double baseCost = inst.typeSupplBinCosts[btype * M + bsuppl];
                    double lostVol = inst.typeBinVolumes[btype] - sc.stage1BinVolumes[j];
                    double netLossCost = inst.typeSupplBinLossXUCosts[btype * M + bsuppl] - inst.typeSupplBinLossXUComps[btype * M + bsuppl];
                    double capacityLossManagement = lostVol * netLossCost;

                    // bar{f}_j^s = f_j + (c-r)*(V - V^s) + lambda_j^s + rho_j * (0.5 - bar{y}_j)
                    modifiedCostY[j] = baseCost + capacityLossManagement + lambdaY[s][j] + rhoY[j] * (0.5 - barY[j]);
                }

                subIlp.setObjectiveCoefficients(modifiedCostW, modifiedCostY);
                OptResult subRes = subIlp.solveProg(subParams);

                solW[s] = subIlp.getSuppliersSelection();
                solY[s] = subIlp.getStage1BinSelection();

                lagrangianObjSum += sc.prob * subRes.objValue;
            }

            // Step 2: Compute global consensus solutions bar{y}_m and bar{y}_j
            Arrays.fill(barW, 0.0);
            Arrays.fill(barY, 0.0);

            for (int s = 0; s < S; s++) {
                double p = inst.scenarios.get(s).prob;
                for (int m = 0; m < M; m++) {
                    if (solW[s][m]) barW[m] += p;
                }
                for (int j = 0; j < J; j++) {
                    if (solY[s][j]) barY[j] += p;
                }
            }

            // Step 3: Compute non-anticipativity / consensus distance
            double deltaSq = 0.0;
            for (int s = 0; s < S; s++) {
                double p = inst.scenarios.get(s).prob;
                for (int m = 0; m < M; m++) {
                    double diff = (solW[s][m] ? 1.0 : 0.0) - barW[m];
                    deltaSq += p * diff * diff;
                }
                for (int j = 0; j < J; j++) {
                    double diff = (solY[s][j] ? 1.0 : 0.0) - barY[j];
                    deltaSq += p * diff * diff;
                }
            }
            finalDelta = Math.sqrt(deltaSq);

            // Step 4: Construct a feasible integer candidate policy and evaluate true Upper Bound
            boolean[] candidateW = projectToFeasibleSuppliers(barW);
            boolean[] candidateY = projectToFeasibleBins(barY, candidateW);

            CpsspAlgoILP evalAlgo = new CpsspAlgoILP(inst, baseParams);
            evalAlgo.setSilent(true);
            OptResult ubResult = evalAlgo.evaluateScenarios(baseParams, candidateW, candidateY, false);

            if (ubResult.objValue < bestUB) {
                bestUB = ubResult.objValue;
                bestW = candidateW.clone();
                bestY = candidateY.clone();
            }

            bestLB = Math.max(bestLB, lagrangianObjSum);

            double iterTime = (System.currentTimeMillis() - iterStart) / 1000.0;
            if (verbose) {
                System.out.printf(Locale.US, "  [PH Iter %2d] Consensus Dist: %7.4f | Current UB: %8.2f | Best UB: %8.2f | Iter Time: %.2fs%n",
                        iter, finalDelta, ubResult.objValue, bestUB, iterTime);
            }

            // Step 5: Check convergence
            if (finalDelta <= consensusTolerance) {
                consensusReached = true;
                if (verbose) {
                    System.out.printf("[PH] Exact consensus reached at iteration %d! Delta = %.6f%n", iter, finalDelta);
                }
                break;
            }

            // Step 6: Multiplier and penalty parameter updates
            for (int s = 0; s < S; s++) {
                for (int m = 0; m < M; m++) {
                    double dev = (solW[s][m] ? 1.0 : 0.0) - barW[m];
                    lambdaW[s][m] += rhoW[m] * dev;
                }
                for (int j = 0; j < J; j++) {
                    double dev = (solY[s][j] ? 1.0 : 0.0) - barY[j];
                    lambdaY[s][j] += rhoY[j] * dev;
                }
            }

            for (int m = 0; m < M; m++) {
                rhoW[m] = Math.min(rhoMax, rhoW[m] * alpha);
            }
            for (int j = 0; j < J; j++) {
                rhoY[j] = Math.min(rhoMax, rhoY[j] * alpha);
            }

            iter++;
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        // Build return result
        OptResult result = new OptResult();
        result.tags.add("");
        result.tags.add("");
        result.tags.add(consensusReached ? "Optimal" : "SubOptimal");
        result.objValue = bestUB;
        result.bestBound = bestLB;
        result.optGap = (bestUB > 0 && bestLB > 0 && bestUB >= bestLB) ? (bestUB - bestLB) / bestLB : 0.0;
        result.runTime = totalTime;

        result.integerValues.add(consensusReached ? 2 : 9);
        int selectedSuppliers = 0;
        for (boolean b : bestW) if (b) selectedSuppliers++;
        result.integerValues.add(selectedSuppliers);
        result.integerValues.add(iter);

        result.doubleValues.add(bestUB);
        result.doubleValues.add(bestLB);
        result.doubleValues.add(finalDelta);

        return result;
    }

    /**
     * Projects continuous consensus bar{y}_m to a valid binary supplier selection
     * respecting minimum suppliers count (N) and minimum average reliability (R).
     */
    private boolean[] projectToFeasibleSuppliers(double[] barW) {
        int M = inst.nrOfSuppliers;
        int N = inst.minNrOfSuppliers;
        boolean[] selected = new boolean[M];

        // Step 1: Initial threshold rounding
        int count = 0;
        for (int m = 0; m < M; m++) {
            if (barW[m] >= 0.5) {
                selected[m] = true;
                count++;
            }
        }

        // Step 2: Ensure at least N suppliers are selected
        if (count < N) {
            List<Integer> unselected = new ArrayList<>();
            for (int m = 0; m < M; m++) {
                if (!selected[m]) unselected.add(m);
            }
            // Sort unselected by consensus value descending
            unselected.sort((a, b) -> Double.compare(barW[b], barW[a]));
            for (int m : unselected) {
                selected[m] = true;
                count++;
                if (count >= N) break;
            }
        }

        // Step 3: Ensure minimum average reliability sum(theta_m) / count >= R
        double sumTheta = 0.0;
        for (int m = 0; m < M; m++) {
            if (selected[m]) sumTheta += inst.supplierThetas[m];
        }

        while (sumTheta / count < inst.minAveReliability - 1e-6) {
            // Find an unselected supplier with highest reliability
            int bestM = -1;
            double bestRel = -1.0;
            for (int m = 0; m < M; m++) {
                if (!selected[m] && inst.supplierThetas[m] > bestRel) {
                    bestRel = inst.supplierThetas[m];
                    bestM = m;
                }
            }
            if (bestM != -1) {
                selected[bestM] = true;
                sumTheta += inst.supplierThetas[bestM];
                count++;
            } else {
                break;
            }
        }

        return selected;
    }

    /**
     * Projects continuous consensus bar{y}_j to binary bin selection,
     * ensuring bins are only activated if their supplier is active.
     */
    private boolean[] projectToFeasibleBins(double[] barY, boolean[] selectedSuppliers) {
        int J = inst.nrOfStage1Bins;
        boolean[] selected = new boolean[J];

        for (int j = 0; j < J; j++) {
            int suppl = inst.stage1BinSuppliers.get(j);
            if (selectedSuppliers[suppl] && barY[j] >= 0.5) {
                selected[j] = true;
            }
        }

        // Ensure at least one bin per selected supplier if feasible
        int[] binsPerSuppl = new int[inst.nrOfSuppliers];
        for (int j = 0; j < J; j++) {
            if (selected[j]) binsPerSuppl[inst.stage1BinSuppliers.get(j)]++;
        }

        for (int m = 0; m < inst.nrOfSuppliers; m++) {
            if (selectedSuppliers[m] && binsPerSuppl[m] == 0) {
                // Pick highest consensus bin for supplier m
                int bestJ = -1;
                double maxVal = -1.0;
                for (int j = 0; j < J; j++) {
                    if (inst.stage1BinSuppliers.get(j) == m && barY[j] > maxVal) {
                        maxVal = barY[j];
                        bestJ = j;
                    }
                }
                if (bestJ != -1) {
                    selected[bestJ] = true;
                }
            }
        }

        return selected;
    }
}
