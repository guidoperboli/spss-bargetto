package cpssp.model;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

/**
 * Encapsulates problem instance data, scenario sets, priority calculations,
 * and statistical instance operations for the CPSSP / SVCSBPP-CL-SS problem.
 */
public class CpsspInstance {

    // Instance file path and name
    public String ifname;

    // Instance statistics
    public int maxNrOfScenarioItems = 0;
    public int maxNrOfStage2Bins = 0;
    public int maxVolOfScenario = 0;
    public double maxS1BinCost = 0.0;
    public double maxLossCost = 0.0;

    // Problem data
    public int nrOfSuppliers;
    public int nrOfBinTypes;
    public int minNrOfSuppliers;
    public int nrOfStage1Bins;

    public double minVolXSupplier;
    public double minAveReliability;

    // Suppliers' data
    public double[] supplierThetas;
    public double[] supplierContractCosts;

    // Bin types' data
    public int[] typeBinVolumes;
    public double[] typeSupplBinCosts;         // indexed by [t * nrOfSuppliers + m]
    public double[] typeSupplBinLossXUCosts;   // indexed by [t * nrOfSuppliers + m]
    public double[] typeSupplBinLossXUComps;   // indexed by [t * nrOfSuppliers + m]

    // 1st stage bins' pool
    public List<Integer> stage1BinTypes;
    public List<Integer> stage1BinSuppliers;

    // Scenarios data
    public int nrOfScenarios;
    public List<Scenario> scenarios;
    public List<List<Integer>> scenarioSampling;
    public Scenario aveScenario;

    // Variable priorities
    public int[] supplierPriority;
    public int[] stage1BinPriority;

    public CpsspInstance(String filePath) throws IOException {
        this.ifname = filePath;
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Instance file not found: " + filePath);
        }
        try (InputStream in = new FileInputStream(file)) {
            read(in);
        }
        computeAveScenario();
    }

    public CpsspInstance(InputStream in, String name) throws IOException {
        this.ifname = name;
        read(in);
        computeAveScenario();
    }

    /**
     * Fast token-based reader handling arbitrary whitespace and line breaks.
     */
    private void read(InputStream in) throws IOException {
        FastScanner scanner = new FastScanner(in);

        maxS1BinCost = 0.0;
        maxLossCost = 0.0;

        nrOfSuppliers = scanner.nextInt();
        minNrOfSuppliers = scanner.nextInt();
        minVolXSupplier = scanner.nextDouble();
        minAveReliability = scanner.nextDouble();

        supplierThetas = new double[nrOfSuppliers];
        for (int m = 0; m < nrOfSuppliers; m++) {
            supplierThetas[m] = scanner.nextDouble();
        }

        supplierContractCosts = new double[nrOfSuppliers];
        for (int m = 0; m < nrOfSuppliers; m++) {
            supplierContractCosts[m] = scanner.nextDouble();
        }

        nrOfBinTypes = scanner.nextInt();

        typeBinVolumes = new int[nrOfBinTypes];
        for (int t = 0; t < nrOfBinTypes; t++) {
            typeBinVolumes[t] = scanner.nextInt();
        }

        int totalTypeSuppl = nrOfBinTypes * nrOfSuppliers;
        typeSupplBinCosts = new double[totalTypeSuppl];
        for (int t = 0; t < nrOfBinTypes; t++) {
            for (int m = 0; m < nrOfSuppliers; m++) {
                int idx = t * nrOfSuppliers + m;
                typeSupplBinCosts[idx] = scanner.nextDouble();
                maxS1BinCost = Math.max(maxS1BinCost, typeSupplBinCosts[idx]);
            }
        }

        typeSupplBinLossXUCosts = new double[totalTypeSuppl];
        for (int t = 0; t < nrOfBinTypes; t++) {
            for (int m = 0; m < nrOfSuppliers; m++) {
                typeSupplBinLossXUCosts[t * nrOfSuppliers + m] = scanner.nextDouble();
            }
        }

        typeSupplBinLossXUComps = new double[totalTypeSuppl];
        for (int t = 0; t < nrOfBinTypes; t++) {
            for (int m = 0; m < nrOfSuppliers; m++) {
                int idx = t * nrOfSuppliers + m;
                typeSupplBinLossXUComps[idx] = scanner.nextDouble();
                double netLoss = typeBinVolumes[t] * (typeSupplBinLossXUCosts[idx] - typeSupplBinLossXUComps[idx]);
                maxLossCost = Math.max(maxLossCost, netLoss);
            }
        }

        nrOfStage1Bins = scanner.nextInt();
        stage1BinTypes = new ArrayList<>(nrOfStage1Bins);
        for (int k = 0; k < nrOfStage1Bins; k++) {
            stage1BinTypes.add(scanner.nextInt());
        }

        stage1BinSuppliers = new ArrayList<>(nrOfStage1Bins);
        for (int k = 0; k < nrOfStage1Bins; k++) {
            stage1BinSuppliers.add(scanner.nextInt());
        }

        nrOfScenarios = scanner.nextInt();
        scenarios = new ArrayList<>(nrOfScenarios);

        maxNrOfScenarioItems = 0;
        maxNrOfStage2Bins = 0;
        maxVolOfScenario = 0;

        for (int s = 0; s < nrOfScenarios; s++) {
            Scenario sc = new Scenario();

            sc.stage1BinVolumes = new int[nrOfStage1Bins];
            for (int k = 0; k < nrOfStage1Bins; k++) {
                sc.stage1BinVolumes[k] = scanner.nextInt();
            }

            sc.nrOfStage2Bins = scanner.nextInt();
            maxNrOfStage2Bins = Math.max(maxNrOfStage2Bins, sc.nrOfStage2Bins);

            sc.stage2BinCosts = new double[totalTypeSuppl];
            for (int t = 0; t < nrOfBinTypes; t++) {
                for (int m = 0; m < nrOfSuppliers; m++) {
                    sc.stage2BinCosts[t * nrOfSuppliers + m] = scanner.nextDouble();
                }
            }

            sc.stage2BinTypes = new int[sc.nrOfStage2Bins];
            for (int l = 0; l < sc.nrOfStage2Bins; l++) {
                sc.stage2BinTypes[l] = scanner.nextInt();
            }

            sc.stage2BinSuppliers = new int[sc.nrOfStage2Bins];
            for (int l = 0; l < sc.nrOfStage2Bins; l++) {
                sc.stage2BinSuppliers[l] = scanner.nextInt();
            }

            sc.nrOfItems = scanner.nextInt();
            maxNrOfScenarioItems = Math.max(maxNrOfScenarioItems, sc.nrOfItems);
            sc.itemVolumes = new int[sc.nrOfItems];
            sc.totVolumeOfItems = 0;
            sc.itemMinVolume = Integer.MAX_VALUE;
            sc.itemMaxVolume = 0;

            for (int i = 0; i < sc.nrOfItems; i++) {
                sc.itemVolumes[i] = scanner.nextInt();
                sc.totVolumeOfItems += sc.itemVolumes[i];
                sc.itemMinVolume = Math.min(sc.itemMinVolume, sc.itemVolumes[i]);
                sc.itemMaxVolume = Math.max(sc.itemMaxVolume, sc.itemVolumes[i]);
            }

            maxVolOfScenario = Math.max(maxVolOfScenario, sc.totVolumeOfItems);
            sc.prob = 1.0 / nrOfScenarios;

            // Compute item sizes frequency
            sc.itemSizesFreq = new int[sc.itemMaxVolume + 1];
            for (int i = 0; i < sc.nrOfItems; i++) {
                sc.itemSizesFreq[sc.itemVolumes[i]]++;
            }

            // Express delivery cost calculation
            sc.expDeliveryCost = 0.0;
            for (double c : sc.stage2BinCosts) {
                sc.expDeliveryCost = Math.max(sc.expDeliveryCost, c);
            }
            sc.expDeliveryCost = Math.max(sc.expDeliveryCost, maxS1BinCost + maxLossCost);

            scenarios.add(sc);
        }
    }

    /**
     * Synthesizes an average scenario representing expected demand and capacity.
     */
    public void computeAveScenario() {
        aveScenario = new Scenario();
        aveScenario.prob = 1.0;

        // Average 1st stage bin volumes
        double[] s1BinAveVols = new double[nrOfStage1Bins];
        for (Scenario s : scenarios) {
            for (int j = 0; j < nrOfStage1Bins; j++) {
                s1BinAveVols[j] += s.prob * s.stage1BinVolumes[j];
            }
        }
        aveScenario.stage1BinVolumes = new int[nrOfStage1Bins];
        for (int j = 0; j < nrOfStage1Bins; j++) {
            aveScenario.stage1BinVolumes[j] = (int) Math.round(s1BinAveVols[j]);
        }

        // Average number of 2nd stage bins
        double tmpAveStage2Bins = 0.0;
        for (Scenario s : scenarios) {
            tmpAveStage2Bins += s.prob * s.nrOfStage2Bins;
        }
        aveScenario.nrOfStage2Bins = (int) Math.round(tmpAveStage2Bins);

        // Average 2nd stage bin costs
        double aveStage2BinCost = 0.0;
        aveScenario.expDeliveryCost = 0.0;
        for (Scenario s : scenarios) {
            aveScenario.expDeliveryCost += s.prob * s.expDeliveryCost;
            double tmpCost = 0.0;
            for (double c : s.stage2BinCosts) {
                tmpCost += c;
            }
            aveStage2BinCost += s.prob * tmpCost / s.stage2BinCosts.length;
        }
        int totalTypeSuppl = nrOfBinTypes * nrOfSuppliers;
        aveScenario.stage2BinCosts = new double[totalTypeSuppl];
        Arrays.fill(aveScenario.stage2BinCosts, aveStage2BinCost);

        // Average bin counts per type and supplier
        double[] aveBinXType = new double[nrOfBinTypes];
        double[] aveBinXSuppl = new double[nrOfSuppliers];
        for (Scenario s : scenarios) {
            for (int t : s.stage2BinTypes) {
                aveBinXType[t] += s.prob;
            }
            for (int m : s.stage2BinSuppliers) {
                aveBinXSuppl[m] += s.prob;
            }
        }

        List<Integer> s2Types = new ArrayList<>();
        for (int t = 0; t < nrOfBinTypes; t++) {
            int ctr = (int) Math.round(aveBinXType[t]);
            for (int j = 0; j < ctr && s2Types.size() < aveScenario.nrOfStage2Bins; j++) {
                s2Types.add(t);
            }
        }
        while (s2Types.size() < aveScenario.nrOfStage2Bins) {
            s2Types.add(nrOfBinTypes - 1);
        }
        aveScenario.stage2BinTypes = s2Types.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> s2Suppliers = new ArrayList<>();
        for (int m = 0; m < nrOfSuppliers; m++) {
            int ctr = (int) Math.round(aveBinXSuppl[m]);
            for (int j = 0; j < ctr && s2Suppliers.size() < aveScenario.nrOfStage2Bins; j++) {
                s2Suppliers.add(m);
            }
        }
        while (s2Suppliers.size() < aveScenario.nrOfStage2Bins) {
            s2Suppliers.add(nrOfSuppliers - 1);
        }
        aveScenario.stage2BinSuppliers = s2Suppliers.stream().mapToInt(Integer::intValue).toArray();

        // Average item volume distribution
        int maxItemVol = 0;
        for (Scenario s : scenarios) {
            for (int v : s.itemVolumes) {
                maxItemVol = Math.max(maxItemVol, v);
            }
        }
        double[] aveItemVolumes = new double[maxItemVol + 1];
        for (Scenario s : scenarios) {
            for (int v : s.itemVolumes) {
                aveItemVolumes[v] += s.prob;
            }
        }

        aveScenario.itemMinVolume = Integer.MAX_VALUE;
        aveScenario.itemMaxVolume = 0;
        aveScenario.itemSizesFreq = new int[aveItemVolumes.length];

        List<Integer> aveItems = new ArrayList<>();
        for (int v = 1; v < aveItemVolumes.length; v++) {
            int ctr = (int) Math.round(aveItemVolumes[v]);
            for (int i = 0; i < ctr; i++) {
                aveItems.add(v);
                aveScenario.totVolumeOfItems += v;
                aveScenario.itemMinVolume = Math.min(aveScenario.itemMinVolume, v);
                aveScenario.itemMaxVolume = Math.max(aveScenario.itemMaxVolume, v);
                aveScenario.itemSizesFreq[v]++;
            }
        }
        aveScenario.nrOfItems = aveItems.size();
        aveScenario.itemVolumes = aveItems.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Adds largest bins to all suppliers if an instance is proven infeasible.
     */
    public void improveFeasibility() {
        int biggerBinType = typeBinVolumes.length - 1;
        int maxVol = 0;
        for (int t = 0; t < typeBinVolumes.length; t++) {
            if (typeBinVolumes[t] > maxVol) {
                biggerBinType = t;
                maxVol = typeBinVolumes[t];
            }
        }

        nrOfStage1Bins += nrOfSuppliers;
        for (int m = 0; m < nrOfSuppliers; m++) {
            stage1BinTypes.add(biggerBinType);
            stage1BinSuppliers.add(m);
        }
    }

    /**
     * Computes branch priorities for suppliers, 1st-stage bins, and 2nd-stage scenario bins.
     */
    public void computePriorities() {
        // Supplier priorities: sorted by contract cost descending
        List<int[]> supplRatio = new ArrayList<>(nrOfSuppliers);
        for (int m = 0; m < nrOfSuppliers; m++) {
            supplRatio.add(new int[]{m, (int) Math.round(supplierContractCosts[m] * 100)});
        }
        supplRatio.sort((a, b) -> Integer.compare(b[1], a[1]));
        supplierPriority = new int[nrOfSuppliers];
        for (int p = 0; p < supplRatio.size(); p++) {
            supplierPriority[supplRatio.get(p)[0]] = p;
        }

        // 1st stage bin priorities: sorted by cost/volume ratio descending
        List<double[]> cvRatio = new ArrayList<>(nrOfStage1Bins);
        for (int u = 0; u < nrOfStage1Bins; u++) {
            int btype = stage1BinTypes.get(u);
            int bsuppl = stage1BinSuppliers.get(u);
            double ratio = typeSupplBinCosts[btype * nrOfSuppliers + bsuppl] / typeBinVolumes[btype];
            cvRatio.add(new double[]{u, ratio});
        }
        cvRatio.sort((a, b) -> Double.compare(b[1], a[1]));
        stage1BinPriority = new int[nrOfStage1Bins];
        for (int p = 0; p < cvRatio.size(); p++) {
            stage1BinPriority[(int) cvRatio.get(p)[0]] = p;
        }

        // Scenario 2nd-stage bin priorities
        for (Scenario s : scenarios) {
            computeScenarioPriorities(s);
        }
        computeScenarioPriorities(aveScenario);
    }

    public void computeScenarioPriorities(Scenario sc) {
        List<double[]> cvRatio = new ArrayList<>(sc.nrOfStage2Bins);
        for (int u = 0; u < sc.nrOfStage2Bins; u++) {
            int btype = sc.stage2BinTypes[u];
            int bsuppl = sc.stage2BinSuppliers[u];
            double ratio = sc.stage2BinCosts[btype * nrOfSuppliers + bsuppl] / typeBinVolumes[btype];
            cvRatio.add(new double[]{u, ratio});
        }
        cvRatio.sort((a, b) -> Double.compare(b[1], a[1]));
        sc.stage2BinPriority = new int[sc.nrOfStage2Bins];
        for (int p = 0; p < cvRatio.size(); p++) {
            sc.stage2BinPriority[(int) cvRatio.get(p)[0]] = p;
        }
    }

    /**
     * Randomly partitions scenarios into samples of given size for SAA testing.
     */
    public int sampleScenarios(int sampleSize) {
        int nrOfSamples = nrOfScenarios / sampleSize;
        List<Integer> samplingVec = new ArrayList<>(nrOfScenarios);
        for (int s = 0; s < nrOfScenarios; s++) {
            samplingVec.add(s);
        }
        Collections.shuffle(samplingVec);

        scenarioSampling = new ArrayList<>(nrOfSamples);
        for (int s = 0; s < nrOfSamples; s++) {
            List<Integer> sample = new ArrayList<>(sampleSize);
            for (int e = 0; e < sampleSize; e++) {
                sample.add(samplingVec.remove(samplingVec.size() - 1));
            }
            scenarioSampling.add(sample);
        }
        return nrOfSamples;
    }

    public String getBaseName() {
        if (ifname == null) return "unknown";
        String normalized = ifname.replace('\\', '/');
        String name = Paths.get(normalized).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public List<Map.Entry<String, String>> getBasicInfo() {
        List<Map.Entry<String, String>> info = new ArrayList<>();
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(nrOfSuppliers), "suppliers"));
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(nrOfStage1Bins), "1st stage bins"));
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(nrOfScenarios), "scenarios"));
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(maxNrOfStage2Bins), "max 2nd stage bins"));
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(maxNrOfScenarioItems), "max scenario items"));
        info.add(new AbstractMap.SimpleEntry<>(String.valueOf(maxVolOfScenario), "max scenario volume of items"));
        info.add(new AbstractMap.SimpleEntry<>(String.format(Locale.US, "%.2f", maxS1BinCost), "max 1st stage bin cost"));
        info.add(new AbstractMap.SimpleEntry<>(String.format(Locale.US, "%.2f", maxLossCost), "max 1st stage bin loss cost"));
        info.add(new AbstractMap.SimpleEntry<>(String.format(Locale.US, "%.2f", aveScenario.expDeliveryCost), "ave express delivery cost"));
        return info;
    }

    public void write2file(String ofname) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ofname))) {
            pw.println(nrOfSuppliers);
            pw.println(minNrOfSuppliers);
            pw.println(String.format(Locale.US, "%.4f", minVolXSupplier));
            pw.println(String.format(Locale.US, "%.4f", minAveReliability));

            for (int m = 0; m < nrOfSuppliers; m++) {
                pw.print(String.format(Locale.US, "%.4f%s", supplierThetas[m], (m + 1 < nrOfSuppliers ? " " : "\n")));
            }
            for (int m = 0; m < nrOfSuppliers; m++) {
                pw.print(String.format(Locale.US, "%.2f%s", supplierContractCosts[m], (m + 1 < nrOfSuppliers ? " " : "\n")));
            }

            pw.println(nrOfBinTypes);
            for (int t = 0; t < nrOfBinTypes; t++) {
                pw.print(typeBinVolumes[t] + (t + 1 < nrOfBinTypes ? " " : "\n"));
            }
            pw.println();

            for (int t = 0; t < nrOfBinTypes; t++) {
                for (int m = 0; m < nrOfSuppliers; m++) {
                    pw.print(String.format(Locale.US, "%.2f%s", typeSupplBinCosts[t * nrOfSuppliers + m], (m + 1 < nrOfSuppliers ? " " : "\n")));
                }
            }

            for (int t = 0; t < nrOfBinTypes; t++) {
                for (int m = 0; m < nrOfSuppliers; m++) {
                    pw.print(String.format(Locale.US, "%.2f%s", typeSupplBinLossXUCosts[t * nrOfSuppliers + m], (m + 1 < nrOfSuppliers ? " " : "\n")));
                }
            }

            for (int t = 0; t < nrOfBinTypes; t++) {
                for (int m = 0; m < nrOfSuppliers; m++) {
                    pw.print(String.format(Locale.US, "%.2f%s", typeSupplBinLossXUComps[t * nrOfSuppliers + m], (m + 1 < nrOfSuppliers ? " " : "\n")));
                }
            }

            pw.println(nrOfStage1Bins);
            for (int k = 0; k < nrOfStage1Bins; k++) {
                pw.print(stage1BinTypes.get(k) + (k + 1 < nrOfStage1Bins ? " " : "\n"));
            }
            for (int k = 0; k < nrOfStage1Bins; k++) {
                pw.print(stage1BinSuppliers.get(k) + (k + 1 < nrOfStage1Bins ? " " : "\n"));
            }

            pw.println(nrOfScenarios);
            for (int s = 0; s < nrOfScenarios; s++) {
                Scenario sc = scenarios.get(s);
                for (int k = 0; k < nrOfStage1Bins; k++) {
                    pw.print(sc.stage1BinVolumes[k] + (k + 1 < nrOfStage1Bins ? " " : "\n"));
                }
                pw.println(sc.nrOfStage2Bins);
                for (int t = 0; t < nrOfBinTypes; t++) {
                    for (int m = 0; m < nrOfSuppliers; m++) {
                        int idx = t * nrOfSuppliers + m;
                        pw.print(String.format(Locale.US, "%.2f%s", sc.stage2BinCosts[idx], (idx + 1 < sc.stage2BinCosts.length ? " " : "\n")));
                    }
                }
                for (int l = 0; l < sc.nrOfStage2Bins; l++) {
                    pw.print(sc.stage2BinTypes[l] + (l + 1 < sc.nrOfStage2Bins ? " " : "\n"));
                }
                for (int l = 0; l < sc.nrOfStage2Bins; l++) {
                    pw.print(sc.stage2BinSuppliers[l] + (l + 1 < sc.nrOfStage2Bins ? " " : "\n"));
                }
                pw.println(sc.nrOfItems);
                for (int i = 0; i < sc.nrOfItems; i++) {
                    pw.print(sc.itemVolumes[i] + (i + 1 < sc.nrOfItems ? " " : "\n"));
                }
            }
        }
    }

    /**
     * Helper fast scanner for reading whitespace-separated tokens.
     */
    private static class FastScanner {
        private final BufferedReader reader;
        private StringTokenizer tokenizer;

        FastScanner(InputStream stream) {
            reader = new BufferedReader(new InputStreamReader(stream));
        }

        String next() throws IOException {
            while (tokenizer == null || !tokenizer.hasMoreTokens()) {
                String line = reader.readLine();
                if (line == null) {
                    throw new EOFException("Unexpected end of file while reading instance");
                }
                tokenizer = new StringTokenizer(line);
            }
            return tokenizer.nextToken();
        }

        int nextInt() throws IOException {
            return Integer.parseInt(next());
        }

        double nextDouble() throws IOException {
            return Double.parseDouble(next());
        }
    }
}
