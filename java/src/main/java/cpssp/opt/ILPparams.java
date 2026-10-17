package cpssp.opt;

/**
 * Parameters passed to Gurobi and the ILP construction/solving routines.
 */
public class ILPparams {

    public ProgType progtype = ProgType.FDE;
    public int algo = 0;              // 0: standard MILP solve, 1: combinatorial supplier enumeration

    public boolean presolve = true;
    public boolean priorities = false;

    public int sifting = -1;
    public int threads2use = 1;
    public int lpAlgo = -1;           // -1: default/auto, 0: primal, 1: dual, 2: barrier

    public double maxmem = 1.0;
    public double timelimsec = 0.0;
    public double timelim2sec = 0.0;  // time limit for subproblem scenario evaluations

    public double eps = 1.0e-9;

    public boolean overrideMinVolXSupplier = false;
    public double minVolXSupplierValue = 0.05;

    public ILPparams() {
    }

    public ILPparams(ILPparams other) {
        this.progtype = other.progtype;
        this.algo = other.algo;
        this.presolve = other.presolve;
        this.priorities = other.priorities;
        this.sifting = other.sifting;
        this.threads2use = other.threads2use;
        this.lpAlgo = other.lpAlgo;
        this.maxmem = other.maxmem;
        this.timelimsec = other.timelimsec;
        this.timelim2sec = other.timelim2sec;
        this.eps = other.eps;
        this.overrideMinVolXSupplier = other.overrideMinVolXSupplier;
        this.minVolXSupplierValue = other.minVolXSupplierValue;
    }
}
