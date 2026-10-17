#include <chrono>
#include <algorithm>
#include <fstream>

#include "CpsspILP.h"

#include "optlogger/MyLog.h"


static constexpr char grb_algo_str[17][24] = 
{
    "GRB_BASIC",
    "GRB_LOADED",
    "GRB_OPTIMAL",
    "GRB_INFEASIBLE",
    "GRB_INF_OR_UNBD",
    "GRB_UNBOUNDED",
    "GRB_CUTOFF",
    "GRB_ITERATION_LIMIT",
    "GRB_NODE_LIMIT",
    "GRB_TIME_LIMIT",
    "GRB_SOLUTION_LIMIT",
    "GRB_INTERRUPTED",
    "GRB_NUMERIC",
    "GRB_SUBOPTIMAL",
    "GRB_INPROGRESS",
    "GRB_USER_OBJ_LIMIT",
    "GRB_WORK_LIMIT"
};

#define GETOPTT(start) ((double)std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count() / 1000.0)


#define INITDEBUGLOG(logstuff) \
    logstuff.push_back(std::make_tuple( 8, 0, "info")); \
    logstuff.push_back(std::make_tuple( 8, 0, "iter")); \
    logstuff.push_back(std::make_tuple(12, 1, "status")); \
    logstuff.push_back(std::make_tuple(16, 1, "curr objv")); \
    logstuff.push_back(std::make_tuple(16, 1, "curr bound")); \
    logstuff.push_back(std::make_tuple(16, 3, "OPTT [s]")); \
    logstuff.push_back(std::make_tuple(16, 3, "EPT [s]"))
    //logstuff.push_back(std::make_tuple(8, 0, "stop"))

#define DEBUGLOGHDR(lglevl, optlogger) \
    FILE_LOG(lglevl) << optlogger.getFormatedHeader()

#define DEBUGLOG(lglevl, optlogger, info, iter, status, ubl, lbl, optt, ept) \
    FILE_LOG(lglevl) << optlogger.getFormatedLine(std::make_tuple(info, iter, status, ubl, lbl, optt, ept))


using namespace cpssp;


typedef OptLogger<std::string, unsigned int, std::string, double, double, double, double> IlpLogger;

CpsspILP::CpsspILP(const std::shared_ptr<CpsspInstance>& i_sptr, 
                   const std::shared_ptr<GRBEnv>& ge_sptr,
                   const ILPparams& ilpp,
                   bool determ)
    : inst_sptr(i_sptr)
    , ilpprms(ilpp)
{
    grbenv_sptr = ge_sptr;

    /// create model
    milprog_sptr.reset(new GRBModel(*grbenv_sptr));
    
    /// create arrays of variables
    X_sptr.reset(new std::vector<std::vector<GRBVar>>());
    #ifdef EXPRDEL
    E_sptr.reset(new std::vector<std::vector<GRBVar>>());
    #endif
    Y_sptr.reset(new std::vector<std::vector<GRBVar>>());
    W_sptr.reset(new std::vector<GRBVar>());
    //Z_sptr.reset(new std::vector<std::vector<GRBVar>>());
    
    /// add problem variables to arrays
    IdCounterType S;
    IdCounterType sdx;
    IdCounterType I;
    IdCounterType J;
    IdCounterType M;
    IdCounterType N;
    IdCounterType btype;
    IdCounterType bsuppl;
    VolumeType vol;
    char x_type;
    
    /// set scenario index if the case
    M = inst_sptr->nr_of_suppliers;
    N = inst_sptr->min_nr_of_suppliers;
    
    S = inst_sptr->nr_of_scenarios;
    std::vector<cpssp::IdCounterType> scenario_ids = std::vector<cpssp::IdCounterType>(inst_sptr->nr_of_scenarios, 0);
    
    for(IdCounterType s = 0; s < S; s++)
        scenario_ids[s] = s;
    
    /// x and e vars
    X_sptr->resize(S);
    #ifdef EXPRDEL
    E_sptr->resize(S);
    #endif
    
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        x_type = GRB_BINARY;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        x_type = GRB_INTEGER;
        #endif
        J = inst_sptr->nr_of_stage1_bins;
        
        if(ilpprms.progtype == ProgType::FDE)
        {
            for(IdCounterType i = 0; i < I; i++)
                for(IdCounterType j = 0; j < J; j++)
                    (*X_sptr)[s].push_back(milprog_sptr->addVar(0.0, GRB_INFINITY, 0, x_type, "x_" + std::to_string(sdx) + "_" + std::to_string(i) + "_" + std::to_string(j)));
        }
        #ifdef EXPRDEL
        for(IdCounterType i = 0; i < I; i++)
            (*E_sptr)[s].push_back(milprog_sptr->addVar(0.0, GRB_INFINITY, 0, x_type, "e_" + std::to_string(sdx) + "_" + std::to_string(i)));
        #endif
    }
    
    /// y vars
    Y_sptr->resize(1);
    
    /// 1st stage bins are in row 0
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        (*Y_sptr)[0].push_back(milprog_sptr->addVar(0.0, 1.0, 0, GRB_BINARY, "y_stage1_" + std::to_string(j)));
    
    
    /// w vars
    for(IdCounterType m = 0; m < M; m++)
        W_sptr->push_back(milprog_sptr->addVar(0.0, 1.0, 0, GRB_BINARY, "w_"  + std::to_string(m)));
    
    /// min. suppliers
    GRBLinExpr min_suppl_cexpr = 0;
    for(IdCounterType m = 0; m < M; m++)
        min_suppl_cexpr += (*W_sptr)[m];
    milprog_sptr->addConstr(min_suppl_cexpr, GRB_GREATER_EQUAL, N);
    
    /// min. volume per supplier
    #ifdef BIGMC
    GRBLinExpr tot_vol_cexpr = 0;
    double bigM = 0;
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
    {
        btype = inst_sptr->stage1_bin_types[j];
        tot_vol_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        bigM += inst_sptr->type_bin_volumes[btype];
    }
        
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr min_vol_x_suppl_cexpr = bigM * (1.0 - (*W_sptr)[m]);
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            if((bsuppl = inst_sptr->stage1_bin_suppliers[j]) != m)
                continue;
            
            btype = inst_sptr->stage1_bin_types[j];
            
            min_vol_x_suppl_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        }
        
        min_vol_x_suppl_cexpr -= inst_sptr->min_vol_x_supplier * tot_vol_cexpr;
        milprog_sptr->addConstr(min_vol_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    #else
    double TotVolSuppl = 0;    
    
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr min_vol_x_suppl_cexpr = 0;
        TotVolSuppl = 0;
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            if((bsuppl = inst_sptr->stage1_bin_suppliers[j]) != m)
                continue;
            
            btype = inst_sptr->stage1_bin_types[j];
            
            TotVolSuppl += inst_sptr->type_bin_volumes[btype];
            min_vol_x_suppl_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        }
        
        min_vol_x_suppl_cexpr -= inst_sptr->min_vol_x_supplier * TotVolSuppl * (*W_sptr)[m];
        milprog_sptr->addConstr(min_vol_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    #endif
    
    /// bin of suppliers
    std::vector<IdCounterType> bin_x_suppl_count(M, 0);
    for(IdCounterType m = 0; m < M; m++)
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            if(m == inst_sptr->stage1_bin_suppliers[j])
                ++bin_x_suppl_count[m];
    
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr bin_x_suppl_cexpr = 0;
        bin_x_suppl_cexpr += bin_x_suppl_count[m] * (*W_sptr)[m];
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            if(m == inst_sptr->stage1_bin_suppliers[j])
                bin_x_suppl_cexpr -= (*Y_sptr)[0][j];
        
        milprog_sptr->addConstr(bin_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    
    /// packing for scenarios
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        #endif
        
        J = inst_sptr->nr_of_stage1_bins;
        
        if(ilpprms.progtype == ProgType::FDE)
        {
            /// 1st stage bins
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                GRBLinExpr stage1_bin_cexpr = 0;
                btype = inst_sptr->stage1_bin_types[j];
                stage1_bin_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
                
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif
                    
                    stage1_bin_cexpr -= vol * (*X_sptr)[s][i * J + j];
                }
                milprog_sptr->addConstr(stage1_bin_cexpr, GRB_GREATER_EQUAL, 0);
            }
            
            /// pack items
            for(IdCounterType i = 0; i < I; i++)
            {
                IdCounterType rhsv;
                #ifdef X_IS_01
                rhsv = 1.0;
                #else
                rhsv = inst_sptr->scenarios[sdx].item_sizes_freq[i + 1];
                #endif
                
                GRBLinExpr pack_i_cexpr = 0;
                
                for(IdCounterType j = 0; j < J; j++)
                    pack_i_cexpr += (*X_sptr)[s][i * J + j];
                
                #ifdef EXPRDEL
                pack_i_cexpr += (*E_sptr)[s][i];
                #endif
                
                milprog_sptr->addConstr(pack_i_cexpr, GRB_EQUAL, rhsv);
            }
        }
        else
        {
            /// 1st stage bins
            GRBLinExpr stage1n2_bins_cexpr = 0;
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                btype = inst_sptr->stage1_bin_types[j];
                stage1n2_bins_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
            }
                        
            #ifdef EXPRDEL
            for(IdCounterType i = 0; i < I; i++)
            {
                #ifdef X_IS_01
                vol = inst_sptr->scenarios[sdx].item_volumes[i];
                #else
                vol = i + 1;
                #endif
                
                stage1n2_bins_cexpr += vol * (*E_sptr)[s][i];
            }
            #endif
            
            milprog_sptr->addConstr(stage1n2_bins_cexpr, GRB_GREATER_EQUAL, inst_sptr->scenarios[sdx].tot_volume_of_items);
        }
    }
    
    /// Dev. cuts
    #ifdef MYCUTS
    for(IdCounterType m = 0; m < M; m++)
    {
        for(IdCounterType t = 0; t < inst_sptr->nr_of_bin_types; t++)
        {
            /// local vars
            GRBLinExpr cut_stage1_bins_cexpr = 0;
            double maxtotvol;
            double totvols;
            double rhs;
            double cf;
            /// init local vars
            maxtotvol = 0;
            
            /// add bin vars to the cut
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                if(inst_sptr->stage1_bin_suppliers[j] == m && inst_sptr->stage1_bin_types[j] == t)
                {
                    cut_stage1_bins_cexpr += (*Y_sptr)[0][j];
                }
            }
            /// compute max tot vol for item
            for(IdCounterType s = 0; s < S; s++)
            {
                IdCounterType ITMSS = inst_sptr->scenarios[sdx = scenario_ids[s]].item_volumes.size();
                totvols = 0;
                
                for(IdCounterType i = 0; i < ITMSS; i++)
                    totvols += inst_sptr->scenarios[sdx = scenario_ids[s]].item_volumes[i];
                
                maxtotvol = std::max(maxtotvol, totvols);
            }
            /// compute rhs and cf
            rhs = std::ceil(maxtotvol / double(inst_sptr->type_bin_volumes[t]));
            cf = std::floor(rhs * inst_sptr->min_vol_x_supplier);
            /// add suppl vars to cut
            for(IdCounterType mm = 0; mm < M; mm++)
            {
                if(mm != m)
                    cut_stage1_bins_cexpr += cf * (*W_sptr)[mm];
            }
            /// add constraint
            milprog_sptr->addConstr(cut_stage1_bins_cexpr, GRB_LESS_EQUAL, rhs);
        }
    }
    #endif
    
    /// objective function
    GRBLinExpr obj = 0.0;
    for(IdCounterType m = 0; m < M; m++)
        obj += inst_sptr->supplier_contract_costs[m] * (*W_sptr)[m];
    
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
    {
        btype = inst_sptr->stage1_bin_types[j];
        bsuppl = inst_sptr->stage1_bin_suppliers[j];
        
        obj += inst_sptr->type_suppl_bin_costs[btype * M + bsuppl] * (*Y_sptr)[0][j];
    }
    
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        #ifdef EXPRDEL
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        #endif
        for(IdCounterType i = 0; i < I; i++)
            obj += inst_sptr->scenarios[sdx].exp_delivery_cost * (*E_sptr)[s][i];
        #endif
    }
    
    milprog_sptr->setObjective(obj, GRB_MINIMIZE);
    
    /// set priorty of variables
    if(false && ilpprms.priorities)
    {
        //IdCounterType maxd = 0;
        IdCounterType xp = 0;
        IdCounterType max_xp = 0;
        IdCounterType zp = 0;
        IdCounterType min_zp = std::numeric_limits<IdCounterType>::max();
        IdCounterType max_zp = 0;
        IdCounterType yp = 0;
        IdCounterType max_yp = 0;
        IdCounterType min_yp = std::numeric_limits<IdCounterType>::max();
        IdCounterType wp = 0;
        IdCounterType min_wp = std::numeric_limits<IdCounterType>::max();
        //IdCounterType max_wp = 0;
        
        /// x priorities
        for(IdCounterType s = 0; s < S; s++)
        {
            sdx = scenario_ids[s];
            
            #ifdef X_IS_01
            I = inst_sptr->scenarios[sdx].nr_of_items;
            #else
            I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
            #endif
            
            J = inst_sptr->nr_of_stage1_bins; // + inst_sptr->scenarios[sdx].nr_of_stage2_bins;
            
            if(ilpprms.progtype == ProgType::FDE)
            {
                //maxd = (IdCounterType)inst_sptr->scenarios[sdx].item_min_max_volume.second;
                
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif
                    
                    for(IdCounterType j = inst_sptr->nr_of_stage1_bins; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
                    {
                        //xp = inst_sptr->scenarios[sdx].stage2_bin_priority[j] * (maxd + 1) + vol;
                        xp = vol;
                        
                        (*X_sptr)[s][i * J + j].set(GRB_IntAttr_BranchPriority, xp);
                        max_xp = std::max(max_xp, xp);
                    }
                }
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif

                    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
                    {
                        //xp = max_xp + 1 + inst_sptr->stage1_bin_priority[j] * (maxd + 1) + vol;
                        xp = vol;
                        
                        (*X_sptr)[s][i * J + j].set(GRB_IntAttr_BranchPriority, xp);
                        max_xp = std::max(max_xp, xp);
                    }
                }
            }
            
            for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
            {
                //unsigned long long int vp = instdata_sptr->vehicle_type_pty.back() * instdata_sptr->nr_vehicles * (maxd + 1) + 
                //                            instdata_sptr->nr_vehicles * (maxd + 1) + 2 * (maxd + 1) + 
                //                            instdata_sptr->vehicle_type_pty[instdata_sptr->vehicle_types[k] - 1] * instdata_sptr->nr_vehicles +
                //                            k;
                zp = max_xp + 1 + inst_sptr->scenarios[sdx].stage2_bin_priority[j];
                (*Y_sptr)[1 + s][j].set(GRB_IntAttr_BranchPriority, zp);
                max_zp = std::max(zp, max_zp);
                min_zp = std::min(zp, min_zp);
            }
        }

        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            yp = max_zp + 1 + inst_sptr->stage1_bin_priority[j];
            (*Y_sptr)[0][j].set(GRB_IntAttr_BranchPriority, yp);
            max_yp = std::max(yp, max_yp);
            min_yp = std::min(yp, min_yp);
        }
        
        for(IdCounterType m = 0; m < M; m++)
        {
            wp = max_yp + 1 + (inst_sptr->supplier_priority[m]);
            (*W_sptr)[m].set(GRB_IntAttr_BranchPriority, wp);
            min_wp = std::min(wp, min_wp);
        }

        /// logging
        /*
        FILE_LOG(logINFO) << "Max xp = " << max_xp;
        FILE_LOG(logINFO) << "Min zp = " << min_zp << ", Max zp = " << max_zp;
        FILE_LOG(logINFO) << "Min yp = " << min_yp << ", Max yp = " << max_yp;
        FILE_LOG(logINFO) << "Min wp = " << min_wp;
         * */

    }
    
    /// symmetry-breaking constraints
    if(true)
    {
        /// stage 1
        for(IdCounterType j = 0; j + 1 < inst_sptr->nr_of_stage1_bins; j++)
            for(IdCounterType k = j + 1; k < inst_sptr->nr_of_stage1_bins; k++)
                if(inst_sptr->stage1_bin_types[j] == inst_sptr->stage1_bin_types[k] &&
                   inst_sptr->stage1_bin_suppliers[j] == inst_sptr->stage1_bin_suppliers[k])
                    milprog_sptr->addConstr((*Y_sptr)[0][j] - (*Y_sptr)[0][k], GRB_GREATER_EQUAL, 0);
        
        /// stage 2 (by scenario)
        /*
        for(IdCounterType s = 0; s < S; s++)
        {
            sdx = scenario_ids[s];
            
            for(IdCounterType j = 0; j + 1 < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
                for(IdCounterType k = j; k + 1 < inst_sptr->scenarios[sdx].nr_of_stage2_bins; k++)
                    if(inst_sptr->scenarios[sdx].stage2_bin_types[j] == inst_sptr->scenarios[sdx].stage2_bin_types[k] &&
                       inst_sptr->scenarios[sdx].stage2_bin_suppliers[j] == inst_sptr->scenarios[sdx].stage2_bin_suppliers[k])
                        milprog_sptr->addConstr((*Y_sptr)[1 + s][j] - (*Y_sptr)[1 + s][k], GRB_GREATER_EQUAL, 0);
        }
         * */
    }
}

CpsspILP::CpsspILP(const std::shared_ptr<CpsspInstance>& i_sptr, 
                   const std::shared_ptr<GRBEnv>& ge_sptr,
                   const ILPparams& ilpp,
                   std::vector<cpssp::IdCounterType> scenario_ids)
    : inst_sptr(i_sptr)
    , ilpprms(ilpp)
{
    grbenv_sptr = ge_sptr;

    /// create model
    milprog_sptr.reset(new GRBModel(*grbenv_sptr));
    
    /// create arrays of variables
    X_sptr.reset(new std::vector<std::vector<GRBVar>>());
    #ifdef EXPRDEL
    E_sptr.reset(new std::vector<std::vector<GRBVar>>());
    #endif
    Y_sptr.reset(new std::vector<std::vector<GRBVar>>());
    W_sptr.reset(new std::vector<GRBVar>());
    //Z_sptr.reset(new std::vector<std::vector<GRBVar>>());
    
    /// add problem variables to arrays
    IdCounterType S;
    IdCounterType sdx;
    IdCounterType I;
    IdCounterType J;
    IdCounterType M;
    IdCounterType N;
    IdCounterType btype;
    IdCounterType bsuppl;
    VolumeType vol;
    bool all_scenarios;
    char x_type;
    
    /// set scenario index if the case
    M = inst_sptr->nr_of_suppliers;
    N = inst_sptr->min_nr_of_suppliers;
    
    if((all_scenarios = scenario_ids.empty()))
    {
        S = inst_sptr->nr_of_scenarios;
        scenario_ids = std::vector<cpssp::IdCounterType>(inst_sptr->nr_of_scenarios, 0);
        
        for(IdCounterType s = 0; s < S; s++)
            scenario_ids[s] = s;
    }
    else
        S = scenario_ids.size();
    
    /// x and e vars
    X_sptr->resize(S);
    #ifdef EXPRDEL
    E_sptr->resize(S);
    #endif
    
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        x_type = GRB_BINARY;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        x_type = GRB_INTEGER;
        #endif
        J = inst_sptr->nr_of_stage1_bins + inst_sptr->scenarios[sdx].nr_of_stage2_bins;
        
        if(ilpprms.progtype == ProgType::FDE)
        {
            for(IdCounterType i = 0; i < I; i++)
                for(IdCounterType j = 0; j < J; j++)
                    (*X_sptr)[s].push_back(milprog_sptr->addVar(0.0, GRB_INFINITY, 0, x_type, "x_" + std::to_string(sdx) + "_" + std::to_string(i) + "_" + std::to_string(j)));
        }
        #ifdef EXPRDEL
        for(IdCounterType i = 0; i < I; i++)
            (*E_sptr)[s].push_back(milprog_sptr->addVar(0.0, GRB_INFINITY, 0, x_type, "e_" + std::to_string(sdx) + "_" + std::to_string(i)));
        #endif
    }
    
    /// y vars
    Y_sptr->resize(1 + S);
    
    /// 1st stage bins are in row 0
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        (*Y_sptr)[0].push_back(milprog_sptr->addVar(0.0, 1.0, 0, GRB_BINARY, "y_stage1_" + std::to_string(j)));
    
    /// 2nd stage bins
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];

        for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
            (*Y_sptr)[1 + s].push_back(milprog_sptr->addVar(0.0, 1.0, 0, GRB_BINARY, "y_stage2_" + std::to_string(sdx) + "_" + std::to_string(j)));
    }
    
    /// w vars
    for(IdCounterType m = 0; m < M; m++)
        W_sptr->push_back(milprog_sptr->addVar(0.0, 1.0, 0, GRB_BINARY, "w_"  + std::to_string(m)));
    
    /// min. suppliers
    GRBLinExpr min_suppl_cexpr = 0;
    for(IdCounterType m = 0; m < M; m++)
        min_suppl_cexpr += (*W_sptr)[m];
    milprog_sptr->addConstr(min_suppl_cexpr, GRB_GREATER_EQUAL, N);
    
    /// min. volume per supplier
    #ifdef BIGMC
    GRBLinExpr tot_vol_cexpr = 0;
    double bigM = 0;
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
    {
        btype = inst_sptr->stage1_bin_types[j];
        tot_vol_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        bigM += inst_sptr->type_bin_volumes[btype];
    }
        
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr min_vol_x_suppl_cexpr = bigM * (1.0 - (*W_sptr)[m]);
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            if((bsuppl = inst_sptr->stage1_bin_suppliers[j]) != m)
                continue;
            
            btype = inst_sptr->stage1_bin_types[j];
            
            min_vol_x_suppl_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        }
        
        min_vol_x_suppl_cexpr -= inst_sptr->min_vol_x_supplier * tot_vol_cexpr;
        milprog_sptr->addConstr(min_vol_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    #else
    double TotVolSuppl = 0;    
    /// DEBUG: hard coded value of min. vol. per suppl. 
    inst_sptr->min_vol_x_supplier = 0.05;
    /// END DEBUG
    
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr min_vol_x_suppl_cexpr = 0;
        TotVolSuppl = 0;
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            if((bsuppl = inst_sptr->stage1_bin_suppliers[j]) != m)
                continue;
            
            btype = inst_sptr->stage1_bin_types[j];
            
            TotVolSuppl += inst_sptr->type_bin_volumes[btype];
            min_vol_x_suppl_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[0][j];
        }
        
        min_vol_x_suppl_cexpr -= inst_sptr->min_vol_x_supplier * TotVolSuppl * (*W_sptr)[m];// tot_vol_cexpr;
        milprog_sptr->addConstr(min_vol_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    #endif
    
    /// min. ave. reliability
    GRBLinExpr min_r_cexpr = 0;
    for(IdCounterType m = 0; m < M; m++)
        min_r_cexpr += (inst_sptr->supplier_thetas[m] - inst_sptr->min_ave_reliability) * (*W_sptr)[m];
    milprog_sptr->addConstr(min_r_cexpr, GRB_GREATER_EQUAL, 0);
    
    /// bin of suppliers
    std::vector<IdCounterType> bin_x_suppl_count(M, 0);
    for(IdCounterType m = 0; m < M; m++)
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            if(m == inst_sptr->stage1_bin_suppliers[j])
                ++bin_x_suppl_count[m];
    
    for(IdCounterType m = 0; m < M; m++)
    {
        GRBLinExpr bin_x_suppl_cexpr = 0;
        bin_x_suppl_cexpr += bin_x_suppl_count[m] * (*W_sptr)[m];
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            if(m == inst_sptr->stage1_bin_suppliers[j])
                bin_x_suppl_cexpr -= (*Y_sptr)[0][j];
        
        milprog_sptr->addConstr(bin_x_suppl_cexpr, GRB_GREATER_EQUAL, 0);
    }
    
    /// packing for scenarios
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        #endif
        
        J = inst_sptr->nr_of_stage1_bins + inst_sptr->scenarios[sdx].nr_of_stage2_bins;
        
        if(ilpprms.progtype == ProgType::FDE)
        {
            /// 1st stage bins
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                GRBLinExpr stage1_bin_cexpr = 0;
                stage1_bin_cexpr += inst_sptr->scenarios[sdx].stage1_bin_volumes[j] * (*Y_sptr)[0][j];
                
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif
                    
                    stage1_bin_cexpr -= vol * (*X_sptr)[s][i * J + j];
                }
                milprog_sptr->addConstr(stage1_bin_cexpr, GRB_GREATER_EQUAL, 0);
            }
            
            /// 2ns stage bins
            for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
            {
                btype = inst_sptr->scenarios[sdx].stage2_bin_types[j];
                GRBLinExpr stage2_bin_cexpr = 0;
                
                stage2_bin_cexpr += inst_sptr->type_bin_volumes[btype] * (*Y_sptr)[1 + s][j];
                
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif
                    
                    stage2_bin_cexpr -= vol * (*X_sptr)[s][i * J + inst_sptr->nr_of_stage1_bins + j];
                }
                milprog_sptr->addConstr(stage2_bin_cexpr, GRB_GREATER_EQUAL, 0);
            }
            
            /// pack items
            for(IdCounterType i = 0; i < I; i++)
            {
                IdCounterType rhsv;
                #ifdef X_IS_01
                rhsv = 1.0;
                #else
                rhsv = inst_sptr->scenarios[sdx].item_sizes_freq[i + 1];
                #endif
                
                GRBLinExpr pack_i_cexpr = 0;
                
                for(IdCounterType j = 0; j < J; j++)
                    pack_i_cexpr += (*X_sptr)[s][i * J + j];
                
                #ifdef EXPRDEL
                pack_i_cexpr += (*E_sptr)[s][i];
                #endif
                
                milprog_sptr->addConstr(pack_i_cexpr, GRB_EQUAL, rhsv);
            }
        }
        else
        {
            /// 1st stage bins
            GRBLinExpr stage1n2_bins_cexpr = 0;
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
                stage1n2_bins_cexpr += inst_sptr->scenarios[sdx].stage1_bin_volumes[j] * (*Y_sptr)[0][j];
            
            /// 2ns stage bins
            for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
                stage1n2_bins_cexpr += inst_sptr->type_bin_volumes[ inst_sptr->scenarios[sdx].stage2_bin_types[j] ] * (*Y_sptr)[1 + s][j];
            
            #ifdef EXPRDEL
            for(IdCounterType i = 0; i < I; i++)
            {
                #ifdef X_IS_01
                vol = inst_sptr->scenarios[sdx].item_volumes[i];
                #else
                vol = i + 1;
                #endif
                
                stage1n2_bins_cexpr += vol * (*E_sptr)[s][i];
            }
            #endif
            
            milprog_sptr->addConstr(stage1n2_bins_cexpr, GRB_GREATER_EQUAL, inst_sptr->scenarios[sdx].tot_volume_of_items);
        }
    }
    
    /// Dev. cuts
    #ifdef MYCUTS
    for(IdCounterType m = 0; m < M; m++)
    {
        for(IdCounterType t = 0; t < inst_sptr->nr_of_bin_types; t++)
        {
            /// local vars
            GRBLinExpr cut_stage1_bins_cexpr = 0;
            double maxtotvol;
            double totvols;
            double rhs;
            double cf;
            /// init local vars
            maxtotvol = 0;
            
            /// add bin vars to the cut
            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                if(inst_sptr->stage1_bin_suppliers[j] == m && inst_sptr->stage1_bin_types[j] == t)
                {
                    cut_stage1_bins_cexpr += (*Y_sptr)[0][j];
                }
            }
            /// compute max tot vol for item
            for(IdCounterType s = 0; s < S; s++)
            {
                IdCounterType ITMSS = inst_sptr->scenarios[sdx = scenario_ids[s]].item_volumes.size();
                totvols = 0;
                
                for(IdCounterType i = 0; i < ITMSS; i++)
                    totvols += inst_sptr->scenarios[sdx = scenario_ids[s]].item_volumes[i];
                
                maxtotvol = std::max(maxtotvol, totvols);
            }
            /// compute rhs and cf
            rhs = std::ceil(maxtotvol / double(inst_sptr->type_bin_volumes[t]));
            cf = std::floor(rhs * inst_sptr->min_vol_x_supplier);
            /// add suppl vars to cut
            for(IdCounterType mm = 0; mm < M; mm++)
            {
                if(mm != m)
                    cut_stage1_bins_cexpr += cf * (*W_sptr)[mm];
            }
            /// add constraint
            milprog_sptr->addConstr(cut_stage1_bins_cexpr, GRB_LESS_EQUAL, rhs);
        }
    }
    #endif
    
    /// objective function
    GRBLinExpr obj = 0.0;
    for(IdCounterType m = 0; m < M; m++)
        obj += inst_sptr->supplier_contract_costs[m] * (*W_sptr)[m];
    
    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
    {
        btype = inst_sptr->stage1_bin_types[j];
        bsuppl = inst_sptr->stage1_bin_suppliers[j];
                obj += inst_sptr->type_suppl_bin_costs[btype * M + bsuppl] * (*Y_sptr)[0][j];
    }
    
    for(IdCounterType s = 0; s < S; s++)
    {
        sdx = scenario_ids[s];
        
        double s_prob = all_scenarios ? inst_sptr->scenarios[sdx].prob : 1.0 / S;
                for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
        {
            btype = inst_sptr->scenarios[sdx].stage2_bin_types[j];
            bsuppl = inst_sptr->scenarios[sdx].stage2_bin_suppliers[j];
            
            obj += s_prob * inst_sptr->scenarios[sdx].stage2_bin_costs[btype * M + bsuppl] * (*Y_sptr)[1 + s][j];
        }
        
        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            btype = inst_sptr->stage1_bin_types[j];
            bsuppl = inst_sptr->stage1_bin_suppliers[j];
            
            double v1 = (inst_sptr->type_bin_volumes[btype] - inst_sptr->scenarios[sdx].stage1_bin_volumes[j]);
            //if(v1 < 0.0) { FILE_LOG(logINFO) << "v1 " << v1 << ", 1st stage bin " << j; }
            
            double v2 = (inst_sptr->type_suppl_bin_loss_x_u_costs[btype * M + bsuppl] - inst_sptr->type_suppl_bin_loss_x_u_comps[btype * M + bsuppl]);
            //if(v2 < 0.0) { FILE_LOG(logINFO) << "v2 " << v2 << ", 1st stage bin " << j << ", bin type " << btype << ", supplier " << bsuppl; }
            
            obj += s_prob * v1 * v2 * (*Y_sptr)[0][j];
        }
        
        #ifdef EXPRDEL
        #ifdef X_IS_01
        I = inst_sptr->scenarios[sdx].nr_of_items;
        #else
        I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
        #endif
        for(IdCounterType i = 0; i < I; i++)
            obj += inst_sptr->scenarios[sdx].exp_delivery_cost * (*E_sptr)[s][i];
        #endif
    }
    
    milprog_sptr->setObjective(obj, GRB_MINIMIZE);
    
    /// set priorty of variables
    if(ilpprms.priorities)
    {
        //IdCounterType maxd = 0;
        IdCounterType xp = 0;
        IdCounterType max_xp = 0;
        IdCounterType zp = 0;
        IdCounterType min_zp = std::numeric_limits<IdCounterType>::max();
        IdCounterType max_zp = 0;
        IdCounterType yp = 0;
        IdCounterType max_yp = 0;
        IdCounterType min_yp = std::numeric_limits<IdCounterType>::max();
        IdCounterType wp = 0;
        IdCounterType min_wp = std::numeric_limits<IdCounterType>::max();
        //IdCounterType max_wp = 0;
        
        /*
        for(sdx = scenario_idx >= 0 ? scenario_idx : 0; sdx < S; sdx++)
                maxd = std::max(maxd, (IdCounterType)inst_sptr->scenarios[sdx].item_min_max_volume.second);
        for(IdCounterType p = 0; p < inst_sptr->bin_priority.size(); p++)
            max_tp = std::max(max_tp, inst_sptr->bin_priority[p]);
         * */
        
        /// x priorities
        for(IdCounterType s = 0; s < S; s++)
        {
            sdx = scenario_ids[s];
            
            #ifdef X_IS_01
            I = inst_sptr->scenarios[sdx].nr_of_items;
            #else
            I = inst_sptr->scenarios[sdx].item_min_max_volume.second;
            #endif
            
            J = inst_sptr->nr_of_stage1_bins + inst_sptr->scenarios[sdx].nr_of_stage2_bins;
            
            if(ilpprms.progtype == ProgType::FDE)
            {
                //maxd = (IdCounterType)inst_sptr->scenarios[sdx].item_min_max_volume.second;
                
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif
                    
                    for(IdCounterType j = inst_sptr->nr_of_stage1_bins; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
                    {
                        //xp = inst_sptr->scenarios[sdx].stage2_bin_priority[j] * (maxd + 1) + vol;
                        xp = vol;
                        
                        (*X_sptr)[s][i * J + j].set(GRB_IntAttr_BranchPriority, xp);
                        max_xp = std::max(max_xp, xp);
                    }
                }
                for(IdCounterType i = 0; i < I; i++)
                {
                    #ifdef X_IS_01
                    vol = inst_sptr->scenarios[sdx].item_volumes[i];
                    #else
                    vol = i + 1;
                    #endif

                    for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
                    {
                        //xp = max_xp + 1 + inst_sptr->stage1_bin_priority[j] * (maxd + 1) + vol;
                        xp = vol;
                        
                        (*X_sptr)[s][i * J + j].set(GRB_IntAttr_BranchPriority, xp);
                        max_xp = std::max(max_xp, xp);
                    }
                }
            }
            
            for(IdCounterType j = 0; j < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
            {
                //unsigned long long int vp = instdata_sptr->vehicle_type_pty.back() * instdata_sptr->nr_vehicles * (maxd + 1) + 
                //                            instdata_sptr->nr_vehicles * (maxd + 1) + 2 * (maxd + 1) + 
                //                            instdata_sptr->vehicle_type_pty[instdata_sptr->vehicle_types[k] - 1] * instdata_sptr->nr_vehicles +
                //                            k;
                zp = max_xp + 1 + inst_sptr->scenarios[sdx].stage2_bin_priority[j];
                (*Y_sptr)[1 + s][j].set(GRB_IntAttr_BranchPriority, zp);
                max_zp = std::max(zp, max_zp);
                min_zp = std::min(zp, min_zp);
            }
        }

        for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
        {
            yp = max_zp + 1 + inst_sptr->stage1_bin_priority[j];
            (*Y_sptr)[0][j].set(GRB_IntAttr_BranchPriority, yp);
            max_yp = std::max(yp, max_yp);
            min_yp = std::min(yp, min_yp);
        }
        
        for(IdCounterType m = 0; m < M; m++)
        {
            wp = max_yp + 1 + (inst_sptr->supplier_priority[m]);
            (*W_sptr)[m].set(GRB_IntAttr_BranchPriority, wp);
            min_wp = std::min(wp, min_wp);
        }

        /// logging
        /*
        FILE_LOG(logINFO) << "Max xp = " << max_xp;
        FILE_LOG(logINFO) << "Min zp = " << min_zp << ", Max zp = " << max_zp;
        FILE_LOG(logINFO) << "Min yp = " << min_yp << ", Max yp = " << max_yp;
        FILE_LOG(logINFO) << "Min wp = " << min_wp;
         * */

    }
    
    /// symmetry-breaking constraints
    if(false)
    {
        /// stage 1
        for(IdCounterType j = 0; j + 1 < inst_sptr->nr_of_stage1_bins; j++)
            for(IdCounterType k = j + 1; k < inst_sptr->nr_of_stage1_bins; k++)
                if(inst_sptr->stage1_bin_types[j] == inst_sptr->stage1_bin_types[k] &&
                   inst_sptr->stage1_bin_suppliers[j] == inst_sptr->stage1_bin_suppliers[k])
                    milprog_sptr->addConstr((*Y_sptr)[0][j] - (*Y_sptr)[0][k], GRB_GREATER_EQUAL, 0);
        /// stage 2 (by scenario)
        for(IdCounterType s = 0; s < S; s++)
        {
            sdx = scenario_ids[s];
            
            for(IdCounterType j = 0; j + 1 < inst_sptr->scenarios[sdx].nr_of_stage2_bins; j++)
                for(IdCounterType k = j; k + 1 < inst_sptr->scenarios[sdx].nr_of_stage2_bins; k++)
                    if(inst_sptr->scenarios[sdx].stage2_bin_types[j] == inst_sptr->scenarios[sdx].stage2_bin_types[k] &&
                       inst_sptr->scenarios[sdx].stage2_bin_suppliers[j] == inst_sptr->scenarios[sdx].stage2_bin_suppliers[k])
                        milprog_sptr->addConstr((*Y_sptr)[1 + s][j] - (*Y_sptr)[1 + s][k], GRB_GREATER_EQUAL, 0);
        }
    }
}

void CpsspILP::fixSuppliers(const std::vector<bool>& supplsel_arg)
{
    /// exception of error
    if(supplsel_arg.size() != inst_sptr->nr_of_suppliers)
        throw std::runtime_error("| suppl. selection | != M");

    /// fix supplier selection
    for(IdCounterType m = 0; m < W_sptr->size(); m++)
    {
        if(supplsel_arg[m])
        {
            (*W_sptr)[m].set(GRB_DoubleAttr_UB, 1.0);
            (*W_sptr)[m].set(GRB_DoubleAttr_LB, 1.0);

            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                if(inst_sptr->stage1_bin_suppliers[j] == m)
                    (*Y_sptr)[0][j].set(GRB_DoubleAttr_UB, 1.0);
            }
        }
        else
        {
            (*W_sptr)[m].set(GRB_DoubleAttr_UB, 0.0);
            (*W_sptr)[m].set(GRB_DoubleAttr_LB, 0.0);

            for(IdCounterType j = 0; j < inst_sptr->nr_of_stage1_bins; j++)
            {
                if(inst_sptr->stage1_bin_suppliers[j] == m)
                    (*Y_sptr)[0][j].set(GRB_DoubleAttr_UB, 0.0);
            }
        }
    }
    
    is_W_sol_fixed = true;
}

void CpsspILP::fixStage1Bins(const std::vector<bool>& binsel_arg)
{
    /// exception of error
    if(binsel_arg.size() != inst_sptr->nr_of_stage1_bins)
        throw std::runtime_error("| bin selection | != J");

    /// fix supplier selection
    for(IdCounterType j = 0; j < (*Y_sptr)[0].size(); j++)
    {
        if(binsel_arg[j])
        {
            (*Y_sptr)[0][j].set(GRB_DoubleAttr_UB, 1.0);
            (*Y_sptr)[0][j].set(GRB_DoubleAttr_LB, 1.0);

        }
        else
        {
            (*Y_sptr)[0][j].set(GRB_DoubleAttr_UB, 0.0);
            (*Y_sptr)[0][j].set(GRB_DoubleAttr_LB, 0.0);
        }
    }
    
    is_Y_sol_fixed = true;
}

void CpsspILP::resetSuppliers()
{
    /// fix supplier selection
    for(IdCounterType m = 0; m < W_sptr->size(); m++)
    {
        (*W_sptr)[m].set(GRB_DoubleAttr_UB, 1.0);
        (*W_sptr)[m].set(GRB_DoubleAttr_LB, 0.0);
    }
    
    is_W_sol_fixed = false;
}

void CpsspILP::resetStage1Bins()
{
    /// fix supplier selection
    for(IdCounterType j = 0; j < (*Y_sptr)[0].size(); j++)
    {
        (*Y_sptr)[0][j].set(GRB_DoubleAttr_UB, 1.0);
        (*Y_sptr)[0][j].set(GRB_DoubleAttr_LB, 0.0);
    }
    
    is_Y_sol_fixed = false;
}

CpsspILP::~CpsspILP()
{
}

/*
void CpsspILP::buildProg()
{
}
 * */

void CpsspILP::verbose()
{
    is_verbose = true;
}
    
void CpsspILP::silent()
{
    is_verbose = false;
}

void comb(const unsigned int N, const unsigned int K, std::vector<std::vector<bool>>& result)
{
    std::vector<bool> bitmask(K, 1); // K leading 1's
    bitmask.resize(N, 0); // N-K trailing 0's

    // compute permutations
    do
    {
        result.push_back(bitmask);
    }
    while (std::prev_permutation(bitmask.begin(), bitmask.end()));
}

optresult CpsspILP::feasibleProg(const ILPparams& prms)
{
    /// set objective to null
    milprog_sptr->setObjective(GRBLinExpr(0.0));
    
    return this->solveProg(prms);
}

optresult CpsspILP::solveProg(const ILPparams& prms)
{
    /// start chrono
    auto sol_tm = std::chrono::steady_clock::now();
    
    /// local vars
    double best_bndv;
    double best_objv;
    double bndv;
    double objv;
    int grb_status;
    bool all_opt;
    bool bforce;
    
    /// set model parameters 
    milprog_sptr->set(GRB_IntParam_OutputFlag, is_verbose && !prms.algo);
    milprog_sptr->set(GRB_IntParam_Presolve, prms.presolve);
    //model.set(GRB_IntParam_SimplexPricing,);
    //if(SIFTONOFF)  && opt_config.alg_mode % 10 <= 2)
    milprog_sptr->set(GRB_IntParam_Sifting, prms.sifting);
    //model.set(GRB_IntParam_SiftMethod, 0);
    milprog_sptr->set(GRB_DoubleParam_MemLimit, prms.maxmem);
    milprog_sptr->set(GRB_DoubleParam_NodefileStart, 0.5);
    milprog_sptr->set(GRB_IntParam_Threads, prms.threads2use);
    /// GRB_METHOD_AUTO -1, PRIMAL 0, DUAL 1, etc.
    //if(opt_config.alg_mode % 10 < 9)
    milprog_sptr->set(GRB_IntParam_Method, prms.lp_algo); // std::max(int(opt_config.alg_mode % 10 - 1), 
    milprog_sptr->set(GRB_DoubleParam_TimeLimit, prms.timelimsec);
    if(prms.priorities)
        milprog_sptr->set(GRB_IntParam_BranchDir, 1);
    
    /// init. local vars
    bforce = false;
    grb_status = -1;
    best_bndv = std::numeric_limits<double>::quiet_NaN();
    best_objv = std::numeric_limits<double>::quiet_NaN();
    
    /// brute force suppliers selection
    if(prms.algo && !is_W_sol_fixed && !is_Y_sol_fixed)
    {
        /// local vars
        std::unique_ptr<std::vector<std::vector<bool>>> suppl_sels;
        unsigned long int iter;
        IdCounterType M;
        IdCounterType N;
        std::string logmsg;
        double opt_tm;
        
        /// init. subset data struct
        bforce = true;
        suppl_sels.reset(new std::vector<std::vector<bool>>());
        best_bndv = best_objv = std::numeric_limits<double>::max();
        M = inst_sptr->nr_of_suppliers;
        N = inst_sptr->min_nr_of_suppliers;
        
        /// init. logger
        std::vector<OptLogMeta> logstuff;
        INITDEBUGLOG(logstuff);
        IlpLogger alglog(logstuff);
        if(is_verbose)
        {
            DEBUGLOGHDR(logDEBUG, alglog);
        }
        
        /// compute suppliers selection to test
        opt_tm = 0.0;
        iter = 0;
        all_opt = true;
        for(IdCounterType k = N; k <= M; k++)
        {
            /// pop.
            suppl_sels->clear();
            comb(M, k, *suppl_sels);
            
            /// solve for k
            for(auto it = suppl_sels->begin(); it != suppl_sels->end(); it++)
            {
                /// init.
                bndv = objv = std::numeric_limits<double>::quiet_NaN();
                logmsg = "k=" + std::to_string(k);
                
                /// teast feasibility
                /// ave realiability of selected suppliers
                double ave_r = 0.0;
                for(IdCounterType m = 0; m < inst_sptr->nr_of_suppliers; m++)
                    if((*it)[m])
                        ave_r += inst_sptr->supplier_thetas[m];
                ave_r = ave_r / double(k);
                
                /// test, skip if infeasible supplier selection
                if(ave_r + prms.eps < inst_sptr->min_ave_reliability)
                {
                    logmsg = "infeas";
                }
                else
                {
                    /// fix suppliers selection
                    this->fixSuppliers(*it);
                    
                    /// optimize model
                    milprog_sptr->optimize();
                    grb_status = milprog_sptr->get(GRB_IntAttr_Status);
                    opt_tm = milprog_sptr->get(GRB_DoubleAttr_Runtime);
                    all_opt = all_opt && grb_status == 2;
                    
                    if(grb_status == 2 || grb_status == 7 || grb_status == 8 || grb_status == 9 || grb_status == 10 || grb_status >= 13)
                    {
                        if((objv = milprog_sptr->get(GRB_DoubleAttr_ObjVal)) + prms.eps < best_objv)
                        {
                            best_objv = objv, logmsg += "*";
                            this->storeSuppliersSelection();
                            this->storeStage1BinSelection();
                        }
                        if((bndv = milprog_sptr->get(GRB_DoubleAttr_ObjBound)) + prms.eps < best_bndv)
                            best_bndv = bndv;
                    }
                    else
                    {
                        /// managing status not optimal or feasible
                        //best_bndv = best_objv = std::numeric_limits<double>::max();
                        //break;
                        throw std::runtime_error("Abort optimization ::: ILP solver status = " + std::to_string(grb_status) + "; " + grb_algo_str[grb_status]);
                    }
                }
                
                /// log
                if(is_verbose)
                {
                    DEBUGLOG(logDEBUG, alglog, logmsg, iter, std::to_string(grb_status), objv, bndv, opt_tm, GETOPTT(sol_tm));
                }
                
                ++iter;
            }
        }
    }
    else
    {
        milprog_sptr->optimize();
        grb_status = milprog_sptr->get(GRB_IntAttr_Status);
        
        if(grb_status == 2 || grb_status == 9)
        {
            best_objv = milprog_sptr->get(GRB_DoubleAttr_ObjVal);
            best_bndv = milprog_sptr->get(GRB_DoubleAttr_ObjBound);
            
            /// store solution
            this->storeSuppliersSelection();
            this->storeStage1BinSelection();
        }
    }
    
    /// export lp
    //milprog_sptr->write("prog.lp");
    /// analyze infeasibility
    //if(grb_status == 3) milprog_sptr->computeIIS(), milprog_sptr->write("prog.ilp");
    
    /// store values
    optresult optres;
    optres.tags.resize(3, "");
    //optres.tags[2] = grb_status >= 0 ? (grb_status == 2 ? "Optimal" : "NotOptimal_" + std::string(grb_algo_str[grb_status])) : "StatusValueLT0";
    
    /// base values
    optres.best_bound = best_bndv;
    optres.obj_value = best_objv;
    optres.run_time = GETOPTT(sol_tm);
    if(bforce)
    {
        optres.tags[2] = all_opt ? "Optimal" : "NotOptimal_" + std::string(grb_algo_str[grb_status]);
        /// get opt gap
        optres.opt_gap = all_opt ? 0.0 : (best_objv - best_bndv) / best_bndv;
        /// integer values
        optres.integer_values.resize(1, 0);
        optres.integer_values[0] = all_opt ? 2 : 9;
    }
    else
    {
        optres.tags[2] = grb_status == 2 ? "Optimal" : "NotOptimal_" + std::string(grb_algo_str[grb_status]);
        /// get opt gap
        if(grb_status == 2 || grb_status == 9)
            optres.opt_gap = milprog_sptr->get(GRB_DoubleAttr_MIPGap);
        /// integer values
        optres.integer_values.resize(1, 0);
        optres.integer_values[0] = grb_status;
    }
    /// set supl sel
    optres.integer_values.resize(2, 0);
    for(IdCounterType m = 0; m < supplsel.size(); m++)
    {
        if(supplsel[m])
            optres.integer_values[1]++;
    }
    //optres.integer_values[3] = milprog_sptr->get(GRB_DoubleAttr_IterCount);
    //optres.integer_values[0] = model.get(??);
    //optres.integer_values[1] = model.get(??);
    /// double values
    /*
    optres.double_values.resize(cmp_times.size(), std::numeric_limits<double>::quiet_NaN());
    optres.double_values.assign(cmp_times.begin(), cmp_times.end());
    optres.double_values.push_back(std::numeric_limits<double>::quiet_NaN());
     * */
    //optres.double_values[1] = (end1 - start1);
    //optres.double_values[2] = (end2 - start2);
    
    
    return optres;
}


IdCounterType CpsspILP::getSuppliersSelection(std::vector<bool>& supplsel_arg)
{
    return (supplsel_arg = supplsel).size();
}

IdCounterType CpsspILP::storeSuppliersSelection()
{
    /// ret. value
    IdCounterType retv = 0;
    /// init sol data
    supplsel = std::vector<bool>(W_sptr->size());
    /// store value
    for(IdCounterType m = 0; m < W_sptr->size(); m++)
    {
        if((supplsel[m] = bool(std::round((*W_sptr)[m].get(GRB_DoubleAttr_X)))))
            ++retv;
    }
    
    return retv;
}

IdCounterType CpsspILP::getStage1BinSelection(std::vector<bool>& binsel_arg)
{
    return (binsel_arg = binsel).size();
}

IdCounterType CpsspILP::storeStage1BinSelection()
{
    /// ret. value
    IdCounterType retv = 0;
    /// init sol data
    binsel = std::vector<bool>((*Y_sptr)[0].size());
    /// store value
    for(IdCounterType j = 0; j < (*Y_sptr)[0].size(); j++)
    {
        if((binsel[j] = bool(std::round((*Y_sptr)[0][j].get(GRB_DoubleAttr_X)))))
            ++retv;
    }
    
    return retv;
}

IdCounterType CpsspILP::storeItemPacking()
{
    /// ret. value
    IdCounterType retv = 0;
    /// init sol data
    itempack = std::vector<std::vector<IdCounterType>>((*X_sptr).size(), std::vector<IdCounterType>());
    
    /// store values
    for(IdCounterType s = 0; s < itempack.size(); s++)
    {
        itempack[s].resize((*X_sptr)[s].size(), false);
        
        for(IdCounterType i = 0; i < itempack[s].size(); i++)
        {
            if((itempack[s][i] = std::round((*X_sptr)[s][i].get(GRB_DoubleAttr_X))))
                ++retv;
        }
    }
    
    return retv;
}

void CpsspILP::writeSolToFile(std::string fname)
{
    std::ofstream ofs(fname);

    /// throw exception if not able to open the file
    if(!ofs) 
        throw std::runtime_error("File creation fails");
    
    if(!ofs.is_open())
        throw std::runtime_error("File not found");
    /// 
    IdCounterType slc = 0;
    for(IdCounterType m = 0; m < supplsel.size(); m++)
    {
        if(supplsel[m])
            ++slc;
    }
    ofs << slc << " " << supplsel.size() << std::endl;
    
    /// write
    for(IdCounterType m = 0; m < supplsel.size(); m++)
    {
        if(supplsel[m])
            ofs << m << " " << inst_sptr->supplier_contract_costs[m] << std::endl;
    }
    ///
    IdCounterType bnc = 0;
    for(IdCounterType j = 0; j < binsel.size(); j++)
        if(binsel[j])
            ++bnc;
    ofs << bnc << std::endl;
        
    for(IdCounterType j = 0; j < binsel.size(); j++)
    {
        if(binsel[j])
        {
            ofs << j 
                << " " << inst_sptr->stage1_bin_suppliers[j]
                << " " << inst_sptr->stage1_bin_types[j]
                << " " << inst_sptr->type_bin_volumes[inst_sptr->stage1_bin_types[j]]
                << " " << inst_sptr->type_suppl_bin_costs[inst_sptr->stage1_bin_types[j] * supplsel.size() + inst_sptr->stage1_bin_suppliers[j]] 
                << std::endl;
        }
    }
    ///
    ofs << inst_sptr->scenarios[0].nr_of_items << " " 
        << inst_sptr->scenarios[0].item_min_max_volume.first << " " 
        << inst_sptr->scenarios[0].item_min_max_volume.second << std::endl;
    ///
    for(IdCounterType s = 0; s < itempack.size(); s++)
    {
        for(IdCounterType e = 0; e < itempack[s].size(); e++)
            if(itempack[s][e])
            {
                ofs << itempack[s][e] << " " 
                    #ifdef X_IS_01
                    << e / binsel.size() << " " 
                    #else
                    << e / binsel.size() + 1 << " " 
                    #endif
                    << e % binsel.size() << std::endl;
            }
    }
        
    ofs.close();
}
