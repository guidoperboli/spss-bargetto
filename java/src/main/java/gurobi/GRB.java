package gurobi;

/**
 * Gurobi API constant definitions.
 * Compatible with Gurobi Java API.
 */
public class GRB {
    public static final char BINARY = 'B';
    public static final char INTEGER = 'I';
    public static final char CONTINUOUS = 'C';
    public static final double INFINITY = 1e100;

    public static final int MINIMIZE = 1;
    public static final int MAXIMIZE = -1;

    public static final char EQUAL = '=';
    public static final char LESS_EQUAL = '<';
    public static final char GREATER_EQUAL = '>';

    public enum IntAttr {
        Status,
        BranchPriority
    }

    public enum DoubleAttr {
        UB,
        LB,
        Obj,
        ObjVal,
        ObjBound,
        Runtime,
        MIPGap,
        X
    }

    public enum IntParam {
        OutputFlag,
        Presolve,
        Sifting,
        Threads,
        Method,
        BranchDir
    }

    public enum DoubleParam {
        MemLimit,
        NodefileStart,
        TimeLimit
    }

    public static class Status {
        public static final int LOADED = 1;
        public static final int OPTIMAL = 2;
        public static final int INFEASIBLE = 3;
        public static final int INF_OR_UNBD = 4;
        public static final int UNBOUNDED = 5;
        public static final int CUTOFF = 6;
        public static final int ITERATION_LIMIT = 7;
        public static final int NODE_LIMIT = 8;
        public static final int TIME_LIMIT = 9;
        public static final int SOLUTION_LIMIT = 10;
        public static final int INTERRUPTED = 11;
        public static final int NUMERIC = 12;
        public static final int SUBOPTIMAL = 13;
        public static final int INPROGRESS = 14;
        public static final int USER_OBJ_LIMIT = 15;
    }
}
