package cpssp.opt;

/**
 * Model formulation type:
 * - FDE: Full Deterministic Equivalent (with explicit item-to-bin packing)
 * - RALB: Row Aggregation Lower Bound (with aggregated volume capacity)
 */
public enum ProgType {
    FDE,
    RALB;

    @Override
    public String toString() {
        return name();
    }
}
