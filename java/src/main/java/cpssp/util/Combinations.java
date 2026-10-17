package cpssp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for generating combinatorial subsets C(N, K) matching C++ std::prev_permutation
 * on bitmasks.
 */
public class Combinations {

    /**
     * Generates all boolean bitmasks of length n containing exactly k true values.
     */
    public static List<boolean[]> generate(int n, int k) {
        List<boolean[]> result = new ArrayList<>();
        if (k < 0 || k > n) {
            return result;
        }
        boolean[] current = new boolean[n];
        backtrack(n, k, 0, 0, current, result);
        return result;
    }

    private static void backtrack(int n, int k, int index, int count, boolean[] current, List<boolean[]> result) {
        if (count == k) {
            result.add(current.clone());
            return;
        }
        if (index >= n || (k - count) > (n - index)) {
            return;
        }

        // Branch 1: include index
        current[index] = true;
        backtrack(n, k, index + 1, count + 1, current, result);

        // Branch 2: exclude index
        current[index] = false;
        backtrack(n, k, index + 1, count, current, result);
    }
}
