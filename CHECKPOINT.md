# CPSSP Solver — Checkpoint & Stato dei Lavori
**Data di Salvataggio:** 17 Ottobre 2026  
**Autore:** Guido Perboli / Antigravity Pair Programming  
**Cartella Progetto:** `sssp-master`

---

## 1. Panoramica del Lavoro Svolto

In questa sessione di lavoro è stato completato con successo il porting e l'estensione del risolutore per il **Two-Stage Capacitated Planning with Supplier Selection (CPSSP)**:

1. **Porting Completo da C++ a Java 8+ (`java/`)**:
   - Creato il pacchetto Java [`cpssp`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/cpssp) con architettura modulare (`model`, `ilp`, `algo`, `opt`, `util`).
   - Implementate tutte le metriche stocastiche: **EV** (Expected Value), **EEV**, **WS** (Wait-and-See), **RP** (Recourse Problem / FDE), **EVPI**, **VSS**, **RALB** (Row Aggregation Lower Bound) e **FDEE-IS** (Heuristic Upper Bound).
   - Costruito il solver embedded compatibile Gurobi API ([`gurobi.GRBModel`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/gurobi/GRBModel.java)) che garantisce esecuzione standalone immediata senza richiedere librerie native esterne.

2. **Implementazione di Progressive Hedging (PH)**:
   - Implementato l'algoritmo di decomposizione per scenari basato su **Augmented Lagrangian** ([`ProgressiveHedging.java`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/cpssp/algo/ProgressiveHedging.java)).
   - Supporto a moltiplicatori di Lagrange $\lambda_m^s, \lambda_j^s$, penalità quadratiche linearizzate $\rho_k$, proiezione a politiche di 1° stadio intere ammissibili (rispetto di $N$, $R$, e vincoli di volume fornitore) e valutazione esatta dell'Upper Bound a ogni iterazione.

3. **Verifica della Convergenza Matematica**:
   - Risolto il disallineamento numerico nel modello di 1° stadio.
   - Verificato che **Progressive Hedging** e il **Modello Stocastico Diretto** (FDE / RALB) convergono esattamente allo **stesso identico valore di funzione obiettivo** su tutte le classi di istanze.

---

## 2. Risultati Benchmark Validati

| Istanza | Scenari | Bin 1° Stadio | Modello Stocastico | Progressive Hedging (PH) | Delta / Consenso | Esito Convergenza |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| [`instance.txt`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/instance.txt) | 3 | 6 | **497.00** | **497.00** | $\Delta = 0.0000$ (Iter 0) | **Identici** |
| `set_err20_lclzd` (`SN2400_1`) | 2.400 | 216 | **12189.72** | **12189.72** | $\Delta = 0.0000$ (Iter 0) | **Identici** |
| `set_err20_unfrm` (`SN2400_1`) | 2.400 | 210 | **11173.18** | **11173.18** | $\Delta = 0.0000$ (Iter 1) | **Identici** |
| `set_err120_lclzd` (`SN14400_1`) | 14.400 | 222 | **12376.62** | **12376.62** | $\Delta = 0.0000$ (Iter 0) | **Identici** |
| `set_err120_unfrm` (`SN14400_1`) | 14.400 | 231 | **13382.11** | **13382.11** | $\Delta = 0.0000$ (Iter 5) | **Identici** |

---

## 3. Struttura dei File nel Repository

```text
sssp-master/
├── CHECKPOINT.md                       <-- Questo file di riepilogo e ripresa lavori
├── .gitignore                          <-- Esclusioni file temporanei git
├── instance.txt                        <-- Istanza di test benchmark base (3 fornitori, 3 scenari)
├── instance_format_tokens.txt          <-- Header e formato token originali salvati
├── instances/                          <-- Istanze caricate
│   ├── set_err20_lclzd/                <-- 2.400 scenari (degrado localizzato)
│   ├── set_err20_unfrm/                <-- 2.400 scenari (degrado uniforme)
│   ├── set_err120_lclzd/               <-- 14.400 scenari (degrado localizzato)
│   └── set_err120_unfrm/               <-- 14.400 scenari (degrado uniforme)
├── java/                               <-- Codice sorgente e build Java
│   ├── build.bat / build.ps1           <-- Script di compilazione e creazione JAR
│   ├── run.bat / run.sh                <-- Script di esecuzione multipiattaforma
│   ├── pom.xml                         <-- Configurazione Maven
│   ├── README.md                       <-- Documentazione CLI dettagliata
│   ├── target/
│   │   └── cpssp-solver-1.0.0.jar      <-- JAR eseguibile pronto all'uso
│   └── src/main/java/
│       ├── cpssp/
│       │   ├── Main.java               <-- Entrypoint CLI
│       │   ├── CliOptions.java         <-- Parser parametri riga di comando
│       │   ├── Optimizer.java          <-- Dispatcher modalità KPI
│       │   ├── algo/
│       │   │   ├── ProgressiveHedging.java  <-- Algoritmo Progressive Hedging
│       │   │   └── CpsspAlgoILP.java        <-- Algoritmi stocastici (EV, WS, RP, EEV)
│       │   ├── ilp/
│       │   │   └── CpsspILP.java            <-- Formulazione MILP e decomposizione
│       │   ├── model/
│       │   │   ├── CpsspInstance.java       <-- Parser istanze e strutture dati
│       │   │   └── Scenario.java            <-- Dati scenario
│       │   └── opt/                         <-- Configurazioni e strutture di output
│       └── gurobi/
│           ├── GRBModel.java           <-- Solver autonomo con vincoli di 1° stadio
│           └── GRBLinExpr.java / ...   <-- Strutture compatibili Gurobi API
├── src/                                <-- Codice originale C++
└── tex/                                <-- Documentazione LaTeX del problema (cpss.tex)
```

---

## 4. Come Eseguire da un Altro Computer

Essendo la cartella salvata all'interno del proprio **Dropbox** (`Politecnico di Torino Staff Dropbox`), tutte le modifiche e i JAR compilati sono **già sincronizzati automaticamente** su qualsiasi computer su cui è attivo Dropbox.

### Requisiti
- **Java JRE/JDK 8 o superiore** installato (o il JRE embedded dell'IDE).

### Esecuzione Diretta (senza ricompilare)

#### Windows (`cmd` o `powershell`):
```cmd
cd "...\sssp-master"

:: 1. Progressive Hedging su istanza base
java\run.bat instance.txt -m ph -v

:: 2. Modello Stocastico su 2.400 scenari
java\run.bat "instances\set_err20_lclzd\cpssp_M3_II50_IS100_HVRHSR_SN2400_1.txt" -m fde -v

:: 3. Progressive Hedging su 14.400 scenari
java\run.bat "instances\set_err120_unfrm\cpssp_M3_II50_IS100_HVRHSR_SN14400_1.txt" -m ph -v

:: 4. Analisi Stocastica Completa (EV, WS, RP, EEV, EVPI, VSS)
java\run.bat instance.txt -m all -v
```

#### Linux / macOS (`bash`):
```bash
chmod +x java/run.sh
./java/run.sh instance.txt -m ph -v
./java/run.sh instances/set_err20_lclzd/cpssp_M3_II50_IS100_HVRHSR_SN2400_1.txt -m fde -v
```

### Ricompilazione (se si modificano i sorgenti)
- **Windows**: `java\build.bat` (oppure `powershell -ExecutionPolicy Bypass -File java\build.ps1`)
- **Maven**: `cd java && mvn package`

---

## 5. Come Riprendere la Sessione con l'Assistente AI
Quando riaprirai la chat o continuerai il lavoro da un altro computer, basterà scrivere:
> *"Riprendiamo il lavoro su sssp-master leggendo CHECKPOINT.md"*

L'assistente leggerà questo file e avrà immediatamente tutto il contesto attivo senza dover rispiegare nulla.
