#ifndef CPSSPILP_H
#define CPSSPILP_H


#include "utility/optresult.h"

#include "CpsspInstance.h"
#include "gurobi_c++.h"


#define CPX_PRSLV true

namespace cpssp
{
    class CpsspILP 
    {
    public:
        /// Model types
        /// - Full deterministic equivalent (FDE) model
        /// - Single scenario model
        /// - Row aggregation LB of FDE model
        /// - Row aggregation LB of single scenario model
        enum ProgType
        {
            FDE,
            //SSFDE,
            //RALBFDE,
            //RASSFDE
            RALB
        };
        
        struct ILPparams
        {
            /// prog. info
            ProgType progtype = FDE;
            unsigned int algo = 0;
            
            /// GRB stuff
            bool presolve = true;
            bool priorities = false;
            
            int sifting = -1;
            int threads2use = 1;
            int lp_algo = -1;
            
            double maxmem = 1;
            double timelimsec = 0;
            double timelim2sec = 0;
            
            double eps = 1.0e-9;
        };
        
        CpsspILP(const std::shared_ptr<CpsspInstance>&, 
                 const std::shared_ptr<GRBEnv>&,
                 const ILPparams& ilpprms,
                 bool determ);
        CpsspILP(const std::shared_ptr<CpsspInstance>&, 
                 const std::shared_ptr<GRBEnv>&,
                 const ILPparams& ilpprms,
                 std::vector<cpssp::IdCounterType> scenario_ids = std::vector<cpssp::IdCounterType>());
        ~CpsspILP();
        
        //void buildProg();
        void fixSuppliers(const std::vector<bool>&);
        void fixStage1Bins(const std::vector<bool>&);
        void resetSuppliers();
        void resetStage1Bins();
        optresult feasibleProg(const ILPparams&);
        optresult solveProg(const ILPparams&);
        
        IdCounterType getSuppliersSelection(std::vector<bool>&);
        IdCounterType getStage1BinSelection(std::vector<bool>&);

        IdCounterType storeSuppliersSelection();
        IdCounterType storeStage1BinSelection();
        IdCounterType storeItemPacking();
        
        void writeSolToFile(std::string fname);
        
        void verbose();
        void silent();
        
    private:
        bool is_verbose = false;
    
        /// pointer to problem data
        const std::shared_ptr<CpsspInstance> inst_sptr;
        /// solution params
        ILPparams ilpprms;
        
        /// solver stuff
        std::shared_ptr<GRBEnv> grbenv_sptr;
        std::shared_ptr<GRBModel> milprog_sptr;
        std::shared_ptr<std::vector<std::vector<GRBVar>>> X_sptr;
        std::shared_ptr<std::vector<std::vector<GRBVar>>> E_sptr;
        std::shared_ptr<std::vector<std::vector<GRBVar>>> Y_sptr;
        std::shared_ptr<std::vector<GRBVar>> W_sptr;
        //std::shared_ptr<std::vector<std::vector<GRBVar>>> Z_sptr;
        
        std::vector<bool> supplsel;
        std::vector<bool> binsel;
        std::vector<std::vector<IdCounterType>> itempack;
        
        bool is_X_sol_fixed = false;
        bool is_Y_sol_fixed = false;
        bool is_W_sol_fixed = false;
    };
    
    inline std::string toString(CpsspILP::ProgType pt)
    {
        if(pt == CpsspILP::ProgType::FDE)
            return "FDE";
        else if(pt == CpsspILP::ProgType::RALB)
            return "RALB";
        else
            return "unknown";    }
}

#endif // CPSSPILP
