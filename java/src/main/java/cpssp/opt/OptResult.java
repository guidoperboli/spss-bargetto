package cpssp.opt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Standard optimization result structure tracking status tags,
 * objective values, lower bounds, runtime, and diagnostic metrics.
 */
public class OptResult {

    public List<String> tags = new ArrayList<>();
    public double bestBound = Double.NaN;
    public double objValue = Double.NaN;
    public double optGap = Double.NaN;
    public double runTime = 0.0;
    public List<Integer> integerValues = new ArrayList<>();
    public List<Double> doubleValues = new ArrayList<>();

    public OptResult() {
    }

    public String getStatus() {
        if (tags != null && tags.size() > 2) {
            return tags.get(2);
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OptResult { ");
        sb.append("status=").append(getStatus());
        sb.append(String.format(Locale.US, ", OF=%.2f, BB=%.2f, Gap=%.2f%%, Time=%.3fs",
                objValue, bestBound, optGap * 100.0, runTime));
        if (!integerValues.isEmpty()) {
            sb.append(", intVals=").append(integerValues);
        }
        if (!doubleValues.isEmpty()) {
            sb.append(", dblVals=[");
            for (int i = 0; i < doubleValues.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format(Locale.US, "%.2f", doubleValues.get(i)));
            }
            sb.append("]");
        }
        sb.append(" }");
        return sb.toString();
    }
}
