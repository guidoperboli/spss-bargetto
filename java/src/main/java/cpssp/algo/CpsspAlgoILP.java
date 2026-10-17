package cpssp.algo;

import cpssp.ilp.CpsspILP;
import cpssp.model.CpsspInstance;
import cpssp.model.Scenario;
import cpssp.opt.ILPparams;
import cpssp.opt.OptResult;
import cpssp.opt.ProgType;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;

import java.util.*;

/**
 * High-level stochastic optimization coordinator.
 * 
 * Computes:
 * - Expected Value (EV) and Expected result of Expected Value (EEV)
 * - Wait-and-See / Perfect Information Bound (WS)
 * - Row Aggregation Lower Bound + Heuristic Upper Bound (RALB + FDEE-IS)
 * - Full problem stochastic measures: EVPI and VSS
 * - Sample Average Approximation (SAA) statistical stability and confidence intervals
 */
public class CpsspAlgoILP {

    private final CpsspInstance inst;
    private final ILPparams ilpprms;
    private GRBEnv grbenv;

    private boolean isVerbose = false;
    private boolean runSilent = false;

    public CpsspAlgoILP(CpsspInstance inst, ILPparams params) throws GRBException {
        this.inst = inst;
        this.ilpprms = params;

        // Initialize Gurobi environment
        this.grbenv = new GRBEnv(true);
        this.grbenv.set("LogFile", "grb.log");
        this.grbenv.set(GRB.IntParam.OutputFlag, 0);
        if (params.maxmem > 0) {
            this.grbenv.set(GRB.DoubleParam.MemLimit, params.maxmem / 1024.0);
        }
        if (params.threads2use > 0) {
            this.grbenv.set(GRB.IntParam.Threads, params.threads2use);
        }
        this.grbenv.start();
    }

    public void setVerbose(boolean verbose) {
        this.isVerbose = verbose;
    }

    public void setSilent(boolean silent) {
        this.runSilent = silent;
    }

    public GRBEnv getGrbEnv() {
        return grbenv;
    }

    public OptResult testProgFeasibility(ILPparams prms) throws GRBException {
        CpsspILP ilp = new CpsspILP(inst, grbenv, prms, Collections.emptyList());
        ilp.setVerbose(isVerbose);
        return ilp.feasibleProg(prms);
    }

    public OptResult solveProg(ILPparams prms) throws GRBException {
        CpsspILP ilp = new CpsspILP(inst, grbenv, prms, Collections.emptyList());
        ilp.setVerbose(isVerbose);
        return ilp.solveProg(prms);
    }

    public OptResult solveProgDeterm(ILPparams prms) throws GRBException, java.io.IOException {
        CpsspILP ilp = new CpsspILP(inst, grbenv, prms, true);
        ilp.setVerbose(isVerbose);
        OptResult optr = ilp.solveProg(prms);
        ilp.storeItemPacking();
        ilp.writeSolToFile(inst.getBaseName() + ".sspsol");
        return optr;
    }

    /**
     * Wait-and-See (WS) bound: Solves each scenario independently with decoupled non-anticipativity.
     */
    public OptResult computePerfectInfoBound(ILPparams grbps) throws GRBException {
        if (!runSilent) {
            System.out.println("Cpssp algorithm ::: WS ::: compute ...");
        }
        ILPparams wsilps = new ILPparams(grbps);
        wsilps.timelimsec = grbps.timelim2sec;
        OptResult optr = evaluateScenarios(wsilps, null, null, false);

        if (!runSilent) {
            System.out.printf(Locale.US, "Cpssp algorithm ::: WS ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f%n",
                    optr.getStatus(), optr.runTime, optr.objValue, optr.bestBound);
        }
        return optr;
    }

    /**
     * Computes EV (Expected Value of mean scenario) and evaluates its 1st-stage solution to obtain EEV.
     */
    public OptResult computeAveScenarioBound(ILPparams ilpps) throws GRBException {
        if (!runSilent) {
            System.out.println("Cpssp algorithm ::: EV ::: compute ...");
        }
        long startTime = System.currentTimeMillis();

        // Swap out scenarios with average scenario
        List<Scenario> originalScenarios = new ArrayList<>(inst.scenarios);
        int originalNrScenarios = inst.nrOfScenarios;

        inst.scenarios = Collections.singletonList(inst.aveScenario);
        inst.nrOfScenarios = 1;

        CpsspILP ilp = new CpsspILP(inst, grbenv, ilpps, Collections.emptyList());
        ilp.setVerbose(isVerbose);
        OptResult evoptr = ilp.solveProg(ilpps);

        if (!runSilent) {
            System.out.printf(Locale.US, "Cpssp algorithm ::: EV ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f%n",
                    evoptr.getStatus(), evoptr.runTime, evoptr.objValue, evoptr.bestBound);
        }

        // Extract 1st-stage solution
        boolean[] supplSel = ilp.getSuppliersSelection();
        boolean[] binSel = ilp.getStage1BinSelection();

        // Restore original scenarios
        inst.scenarios = originalScenarios;
        inst.nrOfScenarios = originalNrScenarios;

        // Evaluate 1st stage solution across all scenarios to compute EEV
        if (!runSilent) {
            System.out.println("Cpssp algorithm ::: EEV ::: compute ...");
        }
        ILPparams esilps = new ILPparams(ilpps);
        esilps.algo = 0;
        esilps.timelimsec = ilpps.timelim2sec;

        OptResult eevoptr = evaluateScenarios(esilps, supplSel, binSel, false);

        if (!runSilent) {
            System.out.printf(Locale.US, "Cpssp algorithm ::: EEV ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f%n",
                    eevoptr.getStatus(), eevoptr.runTime, eevoptr.objValue, eevoptr.bestBound);
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        OptResult optr = new OptResult();
        optr.tags.add("");
        optr.tags.add("");
        boolean eevAllOpt = !eevoptr.integerValues.isEmpty() && eevoptr.integerValues.get(0) == 1;
        optr.tags.add(eevAllOpt ? "Optimal" : "SubOptimal");
        optr.bestBound = eevoptr.bestBound;
        optr.objValue = eevoptr.objValue;
        optr.optGap = eevoptr.optGap;
        optr.runTime = totalTime;

        boolean evOpt = !evoptr.integerValues.isEmpty() && evoptr.integerValues.get(0) == 2;
        optr.integerValues.add(evOpt ? 1 : 0);
        optr.integerValues.add(eevAllOpt ? 1 : 0);

        // Store EV and EEV data in doubleValues
        optr.doubleValues.add(evoptr.bestBound);
        optr.doubleValues.add(evoptr.objValue);
        optr.doubleValues.add(eevoptr.bestBound);
        optr.doubleValues.add(eevoptr.objValue);
        optr.doubleValues.add(evoptr.runTime);
        optr.doubleValues.add(eevoptr.runTime);

        return optr;
    }

    /**
     * Evaluates fixed 1st-stage decisions (or free decisions if null) across all scenarios individually.
     */
    public OptResult evaluateScenarios(ILPparams grbps, boolean[] supplSel, boolean[] binSel, boolean allMustBeOpt) throws GRBException {
        long startTime = System.currentTimeMillis();
        int S = inst.nrOfScenarios;

        double piLbv = 0.0;
        double piObjv = 0.0;
        double piGapv = 0.0;
        boolean allOpt = true;

        for (int sdx = 0; sdx < S; sdx++) {
            CpsspILP ilp = new CpsspILP(inst, grbenv, grbps, Collections.singletonList(sdx));
            if (supplSel != null) {
                ilp.fixSuppliers(supplSel);
            }
            if (binSel != null) {
                ilp.fixStage1Bins(binSel);
            }

            OptResult sOptr = ilp.solveProg(grbps);
            boolean isOpt = (!sOptr.integerValues.isEmpty() && sOptr.integerValues.get(0) == 2);
            allOpt = allOpt && isOpt;

            if (allMustBeOpt && !allOpt) {
                piLbv = Double.NaN;
                piObjv = Double.NaN;
                piGapv = Double.NaN;
                break;
            }

            double sProb = inst.scenarios.get(sdx).prob;
            piLbv += sProb * sOptr.bestBound;
            piObjv += sProb * sOptr.objValue;
            piGapv += sProb * sOptr.optGap;
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        OptResult optr = new OptResult();
        optr.tags.add("");
        optr.tags.add("");
        optr.tags.add(allOpt ? "Optimal" : "SubOptimal");
        optr.bestBound = piLbv;
        optr.objValue = piObjv;
        optr.optGap = piGapv;
        optr.runTime = totalTime;
        optr.integerValues.add(allOpt ? 1 : 0);

        return optr;
    }

    /**
     * Row Aggregation Lower Bound (RALB) + Heuristic Upper Bound (FDEE-IS):
     * Solves RALB to get LB, then fixes 1st-stage decisions and solves individual scenario FDEs to get UB.
     */
    public OptResult computeFullProblemUB(ILPparams ilpps, List<Integer> scenarioIds, boolean scEval) throws GRBException {
        long startTime = System.currentTimeMillis();
        int nrScenarios = (scenarioIds == null || scenarioIds.isEmpty()) ? inst.nrOfScenarios : scenarioIds.size();

        if (!runSilent) {
            System.out.printf("Cpssp algorithm ::: %s ::: %d scenarios ::: solve ...%n",
                    (ilpps.progtype == ProgType.RALB ? "RA ILP" : "FDE"), nrScenarios);
        }

        ILPparams lbilpps = new ILPparams(ilpps);
        CpsspILP ilpprog = new CpsspILP(inst, grbenv, lbilpps, scenarioIds);
        ilpprog.setVerbose(isVerbose);

        OptResult optrlb = ilpprog.solveProg(lbilpps);

        if (!runSilent) {
            System.out.printf(Locale.US, "Cpssp algorithm ::: %s ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f%n",
                    (ilpps.progtype == ProgType.RALB ? "RA ILP" : "FDE"),
                    optrlb.getStatus(), optrlb.runTime, optrlb.objValue, optrlb.bestBound);
        }

        OptResult optrub = null;
        if (scEval && lbilpps.progtype == ProgType.RALB) {
            boolean[] supplSel = ilpprog.getSuppliersSelection();
            boolean[] binSel = ilpprog.getStage1BinSelection();

            if (!runSilent) {
                System.out.println("Cpssp algorithm ::: FDEE-IS ::: compute ...");
            }

            ILPparams ubilpps = new ILPparams(ilpps);
            ubilpps.progtype = ProgType.FDE;
            ubilpps.algo = 0;
            ubilpps.timelimsec = ilpps.timelim2sec;

            optrub = evaluateScenarios(ubilpps, supplSel, binSel, false);

            if (!runSilent) {
                System.out.printf(Locale.US, "Cpssp algorithm ::: FDEE-IS ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f ::: allOpt %d ::: ave gap %.2f%%%n",
                        optrub.getStatus(), optrub.runTime, optrub.objValue, optrub.bestBound,
                        optrub.integerValues.get(0), optrub.optGap * 100.0);
            }
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        OptResult optr = new OptResult();
        optr.tags.add("");
        optr.tags.add("");
        optr.bestBound = optrlb.bestBound;
        optr.runTime = totalTime;

        boolean ralbIsOpt = !optrlb.integerValues.isEmpty() && optrlb.integerValues.get(0) == 2;
        optr.integerValues.add(ralbIsOpt ? 1 : 0);

        if (scEval && lbilpps.progtype == ProgType.RALB && optrub != null) {
            boolean isOpt = optrub.objValue <= optrlb.bestBound + ilpps.eps;
            optr.tags.add(isOpt ? "Optimal" : "SubOptimal");
            optr.objValue = optrub.objValue;
            optr.optGap = (optrub.objValue - optrlb.bestBound) / optrlb.bestBound;
            optr.integerValues.add(optrub.integerValues.get(0));

            optr.doubleValues.add(optrlb.objValue);
            optr.doubleValues.add(optrlb.optGap);
            optr.doubleValues.add(optrlb.runTime);
            optr.doubleValues.add(optrub.bestBound);
            optr.doubleValues.add(optrub.optGap);
            optr.doubleValues.add(optrub.runTime);
        } else {
            optr.tags.add(ralbIsOpt ? "Optimal" : "SubOptimal");
            optr.objValue = optrlb.objValue;
            optr.optGap = optrlb.optGap;
            optr.integerValues.add(0);

            optr.doubleValues.add(optrlb.objValue);
            optr.doubleValues.add(optrlb.optGap);
            optr.doubleValues.add(optrlb.runTime);
        }

        if (!runSilent) {
            System.out.printf(Locale.US, "Cpssp algorithm ::: %s ::: status %s ::: time %.3fs ::: OF %.2f ::: BB %.2f ::: gap %.2f%%%n",
                    (scEval && lbilpps.progtype == ProgType.RALB ? "FDEEIS" : lbilpps.progtype.toString()),
                    optr.getStatus(), optr.runTime, optr.objValue, optr.bestBound, optr.optGap * 100.0);
        }

        return optr;
    }

    /**
     * Full Stochastic Problem Evaluation:
     * Computes EV, WS, RP, EEV, verifying EV <= WS <= RP <= EEV and calculating EVPI and VSS.
     */
    public OptResult computeRootEvaluation(ILPparams ilpps) throws GRBException {
        System.out.println("Cpssp algorithm ::: stochastic problem evaluation ...");
        long startTime = System.currentTimeMillis();

        OptResult asOptr = computeAveScenarioBound(ilpps);
        OptResult piOptr = computePerfectInfoBound(ilpps);
        OptResult ubOptr = computeFullProblemUB(ilpps, Collections.emptyList(), true);

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        boolean evIsOpt = asOptr.integerValues.get(0) == 1;
        boolean eevIsOpt = asOptr.integerValues.get(1) == 1;
        boolean wsIsOpt = piOptr.integerValues.get(0) == 1;
        boolean rapIsOpt = ubOptr.integerValues.get(0) == 1;

        double ev = evIsOpt ? asOptr.doubleValues.get(1) : asOptr.doubleValues.get(0);
        double ws = wsIsOpt ? piOptr.objValue : piOptr.bestBound;
        double rp = rapIsOpt ? ubOptr.doubleValues.get(0) : ubOptr.bestBound;
        double eev = eevIsOpt ? asOptr.doubleValues.get(3) : asOptr.doubleValues.get(2);

        double evpiAbs = rp - ws;
        double evpiPct = (rp - ws) / ws;
        double vssAbs = eev - rp;
        double vssPct = (eev - rp) / rp;

        System.out.printf(Locale.US, "Cpssp algorithm ::: stochastic problem evaluation ::: EV %.1f <= WS %.1f <= RP %.1f <= EEV %.1f%n",
                ev, ws, rp, eev);
        System.out.printf(Locale.US, "Cpssp algorithm ::: stochastic problem evaluation ::: EVPI %.2f ::: %.2f%%%n",
                evpiAbs, evpiPct * 100.0);
        System.out.printf(Locale.US, "Cpssp algorithm ::: stochastic problem evaluation ::: VSS %.2f ::: %.2f%%%n",
                vssAbs, vssPct * 100.0);

        OptResult optr = new OptResult();
        optr.tags = new ArrayList<>(ubOptr.tags);
        optr.bestBound = ubOptr.bestBound;
        optr.objValue = ubOptr.objValue;
        optr.optGap = ubOptr.optGap;
        optr.runTime = totalTime;

        optr.integerValues.add(evIsOpt ? 1 : 0);
        optr.integerValues.add(eevIsOpt ? 1 : 0);
        optr.integerValues.add(wsIsOpt ? 1 : 0);
        optr.integerValues.add(rapIsOpt ? 1 : 0);

        optr.doubleValues.add(ev);
        optr.doubleValues.add(ws);
        optr.doubleValues.add(rp);
        optr.doubleValues.add(eev);
        optr.doubleValues.add(evpiAbs);
        optr.doubleValues.add(vssAbs);

        return optr;
    }

    /**
     * SAA stability evaluation across samples.
     */
    public OptResult computeFullProblemUBerr(ILPparams ilpps, int sampleSize) throws GRBException {
        System.out.printf("Cpssp algorithm ::: %s ::: RP error computation ...%n",
                (ilpps.progtype == ProgType.RALB ? "RA ILP" : "FDE"));
        long startTime = System.currentTimeMillis();

        int nrOfSamples = inst.sampleScenarios(sampleSize);
        if (inst.scenarioSampling == null || inst.scenarioSampling.isEmpty()) {
            throw new IllegalStateException("No scenario sampling available");
        }

        double[] piLblvs = new double[nrOfSamples];
        double[] piObjvs = new double[nrOfSamples];
        double piLbv = 0.0;
        double piObjv = 0.0;
        double piGapv = 0.0;

        runSilent = true;
        for (int s = 0; s < nrOfSamples; s++) {
            OptResult optr = computeFullProblemUB(ilpps, inst.scenarioSampling.get(s), false);
            piLblvs[s] = optr.bestBound;
            piObjvs[s] = optr.objValue;
            piLbv += optr.bestBound / nrOfSamples;
            piObjv += optr.objValue / nrOfSamples;
            piGapv += optr.optGap / nrOfSamples;
        }
        runSilent = false;

        double sdLbv = 0.0;
        double sdObjv = 0.0;
        for (int s = 0; s < nrOfSamples; s++) {
            sdLbv += Math.pow(piLblvs[s] - piLbv, 2.0);
            sdObjv += Math.pow(piObjvs[s] - piObjv, 2.0);
        }
        sdLbv = Math.sqrt(sdLbv / nrOfSamples);
        sdObjv = Math.sqrt(sdObjv / nrOfSamples);

        double meObjv = 1.96 * sdObjv / Math.sqrt(nrOfSamples);

        System.out.printf(Locale.US, "Cpssp algorithm ::: RP error ::: Ave %.2f ::: stdev %.2f ::: CI.95 [%.2f, %.2f] ::: Err.95 %.2f%%%n",
                piObjv, sdObjv, (piObjv - meObjv), (piObjv + meObjv), 100.0 * meObjv / piObjv);

        OptResult optres = new OptResult();
        optres.tags.add("");
        optres.tags.add("");
        optres.tags.add(piGapv <= ilpps.eps ? "Optimal" : "SubOptimal");
        optres.bestBound = piLbv;
        optres.objValue = piObjv;
        optres.optGap = piGapv;
        optres.runTime = (System.currentTimeMillis() - startTime) / 1000.0;

        optres.integerValues.add(nrOfSamples);
        optres.integerValues.add(sampleSize);

        optres.doubleValues.add(piObjv);
        optres.doubleValues.add(sdObjv);
        optres.doubleValues.add(meObjv);

        return optres;
    }
}
