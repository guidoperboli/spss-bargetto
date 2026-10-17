# CPSSP Solver — Checkpoint & Stato dei Lavori
**Data di Salvataggio:** 17 Ottobre 2026  
**Autore:** Guido Perboli / Antigravity Pair Programming  
**Cartella Progetto:** `sssp-master`  
**Repository GitHub:** [https://github.com/guidoperboli/spss-bargetto](https://github.com/guidoperboli/spss-bargetto) (branch `main`)

---

## 1. Panoramica del Lavoro Svolto

In questa sessione di lavoro è stato completato con successo il porting e l'estensione del risolutore per il **Two-Stage Capacitated Planning with Supplier Selection (CPSSP)**:

1. **Porting Completo da C++ a Java 8+ (`java/`)**:
   - Creato il pacchetto Java [`cpssp`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/cpssp) con architettura modulare (`model`, `ilp`, `algo`, `opt`, `util`).
   - Implementate tutte le metriche stocastiche: **EV** (Expected Value), **EEV**, **WS** (Wait-and-See), **RP** (Recourse Problem / FDE), **EVPI**, **VSS**, **RALB** (Row Aggregation Lower Bound) e **FDEE-IS** (Heuristic Upper Bound).
   - Costruito il solver embedded compatibile Gurobi API ([`gurobi.GRBModel`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/gurobi/GRBModel.java)) che garantisce esecuzione standalone immediata senza richiedere librerie native esterne, con verifica esatta dei vincoli di affidabilità media $R$ e numero minimo di fornitori $N$.

2. **Implementazione di Progressive Hedging (PH)**:
   - Implementato l'algoritmo di decomposizione per scenari basato su **Augmented Lagrangian** ([`ProgressiveHedging.java`](file:///c:/Users/guido/Politecnico%20di%20Torino%20Staff%20Dropbox/Guido%20Perboli/Documenti/ricerca/spss-bargetto/sssp-master/java/src/main/java/cpssp/algo/ProgressiveHedging.java)).
   - Supporto a moltiplicatori di Lagrange $\lambda_m^s, \lambda_j^s$, penalità quadratiche linearizzate $\rho_k$, proiezione a politiche di 1° stadio intere ammissibili e tracciamento continuo della convergenza $\Delta \to 0$.

3. **Verifica della Convergenza Matematica**:
   - Risolto il disallineamento numerico nel modello di 1° stadio (parsing del fornitore tramite `lastIndexOf("_s")` e corretta attivazione selettiva dei bin).
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

## 3. Note Teoriche e Modellistiche

### A. Come PH Risolve i Sottoproblemi (Modello vs Euristica)
- **Sottoproblemi per scenario (Modello MILP)**: Ciascun sottoproblema di scenario $s$ viene risolto tramite il **modello matematico MILP** con funzione obiettivo aumentata da moltiplicatori $\lambda^s$ e penalità $\rho$.
- **Aggiornamento Moltiplicatori (Dual Ascent)**: Aggiorna $\lambda_m^s, \lambda_j^s$ e scala le penalità $\rho \leftarrow \alpha \rho$ in base allo scostamento dalla media.
- **Estrazione Politica Intera (Euristica Rounding & Repair)**: Le medie frazionarie $\bar{w}, \bar{y} \in [0, 1]$ vengono proiettate a una soluzione binaria $0/1$ ammissibile con arrotondamento $\ge 0.5$ e riparazione per rispettare $N$, $R$ e consistenza fornitore-bin.
- **Valutazione Upper Bound (Modello di Ricorso)**: La politica intera estratta viene valutata esattamente risolvendo il problema di ricorso su tutti gli scenari.

### B. Perché le Funzioni Obiettivo sono Continue (con Decimali)?
Sebbene le variabili decisionali siano intere/binarie ($w_m, y_j, z_k^s, x_{i,j}^s \in \{0, 1\}$), il valore della FO è continuo perché:
1. **Valore Atteso Stocastico ($\mathbb{E}$)**: La FO minimizza $\sum_s p^s Q(y, \omega^s)$ con $p^s = \frac{1}{S}$ (es. $\frac{1}{2400}$ o $\frac{1}{14400}$). La media ponderata di valori discreti è un numero reale.
2. **Parametri Unitari Frazionari**: I costi di degrado netto capacità $(c_m^t - r_m^t)$ sono espressi in €/m³ continui (es. $0.30$ €/m³), producendo centesimi e frazioni monetarie quando moltiplicati per i volumi persi.

### C. Formulazione per Grandi Scenari ($S \ge 2400$)
- Su istanze con 14.400 scenari e 100 item/scenario, la formulazione FDE con variabili di packing esplicito $x_{s,i,j}$ richiederebbe oltre 450 milioni di variabili.
- Per grandi istanze si usa la formulazione surrogata di volume **RALB** (`-m ralb` o `-f RALB`), mentre per istanze piccole ($S \le 20$) si usa FDE con packing esplicito.

---

## 4. Struttura dei File nel Repository

```text
sssp-master/
├── CHECKPOINT.md                       <-- Questo file di riepilogo e ripresa lavori
├── .gitignore                          <-- Esclusioni file temporanei git
├── instance.txt                        <-- Istanza di test benchmark base (3 fornitori, 3 scenari)
├── instance_format_tokens.txt          <-- Header e formato token originali salvati
├── instances/                          <-- Istanze caricate (sincronizzate via Dropbox)
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

## 5. Come Richiamarlo ed Eseguirlo da un Altro Computer

Il codice è salvato sia su **GitHub** ([https://github.com/guidoperboli/spss-bargetto](https://github.com/guidoperboli/spss-bargetto)) che su **Dropbox** (`Politecnico di Torino Staff Dropbox`).

### Requisiti
- **Java JRE/JDK 8 o superiore** installato (o il JRE embedded dell'IDE).

### Esecuzione Diretta

#### Da GitHub (su nuovo computer):
```bash
git clone https://github.com/guidoperboli/spss-bargetto.git
cd spss-bargetto
```

#### Comandi Windows (`cmd` o `powershell`):
```cmd
:: 1. Progressive Hedging su istanza base
java\run.bat instance.txt -m ph -v

:: 2. Modello Stocastico su 2.400 scenari
java\run.bat "instances\set_err20_lclzd\cpssp_M3_II50_IS100_HVRHSR_SN2400_1.txt" -m fde -v

:: 3. Progressive Hedging su 14.400 scenari
java\run.bat "instances\set_err120_unfrm\cpssp_M3_II50_IS100_HVRHSR_SN14400_1.txt" -m ph -v

:: 4. Analisi Stocastica Completa (EV, WS, RP, EEV, EVPI, VSS)
java\run.bat instance.txt -m all -v
```

#### Comandi Linux / macOS (`bash`):
```bash
chmod +x java/run.sh
./java/run.sh instance.txt -m ph -v
./java/run.sh instances/set_err20_lclzd/cpssp_M3_II50_IS100_HVRHSR_SN2400_1.txt -m fde -v
```

### Ricompilazione (opzionale)
- **Windows**: `java\build.bat` (oppure `powershell -ExecutionPolicy Bypass -File java\build.ps1`)
- **Maven**: `cd java && mvn package`

---

## 6. Come Riprendere la Sessione con l'Assistente AI
Quando riaprirai la chat o continuerai il lavoro da un altro computer, basterà scrivere:
> *"Riprendiamo il lavoro su sssp-master leggendo CHECKPOINT.md"*

L'assistente leggerà questo file e avrà immediatamente tutto il contesto attivo senza dover rispiegare nulla.
