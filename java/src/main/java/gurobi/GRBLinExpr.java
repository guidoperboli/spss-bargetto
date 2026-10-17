package gurobi;

import java.util.ArrayList;
import java.util.List;

/**
 * Linear expression container compatible with Gurobi Java API.
 */
public class GRBLinExpr {

    private final List<Double> coeffs = new ArrayList<>();
    private final List<GRBVar> vars = new ArrayList<>();
    private double constant = 0.0;

    public GRBLinExpr() {
    }

    public void addTerm(double coeff, GRBVar var) {
        coeffs.add(coeff);
        vars.add(var);
    }

    public void add(GRBLinExpr other) {
        coeffs.addAll(other.coeffs);
        vars.addAll(other.vars);
        constant += other.constant;
    }

    public void addConstant(double c) {
        constant += c;
    }

    public int size() {
        return vars.size();
    }

    public double getCoeff(int i) {
        return coeffs.get(i);
    }

    public GRBVar getVar(int i) {
        return vars.get(i);
    }

    public double getConstant() {
        return constant;
    }
}
