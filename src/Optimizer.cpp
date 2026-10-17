#include "Optimizer.h"
#include "CpsspILP.h"

#include "gurobi_c++.h"
#include "CpsspAlgoILP.h"

#include "optlogger/MyLog.h"

/// Logger
#define LOGLEVEL        logDEBUG

/// alg. names
const std::vector<std::string> alg_string({"test feasibilty", "solve deterministic ILP", "WS", "EV-EEV", "UB", "all SO measures", "RP error", "Determinist ILP Model" });

using namespace cpssp;

optresult Optimizer::operator()(const std::shared_ptr<CpsspInstance>& inst_sptr, const optcfg& ocfg, std::string logfn)
{
    /// log base info
    if(logfn != "")
        init_logging(false, logfn, LOGLEVEL, "w+");

    FILE_LOG(logINFO) << "Instance " << inst_sptr->getBaseName() << " ::: filename " << inst_sptr->ifname;
    auto iinfo = inst_sptr->getBasicInfo();
    for(auto p : iinfo)
    {
        FILE_LOG(logINFO) << "Cpssp instance ::: " << p.first << " " << p.second;
    }
    //inst_sptr->write2file("testout.txt");
    
    /// algo config.
    unsigned int ilp_kpi = ocfg.alg_mode / 10000 % 10;
    unsigned int ilp_bff = ocfg.alg_mode / 1000 % 10;
    unsigned int ilp_prt = ocfg.alg_mode / 100 % 10;
    unsigned int ilp_prg = ocfg.alg_mode / 10 % 10;
    unsigned int lp_algo = ocfg.alg_mode / 1 % 10;
    /// log algo config.
    FILE_LOG(logINFO) << "Cpssp optimizer ::: " << alg_string[ilp_kpi] << " algorithm";
    FILE_LOG(logINFO) << "Cpssp optimizer ::: enumerate supplier selection flag " << ilp_bff;
    FILE_LOG(logINFO) << "Cpssp optimizer ::: ILP priority of vars flag " << ilp_prt;
    FILE_LOG(logINFO) << "Cpssp optimizer ::: ILP type " << toString(ilp_prg ? CpsspILP::ProgType::RALB : CpsspILP::ProgType::FDE);
    FILE_LOG(logINFO) << "Cpssp optimizer ::: LP algorithm " << lp_algo;
    
    if(ilp_prt)
        inst_sptr->computePriorities();
    
    /// local vars
    optresult optr;
    
    /// switch
    if(ocfg.opt_algo == optcfg::Algo::MILP)
    {
        /// opt. model
        CpsspILP::ILPparams ilpps;
        ilpps.progtype = ilp_prg ? CpsspILP::ProgType::RALB : CpsspILP::ProgType::FDE;
        ilpps.algo = ilp_bff;
        ilpps.lp_algo = lp_algo == 0 ? -1 : lp_algo - 1;
        ilpps.priorities = ilp_prt;
        ilpps.presolve = true;
        ilpps.maxmem = ocfg.maxmem;
        ilpps.threads2use = ocfg.threads2use;
        ilpps.timelimsec = ocfg.timelimsec;
        ilpps.timelim2sec = ocfg.maxgap;
        ilpps.sifting = 2;
        
        /// algo
        CpsspAlgoILP algo(inst_sptr, ilpps);
        
        /// set flags
        if(is_verbose)
            algo.verbose();    
        
        if(ilp_kpi == 0)
        {
            /// run
            do
            {
                optr = algo.testProgFeasibility(ilpps);
                if(optr.integer_values[0] == 3)
                    inst_sptr->improveFeasibilty();
            }
            while(optr.integer_values[0] == 3);
        }
        else if(ilp_kpi == 1)
        {
            /// run
            optr = algo.solveProg(ilpps);
        }
        else if(ilp_kpi == 2)
        {            /// run
            optr = algo.computePerfectInfoBound(ilpps);
        }
        else if(ilp_kpi == 3)
        {
            /// run            optr = algo.computeAveScenarioBound(ilpps);
        }
        else if(ilp_kpi == 4)
        {
            /// run
            optr = algo.computeFullProblemUB(ilpps);
        }
        else if(ilp_kpi == 5)
        {
            /// run
            optr = algo.computeRootEvaluation(ilpps);
        }
        else if(ilp_kpi == 6)
        {
            /// run
            IdCounterType nr_of_samples = 120;
            IdCounterType sample_size = inst_sptr->nr_of_scenarios / nr_of_samples;
            optr = algo.computeFullProblemUBerr(ilpps, sample_size);
        }
        else if(ilp_kpi == 7)
        {
            /// run
            optr = algo.solveProgDeterm(ilpps);
        }
    }
    else if(ocfg.opt_algo == optcfg::Algo::BAB)
    {
    }
    /// log results
    FILE_LOG(logINFO) << log_opt_result(optr);
 
    if(logfn != "")
        init_logging(true, logfn);
      
    return optr;
}