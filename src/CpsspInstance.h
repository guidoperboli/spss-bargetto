#ifndef CPSSPINST_H
#define CPSSPINST_H

#include <string>
#include <memory>
#include <vector>
#include <limits>
#include <cmath>
#include <iostream>

namespace cpssp
{
    typedef unsigned int IdCounterType;
    typedef int IdCounterTypeSGND;
    typedef unsigned int VolumeType;

    typedef double CostType;
    //typedef double PriorityType;

    struct Scenario
    {
        double prob;
        
        /// 1st stage bins' volume
        std::vector<VolumeType> stage1_bin_volumes;
        /// 2nd stage bins
        IdCounterType nr_of_stage2_bins;
        std::vector<CostType> stage2_bin_costs;
        std::vector<IdCounterType> stage2_bin_types;
        std::vector<IdCounterType> stage2_bin_suppliers;
        //std::vector<VolumeType> stage2_bin_volumes;
        /// items' data
        IdCounterType nr_of_items;
        VolumeType tot_volume_of_items;
        std::vector<VolumeType> item_volumes;
        
        CostType exp_delivery_cost = 0.0;
        
        std::vector<IdCounterType> item_sizes_freq;
        std::pair<VolumeType,VolumeType> item_min_max_volume;

        /// priorities
        std::vector<IdCounterType> stage2_bin_priority;
    };

    struct CpsspInstance
    {
        /// loaded instance file name
        std::string ifname;
        
        /// instance statistics
        IdCounterType   max_nr_of_scenario_items;
        IdCounterType   max_nr_of_stage2_bins;
        VolumeType      max_vol_of_scenario;
        CostType        max_s1_bin_cost;
        CostType        max_loss_cost;
        
        /// problem data
        IdCounterType nr_of_suppliers;
        IdCounterType nr_of_bin_types;
        
        IdCounterType min_nr_of_suppliers;
        IdCounterType nr_of_stage1_bins;
        
        double min_vol_x_supplier;
        double min_ave_reliability;
        
        /// suppliers' data
        std::vector<double> supplier_thetas;
        std::vector<CostType> supplier_contract_costs;
        
        /// bin types' data
        std::vector<VolumeType> type_bin_volumes;
        std::vector<CostType> type_suppl_bin_costs;
        std::vector<CostType> type_suppl_bin_loss_x_u_costs;
        std::vector<CostType> type_suppl_bin_loss_x_u_comps;
        
        /// 1st stage bins' data
        std::vector<IdCounterType> stage1_bin_types;
        std::vector<IdCounterType> stage1_bin_suppliers;
        
        /// scenarios' data
        IdCounterType nr_of_scenarios;
        std::vector<Scenario> scenarios;
        std::vector<std::vector<IdCounterType>> scenario_sampling;
        Scenario ave_scenario;
        
        /// priorities
        std::vector<IdCounterType> supplier_priority;
        std::vector<IdCounterType> stage1_bin_priority;
        
        /// Cpssp instance
        CpsspInstance(std::string);
        ~CpsspInstance();

        std::vector<std::pair<std::string,std::string>> getBasicInfo();
        
        
        void computePriorities();
        void computeScenarioPriorities(Scenario&);
        void computeAveScenario();
        void improveFeasibilty();
        
        IdCounterType sampleScenarios(IdCounterType);
        
        inline std::string getBaseName()
        {
            std::string rets = ifname.substr(0, ifname.find_last_of("."));
            return rets.substr(rets.find_last_of("/") + 1);
        }
        
        inline void readSol(std::string)
        {
        }
        
        void write2file(std::string);
    };
}

#endif

