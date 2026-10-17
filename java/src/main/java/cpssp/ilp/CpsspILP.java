package cpssp.ilp;

import cpssp.model.CpsspInstance;
import cpssp.model.Scenario;
import cpssp.opt.ILPparams;
import cpssp.opt.OptResult;
import cpssp.opt.ProgType;
import cpssp.util.Combinations;

import gurobi.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Gurobi-based MILP model builder and solver for the CPSSP problem.
 * 
 * Supports:
 * - Full Deterministic Equivalent (FDE) with item-size packing counts
 * - Row Aggregation Lower Bound (RALB) with capacity sum surrogates
 * - Branching priorities enforcement
 * - Supplier selection combinatorial enumeration
 * - Solution fixing and export to .sspsol format
 */
public class CpsspILP {

    private final CpsspInstance inst;
    private final ILPparams ilpprms;
    private final GRBEnv grbenv;
    private final GRBModel model;

    // Decision variables
    private GRBVar[][] X;       // [scenario_idx][item_type * J + bin_j]
    private GRBVar[][] E;       // [scenario_idx][item_type] optional express delivery
    private GRBVar[][] Y;       // Y[0] = stage 1 bins, Y[1+s] = stage 2 bins
    private GRBVar[] W;         // suppliers

    private boolean[] supplsel;
    private boolean[] binsel;
    private int[][] itempack;

    private boolean isWSolFixed = false;
    private boolean isYSolFixed = false;
    private boolean isVerbose = false;

    // Flag whether express delivery slacks are enabled
    private boolean enableExpressDelivery = true;

    /**
     * Deterministic constructor (used for single scenario or deterministic comparisons).
     */
    public CpsspILP(CpsspInstance inst, GRBEnv env, ILPparams params, boolean determ) throws GRBException {
        this.inst = inst;
        this.ilpprms = params;
        this.grbenv = env;
        this.model = new GRBModel(grbenv);

        int M = inst.nrOfSuppliers;
        int N = inst.minNrOfSuppliers;
        int S = inst.nrOfScenarios;

        X = new GRBVar[S][];
        if (enableExpressDelivery) {
            E = new GRBVar[S][];
        }

        // X and E variables
        for (int s = 0; s < S; s++) {
            Scenario sc = inst.scenarios.get(s);
            int I = sc.itemMaxVolume;
            int J = inst.nrOfStage1Bins;

            if (ilpprms.progtype == ProgType.FDE) {
                X[s] = new GRBVar[I * J];
                for (int i = 0; i < I; i++) {
                    for (int j = 0; j < J; j++) {
                        X[s][i * J + j] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "x_" + s + "_" + i + "_" + j);
                    }
                }
            }

            if (enableExpressDelivery) {
                E[s] = new GRBVar[I];
                for (int i = 0; i < I; i++) {
                    E[s][i] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "e_" + s + "_" + i);
                }
            }
        }

        // Y variables: only stage 1 bins
        Y = new GRBVar[1][inst.nrOfStage1Bins];
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            Y[0][j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_stage1_" + j + "_s" + inst.stage1BinSuppliers.get(j));
        }

        // W variables: suppliers
        W = new GRBVar[M];
        for (int m = 0; m < M; m++) {
            W[m] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "w_" + m);
        }

        // Minimum suppliers constraint: sum(w_m) >= N
        GRBLinExpr minSupplExpr = new GRBLinExpr();
        for (int m = 0; m < M; m++) {
            minSupplExpr.addTerm(1.0, W[m]);
        }
        model.addConstr(minSupplExpr, GRB.GREATER_EQUAL, N, "min_suppliers");

        // Minimum volume per supplier
        double minVolRatio = ilpprms.overrideMinVolXSupplier ? ilpprms.minVolXSupplierValue : inst.minVolXSupplier;
        for (int m = 0; m < M; m++) {
            GRBLinExpr minVolExpr = new GRBLinExpr();
            double totVolSuppl = 0.0;

            for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                if (inst.stage1BinSuppliers.get(j) != m) continue;
                int btype = inst.stage1BinTypes.get(j);
                double vol = inst.typeBinVolumes[btype];
                totVolSuppl += vol;
                minVolExpr.addTerm(vol, Y[0][j]);
            }
            minVolExpr.addTerm(-minVolRatio * totVolSuppl, W[m]);
            model.addConstr(minVolExpr, GRB.GREATER_EQUAL, 0.0, "min_vol_suppl_" + m);
        }

        // Linking bins to suppliers: sum_{j in M} y_j <= |J_m| * w_m
        int[] binXSupplCount = new int[M];
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            binXSupplCount[inst.stage1BinSuppliers.get(j)]++;
        }
        for (int m = 0; m < M; m++) {
            GRBLinExpr linkExpr = new GRBLinExpr();
            linkExpr.addTerm(binXSupplCount[m], W[m]);
            for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                if (inst.stage1BinSuppliers.get(j) == m) {
                    linkExpr.addTerm(-1.0, Y[0][j]);
                }
            }
            model.addConstr(linkExpr, GRB.GREATER_EQUAL, 0.0, "link_bins_suppl_" + m);
        }

        // Scenario packing constraints
        for (int s = 0; s < S; s++) {
            Scenario sc = inst.scenarios.get(s);
            int I = sc.itemMaxVolume;
            int J = inst.nrOfStage1Bins;

            if (ilpprms.progtype == ProgType.FDE) {
                // 1st stage bin capacities
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    int btype = inst.stage1BinTypes.get(j);
                    GRBLinExpr capExpr = new GRBLinExpr();
                    capExpr.addTerm(inst.typeBinVolumes[btype], Y[0][j]);

                    for (int i = 0; i < I; i++) {
                        int vol = i + 1;
                        capExpr.addTerm(-vol, X[s][i * J + j]);
                    }
                    model.addConstr(capExpr, GRB.GREATER_EQUAL, 0.0, "cap_s1_" + s + "_" + j);
                }

                // Pack items
                for (int i = 0; i < I; i++) {
                    int freq = sc.itemSizesFreq.length > (i + 1) ? sc.itemSizesFreq[i + 1] : 0;
                    GRBLinExpr packExpr = new GRBLinExpr();
                    for (int j = 0; j < J; j++) {
                        packExpr.addTerm(1.0, X[s][i * J + j]);
                    }
                    if (enableExpressDelivery) {
                        packExpr.addTerm(1.0, E[s][i]);
                    }
                    model.addConstr(packExpr, GRB.EQUAL, freq, "pack_i_" + s + "_" + i);
                }
            } else {
                // Row aggregation (RALB)
                GRBLinExpr totalVolExpr = new GRBLinExpr();
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    int btype = inst.stage1BinTypes.get(j);
                    totalVolExpr.addTerm(inst.typeBinVolumes[btype], Y[0][j]);
                }
                if (enableExpressDelivery) {
                    for (int i = 0; i < I; i++) {
                        totalVolExpr.addTerm(i + 1, E[s][i]);
                    }
                }
                model.addConstr(totalVolExpr, GRB.GREATER_EQUAL, sc.totVolumeOfItems, "ra_vol_" + s);
            }
        }

        // Objective function
        GRBLinExpr obj = new GRBLinExpr();
        for (int m = 0; m < M; m++) {
            obj.addTerm(inst.supplierContractCosts[m], W[m]);
        }
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            int btype = inst.stage1BinTypes.get(j);
            int bsuppl = inst.stage1BinSuppliers.get(j);
            obj.addTerm(inst.typeSupplBinCosts[btype * M + bsuppl], Y[0][j]);
        }
        if (enableExpressDelivery) {
            for (int s = 0; s < S; s++) {
                Scenario sc = inst.scenarios.get(s);
                int I = sc.itemMaxVolume;
                for (int i = 0; i < I; i++) {
                    obj.addTerm(sc.expDeliveryCost, E[s][i]);
                }
            }
        }
        model.setObjective(obj, GRB.MINIMIZE);

        // Symmetry breaking
        for (int j = 0; j + 1 < inst.nrOfStage1Bins; j++) {
            for (int k = j + 1; k < inst.nrOfStage1Bins; k++) {
                if (inst.stage1BinTypes.get(j).equals(inst.stage1BinTypes.get(k)) &&
                    inst.stage1BinSuppliers.get(j).equals(inst.stage1BinSuppliers.get(k))) {
                    GRBLinExpr symExpr = new GRBLinExpr();
                    symExpr.addTerm(1.0, Y[0][j]);
                    symExpr.addTerm(-1.0, Y[0][k]);
                    model.addConstr(symExpr, GRB.GREATER_EQUAL, 0.0, "sym_s1_" + j + "_" + k);
                }
            }
        }
    }

    /**
     * Primary Stochastic / Scenario-subset constructor.
     */
    public CpsspILP(CpsspInstance inst, GRBEnv env, ILPparams params, List<Integer> scenarioIds) throws GRBException {
        this.inst = inst;
        this.ilpprms = params;
        this.grbenv = env;
        this.model = new GRBModel(grbenv);

        int M = inst.nrOfSuppliers;
        int N = inst.minNrOfSuppliers;

        boolean allScenarios = (scenarioIds == null || scenarioIds.isEmpty());
        List<Integer> activeScenarios;
        if (allScenarios) {
            activeScenarios = new ArrayList<>(inst.nrOfScenarios);
            for (int s = 0; s < inst.nrOfScenarios; s++) {
                activeScenarios.add(s);
            }
        } else {
            activeScenarios = new ArrayList<>(scenarioIds);
        }
        int S = activeScenarios.size();

        X = new GRBVar[S][];
        if (enableExpressDelivery) {
            E = new GRBVar[S][];
        }

        // X and E variables
        for (int s = 0; s < S; s++) {
            int sdx = activeScenarios.get(s);
            Scenario sc = inst.scenarios.get(sdx);
            int I = sc.itemMaxVolume;
            int J = inst.nrOfStage1Bins + sc.nrOfStage2Bins;

            if (ilpprms.progtype == ProgType.FDE) {
                X[s] = new GRBVar[I * J];
                for (int i = 0; i < I; i++) {
                    for (int j = 0; j < J; j++) {
                        X[s][i * J + j] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "x_" + sdx + "_" + i + "_" + j);
                    }
                }
            }

            if (enableExpressDelivery) {
                E[s] = new GRBVar[I];
                for (int i = 0; i < I; i++) {
                    E[s][i] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "e_" + sdx + "_" + i);
                }
            }
        }

        // Y variables: Y[0] = 1st stage bins, Y[1+s] = 2nd stage bins
        Y = new GRBVar[1 + S][];
        Y[0] = new GRBVar[inst.nrOfStage1Bins];
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            Y[0][j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_stage1_" + j + "_s" + inst.stage1BinSuppliers.get(j));
        }

        for (int s = 0; s < S; s++) {
            int sdx = activeScenarios.get(s);
            Scenario sc = inst.scenarios.get(sdx);
            Y[1 + s] = new GRBVar[sc.nrOfStage2Bins];
            for (int j = 0; j < sc.nrOfStage2Bins; j++) {
                Y[1 + s][j] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_stage2_" + sdx + "_" + j);
            }
        }

        // W variables: suppliers
        W = new GRBVar[M];
        for (int m = 0; m < M; m++) {
            W[m] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "w_" + m);
        }

        // Minimum suppliers constraint: sum(w_m) >= N
        GRBLinExpr minSupplExpr = new GRBLinExpr();
        for (int m = 0; m < M; m++) {
            minSupplExpr.addTerm(1.0, W[m]);
        }
        model.addConstr(minSupplExpr, GRB.GREATER_EQUAL, N, "min_suppliers");

        // Minimum volume per supplier
        double minVolRatio = ilpprms.overrideMinVolXSupplier ? ilpprms.minVolXSupplierValue : inst.minVolXSupplier;
        for (int m = 0; m < M; m++) {
            GRBLinExpr minVolExpr = new GRBLinExpr();
            double totVolSuppl = 0.0;

            for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                if (inst.stage1BinSuppliers.get(j) != m) continue;
                int btype = inst.stage1BinTypes.get(j);
                double vol = inst.typeBinVolumes[btype];
                totVolSuppl += vol;
                minVolExpr.addTerm(vol, Y[0][j]);
            }
            minVolExpr.addTerm(-minVolRatio * totVolSuppl, W[m]);
            model.addConstr(minVolExpr, GRB.GREATER_EQUAL, 0.0, "min_vol_suppl_" + m);
        }

        // Minimum average reliability: sum (theta_m - R) * w_m >= 0
        GRBLinExpr minRelExpr = new GRBLinExpr();
        for (int m = 0; m < M; m++) {
            minRelExpr.addTerm(inst.supplierThetas[m] - inst.minAveReliability, W[m]);
        }
        model.addConstr(minRelExpr, GRB.GREATER_EQUAL, 0.0, "min_ave_reliability");

        // Linking bins to suppliers: sum_{j in M} y_j <= |J_m| * w_m
        int[] binXSupplCount = new int[M];
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            binXSupplCount[inst.stage1BinSuppliers.get(j)]++;
        }
        for (int m = 0; m < M; m++) {
            GRBLinExpr linkExpr = new GRBLinExpr();
            linkExpr.addTerm(binXSupplCount[m], W[m]);
            for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                if (inst.stage1BinSuppliers.get(j) == m) {
                    linkExpr.addTerm(-1.0, Y[0][j]);
                }
            }
            model.addConstr(linkExpr, GRB.GREATER_EQUAL, 0.0, "link_bins_suppl_" + m);
        }

        // Packing for scenarios
        for (int s = 0; s < S; s++) {
            int sdx = activeScenarios.get(s);
            Scenario sc = inst.scenarios.get(sdx);
            int I = sc.itemMaxVolume;
            int J = inst.nrOfStage1Bins + sc.nrOfStage2Bins;

            if (ilpprms.progtype == ProgType.FDE) {
                // 1st stage bins: degraded volumes in scenario s
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    GRBLinExpr capExpr = new GRBLinExpr();
                    capExpr.addTerm(sc.stage1BinVolumes[j], Y[0][j]);

                    for (int i = 0; i < I; i++) {
                        int vol = i + 1;
                        capExpr.addTerm(-vol, X[s][i * J + j]);
                    }
                    model.addConstr(capExpr, GRB.GREATER_EQUAL, 0.0, "cap_s1_" + sdx + "_" + j);
                }

                // 2nd stage bins
                for (int j = 0; j < sc.nrOfStage2Bins; j++) {
                    int btype = sc.stage2BinTypes[j];
                    GRBLinExpr capExpr = new GRBLinExpr();
                    capExpr.addTerm(inst.typeBinVolumes[btype], Y[1 + s][j]);

                    for (int i = 0; i < I; i++) {
                        int vol = i + 1;
                        capExpr.addTerm(-vol, X[s][i * J + inst.nrOfStage1Bins + j]);
                    }
                    model.addConstr(capExpr, GRB.GREATER_EQUAL, 0.0, "cap_s2_" + sdx + "_" + j);
                }

                // Item packing balance
                for (int i = 0; i < I; i++) {
                    int freq = sc.itemSizesFreq.length > (i + 1) ? sc.itemSizesFreq[i + 1] : 0;
                    GRBLinExpr packExpr = new GRBLinExpr();
                    for (int j = 0; j < J; j++) {
                        packExpr.addTerm(1.0, X[s][i * J + j]);
                    }
                    if (enableExpressDelivery) {
                        packExpr.addTerm(1.0, E[s][i]);
                    }
                    model.addConstr(packExpr, GRB.EQUAL, freq, "pack_i_" + sdx + "_" + i);
                }
            } else {
                // Row aggregation (RALB)
                GRBLinExpr totalVolExpr = new GRBLinExpr();
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    totalVolExpr.addTerm(sc.stage1BinVolumes[j], Y[0][j]);
                }
                for (int j = 0; j < sc.nrOfStage2Bins; j++) {
                    int btype = sc.stage2BinTypes[j];
                    totalVolExpr.addTerm(inst.typeBinVolumes[btype], Y[1 + s][j]);
                }
                if (enableExpressDelivery) {
                    for (int i = 0; i < I; i++) {
                        totalVolExpr.addTerm(i + 1, E[s][i]);
                    }
                }
                model.addConstr(totalVolExpr, GRB.GREATER_EQUAL, sc.totVolumeOfItems, "ra_vol_" + sdx);
            }
        }

        // Objective Function
        GRBLinExpr obj = new GRBLinExpr();

        // 1st stage supplier contract fees
        for (int m = 0; m < M; m++) {
            obj.addTerm(inst.supplierContractCosts[m], W[m]);
        }

        // 1st stage bin booking costs
        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            int btype = inst.stage1BinTypes.get(j);
            int bsuppl = inst.stage1BinSuppliers.get(j);
            obj.addTerm(inst.typeSupplBinCosts[btype * M + bsuppl], Y[0][j]);
        }

        // 2nd stage scenario recourse costs
        for (int s = 0; s < S; s++) {
            int sdx = activeScenarios.get(s);
            Scenario sc = inst.scenarios.get(sdx);
            double sProb = allScenarios ? sc.prob : (1.0 / S);

            // Spot bins
            for (int j = 0; j < sc.nrOfStage2Bins; j++) {
                int btype = sc.stage2BinTypes[j];
                int bsuppl = sc.stage2BinSuppliers[j];
                double spotCost = sc.stage2BinCosts[btype * M + bsuppl];
                obj.addTerm(sProb * spotCost, Y[1 + s][j]);
            }

            // Capacity loss management costs net of compensation: (c - r) * lost_volume
            for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                int btype = inst.stage1BinTypes.get(j);
                int bsuppl = inst.stage1BinSuppliers.get(j);
                double lostVol = inst.typeBinVolumes[btype] - sc.stage1BinVolumes[j];
                double netLossCost = inst.typeSupplBinLossXUCosts[btype * M + bsuppl] - inst.typeSupplBinLossXUComps[btype * M + bsuppl];
                obj.addTerm(sProb * lostVol * netLossCost, Y[0][j]);
            }

            // Express delivery slack penalties
            if (enableExpressDelivery) {
                int I = sc.itemMaxVolume;
                for (int i = 0; i < I; i++) {
                    obj.addTerm(sProb * sc.expDeliveryCost, E[s][i]);
                }
            }
        }

        model.setObjective(obj, GRB.MINIMIZE);

        // Branching priorities
        if (ilpprms.priorities) {
            setBranchingPriorities(S, activeScenarios);
        }
    }

    private void setBranchingPriorities(int S, List<Integer> activeScenarios) throws GRBException {
        int maxXP = 0;
        int maxZP = 0;
        int maxYP = 0;

        for (int s = 0; s < S; s++) {
            int sdx = activeScenarios.get(s);
            Scenario sc = inst.scenarios.get(sdx);
            int I = sc.itemMaxVolume;
            int J = inst.nrOfStage1Bins + sc.nrOfStage2Bins;

            if (ilpprms.progtype == ProgType.FDE) {
                for (int i = 0; i < I; i++) {
                    int vol = i + 1;
                    for (int j = 0; j < J; j++) {
                        X[s][i * J + j].set(GRB.IntAttr.BranchPriority, vol);
                        maxXP = Math.max(maxXP, vol);
                    }
                }
            }

            for (int j = 0; j < sc.nrOfStage2Bins; j++) {
                int zp = maxXP + 1 + sc.stage2BinPriority[j];
                Y[1 + s][j].set(GRB.IntAttr.BranchPriority, zp);
                maxZP = Math.max(maxZP, zp);
            }
        }

        for (int j = 0; j < inst.nrOfStage1Bins; j++) {
            int yp = maxZP + 1 + inst.stage1BinPriority[j];
            Y[0][j].set(GRB.IntAttr.BranchPriority, yp);
            maxYP = Math.max(maxYP, yp);
        }

        for (int m = 0; m < inst.nrOfSuppliers; m++) {
            int wp = maxYP + 1 + inst.supplierPriority[m];
            W[m].set(GRB.IntAttr.BranchPriority, wp);
        }
    }

    public void fixSuppliers(boolean[] supplSelArg) throws GRBException {
        if (supplSelArg.length != inst.nrOfSuppliers) {
            throw new IllegalArgumentException("|suppl. selection| != M");
        }

        for (int m = 0; m < W.length; m++) {
            if (supplSelArg[m]) {
                W[m].set(GRB.DoubleAttr.UB, 1.0);
                W[m].set(GRB.DoubleAttr.LB, 1.0);
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    if (inst.stage1BinSuppliers.get(j) == m) {
                        Y[0][j].set(GRB.DoubleAttr.UB, 1.0);
                    }
                }
            } else {
                W[m].set(GRB.DoubleAttr.UB, 0.0);
                W[m].set(GRB.DoubleAttr.LB, 0.0);
                for (int j = 0; j < inst.nrOfStage1Bins; j++) {
                    if (inst.stage1BinSuppliers.get(j) == m) {
                        Y[0][j].set(GRB.DoubleAttr.UB, 0.0);
                    }
                }
            }
        }
        isWSolFixed = true;
    }

    public void fixStage1Bins(boolean[] binSelArg) throws GRBException {
        if (binSelArg.length != inst.nrOfStage1Bins) {
            throw new IllegalArgumentException("|bin selection| != J");
        }

        for (int j = 0; j < Y[0].length; j++) {
            if (binSelArg[j]) {
                Y[0][j].set(GRB.DoubleAttr.UB, 1.0);
                Y[0][j].set(GRB.DoubleAttr.LB, 1.0);
            } else {
                Y[0][j].set(GRB.DoubleAttr.UB, 0.0);
                Y[0][j].set(GRB.DoubleAttr.LB, 0.0);
            }
        }
        isYSolFixed = true;
    }

    public void resetSuppliers() throws GRBException {
        for (GRBVar w : W) {
            w.set(GRB.DoubleAttr.UB, 1.0);
            w.set(GRB.DoubleAttr.LB, 0.0);
        }
        isWSolFixed = false;
    }

    public void resetStage1Bins() throws GRBException {
        for (GRBVar y : Y[0]) {
            y.set(GRB.DoubleAttr.UB, 1.0);
            y.set(GRB.DoubleAttr.LB, 0.0);
        }
        isYSolFixed = false;
    }

    public OptResult feasibleProg(ILPparams prms) throws GRBException {
        model.setObjective(new GRBLinExpr(), GRB.MINIMIZE);
        return solveProg(prms);
    }

    /**
     * Solves the program, either directly via Gurobi or through combinatorial supplier enumeration.
     */
    public OptResult solveProg(ILPparams prms) throws GRBException {
        long start = System.currentTimeMillis();

        // Gurobi solver parameters
        model.set(GRB.IntParam.OutputFlag, (isVerbose && prms.algo == 0) ? 1 : 0);
        model.set(GRB.IntParam.Presolve, prms.presolve ? 1 : 0);
        if (prms.sifting >= 0) {
            model.set(GRB.IntParam.Sifting, prms.sifting);
        }
        model.set(GRB.DoubleParam.MemLimit, prms.maxmem);
        model.set(GRB.DoubleParam.NodefileStart, 0.5);
        model.set(GRB.IntParam.Threads, prms.threads2use);
        if (prms.lpAlgo >= 0) {
            model.set(GRB.IntParam.Method, prms.lpAlgo);
        }
        if (prms.timelimsec > 0) {
            model.set(GRB.DoubleParam.TimeLimit, prms.timelimsec);
        }
        if (prms.priorities) {
            model.set(GRB.IntParam.BranchDir, 1);
        }

        double bestBndv = Double.NaN;
        double bestObjv = Double.NaN;
        int grbStatus = -1;
        boolean bforce = false;
        boolean allOpt = true;

        // Combinatorial supplier enumeration
        if (prms.algo != 0 && !isWSolFixed && !isYSolFixed) {
            bforce = true;
            bestBndv = Double.MAX_VALUE;
            bestObjv = Double.MAX_VALUE;

            int M = inst.nrOfSuppliers;
            int N = inst.minNrOfSuppliers;

            for (int k = N; k <= M; k++) {
                List<boolean[]> combinations = Combinations.generate(M, k);

                for (boolean[] bitmask : combinations) {
                    double aveR = 0.0;
                    for (int m = 0; m < M; m++) {
                        if (bitmask[m]) aveR += inst.supplierThetas[m];
                    }
                    aveR /= k;

                    // Prune supplier subsets that violate reliability threshold
                    if (aveR + prms.eps < inst.minAveReliability) {
                        continue;
                    }

                    fixSuppliers(bitmask);
                    model.optimize();
                    grbStatus = model.get(GRB.IntAttr.Status);
                    allOpt = allOpt && (grbStatus == GRB.Status.OPTIMAL);

                    if (grbStatus == GRB.Status.OPTIMAL || grbStatus == GRB.Status.TIME_LIMIT ||
                        grbStatus == GRB.Status.ITERATION_LIMIT || grbStatus == GRB.Status.NODE_LIMIT) {
                        double objv = model.get(GRB.DoubleAttr.ObjVal);
                        double bndv = model.get(GRB.DoubleAttr.ObjBound);

                        if (objv + prms.eps < bestObjv) {
                            bestObjv = objv;
                            storeSuppliersSelection();
                            storeStage1BinSelection();
                        }
                        if (bndv + prms.eps < bestBndv) {
                            bestBndv = bndv;
                        }
                    }
                }
            }
        } else {
            // Direct optimization
            model.optimize();
            grbStatus = model.get(GRB.IntAttr.Status);

            if (grbStatus == GRB.Status.OPTIMAL || grbStatus == GRB.Status.TIME_LIMIT) {
                bestObjv = model.get(GRB.DoubleAttr.ObjVal);
                bestBndv = model.get(GRB.DoubleAttr.ObjBound);
                storeSuppliersSelection();
                storeStage1BinSelection();
            }
        }

        double runTime = (System.currentTimeMillis() - start) / 1000.0;

        OptResult optres = new OptResult();
        optres.tags.add("");
        optres.tags.add("");
        optres.runTime = runTime;
        optres.bestBound = bestBndv;
        optres.objValue = bestObjv;

        if (bforce) {
            optres.tags.add(allOpt ? "Optimal" : "SubOptimal");
            optres.optGap = allOpt ? 0.0 : (bestObjv - bestBndv) / bestBndv;
            optres.integerValues.add(allOpt ? 2 : 9);
        } else {
            optres.tags.add(grbStatus == GRB.Status.OPTIMAL ? "Optimal" : "NotOptimal_" + grbStatus);
            if (grbStatus == GRB.Status.OPTIMAL || grbStatus == GRB.Status.TIME_LIMIT) {
                optres.optGap = model.get(GRB.DoubleAttr.MIPGap);
            }
            optres.integerValues.add(grbStatus);
        }

        // Count selected suppliers
        int selectedSuppliers = 0;
        if (supplsel != null) {
            for (boolean b : supplsel) {
                if (b) selectedSuppliers++;
            }
        }
        optres.integerValues.add(selectedSuppliers);

        return optres;
    }

    public int storeSuppliersSelection() throws GRBException {
        int count = 0;
        supplsel = new boolean[W.length];
        for (int m = 0; m < W.length; m++) {
            supplsel[m] = (Math.round(W[m].get(GRB.DoubleAttr.X)) == 1);
            if (supplsel[m]) count++;
        }
        return count;
    }

    public int storeStage1BinSelection() throws GRBException {
        int count = 0;
        binsel = new boolean[Y[0].length];
        for (int j = 0; j < Y[0].length; j++) {
            binsel[j] = (Math.round(Y[0][j].get(GRB.DoubleAttr.X)) == 1);
            if (binsel[j]) count++;
        }
        return count;
    }

    public int storeItemPacking() throws GRBException {
        if (X == null || X.length == 0) return 0;
        int count = 0;
        itempack = new int[X.length][];
        for (int s = 0; s < X.length; s++) {
            if (X[s] == null) continue;
            itempack[s] = new int[X[s].length];
            for (int e = 0; e < X[s].length; e++) {
                itempack[s][e] = (int) Math.round(X[s][e].get(GRB.DoubleAttr.X));
                if (itempack[s][e] > 0) count++;
            }
        }
        return count;
    }

    public boolean[] getSuppliersSelection() {
        return supplsel != null ? supplsel.clone() : new boolean[0];
    }

    public boolean[] getStage1BinSelection() {
        return binsel != null ? binsel.clone() : new boolean[0];
    }

    public void writeSolToFile(String fname) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fname))) {
            int slc = 0;
            if (supplsel != null) {
                for (boolean b : supplsel) if (b) slc++;
            }
            pw.println(slc + " " + (supplsel != null ? supplsel.length : 0));

            if (supplsel != null) {
                for (int m = 0; m < supplsel.length; m++) {
                    if (supplsel[m]) {
                        pw.println(m + " " + String.format(Locale.US, "%.2f", inst.supplierContractCosts[m]));
                    }
                }
            }

            int bnc = 0;
            if (binsel != null) {
                for (boolean b : binsel) if (b) bnc++;
            }
            pw.println(bnc);

            if (binsel != null) {
                for (int j = 0; j < binsel.length; j++) {
                    if (binsel[j]) {
                        int bsuppl = inst.stage1BinSuppliers.get(j);
                        int btype = inst.stage1BinTypes.get(j);
                        pw.println(j + " " + bsuppl + " " + btype + " " +
                                inst.typeBinVolumes[btype] + " " +
                                String.format(Locale.US, "%.2f", inst.typeSupplBinCosts[btype * inst.nrOfSuppliers + bsuppl]));
                    }
                }
            }

            Scenario sc0 = inst.scenarios.get(0);
            pw.println(sc0.nrOfItems + " " + sc0.itemMinVolume + " " + sc0.itemMaxVolume);

            if (itempack != null && binsel != null) {
                int binCount = binsel.length;
                for (int s = 0; s < itempack.length; s++) {
                    for (int e = 0; e < itempack[s].length; e++) {
                        if (itempack[s][e] > 0) {
                            pw.println(itempack[s][e] + " " + (e / binCount + 1) + " " + (e % binCount));
                        }
                    }
                }
            }
        }
    }

    public void setObjectiveCoefficients(double[] supplCoeffs, double[] stage1BinCoeffs) throws GRBException {
        if (supplCoeffs != null && W != null) {
            for (int m = 0; m < W.length && m < supplCoeffs.length; m++) {
                W[m].set(GRB.DoubleAttr.Obj, supplCoeffs[m]);
            }
        }
        if (stage1BinCoeffs != null && Y != null && Y.length > 0 && Y[0] != null) {
            for (int j = 0; j < Y[0].length && j < stage1BinCoeffs.length; j++) {
                Y[0][j].set(GRB.DoubleAttr.Obj, stage1BinCoeffs[j]);
            }
        }
        model.update();
    }

    public void setVerbose(boolean verbose) {
        this.isVerbose = verbose;
    }
}
