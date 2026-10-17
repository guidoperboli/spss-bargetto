"""
    Instance generator of the supplier selection problem
"""

import sys
import random
import math
import copy as cp
from sspscenario import SSPScenario
from sspinstbasetypes import ItemType, BinType, Bin, RNDINTLB, RNDINTUB

class SSPInstance:
    # class constructor
    def __init__(self, 
                 rseed,
                 suppliers_count,
                 min_suppls,
                 min_vol_x_suppl,
                 min_avg_r,
                 supplier_r_distr,
                 scenarios_count,
                 items_count,
                 item_types,
                 bin_types,
                 alfa,
                 beta_inf_sup,
                 gamma_inf_sup,
                 zeta_inf_sup):
        # attributes
        self._rng = random.Random() 
        self._rseed = rseed
        self._rng.seed(rseed)
        self._suppliers_count = suppliers_count
        self._min_nr_suppliers = min_suppls
        self._min_vol_x_supplier = min_vol_x_suppl
        self._min_avg_reliability = min_avg_r
        self._supplier_r_distr = supplier_r_distr
        self._scenarios_count = scenarios_count
        self._items_count_min = items_count[0]
        self._items_count_max = items_count[1]
        self._item_types = item_types
        self._bin_types = bin_types
        self._alfa = alfa
        self._beta_inf = beta_inf_sup[0]
        self._beta_sup = beta_inf_sup[1]
        self._gamma_inf = gamma_inf_sup[0]
        self._gamma_sup = gamma_inf_sup[1]
        self._zeta_inf = zeta_inf_sup[0]
        self._zeta_sup = zeta_inf_sup[1]
        
        # generataed attributes
        self._thetas = []
        self._contract_costs = []
        self._bin_type_suppl_costs = []
        self._loss_type_suppl_costs = []
        self._comp_type_suppl_costs = []
        self._bxtype_count = []
        self._bt_1s_count = []
        self._scenarios = []
    
    def generate(self):
            # generate theta_m
            for m in range(self._suppliers_count):
                self._thetas.append(self._supplier_r_distr.drawReliability())
            
            if sum(self._thetas) / self._suppliers_count < self._min_avg_reliability:
                self._min_avg_reliability = max(sum(self._thetas) / self._suppliers_count - self._supplier_r_distr.getEPS(), 0.0)
            
            # generate _scenarios
            for s in range(self._scenarios_count):
                sc = SSPScenario(self._rng.randint(RNDINTLB, RNDINTUB))
                self._scenarios.append(sc)
            
            # generate items get max volume
            max_tot_item_vol = 0
            for s in range(self._scenarios_count):
                self._scenarios[s].generateItems([self._items_count_min, self._items_count_max], self._item_types)
                max_tot_item_vol = max(self._scenarios[s].getTotItemVolume(), max_tot_item_vol)
            
            #print("Max tot. volume " + str(max_tot_item_vol) + " over " + str(self._scenarios_count) + " scenarios")
            
            # generate 1st stage bins
            bt_2s_count = []

            bin_type_suppl_2nd_stage_costs = []
            bin_types_count = len(self._bin_types)
            
            for j in range(bin_types_count):
                
                V = self._bin_types[j].getVolume()
                bc = math.ceil(max_tot_item_vol / V)
                self._bxtype_count.append(bc)
                self._bin_type_suppl_costs.append( [] )
                self._loss_type_suppl_costs.append( [] )
                self._comp_type_suppl_costs.append( [] )
                
                for m in range(self._suppliers_count):
                    self._bt_1s_count.append(bc)
                    
                    gm = self._rng.uniform(self._gamma_inf, self._gamma_sup)
                    btcst = round( V * (1.0 + gm) ) # , 4
                    self._bin_type_suppl_costs[j].append( btcst )
                    self._loss_type_suppl_costs[j].append( round((1.0 + self._alfa) * (1.0 + gm), 1) ) # , 4
                    self._comp_type_suppl_costs[j].append( round(1.0 + gm, 1) ) # , 4
            
            # generate 1st stage bins
            #print("1st stage bins " + str(sum(self._bt_1s_count)))
            for s in range(self._scenarios_count):
                self._scenarios[s].generate1stStageBins(self._bin_types, 
                                                        self._bt_1s_count, 
                                                        self._suppliers_count, 
                                                        self._thetas,
                                                        self._supplier_r_distr)
            
            # generate 2nd stage bins
            for s in range(self._scenarios_count):
                self._scenarios[s].generate2ndStageBins(self._bin_types,
                                                        self._bxtype_count, 
                                                        self._suppliers_count, 
                                                        self._bin_type_suppl_costs, 
                                                        self._beta_inf, 
                                                        self._beta_sup)
            
                #print("Scenarion " + str(s) + " 2nd stage bins " + str(self._scenarios[s].get2ndStageBinCount()))
                
            # self._min_vol_x_supplier * max_tot_item_vol * x_zeta
            for m in range(self._suppliers_count):
                z = self._rng.uniform(self._zeta_inf, self._zeta_sup)
                self._contract_costs.append( round(self._min_vol_x_supplier * max_tot_item_vol * z) ) # , 4
    
    def write2file(self, ofname):
        with open(ofname, 'w') as f:
            f.write( str(self._suppliers_count) + "\n" )
            f.write( str(self._min_nr_suppliers) + "\n" )
            f.write( str(self._min_vol_x_supplier) + "\n" )
            f.write( str(self._min_avg_reliability) + "\n" )

            f.write( " ".join(format(x, ".4f") for x in self._thetas) + "\n" )
            f.write( " ".join(format(x, ".2f") for x in self._contract_costs) + "\n" )
            f.write( str(len(self._bin_types)) + "\n" )
            f.write( " ".join(format(x, "d") for x in [self._bin_types[i].getVolume() for i in range(len(self._bin_types))]) + "\n" )
            for j in range(len(self._bin_types)):
                f.write( " ".join(format(x, ".2f") for x in [self._bin_type_suppl_costs[j][m] for m in range(self._suppliers_count)]) + "\n" )
            for j in range(len(self._bin_types)):
                f.write( " ".join(format(x, ".2f") for x in [self._loss_type_suppl_costs[j][m] for m in range(self._suppliers_count)]) + "\n" )
            for j in range(len(self._bin_types)):
                f.write( " ".join(format(x, ".2f") for x in [self._comp_type_suppl_costs[j][m] for m in range(self._suppliers_count)]) + "\n" )
            f.write( str(sum(self._bt_1s_count)) + "\n" )
            btypes = []
            bsuppls = []
            for j in range(len(self._bin_types)):
                for m in range(self._suppliers_count):
                    for k in range(self._bt_1s_count[j * self._suppliers_count + m]):
                        btypes.append(j)
                        bsuppls.append(m)
            f.write( " ".join(format(x, "d") for x in btypes) + "\n" )
            f.write( " ".join(format(x, "d") for x in bsuppls) + "\n" )
            f.write( str(self._scenarios_count) + "\n" )
            
            # write scenarios
            for s in range(self._scenarios_count):
                # write 1st bins data
                binvols = self._scenarios[s].get1stStageBinVolumes()
                tow = []
                
                for j in range(len(self._bin_types)):
                    for m in range(self._suppliers_count):
                        for v in binvols[j * self._suppliers_count + m]:
                            tow.append( v )
                            
                f.write( " ".join(format(v, "d") for v in tow) + "\n" )
                
                # write 2nd stage bins data 
                bin_2s_count = self._scenarios[s].get2ndStageBinCount()
                
                f.write( str( sum(bin_2s_count) ) + "\n" )
                b2stypes = []
                b2ssuppls = []
                for j in range(len(self._bin_types)):
                    for m in range(self._suppliers_count):
                        for k in range(bin_2s_count[j * self._suppliers_count + m]):
                            b2stypes.append(j)
                            b2ssuppls.append(m)
                
                f.write( " ".join(format(c, ".2f") for c in self._scenarios[s].get2ndStageBinCost()) + "\n" )
                f.write( " ".join(format(t, "d") for t in b2stypes) + "\n" )
                f.write( " ".join(format(m, "d") for m in b2ssuppls) + "\n" )
                
                # write nr. of items
                f.write( str( self._scenarios[s].getItemCount() ) + "\n" )
                # write items volume
                f.write( " ".join(format(v.getVolume(), "d") for v in self._scenarios[s].getItems()) + "\n" )
            
    # serializer
    def __str__(self):
        return ""
        # return str(self.suppliers) + ', ' + str(self.items)




























