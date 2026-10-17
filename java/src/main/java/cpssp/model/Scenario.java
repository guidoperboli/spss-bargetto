package cpssp.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a realized scenario in the two-stage stochastic capacity planning problem.
 * 
 * Each scenario specifies:
 * - Realized (degraded) volumes of 1st-stage bins due to supplier capacity loss
 * - Number, types, suppliers, and spot rates of 2nd-stage bins available
 * - Customer orders (items) and their individual volumes
 */
public class Scenario {

    public double prob;

    // 1st stage bins' degraded volume in this scenario: V_{j,m}^{t,s}
    public int[] stage1BinVolumes;

    // 2nd stage spot bins
    public int nrOfStage2Bins;
    public double[] stage2BinCosts;
    public int[] stage2BinTypes;
    public int[] stage2BinSuppliers;

    // Items (orders) data
    public int nrOfItems;
    public int totVolumeOfItems;
    public int[] itemVolumes;

    public double expDeliveryCost = 0.0;

    // Item size frequency: index is size, value is count of items of that size
    public int[] itemSizesFreq;
    public int itemMinVolume = Integer.MAX_VALUE;
    public int itemMaxVolume = 0;

    // Branching priorities for 2nd stage bins
    public int[] stage2BinPriority;

    public Scenario() {
        this.prob = 0.0;
    }

    /**
     * Copy constructor for deep-copying scenarios (used in EV/EEV scenario swapping).
     */
    public Scenario(Scenario other) {
        this.prob = other.prob;
        if (other.stage1BinVolumes != null) {
            this.stage1BinVolumes = other.stage1BinVolumes.clone();
        }
        this.nrOfStage2Bins = other.nrOfStage2Bins;
        if (other.stage2BinCosts != null) {
            this.stage2BinCosts = other.stage2BinCosts.clone();
        }
        if (other.stage2BinTypes != null) {
            this.stage2BinTypes = other.stage2BinTypes.clone();
        }
        if (other.stage2BinSuppliers != null) {
            this.stage2BinSuppliers = other.stage2BinSuppliers.clone();
        }
        this.nrOfItems = other.nrOfItems;
        this.totVolumeOfItems = other.totVolumeOfItems;
        if (other.itemVolumes != null) {
            this.itemVolumes = other.itemVolumes.clone();
        }
        this.expDeliveryCost = other.expDeliveryCost;
        if (other.itemSizesFreq != null) {
            this.itemSizesFreq = other.itemSizesFreq.clone();
        }
        this.itemMinVolume = other.itemMinVolume;
        this.itemMaxVolume = other.itemMaxVolume;
        if (other.stage2BinPriority != null) {
            this.stage2BinPriority = other.stage2BinPriority.clone();
        }
    }

    @Override
    public String toString() {
        return String.format("Scenario [prob=%.4f, items=%d, totVol=%d, s2Bins=%d, expCost=%.2f]",
                prob, nrOfItems, totVolumeOfItems, nrOfStage2Bins, expDeliveryCost);
    }
}
