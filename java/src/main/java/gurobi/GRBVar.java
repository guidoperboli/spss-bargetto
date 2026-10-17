package gurobi;

/**
 * Gurobi decision variable representation compatible with Gurobi Java API.
 */
public class GRBVar {

    private double lb;
    private double ub;
    private double obj;
    private char type;
    private String name;
    private int priority;
    private double x;

    public GRBVar(double lb, double ub, double obj, char type, String name) {
        this.lb = lb;
        this.ub = ub;
        this.obj = obj;
        this.type = type;
        this.name = name;
        this.priority = 0;
        this.x = 0.0;
    }

    public void set(GRB.DoubleAttr attr, double val) {
        if (attr == GRB.DoubleAttr.UB) {
            this.ub = val;
        } else if (attr == GRB.DoubleAttr.LB) {
            this.lb = val;
        } else if (attr == GRB.DoubleAttr.X) {
            this.x = val;
        } else if (attr == GRB.DoubleAttr.Obj) {
            this.obj = val;
        }
    }

    public void set(GRB.IntAttr attr, int val) {
        if (attr == GRB.IntAttr.BranchPriority) {
            this.priority = val;
        }
    }

    public double get(GRB.DoubleAttr attr) {
        if (attr == GRB.DoubleAttr.X) {
            return x;
        }
        if (attr == GRB.DoubleAttr.UB) {
            return ub;
        }
        if (attr == GRB.DoubleAttr.LB) {
            return lb;
        }
        if (attr == GRB.DoubleAttr.Obj) {
            return obj;
        }
        return 0.0;
    }

    public int get(GRB.IntAttr attr) {
        if (attr == GRB.IntAttr.BranchPriority) {
            return priority;
        }
        return 0;
    }

    public String getName() {
        return name;
    }
}
