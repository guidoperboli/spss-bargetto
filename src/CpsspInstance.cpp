#include <fstream>
#include <iomanip>
#include <algorithm>

#include "CpsspInstance.h"


//#define STDAVESC

using namespace cpssp;

CpsspInstance::CpsspInstance(std::string ifn)
    : ifname(ifn)
{
    /// open file stream
    std::ifstream ifs(ifn);
    
    /// open input file
    if(!ifs.is_open())
    {
        throw std::runtime_error("File not found");
    }
    
    max_s1_bin_cost = 0.0;
    max_loss_cost = 0.0;
    
    /// read data
    ifs >> nr_of_suppliers;
    ifs >> min_nr_of_suppliers;
    ifs >> min_vol_x_supplier;
    ifs >> min_ave_reliability;
    
    /// init vectors
    supplier_thetas.resize(nr_of_suppliers, 1.0);
    supplier_contract_costs.resize(nr_of_suppliers, 0.0);
    
    /// read suppliers data
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        ifs >> supplier_thetas[m];
    
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        ifs >> supplier_contract_costs[m];
    
    /// read bin types data
    ifs >> nr_of_bin_types;
    
    /// init vectors
    type_bin_volumes.resize(nr_of_bin_types, 0);
    type_suppl_bin_costs.resize(nr_of_bin_types * nr_of_suppliers, 0.0);
    type_suppl_bin_loss_x_u_costs.resize(nr_of_bin_types * nr_of_suppliers, 0.0);
    type_suppl_bin_loss_x_u_comps.resize(nr_of_bin_types * nr_of_suppliers, 0.0);
    
    /// read bin volumes
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        ifs >> type_bin_volumes[t];
    
    /// read bin costs
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        {
            ifs >> type_suppl_bin_costs[t * nr_of_suppliers + m];
            max_s1_bin_cost = std::max(max_s1_bin_cost, type_suppl_bin_costs[t * nr_of_suppliers + m]);
        }
    
    /// read bin loss costs
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
            ifs >> type_suppl_bin_loss_x_u_costs[t * nr_of_suppliers + m];
    
    /// read bin loss comps
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        {
            ifs >> type_suppl_bin_loss_x_u_comps[t * nr_of_suppliers + m];
            max_loss_cost = std::max(max_loss_cost, type_bin_volumes[t] * (type_suppl_bin_loss_x_u_costs[t * nr_of_suppliers + m] - type_suppl_bin_loss_x_u_comps[t * nr_of_suppliers + m]));
        }
    
    /// read 1st stage bin count
    ifs >> nr_of_stage1_bins;
    stage1_bin_types.resize(nr_of_stage1_bins, 0);
    stage1_bin_suppliers.resize(nr_of_stage1_bins, 0);
    
    /// read 1st stage bin types
    for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
        ifs >> stage1_bin_types[k];
    
    for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
        ifs >> stage1_bin_suppliers[k];
    
    /// read nr of scenario
    ifs >> nr_of_scenarios;
    scenarios.resize(nr_of_scenarios);
    
    /// read scenarios
    max_nr_of_scenario_items = 0;
    max_nr_of_stage2_bins = 0;
    max_vol_of_scenario = 0;

    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
    {
        /// read 1st stage bin volumes
        scenarios[s].stage1_bin_volumes.resize(nr_of_stage1_bins, 0);
        scenarios[s].stage2_bin_costs.resize(nr_of_bin_types * nr_of_suppliers, 0.0);
        
        for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
            ifs >> scenarios[s].stage1_bin_volumes[k];
        
        /// read 2nd stage bins count
        ifs >> scenarios[s].nr_of_stage2_bins;
        
        max_nr_of_stage2_bins = std::max(max_nr_of_stage2_bins, scenarios[s].nr_of_stage2_bins);
        scenarios[s].stage2_bin_types.resize(scenarios[s].nr_of_stage2_bins, 0);
        //scenarios[s].stage2_bin_volumes.resize(scenarios[s].nr_of_stage2_bins, 0.0);
        scenarios[s].stage2_bin_suppliers.resize(scenarios[s].nr_of_stage2_bins, 0);
        
        /// read 2nd stage bin costs
        for(IdCounterType t = 0; t < nr_of_bin_types; t++)
            for(IdCounterType m = 0; m < nr_of_suppliers; m++)
                ifs >> scenarios[s].stage2_bin_costs[t * nr_of_suppliers + m];
        
        /// read 2nd stage bin types
        for(IdCounterType l = 0; l < scenarios[s].nr_of_stage2_bins; l++)
            ifs >> scenarios[s].stage2_bin_types[l];
        
        /// read 2nd stage bin suppliers
        for(IdCounterType l = 0; l < scenarios[s].nr_of_stage2_bins; l++)
            ifs >> scenarios[s].stage2_bin_suppliers[l];
        
        /// read 2nd stage bin volumes
        //for(IdCounterType l = 0; l < scenarios[s].nr_of_stage2_bins; l++)
        //    ifs >> scenarios[s].stage2_bin_volumes[l];
        
        /// read nr of items
        ifs >> scenarios[s].nr_of_items;
        
        max_nr_of_scenario_items = std::max(max_nr_of_scenario_items, scenarios[s].nr_of_items);
        scenarios[s].tot_volume_of_items = 0;
        scenarios[s].item_volumes.resize(scenarios[s].nr_of_items, 0);
        /// read item volumes
        scenarios[s].item_min_max_volume.first  = std::numeric_limits<VolumeType>::max();
        scenarios[s].item_min_max_volume.second = 0;
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
        {
            ifs >> scenarios[s].item_volumes[i];
            
            scenarios[s].tot_volume_of_items += scenarios[s].item_volumes[i];
            scenarios[s].item_min_max_volume.first = std::min(scenarios[s].item_min_max_volume.first, scenarios[s].item_volumes[i]);
            scenarios[s].item_min_max_volume.second = std::max(scenarios[s].item_min_max_volume.second, scenarios[s].item_volumes[i]);
        }
        
        max_vol_of_scenario = std::max(max_vol_of_scenario, scenarios[s].tot_volume_of_items);
        
        /// set prob
        scenarios[s].prob = 1.0 / nr_of_scenarios;
        
        /// pop v freq
        scenarios[s].item_sizes_freq = std::vector<IdCounterType>(1 + scenarios[s].item_min_max_volume.second, 0);
        
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
            scenarios[s].item_sizes_freq[scenarios[s].item_volumes[i]] += 1;
            
        /// compute express delivery cost
        scenarios[s].exp_delivery_cost = 0.0;
        for(IdCounterType c = 0; c < scenarios[s].stage2_bin_costs.size(); c++)
            scenarios[s].exp_delivery_cost = std::max(scenarios[s].exp_delivery_cost, scenarios[s].stage2_bin_costs[c]);
        
        scenarios[s].exp_delivery_cost = std::max(scenarios[s].exp_delivery_cost, max_s1_bin_cost + max_loss_cost);
    }
    
    /// close file
    ifs.close();    

    /// copute ave. scenario
    this->computeAveScenario();
}

void CpsspInstance::improveFeasibilty()
{
    IdCounterType bigger_bin_type = type_bin_volumes.size() - 1;
    VolumeType max_vol = 0;
    
    for(IdCounterType t = 0; t < type_bin_volumes.size(); t++)
        if(type_bin_volumes[t] > max_vol)
            bigger_bin_type = t, max_vol = t;
    
    nr_of_stage1_bins += nr_of_suppliers;
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
    {
        stage1_bin_types.push_back(bigger_bin_type);
        stage1_bin_suppliers.push_back(m);
    }
}

void CpsspInstance::computePriorities()
{
    /// supplier priorities
    std::vector<std::pair<IdCounterType, double>> supplratio(nr_of_suppliers);
    
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        supplratio.push_back(std::make_pair(m, supplier_contract_costs[m])); ///  div. supplier_thetas[m]
    
    std::sort(supplratio.begin(), supplratio.end(), [](const auto& a, const auto& b) { return a.second > b.second; } );
    
    supplier_priority.resize(nr_of_suppliers);
    
    for(IdCounterType p = 0; p < supplratio.size(); p++)
        supplier_priority[supplratio[p].first] = p;
    
    /// s1 bin priorities
    std::vector<std::pair<IdCounterType, double>> cvratio(nr_of_stage1_bins);
    
    for(IdCounterType u = 0; u < nr_of_stage1_bins; u++)
    {
        IdCounterType btype = stage1_bin_types[u];
        IdCounterType bsuppl = stage1_bin_suppliers[u];
        
        cvratio[u] = std::make_pair(u, double(type_suppl_bin_costs[btype * nr_of_suppliers + bsuppl]) / type_bin_volumes[btype]);
    }
    std::sort(cvratio.begin(), cvratio.end(), [](const auto& a, const auto& b) { return a.second > b.second ; } );
    
    stage1_bin_priority.resize(nr_of_stage1_bins);
    
    for(IdCounterType p = 0; p < cvratio.size(); p++)
        stage1_bin_priority[cvratio[p].first] = p;
        ///  prioirties of scenario bins
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        this->computeScenarioPriorities(scenarios[s]);
    
    /// compute priorities of ave. scenario
    this->computeScenarioPriorities(ave_scenario);
}


CpsspInstance::~CpsspInstance()
{
}

void CpsspInstance::computeScenarioPriorities(Scenario& sc)
{
    std::vector<std::pair<IdCounterType, double>> cvratio(sc.nr_of_stage2_bins);
    
    for(IdCounterType u = 0; u < sc.nr_of_stage2_bins; u++)
    {
        IdCounterType btype = sc.stage2_bin_types[u];
        IdCounterType bsuppl = sc.stage2_bin_suppliers[u];
        
        cvratio[u] = std::make_pair(u, double(sc.stage2_bin_costs[btype * nr_of_suppliers + bsuppl]) / type_bin_volumes[btype]);
    }
    std::sort(cvratio.begin(), cvratio.end(), [](const auto& a, const auto& b) { return a.second > b.second ; } );
    
    sc.stage2_bin_priority.resize(sc.nr_of_stage2_bins);
    
    for(IdCounterType p = 0; p < cvratio.size(); p++)
        sc.stage2_bin_priority[cvratio[p].first] = p;
}

void CpsspInstance::computeAveScenario()
{
    /// logging
    //FILE_LOG(logDEBUG) << "Compute average scenario ...";
    
    /// local vars
    std::vector<double> s1_bin_ave_vols;
    std::vector<double> ave_frac_type_suppl(nr_of_bin_types * nr_of_suppliers, 0.0);
    std::vector<double> ave_bin_x_type(nr_of_bin_types, 0);
    std::vector<double> ave_bin_x_suppl(nr_of_suppliers, 0);
    double tmp_ave_stage2_bins = 0.0;
    
    /// init. scenario prob
    ave_scenario.prob = 1.0;
    
    /// compute average bin vol.
    s1_bin_ave_vols.resize(nr_of_stage1_bins, 0);
    
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        for(IdCounterType j = 0; j < nr_of_stage1_bins; j++)
            s1_bin_ave_vols[j] += scenarios[s].prob * double(scenarios[s].stage1_bin_volumes[j]);
    
    ave_scenario.stage1_bin_volumes.resize(nr_of_stage1_bins, 0);
    for(IdCounterType j = 0; j < nr_of_stage1_bins; j++)
        ave_scenario.stage1_bin_volumes[j] = std::round(s1_bin_ave_vols[j]);
    
    /// s2 ave bin count
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        tmp_ave_stage2_bins += scenarios[s].prob * scenarios[s].nr_of_stage2_bins;
    ave_scenario.nr_of_stage2_bins = std::round(tmp_ave_stage2_bins);
    
    /// 2nd stage bin costs
    CostType ave_stage2_bin_cost = 0.0;
    CostType ave_tmp_cost = 0.0;
    ave_scenario.exp_delivery_cost = 0.0;
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
    {
        /// ave. express delivery cost
        ave_scenario.exp_delivery_cost += scenarios[s].prob * scenarios[s].exp_delivery_cost;
        /// ave. S2 bin cost
        ave_tmp_cost = 0.0;
        for(IdCounterType c = 0; c < scenarios[s].stage2_bin_costs.size(); c++)
            ave_tmp_cost += scenarios[s].stage2_bin_costs[c];
        
        ave_stage2_bin_cost += scenarios[s].prob * ave_tmp_cost / double(scenarios[s].stage2_bin_costs.size());
    }
    ave_scenario.stage2_bin_costs.resize(scenarios[0].stage2_bin_costs.size(), ave_stage2_bin_cost);
    //FILE_LOG(logDEBUG) << "Ave. S2 bin cost = " << ave_stage2_bin_cost;
    
    /// ave. nr. of bins for each type and for each supplier
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
    {
        for(IdCounterType j = 0; j < scenarios[s].stage2_bin_types.size(); j++)
            ave_bin_x_type[scenarios[s].stage2_bin_types[j]] += scenarios[s].prob;
        
        for(IdCounterType j = 0; j < scenarios[s].stage2_bin_suppliers.size(); j++)
            ave_bin_x_suppl[scenarios[s].stage2_bin_suppliers[j]] += scenarios[s].prob;
    }
    
    /// pop. ave. scenario: bin types
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
    {        IdCounterType ctr = std::round(ave_bin_x_type[t]);
        
        for(IdCounterType j = 0; j < ctr && ave_scenario.stage2_bin_types.size() < ave_scenario.nr_of_stage2_bins; j++)
            ave_scenario.stage2_bin_types.push_back(t);
    }
    /// equalize count
    while(ave_scenario.stage2_bin_types.size() < ave_scenario.nr_of_stage2_bins)
        ave_scenario.stage2_bin_types.push_back(nr_of_bin_types - 1);
    
    /// pop. ave. scenario: bin supplier
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
    {
        IdCounterType ctr = std::round(ave_bin_x_suppl[m]);
        
        for(IdCounterType j = 0; j < ctr && ave_scenario.stage2_bin_suppliers.size() < ave_scenario.nr_of_stage2_bins; j++)
            ave_scenario.stage2_bin_suppliers.push_back(m);
    }
    /// equalize count
    while(ave_scenario.stage2_bin_suppliers.size() < ave_scenario.nr_of_stage2_bins)
        ave_scenario.stage2_bin_suppliers.push_back(nr_of_suppliers - 1);
    
    /*
    FILE_LOG(logDEBUG) << "Ave. s2 bins " << ave_stage2_bins 
                       << " type of bins " << ave_scenario.stage2_bin_types.size() 
                       << " suppl of bins " << ave_scenario.stage2_bin_suppliers.size();
     * */
    
    #ifdef STDAVESC
    /// alternative method
    VolumeType ave_volume;
    double ave_count = 0;
    double ave_vol = 0;
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
    {
        ave_count += scenarios[s].prob * scenarios[s].nr_of_items;
        
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
            ave_vol += scenarios[s].prob * scenarios[s].item_volumes[i] / double(scenarios[s].nr_of_items);
    }
    
    ave_scenario.nr_of_items = std::round(ave_count);
    ave_volume = std::round(ave_vol);
    
    ave_scenario.item_sizes_freq.resize(ave_volume + 1, 0);
    ave_scenario.item_sizes_freq[ave_volume] = ave_scenario.nr_of_items;
    
    for(IdCounterType i = 0; i < ave_scenario.nr_of_items; i++)
        ave_scenario.item_volumes.push_back(ave_volume);
    
    ave_scenario.tot_volume_of_items = ave_volume * ave_scenario.nr_of_items;
    ave_scenario.item_min_max_volume = std::make_pair(ave_volume, ave_volume);
    #else
    /// compute item volumes distribution
    VolumeType max_item_vol = 0;
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
            max_item_vol = std::max(max_item_vol, scenarios[s].item_volumes[i]);
            
    std::vector<double> ave_item_volumes(max_item_vol + 1, 0);
    
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
            ave_item_volumes[scenarios[s].item_volumes[i]] += scenarios[s].prob;
            
    /// min max and freq
    ave_scenario.item_min_max_volume.first = std::numeric_limits<VolumeType>::max();
    ave_scenario.item_min_max_volume.second = 0;
    ave_scenario.item_sizes_freq.resize(ave_item_volumes.size(), 0);
    
    for(IdCounterType v = 1; v < ave_item_volumes.size(); v++)
    {
        IdCounterType ctr = std::round(ave_item_volumes[v]);
        
        for(IdCounterType i = 0; i < ctr; i++)
        {
            ave_scenario.item_volumes.push_back(v);
            ave_scenario.tot_volume_of_items += v;
            ave_scenario.item_min_max_volume.first = std::min(v, ave_scenario.item_min_max_volume.first);
            ave_scenario.item_min_max_volume.second = std::max(v, ave_scenario.item_min_max_volume.second);
            ave_scenario.item_sizes_freq[v] += 1;
        }
    }
    
    ave_scenario.nr_of_items = ave_scenario.item_volumes.size();
    #endif
    /*
    FILE_LOG(logDEBUG) << "Ave. scenario tot. volume of items " << ave_scenario.tot_volume_of_items
                       << " min vol " << ave_scenario.item_min_max_volume.first
                       << " max vol " << ave_scenario.item_min_max_volume.second
                       << " for " << ave_scenario.nr_of_items << " items";
     * */
}

IdCounterType CpsspInstance::sampleScenarios(IdCounterType sample_size)
{
    IdCounterType nr_of_samples = std::floor(double(nr_of_scenarios) / sample_size);
    std::vector<IdCounterType> sampling_vec;
    
    /// init
    sampling_vec.resize(nr_of_scenarios);
    
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
        sampling_vec[s] = s;
    /// shuffle
    std::random_shuffle(sampling_vec.begin(), sampling_vec.end());    
    
    /// pop
    scenario_sampling.resize(nr_of_samples);
    
    for(IdCounterType s = 0; s < nr_of_samples; s++)
    {
        scenario_sampling[s].resize(sample_size);
        
        for(IdCounterType e = 0; e < sample_size; e++)
            scenario_sampling[s][e] = sampling_vec.back(), sampling_vec.pop_back();
    } 
    
    return nr_of_samples;
}

void CpsspInstance::write2file(std::string ofname)
{
    /// open file stream
    std::ofstream ofs(ofname);
    
    /// open input file
    if(!ofs.is_open())
    {
        throw std::runtime_error("File not found");
    }

    /// write data
    ofs << nr_of_suppliers << std::endl;
    ofs << min_nr_of_suppliers << std::endl;
    ofs << min_vol_x_supplier << std::endl;
    ofs << min_ave_reliability << std::endl;
    
    /// write suppliers data
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        ofs << std::fixed << std::setprecision(4) << supplier_thetas[m] << (m + 1 < nr_of_suppliers ? " " : "\n");
    
    for(IdCounterType m = 0; m < nr_of_suppliers; m++)
        ofs << std::fixed << std::setprecision(2) << supplier_contract_costs[m] << (m + 1 < nr_of_suppliers ? " " : "\n");
    
    /// write bin types data
    ofs << nr_of_bin_types << std::endl;
    
    /// write bin volumes
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        ofs << type_bin_volumes[t] << (t + 1 < nr_of_bin_types ? " " : "\n");
    ofs << std::endl;
    
    /// write bin costs
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
    {
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
            ofs << std::fixed << std::setprecision(2) << type_suppl_bin_costs[t * nr_of_suppliers + m] << (m + 1 < nr_of_suppliers ? " " : "\n");
    }
    
    /// write bin loss costs
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
    {
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
            ofs << std::fixed << std::setprecision(2) << type_suppl_bin_loss_x_u_costs[t * nr_of_suppliers + m] << (m + 1 < nr_of_suppliers ? " " : "\n");
    }
    
    /// write bin loss comps
    for(IdCounterType t = 0; t < nr_of_bin_types; t++)
    {
        for(IdCounterType m = 0; m < nr_of_suppliers; m++)
            ofs << std::fixed << std::setprecision(2) << type_suppl_bin_loss_x_u_comps[t * nr_of_suppliers + m] << (m + 1 < nr_of_suppliers ? " " : "\n");
    }
    
    /// write 1st stage bin count
    ofs << nr_of_stage1_bins << std::endl;
    
    /// write 1st stage bin types
    for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
        ofs << stage1_bin_types[k] << (k + 1 < nr_of_stage1_bins ? " " : "\n");
    
    for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
        ofs << stage1_bin_suppliers[k] << (k + 1 < nr_of_stage1_bins ? " " : "\n");
    
    /// write nr of scenario
    ofs << nr_of_scenarios << std::endl;
    
    /// write scenarios
    for(IdCounterType s = 0; s < nr_of_scenarios; s++)
    {
        /// write 1st stage bin volumes
        for(IdCounterType k = 0; k < nr_of_stage1_bins; k++)
            ofs << scenarios[s].stage1_bin_volumes[k] << (k + 1 < nr_of_stage1_bins ? " " : "\n");
        
        /// write 2nd stage bins count
        ofs << scenarios[s].nr_of_stage2_bins << std::endl;
        
        /// write 2nd stage bin costs
        for(IdCounterType t = 0; t < nr_of_bin_types; t++)
        {
            for(IdCounterType m = 0; m < nr_of_suppliers; m++)
                ofs << std::fixed << std::setprecision(2) << scenarios[s].stage2_bin_costs[t * nr_of_suppliers + m] 
                    << (t * nr_of_suppliers + m + 1 < nr_of_bin_types * nr_of_suppliers ? " " : "\n");
        }
        
        /// write 2nd stage bin types
        for(IdCounterType l = 0; l < scenarios[s].nr_of_stage2_bins; l++)
            ofs << scenarios[s].stage2_bin_types[l] << (l + 1 < scenarios[s].nr_of_stage2_bins ? " " : "\n");
        
        /// write 2nd stage bin suppliers
        for(IdCounterType l = 0; l < scenarios[s].nr_of_stage2_bins; l++)
            ofs << scenarios[s].stage2_bin_suppliers[l] << (l + 1 < scenarios[s].nr_of_stage2_bins ? " " : "\n");

        /// write nr of items
        ofs << scenarios[s].nr_of_items << std::endl;
        
        /// write item volumes
        for(IdCounterType i = 0; i < scenarios[s].nr_of_items; i++)
            ofs << scenarios[s].item_volumes[i] << (i + 1 < scenarios[s].nr_of_items ? " " : "\n");
    }
    
    ofs.close();
}


std::vector<std::pair<std::string,std::string>> CpsspInstance::getBasicInfo()
{
    std::vector<std::pair<std::string,std::string>> retv;
    retv.push_back(std::make_pair(std::to_string(nr_of_suppliers), "suppliers"));
    retv.push_back(std::make_pair(std::to_string(nr_of_stage1_bins), "1st stage bins"));
    retv.push_back(std::make_pair(std::to_string(nr_of_scenarios), "scenarios"));
    retv.push_back(std::make_pair(std::to_string(max_nr_of_stage2_bins), "max 2nd stage bins"));
    retv.push_back(std::make_pair(std::to_string(max_nr_of_scenario_items), "max scenario items"));
    retv.push_back(std::make_pair(std::to_string(max_vol_of_scenario), "max scenario volume of items"));
    retv.push_back(std::make_pair(std::to_string(max_s1_bin_cost), "max 1st stage bin cost"));
    retv.push_back(std::make_pair(std::to_string(max_loss_cost), "max 1st stage bin loss cost"));
    retv.push_back(std::make_pair(std::to_string(ave_scenario.exp_delivery_cost), "ave express delivery cost"));
    
    return retv;
}