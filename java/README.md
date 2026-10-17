# CPSSP Solver — Java Implementation & CLI Guide

This directory contains the Java implementation of the **Capacity Planning Problem with Supplier Selection and Uncertainty on Contract Fulfillment** (also referred to as **SVCSBPP-CL-SS** — *Stochastic Variable Cost and Size Bin Packing with Capacity Loss and Supplier Selection*).

The Java codebase mirrors the C++ solver in [`src/`](../src) while offering a **human-friendly, intuitive command-line interface**, detailed diagnostic reporting, and full backward compatibility with legacy research scripts.

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Command Line Reference](#command-line-reference)
3. [Optimization Modes Guide](#optimization-modes-guide)
4. [Algorithm Tuning Options](#algorithm-tuning-options)
5. [Legacy 5-Digit Mode Mapping (`alg_mode`)](#legacy-5-digit-mode-mapping-alg_mode)
6. [Compilation & Gurobi Setup](#compilation--gurobi-setup)
7. [Solution File Export (`.sspsol`)](#solution-file-export-sspsol)
8. [Exit Codes](#exit-codes)

---

## Quick Start

### Basic Run (Recommended Default)
Runs the **Row Aggregation Lower Bound + Heuristic Recourse (RALB + FDEE-IS)**, finding near-optimal solutions in seconds:
```bash
java -jar cpssp-solver.jar instance.txt
```

### Full Stochastic Analysis
Computes Expected Value (EV), Wait-and-See (WS), Recourse Problem (RP), EEV, Expected Value of Perfect Information (EVPI), and Value of Stochastic Solution (VSS):
```bash
java -jar cpssp-solver.jar instance.txt --mode all
```

### Fast Solve with 60s Time Limit and Solution Export
```bash
java -jar cpssp-solver.jar instance.txt -m ub -t 60 -o solution.sspsol
```

### View Help Screen
```bash
java -jar cpssp-solver.jar --help
```

---

## Command Line Reference

### Syntax
```bash
java -jar cpssp-solver.jar [OPTIONS] <instance_file>
# or using explicit class path:
java -cp "target/cpssp-solver-1.0.0.jar:${GUROBI_HOME}/lib/gurobi.jar" cpssp.Main [OPTIONS] -i <instance_file>
```

### All Command Line Options

| Flag | Long Option | Value / Type | Default | Description |
| :--- | :--- | :---: | :---: | :--- |
| `-i` | `--instance` | `<path>` | *Required* | Path to the CPSSP instance text file. Can also be passed as the first positional argument. |
| `-m` | `--mode` | `<name>` | `ub` | High-level optimization mode (see [Modes Guide](#optimization-modes-guide)). |
| `-t` | `--time-limit` | `<seconds>` | `3600.0` | Global solver time limit in seconds. |
| | `--scenario-time-limit` | `<seconds>` | `600.0` | Time limit per individual scenario subproblem during evaluations. |
| `-o` | `--output-sol` | `<path>` | *None* | Export optimal solution to a `.sspsol` file for visualization/KPI extraction with Python. |
| `-v` | `--verbose` | *Flag* | `false` | Enable detailed Gurobi branch-and-cut output and step-by-step logging. |
| `-q` | `--quiet` | *Flag* | `false` | Quiet mode: suppresses informational banners, printing only final metrics. |
| `-h` | `--help` | *Flag* | — | Displays the interactive help manual and usage examples. |
| `-f` | `--formulation` | `fde` \| `ralb` | *Auto* | Override model formulation: `fde` (explicit item packing) or `ralb` (volume surrogate). |
| `-e` | `--enum-suppliers` | *Flag* | `false` | Enable combinatorial enumeration of supplier combinations $\binom{M}{k}$ with reliability pruning. |
| | `--no-enum` | *Flag* | `true` | Disable supplier enumeration (lets Gurobi branch on suppliers via MIP). |
| `-p` | `--priorities` | *Flag* | `true` | Enable hierarchical branching priorities ($w \to y_0 \to z \to x$). |
| | `--no-priorities` | *Flag* | `false` | Disable branching priorities. |
| | `--threads` | `<int>` | `1` | Number of CPU threads for Gurobi parallel search. |
| | `--memory` | `<mb>` | `1024.0` | Maximum memory limit allocated to Gurobi in megabytes. |
| | `--lp-algo` | `<name>` | `auto` | Continuous relaxation algorithm: `auto` (0), `primal` (1), `dual` (2), `barrier` (3), `sifting` (4). |
| | `--ph-iterations` | `<int>` | `30` | Maximum iterations for Progressive Hedging decomposition. |
| | `--ph-rho` | `<float>` | `1.0` | Initial proximal penalty parameter $\rho_0$ for Progressive Hedging. |
| | `--ph-alpha` | `<float>` | `1.05` | Progressive Hedging penalty multiplier $\alpha$ ($\rho_{k+1} = \min(\rho_{\max}, \alpha \rho_k)$). |
| | `--ph-tol` | `<float>` | `1e-4` | Consensus termination tolerance $\sqrt{\sum p^s (y^s - \bar{y})^2} \le \varepsilon$. |
| `-c` | `--config` | `<path>` | *None* | Load settings from a configuration file with `key=value` pairs. |
| | `--alg-mode` | `<code>` | *None* | Direct 5-digit legacy mode code (e.g. `40110`, `50110`, `80110`). |

---

## Optimization Modes Guide

Specify the method using `-m <mode>` or `--mode <mode>`:

### 1. `ub` / `heuristic` / `ralb-is` *(Default)*
* **Mathematical Concept**: Computes a fast global lower bound using Row Aggregation (`RALB`), extracts the 1st-stage supplier and bin decisions, and evaluates them over decoupled individual scenario subproblems (`FDEE-IS`) to produce a valid Upper Bound (`UB`).
* **When to use**: **Best general-purpose mode**. Solves large, multi-scenario instances in seconds with near-zero optimality gaps ($< 0.5\%$).

### 2. `ph` / `progressive-hedging` / `hedging`
* **Mathematical Concept**: Implements the **Progressive Hedging (PH)** algorithm (Rockafellar & Wets Augmented Lagrangian decomposition described in Section 4 of `cpss.tex`):
  - Decomposes the extensive two-stage model by scenario, relaxing non-anticipativity on 1st-stage suppliers $w_m$ and bins $y_j$.
  - Adds Lagrange multipliers $\lambda_m^s, \lambda_j^s$ and proximal penalty terms $\frac{\rho}{2}(y^s - \bar{y})^2$ penalizing deviation from probability-weighted consensus $\bar{y} = \sum_s p^s y^s$.
  - **Exact Linearization**: Because 1st-stage variables are binary ($y \in \{0, 1\}$), $y^2 = y$. The augmented penalty term expands as:
    $$\frac{\rho}{2}(y^s - \bar{y})^2 = \rho \left(\frac{1}{2} - \bar{y}\right) y^s + \frac{\rho}{2}\bar{y}^2$$
    This allows updating subproblems simply by adjusting linear objective coefficients in Gurobi without quadratic overhead.
  - **Feasible Upper Bound Projection**: In every iteration, the continuous consensus $(\bar{w}, \bar{y})$ is projected into a feasible integer 1st-stage policy satisfying minimum suppliers ($N$) and minimum average reliability ($R$), and evaluated across all scenarios to track true Upper Bounds.
  - Multipliers update via $\lambda_m^{s, k+1} = \lambda_m^{s, k} + \rho_m^k (w_m^{s,k} - \bar{w}_m^k)$ and penalties scale with factor $\alpha$.
* **When to use**: Massive scenario counts where extensive forms exceed memory or standard MIP solvers stall.

### 3. `all` / `stochastic` / `so`
* **Mathematical Concept**: Performs a complete Stochastic Optimization Analysis. Computes:
  - **EV**: Expected Value problem on the mean scenario.
  - **EEV**: Recourse cost of applying the EV 1st-stage policy across all scenarios.
  - **WS**: Wait-and-See lower bound (perfect information across scenarios).
  - **RP**: Recourse Problem upper bound.
  - **EVPI**: Expected Value of Perfect Information ($\text{EVPI} = RP - WS$).
  - **VSS**: Value of Stochastic Solution ($\text{VSS} = EEV - RP$).
* **When to use**: Whenever evaluating the economic value of stochastic modeling over deterministic averages.

### 4. `ws` / `wait-and-see` / `perfect-info`
* **Mathematical Concept**: Relaxes non-anticipativity, allowing 1st-stage decisions to adapt to each scenario independently. Computes the weighted average of individual scenario optima.
* **When to use**: Computing the theoretical best-case performance with clairvoyant information.

### 5. `ev-eev` / `expected-value`
* **Mathematical Concept**: Solves the single expected-value scenario problem to obtain 1st-stage decisions, then fixes those decisions and evaluates expected operations across all realized scenarios.
* **When to use**: Evaluating how a naive deterministic planning model performs under uncertainty.

### 6. `fde` / `exact` / `deterministic-equivalent`
* **Mathematical Concept**: Formulates and solves the Full Deterministic Equivalent Extensive Form with exact item-to-bin packing variables $x_{s,d,j} \in \mathbb{Z}^+$.
* **When to use**: For small instances or benchmark comparisons where provable exact optimality is required regardless of runtime.

### 7. `ralb` / `lb` / `lower-bound`
* **Mathematical Concept**: Solves the Row Aggregation model directly, replacing individual packing constraints with scenario total volume surrogates ($\sum_j {\cal V}_j^s y_j + \sum_k V_k z_k^s \ge \Upsilon^s$).
* **When to use**: Obtaining a mathematical lower bound without recourse evaluation.

### 8. `saa` / `error` / `sampling`
* **Mathematical Concept**: Partitions scenarios into samples, solves each sample with `RALB-IS`, and computes sample variances and 95% confidence intervals / margins of error (Sample Average Approximation stability).
* **When to use**: Testing in-sample stability and scenario sample size adequacy.

### 9. `determ` / `export`
* **Mathematical Concept**: Solves the deterministic version of the instance and writes the full item allocation and bin packing solution to a `.sspsol` file.
* **When to use**: Generating solution files for downstream processing with Python scripts.

### 10. `feasibility` / `check`
* **Mathematical Concept**: Solves the feasibility problem (zero objective). If proven infeasible, it expands the supplier bin pool with largest-type bins until feasibility is restored.
* **When to use**: Sanitizing or validating difficult or tightly constrained instances.

---

## Algorithm Tuning Options

### 1. Supplier Combinatorial Enumeration (`-e, --enum-suppliers`)
By default, Gurobi branches on binary supplier variables $w_m$. When `-e` is passed:
* The solver enumerates all subsets $\binom{M}{k}$ of $k \in [N, M]$ suppliers.
* Subsets failing the average reliability constraint ($\sum_{m \in S} \theta_m / k < R$) are immediately pruned without calling the solver.
* Feasible subsets are fixed, and Gurobi solves the reduced model.
* **Advantage**: Highly effective when the number of suppliers $M$ is small ($\le 20$), drastically reducing the branch-and-cut tree.

### 2. Hierarchical Branching Priorities (`-p, --priorities`)
Enabled by default. Directs Gurobi to branch in accordance with the decision hierarchy:
$$\text{Suppliers } (w_m) \quad \longrightarrow \quad \text{1st-Stage Bins } (y_{0,j}) \quad \longrightarrow \quad \text{2nd-Stage Spot Bins } (z_{s,k}) \quad \longrightarrow \quad \text{Packing } (x_{s,d,j})$$
Disabling priorities (`--no-priorities`) lets Gurobi pick branching variables purely through default pseudo-cost heuristics.

### 3. Model Formulation (`-f, --formulation <fde|ralb>`)
* `ralb`: Uses aggregated volume capacity constraints. Far fewer variables and constraints; solves orders of magnitude faster.
* `fde`: Uses individual item-size bin packing constraints. Exact, but scales poorly with scenario count.

---

## Legacy 5-Digit Mode Mapping (`alg_mode`)

For compatibility with existing bash/batch scripts, the legacy 5-digit code `[KPI][BFF][PRT][PRG][LP]` is fully supported:

```
                  ┌─── Digit 1: KPI (Algorithm mode 0 to 8)
                  │┌── Digit 2: BFF (1 = Enumerate suppliers, 0 = MIP branch)
                  ││┌─ Digit 3: PRT (1 = Hierarchical priorities, 0 = default)
                  │││┌ Digit 4: PRG (0 = FDE packing, 1 = RALB volume)
                  ││││┌ Digit 5: LP Algorithm (-1/0 = auto, 1 = primal, 2 = dual, etc.)
  Mode Code:      4 0 1 1 0
```

### Equivalence Table

| Legacy Code | Equivalent Human-Friendly Command | Description |
| :---: | :--- | :--- |
| `40110` | `-m ub` *(Default)* | Row Aggregation + Heuristic Recourse (RALB-IS), priorities on |
| `41110` | `-m ub -e` | RALB-IS with combinatorial supplier enumeration |
| `80110` | `-m ph` | Progressive Hedging scenario decomposition |
| `50110` | `-m all` | Full stochastic analysis (EV, WS, RP, EEV, EVPI, VSS) |
| `51110` | `-m all -e` | Full stochastic analysis with supplier enumeration |
| `20110` | `-m ws` | Wait-and-See bound |
| `30110` | `-m ev-eev` | Expected Value and EEV recourse |
| `10100` | `-m fde` | Full Deterministic Equivalent exact solve |
| `10110` | `-m ralb` | Row Aggregation Lower Bound solve |
| `60110` | `-m saa` | SAA statistical stability & error estimation |
| `70100` | `-m determ` | Deterministic solve and export to `.sspsol` |

---

## Compilation & Gurobi Setup

### 1. Compiling with Maven
```bash
cd java
mvn clean package
```
This builds `target/cpssp-solver-1.0.0.jar`.

### 2. Setting Environment Variables
Ensure Gurobi's native libraries are on the path:

* **Linux**:
  ```bash
  export GUROBI_HOME="/opt/gurobi/linux64"
  export LD_LIBRARY_PATH="${GUROBI_HOME}/lib:${LD_LIBRARY_PATH}"
  ```
* **macOS**:
  ```bash
  export GUROBI_HOME="/Library/gurobi1100/macos_universal2"
  export DYLD_LIBRARY_PATH="${GUROBI_HOME}/lib:${DYLD_LIBRARY_PATH}"
  ```
* **Windows**:
  ```cmd
  set GUROBI_HOME=C:\gurobi1100\win64
  set PATH=%GUROBI_HOME%\bin;%PATH%
  ```

### 3. Execution Command
Include `gurobi.jar` on the classpath:
```bash
java -cp "target/cpssp-solver-1.0.0.jar:${GUROBI_HOME}/lib/gurobi.jar" cpssp.Main [OPTIONS] <instance_file>
```

---

## Solution File Export (`.sspsol`)

When passing `-o <file>` or running with `--mode determ`, the solver generates a `.sspsol` solution file.

### Format Specification
```
<selected_suppliers_count> <total_suppliers_count>
<supplier_id> <contract_fixed_cost>
...
<booked_bins_count>
<bin_id> <supplier_id> <bin_type> <bin_volume> <bin_contract_cost>
...
<nr_items> <min_item_volume> <max_item_volume>
<item_count> <item_size> <assigned_bin_id>
...
```

### Python Analysis Integration
The exported file is directly compatible with the post-processing script [`scr/py/prcsol.py`](../scr/py/prcsol.py):
```bash
python scr/py/prcsol.py solution.sspsol
```
This extracts KPI metrics (booked volume, supplier shares, bin utilization) and generates publication-ready plots.

---

## Exit Codes

| Exit Code | Meaning |
| :---: | :--- |
| **`0`** | Successful completion. |
| **`1`** | Invalid command line arguments, unrecognized flag, or missing instance file. |
| **`2`** | Runtime exception during optimization or Gurobi license/solver failure. |
