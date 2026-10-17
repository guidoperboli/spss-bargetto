#ifndef CPSSPALGOILP_H
#define CPSSPALGOILP_H

#include "utility/optresult.h"

#include "CpsspILP.h"


namespace cpssp
{
    class CpsspAlgoILP
    {
    public:
        CpsspAlgoILP(const std::shared_ptr<CpsspInstance>&, const CpsspILP::ILPparams&);
        ~CpsspAlgoILP();
    
        optresult testProgFeasibility(const CpsspILP::ILPparams&);
        optresult solveProg(const CpsspILP::ILPparams&);
        optresult solveProgDeterm(const CpsspILP::ILPparams&);
        optresult computeRootEvaluation(const CpsspILP::ILPparams&);
        optresult computeFullProblemUBerr(const CpsspILP::ILPparams&, IdCounterType sample_size);
        optresult computeFullProblemUB(const CpsspILP::ILPparams&,
                                       std::vector<cpssp::IdCounterType> scenario_ids = std::vector<cpssp::IdCounterType>(),
                                       bool sc_eval = true);
        optresult computeAveScenarioBound(const CpsspILP::ILPparams&);
        optresult computePerfectInfoBound(const CpsspILP::ILPparams&);
        optresult evaluateScenarios(const CpsspILP::ILPparams&,
                                    const std::shared_ptr<std::vector<bool>>& = nullptr, 
                                    const std::shared_ptr<std::vector<bool>>& = nullptr,
                                    bool all_must_be_opt = false);
    
        void verbose();
        void silent();
        
    private:
        /// algorithm data
        bool is_verbose = false;
        bool runsilent = false;
        
        std::shared_ptr<GRBEnv> grbenv_sptr;
        
        /// pointer to problem data
        const std::shared_ptr<CpsspInstance> inst_sptr;
        /// ilp params
        CpsspILP::ILPparams ilpprms;
    };
}


#endif // CPSSPALGOILP_H