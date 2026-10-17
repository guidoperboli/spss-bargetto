#include <memory>
#include <chrono>


#include "CpsspAlgoILP.h"
#include "gurobi_c++.h"

#include "optlogger/MyLog.h"

#define EVAL_ALWAYS

#define GETOPTT(start) ((double)std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count() / 1000.0)

#define INITDEBUGLOG(logstuff) \
    logstuff.push_back(std::make_tuple( 8, 0, "info")); \
    logstuff.push_back(std::make_tuple( 8, 0, "s")); \
    logstuff.push_back(std::make_tuple(12, 1, "status")); \
    logstuff.push_back(std::make_tuple(16, 1, "UB")); \
    logstuff.push_back(std::make_tuple(16, 1, "LB")); \
    logstuff.push_back(std::make_tuple(16, 2, "gap [%]")); \
    logstuff.push_back(std::make_tuple(16, 3, "OPTT [s]")); \
    logstuff.push_back(std::make_tuple(16, 3, "EPT [s]"))
    //logstuff.push_back(std::make_tuple(8, 0, "stop"))

#define DEBUGLOGHDR(lglevl, optlogger) \
    FILE_LOG(lglevl) << optlogger.getFormatedHeader()

#define DEBUGLOG(lglevl, optlogger, info, iter, status, ubl, lbl, gap, optt, ept) \
    FILE_LOG(lglevl) << optlogger.getFormatedLine(std::make_tuple(info, iter, status, ubl, lbl, gap, optt, ept))


using namespace cpssp;

typedef OptLogger<std::string, unsigned int, std::string, double, double, double, double, double> IlpAlgoLogger;



CpsspAlgoILP::CpsspAlgoILP(const std::shared_ptr<CpsspInstance>& i_ptr, const CpsspILP::ILPparams& grbps)
    : inst_sptr(i_ptr)
    , ilpprms(grbps)
{
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: start gurobi solver ...";
    
    /// init. env.
    grbenv_sptr.reset(new GRBEnv(true));
    /// set env. parameter
    grbenv_sptr->set("LogFile", "grb.log");
    //if(!is_verbose)
    grbenv_sptr->set(GRB_IntParam_OutputFlag, 0);
    grbenv_sptr->set(GRB_DoubleParam_MemLimit, grbps.maxmem / 1024.0);
    grbenv_sptr->set(GRB_IntParam_Threads, grbps.threads2use);
    /// start env.
    grbenv_sptr->start();
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: gurobi solver started";
}
    
CpsspAlgoILP::~CpsspAlgoILP()
{
}

void CpsspAlgoILP::verbose()
{
    is_verbose = true;
}
    
void CpsspAlgoILP::silent()
{
    is_verbose = false;
}

optresult CpsspAlgoILP::testProgFeasibility(const CpsspILP::ILPparams& ilpps)
{
    /// logging
    std::string algostr = toString(ilpps.progtype) + " ILP";
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr << " ::: test feasibilty ...";
    
    /// create ilp
    CpsspILP ilpprog(inst_sptr, grbenv_sptr, ilpps);
    
    /// set flags
    if(is_verbose)
        ilpprog.verbose();    

    /// run
    optresult optr = ilpprog.feasibleProg(ilpps);
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr
                      << " ::: status " << optr.tags[2] 
                      << " ::: time " << optr.run_time << " [s]" 
                      << " ::: OF " << optr.obj_value;
            
    return optr;
}

optresult CpsspAlgoILP::solveProg(const CpsspILP::ILPparams& ilpps)
{
    /// logging
    std::string algostr = toString(ilpps.progtype) + " ILP";
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr << " ::: solve ...";
    
    /// create ilp
    CpsspILP ilpprog(inst_sptr, grbenv_sptr, ilpps);
    
    /// set flags
    if(is_verbose)
        ilpprog.verbose();    

    /// run
    optresult optr = ilpprog.solveProg(ilpps);
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr
                      << " ::: status " << optr.tags[2] 
                      << " ::: time " << optr.run_time << " [s]" 
                      << " ::: OF " << optr.obj_value;
            
    return optr;
}

optresult CpsspAlgoILP::solveProgDeterm(const CpsspILP::ILPparams& ilpps)
{
    /// logging
    std::string algostr = toString(ilpps.progtype) + " ILP";
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr << " ::: solve ...";
    
    /// create ilp
    CpsspILP ilpprog(inst_sptr, grbenv_sptr, ilpps, true);
    
    /// set flags
    if(is_verbose)
        ilpprog.verbose();    

    /// run
    optresult optr = ilpprog.solveProg(ilpps);
    ilpprog.storeItemPacking();
    ilpprog.writeSolToFile(inst_sptr->getBaseName() + std::string(".sspsol"));
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << algostr
                      << " ::: status " << optr.tags[2] 
                      << " ::: time " << optr.run_time << " [s]" 
                      << " ::: OF " << optr.obj_value;
            
    return optr;
}

optresult CpsspAlgoILP::computeRootEvaluation(const CpsspILP::ILPparams& ilpps)
{
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: stochastic problem evaluation ...";

    /// strart chrono
    auto cmp_tm = std::chrono::steady_clock::now();
    double opt_tm;
    double ev;
    double eev;
    double ws;
    double rp;
    /// flags
    bool ev_is_opt;
    bool eev_is_opt;
    bool ws_is_opt;
    bool rap_is_opt;
    bool intp_is_opt;
    
    /// computation
    optresult as_optr = this->computeAveScenarioBound(ilpps);
    optresult pi_optr = this->computePerfectInfoBound(ilpps);
    optresult ub_optr = this->computeFullProblemUB(ilpps);
    /// get time
    opt_tm = GETOPTT(cmp_tm);
    
    optresult optr;
    optr.tags = ub_optr.tags;
    optr.best_bound = ub_optr.best_bound;
    optr.obj_value = ub_optr.obj_value;
    optr.opt_gap = ub_optr.opt_gap;
    optr.run_time = opt_tm;
    /// integers
    optr.integer_values.push_back(ev_is_opt = as_optr.integer_values[0]);
    optr.integer_values.push_back(eev_is_opt = as_optr.integer_values[1]);
    optr.integer_values.push_back(ws_is_opt = pi_optr.integer_values[0]);
    optr.integer_values.push_back(rap_is_opt = intp_is_opt = ub_optr.integer_values[0]);
    optr.integer_values.push_back(intp_is_opt = ub_optr.integer_values[1]);
    
    /// doubles
    optr.double_values.resize(19, std::numeric_limits<double>::quiet_NaN());
    optr.double_values[0] = ev  = (ev_is_opt ? as_optr.double_values[1] : as_optr.double_values[0]); // EV
    optr.double_values[1] = ws  = (ws_is_opt ? pi_optr.obj_value : pi_optr.best_bound); // WS
    optr.double_values[2] = rp  = (rap_is_opt ? ub_optr.double_values[0] : ub_optr.best_bound); // RP
    optr.double_values[3] = eev = (eev_is_opt ? as_optr.double_values[3] : as_optr.double_values[2]); // EEV
    /// EV opt data
    optr.double_values[4] = as_optr.double_values[0]; // BB
    optr.double_values[5] = as_optr.double_values[1]; // OF
    optr.double_values[6] = as_optr.double_values[4]; // tm
    /// EEV opt data
    optr.double_values[7] = as_optr.double_values[2]; // BB
    optr.double_values[8] = as_optr.double_values[3]; // OF
    optr.double_values[9] = as_optr.double_values[5]; // tm
    // WS opt data
    optr.double_values[10] = pi_optr.best_bound; // BB
    optr.double_values[11] = pi_optr.obj_value; // OF
    optr.double_values[12] = pi_optr.run_time; // tm
    
    optr.double_values[13] = ub_optr.double_values[0]; // RA objf
    optr.double_values[14] = ub_optr.double_values[1]; // RA gap
    optr.double_values[15] = ub_optr.double_values[2]; // RA tm
    
    optr.double_values[16] = ub_optr.double_values[3]; // Full model BB
    optr.double_values[17] = ub_optr.double_values[4]; // Full model gap
    optr.double_values[18] = ub_optr.double_values[5]; // tm
    
    /// EVPI% = (RP - WS) / WS
    double evpi_abs = rp - ws;
    double evpi = (rp - ws) / ws;
    /// VSS% = (EEV - RP) / RP
    double vss_abs = eev - rp;
    double vss = (eev - rp) / rp;
    /// logging
    std::stringstream ss;
    ss << "EV "<< std::fixed << std::setprecision(1) << ev;
    ss << " <= WS " << std::fixed << std::setprecision(1) << ws;
    ss << " <= RP "<< std::fixed << std::setprecision(1) << rp;
    ss << " <= EEV " << std::fixed << std::setprecision(1) << eev;
    FILE_LOG(logINFO) << "Cpssp algorithm ::: stochastic problem evaluation ::: " << ss.str();
    FILE_LOG(logINFO) << "Cpssp algorithm ::: stochastic problem evaluation ::: EVPI " << evpi_abs << " ::: "
                      << std::fixed << std::setprecision(2) << 100.0 * evpi << "%";
    FILE_LOG(logINFO) << "Cpssp algorithm ::: stochastic problem evaluation ::: SSV " << vss_abs << " ::: "
                      << std::fixed << std::setprecision(2) << 100.0 * vss << "%";
    
    return optr;
}

optresult CpsspAlgoILP::computeFullProblemUBerr(const CpsspILP::ILPparams& ilpps, IdCounterType sample_size)
{
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: " << (ilpps.progtype == CpsspILP::ProgType::RALB ? "RA ILP" : "FDEE") 
                      << " ::: RP error computation ...";
    
    /// start chrono
    auto sol_tm = std::chrono::steady_clock::now();

    /// sample scenarios
    inst_sptr->sampleScenarios(sample_size);
    
    /// throw except if it fails
    if(inst_sptr->scenario_sampling.empty())
        throw std::runtime_error("No sampling of scenarios available");

    /// init values 
    IdCounterType nr_of_samples = inst_sptr->scenario_sampling.size();
    
    /// stats
    std::vector<CostType> pi_lblvs;
    std::vector<CostType> pi_objvs;
    //CostType me_lbv;
    CostType pi_lbv;
    CostType mi_lbv;
    CostType mx_lbv;
    CostType sd_lbv;
    CostType me_objv;
    CostType pi_objv;
    CostType mi_objv;
    CostType mx_objv;
    CostType sd_objv;
    CostType pi_gapv;
    
    /// init values
    pi_lblvs.resize(nr_of_samples, 0);
    pi_objvs.resize(nr_of_samples, 0);
    /// ave, min, max
    sd_lbv = sd_objv = pi_lbv = pi_objv = pi_gapv = 0.0;
    mi_lbv = mi_objv = std::numeric_limits<CostType>::max();
    mx_lbv = mx_objv = std::numeric_limits<CostType>::lowest();
    /// loop over samples
    runsilent = true;
    for(IdCounterType s = 0; s < nr_of_samples; s++)
    {
        optresult optr;
        //std::stringstream ss;
        for(IdCounterType e = 0; e < sample_size; e++)
            //ss << " " << 
            inst_sptr->scenario_sampling[s][e];
        
        optr = this->computeFullProblemUB(ilpps, inst_sptr->scenario_sampling[s], false);
        /// LB stats
        pi_lbv += 1.0 / double(nr_of_samples) * optr.best_bound;
        mi_lbv = std::min(mi_lbv, optr.best_bound);
        mx_lbv = std::max(mx_lbv, optr.best_bound);
        pi_lblvs[s] = optr.best_bound;
        /// objf stats
        pi_objv += 1.0 / double(nr_of_samples) * optr.obj_value;
        mi_objv = std::min(mi_objv, optr.obj_value);
        mx_objv = std::max(mx_objv, optr.obj_value);
        pi_objvs[s] = optr.obj_value;
        /// opt gap
        pi_gapv += 1.0 / double(nr_of_samples) * optr.opt_gap;
    }
    runsilent = false;
    
    /// compute stdev
    for(IdCounterType s = 0; s < nr_of_samples; s++)
    {
        sd_lbv += std::pow(pi_lblvs[s] - pi_lbv, 2.0);
        sd_objv += std::pow(pi_objvs[s] - pi_objv, 2.0);
    }
    sd_lbv = std::pow(sd_lbv, 0.5);
    sd_objv = std::pow(sd_objv, 0.5);
    /// marginal error
    me_objv = 1.96 * sd_objv / std::pow(nr_of_samples, 0.5);
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: RP error ::: Ave " << pi_objv 
                      << " ::: stdev " << sd_objv
                      << " ::: min " << mi_objv
                      << " ::: max " << mx_objv 
                      << " ::: CI.95 " << "[" << (pi_objv - me_objv) << "," << (pi_objv + me_objv) << "]"
                      << " ::: Err.95 " << std::fixed << std::setprecision(2) << 100.0 * me_objv / pi_objv << "%";
    /// ret value
    optresult optres;
    optres.tags.resize(3, "");
    optres.tags[2] = !(pi_gapv > ilpps.eps) ? "Optimal" : "SubOptimal";
    
    /// base values
    optres.best_bound = pi_lbv;
    optres.obj_value = pi_objv;
    optres.opt_gap = pi_gapv;
    optres.run_time = GETOPTT(sol_tm);
    optres.integer_values.resize(2, 0);
    optres.integer_values[0] = nr_of_samples;
    optres.integer_values[1] = sample_size;
 
    optres.double_values.resize(6, std::numeric_limits<double>::quiet_NaN());
    optres.double_values[0] = pi_objv;
    optres.double_values[1] = sd_objv;
    optres.double_values[2] = mi_objv;
    optres.double_values[3] = mx_objv;
    //optres.double_values[4] = me_objv;

    return optres;
}

optresult CpsspAlgoILP::computeFullProblemUB(const CpsspILP::ILPparams& ilpps, std::vector<cpssp::IdCounterType> scenario_ids, bool sc_eval)
{
    /// logging
    if(!runsilent)
    {
        FILE_LOG(logINFO) << "Cpssp algorithm ::: " << (ilpps.progtype == CpsspILP::ProgType::RALB ? "RA ILP" : "FDEE") 
                          << " ::: " << (scenario_ids.empty() ? inst_sptr->nr_of_scenarios : scenario_ids.size()) << " scenarios"
                          << " ::: solve ...";
    }
    
    /// strart chrono
    auto cmp_tm = std::chrono::steady_clock::now();
    double opt_tm;

    /// create ilp
    CpsspILP::ILPparams lbilpps = ilpps;
    //lbilpps.progtype = CpsspILP::ProgType::RALB;
    CpsspILP ilpprog(inst_sptr, grbenv_sptr, lbilpps, scenario_ids);
    
    /// set flags
    if(is_verbose)
        ilpprog.verbose();    

    /// run
    optresult optrlb = ilpprog.solveProg(lbilpps);
    
    /// logging
    if(!runsilent)
    {
        FILE_LOG(logINFO) << "Cpssp algorithm ::: " << (ilpps.progtype == CpsspILP::ProgType::RALB ? "RA ILP" : "FDEE")
                          << " ::: status " << optrlb.tags[2] 
                          << " ::: time " << optrlb.run_time << " [s]" 
                          << " ::: OF " << optrlb.obj_value
                          << " ::: BB " << optrlb.best_bound;
    }
    
    /// evaluate
    optresult optrub;
    #ifdef EVAL_ALWAYS /// if RA model
    if(sc_eval && lbilpps.progtype == CpsspILP::ProgType::RALB)
    #else    
    if(lbilpps.progtype == CpsspILP::ProgType::RALB && optrlb.integer_values[0] == 2)
    #endif
    {
        /// extract stage 1 solution
        std::shared_ptr<std::vector<bool>> supplsel;
        std::shared_ptr<std::vector<bool>> binsel;
        
        supplsel.reset(new std::vector<bool>());
        binsel.reset(new std::vector<bool>());
        
        ilpprog.getSuppliersSelection(*supplsel);
        ilpprog.getStage1BinSelection(*binsel);
        
        /// logging
        if(!runsilent)
        {
            FILE_LOG(logINFO) << "Cpssp algorithm ::: FDEE-IS ::: compute ...";
        }

        CpsspILP::ILPparams ubilpps = ilpps;
        ubilpps.progtype = CpsspILP::ProgType::FDE;
        ubilpps.algo = 0;
        ubilpps.timelimsec = ilpps.timelim2sec;
        optrub = this->evaluateScenarios(ubilpps, supplsel, binsel);
        
        /// logging
        if(!runsilent)
        {
            FILE_LOG(logINFO) << "Cpssp algorithm ::: FDEE-IS" 
                              << " ::: status " << optrub.tags[2] 
                              << " ::: time " << optrub.run_time << " [s]" 
                              << " ::: OF " << optrub.obj_value
                              << " ::: BB " << optrub.best_bound
                              << " ::: all opt " << optrub.integer_values[0]
                              << " ::: ave scenario opt gap " << std::fixed << std::setprecision(2) << 100.0 * optrub.opt_gap << "%";
        }
    }

    /// get time
    opt_tm = GETOPTT(cmp_tm);
    /// results
    optresult optr;
    optr.tags.resize(3, "");
    optr.best_bound = optrlb.best_bound;
    optr.run_time = opt_tm;
    
    optr.integer_values.resize(2, 0);
    optr.integer_values[0] = optrlb.integer_values[0] == 2;
    
    optr.double_values.resize(6, std::numeric_limits<double>::quiet_NaN());
    
    if(sc_eval && lbilpps.progtype == CpsspILP::ProgType::RALB)
    {
        optr.tags[2] = optrub.obj_value > optrlb.best_bound + ilpps.eps ? "SubOptimal" : "Optimal";
        optr.obj_value = optrub.obj_value;
        optr.opt_gap = (optrub.obj_value - optrlb.best_bound) / optrlb.best_bound;
        /// integers
        optr.integer_values[1] = optrub.integer_values[0];
        /// doubles
        optr.double_values[0] = optrlb.obj_value;
        optr.double_values[1] = optrlb.opt_gap;
        optr.double_values[2] = optrlb.run_time;
        
        optr.double_values[3] = optrub.best_bound;
        optr.double_values[4] = optrub.opt_gap;
        optr.double_values[5] = optrub.run_time;
    }
    else
    {
        optr.tags[2] = optrlb.integer_values[0] == 2 ? "Optimal" : "SubOptimal";
        optr.obj_value = optrlb.obj_value;
        optr.opt_gap = optrlb.opt_gap;
        /// integers
        optr.integer_values[1] = 0;
        /// doubles
        optr.double_values[0] = optrlb.obj_value;
        optr.double_values[1] = optrlb.opt_gap;
        optr.double_values[2] = optrlb.run_time;
    }

    /// logging
    if(!runsilent)
    {
        FILE_LOG(logINFO) << "Cpssp algorithm ::: " << (sc_eval && lbilpps.progtype == CpsspILP::ProgType::RALB ? std::string("FDEEIS") : toString(lbilpps.progtype))
                          << " ::: status " << optr.tags[2] 
                          << " ::: time " << optr.run_time << " [s]" 
                          << " ::: OF " << optr.obj_value
                          << " ::: BB " << optr.best_bound
                          << " ::: opt gap " << std::fixed << std::setprecision(2) << 100.0 * optr.opt_gap << "%";
    }
    
    return optr;
}

optresult CpsspAlgoILP::computeAveScenarioBound(const CpsspILP::ILPparams& ilpps)
{
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: EV ::: compute ...";
    
    /// strart chrono
    auto cmp_tm = std::chrono::steady_clock::now();
    double opt_tm;

    /// save scenario data
    std::shared_ptr<std::vector<cpssp::Scenario>> scenarios_sptr;
    scenarios_sptr.reset(new std::vector<cpssp::Scenario>());
    *scenarios_sptr = std::move(inst_sptr->scenarios);
    /// reset to ave. scenario
    inst_sptr->scenarios.clear();
    inst_sptr->scenarios.push_back(inst_sptr->ave_scenario);
    inst_sptr->nr_of_scenarios = 1;
    
    /// create ilp
    CpsspILP ilpprog(inst_sptr, grbenv_sptr, ilpps);
    
    /// set flags
    if(is_verbose)
        ilpprog.verbose();    

    /// run
    optresult evoptr = ilpprog.solveProg(ilpps);
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: EV" 
                      << " ::: status " << evoptr.tags[2] 
                      << " ::: time " << evoptr.run_time << " [s]" 
                      << " ::: OF " << evoptr.obj_value
                      << " ::: BB " << evoptr.best_bound;
    /// compute eev
    optresult eevoptr;
    bool sc_eval = false;
    #ifdef EVAL_ALWAYS
    #else
    if(evoptr.integer_values[0] == 2)
    #endif
    {
        /// extract stage 1 solution
        std::shared_ptr<std::vector<bool>> supplsel;
        std::shared_ptr<std::vector<bool>> binsel;
        
        supplsel.reset(new std::vector<bool>());
        binsel.reset(new std::vector<bool>());
        
        ilpprog.getSuppliersSelection(*supplsel);
        ilpprog.getStage1BinSelection(*binsel);
        
        inst_sptr->scenarios = std::move(*scenarios_sptr);
        inst_sptr->nr_of_scenarios = inst_sptr->scenarios.size();
        
        /// logging
        FILE_LOG(logINFO) << "Cpssp algorithm ::: EEV ::: compute ...";
        CpsspILP::ILPparams esilps = ilpps;
        esilps.algo = 0;
        esilps.timelimsec = ilpps.timelim2sec;
        eevoptr = this->evaluateScenarios(esilps, supplsel, binsel); // , true
        sc_eval = true;
        
        /// logging
        FILE_LOG(logINFO) << "Cpssp algorithm ::: EEV" 
                          << " ::: status " << eevoptr.tags[2] 
                          << " ::: time " << eevoptr.run_time << " [s]" 
                          << " ::: OF " << eevoptr.obj_value
                          << " ::: BB " << eevoptr.best_bound;
    }

    /// get time
    opt_tm = GETOPTT(cmp_tm);
    
    /// result
    optresult optr;
    optr.tags.resize(3, "");
    optr.double_values.resize(6, std::numeric_limits<double>::quiet_NaN());
    optr.integer_values.resize(2, 0);
    if(sc_eval)
    {
        optr.tags[2] = eevoptr.integer_values[0] ? "Optimal" : "SubOptimal";
        optr.best_bound = eevoptr.best_bound;
        optr.obj_value = eevoptr.obj_value;
        optr.opt_gap = eevoptr.opt_gap;
        /// integer values
        optr.integer_values[0] = evoptr.integer_values[0] == 2;
        optr.integer_values[1] = eevoptr.integer_values[0];
        /// double values
        optr.double_values[0] = evoptr.best_bound;
        optr.double_values[1] = evoptr.obj_value;
        optr.double_values[2] = eevoptr.best_bound;
        optr.double_values[3] = eevoptr.obj_value;
        optr.double_values[4] = evoptr.run_time;
        optr.double_values[5] = eevoptr.run_time;
    }
    else
    {
        optr = evoptr;
    }
    /// run time
    optr.run_time = opt_tm;
    
    return optr;
}

optresult CpsspAlgoILP::computePerfectInfoBound(const CpsspILP::ILPparams& grbps)
{
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: WS ::: compute ...";
    
    CpsspILP::ILPparams wsilps = grbps;
    wsilps.timelimsec = grbps.timelim2sec;
    optresult optr = this->evaluateScenarios(wsilps, nullptr, nullptr); // , true
    
    /// logging
    FILE_LOG(logINFO) << "Cpssp algorithm ::: WS" 
                      << " ::: status " << optr.tags[2] 
                      << " ::: time " << optr.run_time << " [s]" 
                      << " ::: OF " << optr.obj_value
                      << " ::: BB " << optr.best_bound;
    
    return optr;
}

optresult CpsspAlgoILP::evaluateScenarios(const CpsspILP::ILPparams& grbps, 
                                          const std::shared_ptr<std::vector<bool>>& supplsl_sptr,
                                          const std::shared_ptr<std::vector<bool>>& binsl_sptr,
                                          bool all_must_be_opt)
{
    /// start timer
    auto cmp_tm = std::chrono::steady_clock::now();
    
    /// write progs
    IdCounterType S;
    std::unique_ptr<std::vector<optresult>> scenario_prob_optrs;
    optresult s_optr;
    std::string logmsg;
    std::string sttmsg;
    CostType pi_lbv;
    CostType pi_objv;
    CostType pi_gapv;
    double s_prob;
    double opt_tm;
    bool all_opt;
    
    S = inst_sptr->nr_of_scenarios;
    scenario_prob_optrs.reset(new std::vector<optresult>(S));
    logmsg = "grb:" + std::to_string(grbps.lp_algo);
    opt_tm = 0.0;
    
    /// init step
    std::vector<OptLogMeta> logstuff;
    INITDEBUGLOG(logstuff);
    IlpAlgoLogger alglog(logstuff);
    if(is_verbose)
    {
        DEBUGLOGHDR(logDEBUG, alglog);
    }
    
    /// loop over scenarios
    all_opt = true;
    pi_lbv = pi_objv = pi_gapv = 0.0;
    for(IdCounterType sdx = 0; sdx < S; sdx++)
    {
        /// build prog.
        std::vector<cpssp::IdCounterType> scids;
        scids.push_back(sdx);
        CpsspILP ilprog(inst_sptr, grbenv_sptr, grbps, scids);
        /// fix supplier selection if
        if(supplsl_sptr)
            ilprog.fixSuppliers(*supplsl_sptr);
        if(binsl_sptr)
            ilprog.fixStage1Bins(*binsl_sptr);
        
        /// set flags
        //if(is_verbose) 
        //ilprog.verbose();    
        s_optr = scenario_prob_optrs->at(sdx) = ilprog.solveProg(grbps);
        all_opt = all_opt && s_optr.integer_values[0] == 2;
        
        /// logging
        if(is_verbose)
        {
            sttmsg = std::to_string(s_optr.integer_values[0]);
            
            DEBUGLOG(logDEBUG, alglog, logmsg, sdx, sttmsg, s_optr.obj_value, s_optr.best_bound, 100.0 * s_optr.opt_gap, s_optr.run_time, GETOPTT(cmp_tm));
        }
        
        /// break-if scenario infeasible
        if(all_must_be_opt && !all_opt)
        {
            pi_lbv = pi_objv = pi_gapv = std::numeric_limits<double>::quiet_NaN();            break;
        }
        /// compute average objf.
        s_prob = inst_sptr->scenarios[sdx].prob;
        pi_lbv += s_prob * s_optr.best_bound;
        //pi_objv += s_prob * (!all_must_be_opt && s_optr.integer_values[0] != 2 ? s_optr.best_bound : s_optr.obj_value);
        pi_objv += s_prob * s_optr.obj_value;
        pi_gapv += s_prob * s_optr.opt_gap;
        //all_opt = all_opt && (*scenario_prob_optrs)[sdx].integer_values[0] == 2;
    }
    
    opt_tm = GETOPTT(cmp_tm);
    
    /// compute result
    optresult optr;
    optr.tags.resize(3, "");
    optr.tags[2] = all_opt ? "Optimal" : "SubOptimal";
    optr.best_bound = pi_lbv;
    optr.obj_value = pi_objv;
    optr.opt_gap = pi_gapv;
    optr.run_time = opt_tm;
    optr.integer_values.push_back(all_opt);

    return optr;
}
