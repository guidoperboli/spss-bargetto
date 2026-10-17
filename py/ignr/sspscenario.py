"""
    SSP scenario
"""

import sys
import copy as cp
import random
import math
from sspinstbasetypes import ItemType, BinType, Item, Bin

class SupplierReliabDistr:
    def __init__(self, rseed, minth, maxth, epsv):
        self._theta_inf = float(minth)
        self._theta_sup = float(maxth)
        self._eps = float(epsv)
        self._rng = random.Random()
        self._rng.seed(rseed)
        
    def getThetaInf(self):
        return cp.copy(self._theta_inf)
    
    def getThetaSup(self):
        return cp.copy(self._theta_sup)
    
    def drawReliability(self):
        return self._rng.uniform(self._theta_inf, self._theta_sup)
    
    def perturbReliability(self, relv):
        x = relv + self._rng.uniform(-self._eps, self._eps)
        return min(1.0, max(0.0, x))

    def getEPS(self):
        return cp.copy(self._eps)

    def __str__(self):
        return str(self._theta_inf) + "," + str(self._theta_sup) + "," + str(self._eps)

class SSPScenario:
    def __init__(self, rseed):
        self._rndseed = rseed
        self._rng = random.Random()
        self._rng.seed(rseed)
        self._items = []
        self._tot_item_vol = 0
        self._bin_type_suppl_2nd_stage_costs = []
        self._bt_2s_count = []
        self._bin_type_suppl_1st_stage_volumes = []
    
    def getTotItemVolume(self):
        return cp.copy(self._tot_item_vol)
    
    def getItemCount(self):
        return len(self._items)
    
    def getItems(self):
        return self._items
    
    # generate
    def generateItems(self, item_min_max, item_types):
        # create distr
        T = len(item_types)
        distr = []
        for i in range(T):
            distr.append(item_types[i].getProb())
        
        for i in range(T - 1):
            for j in range(i + 1, T):
                distr[i] += distr[j]
        distr.append(0.0)
        
        nr_items = item_min_max[0]
        if item_min_max[0] < item_min_max[1]:
            nr_items = self._rng.randint(item_min_max[0], item_min_max[1])
        
        # generate items
        for i in range(nr_items):
            u = self._rng.uniform(1.0e-6, 1.0)
            for j in range(T):
                if distr[j] >= u > distr[j + 1]:
                    self._items.append(Item(self._rng.randint(item_types[j].getVolLB(), item_types[j].getVolUB())))
                    self._tot_item_vol += self._items[i].getVolume()
                    break
    
    def generate1stStageBins(self, bin_types, bt_1s_count, suppl_count, thetas, supplier_r_distr):
        if round(self._tot_item_vol) == 0:
            raise Exception("Null volume of items")
        
        # generate suppliers reliability
        scenario_thetas = []
        for m in range(suppl_count):
            scenario_thetas.append( supplier_r_distr.perturbReliability( thetas[m] ) )
        
        # generate suppliers loss
        scenario_loss = []
        for m in range(suppl_count):
            scenario_loss.append( max(round((1.0 - scenario_thetas[m]) * self._tot_item_vol), 0) )
        #print(scenario_loss)
        
        # generate bins volume
        T = len(bin_types)
        
        for j in range(T):
            for m in range(suppl_count):
                self._bin_type_suppl_1st_stage_volumes.append([])
                for k in range(bt_1s_count[j * suppl_count + m]):
                    self._bin_type_suppl_1st_stage_volumes[j * suppl_count + m].append( bin_types[j].getVolume() )
        
        for m in range(suppl_count):
            for j in range(T):
                tot_loss = cp.copy(scenario_loss[m])
                #print("P ::: m " + str(m) + " j " + str(j) + " :: " + str(bt_1s_count[j * suppl_count + m]) + "(" + str(bin_types[j].getVolume()) + "): " + str(tot_loss))
                
                bv = bin_types[j].getVolume()
                test = [1 for k in range(bt_1s_count[j * suppl_count + m])]
                ct = 0
                
                if bin_types[j].getLossType() == "U":
                    ct = len(test) # round(tot_loss / round(bin_types[j].getLossPerc() * bv))

                    if ct > len(test):
                        ct = len(test)                        
                    
                    for k in range(ct):
                        test[k] = 0

                    #for k in range(bt_1s_count[j * suppl_count + m]):
                        #if self._rng.uniform(0,1) <= bin_types[j].getTypeLossProb():
                            #test[k] = 0 
                            #ct += 1
                    
                    #if ct * bv < tot_loss:
                        #for k in range(bt_1s_count[j * suppl_count + m]):
                            #if test[k] == 1:
                                #test[k] = 0
                                #ct += 1
                                #if ct * bv >= tot_loss:
                                    #break
                
                elif bin_types[j].getLossType() == "L":
                    ct = round(tot_loss / bv)
                    
                    if ct > len(test):
                        ct = len(test)
                    
                    for k in range(ct):
                        test[k] = 0
                
                test.reverse()
                tot_loss_p = 0
                sbin_loss = 0
                if ct > 0:                
                    sbin_loss = round(tot_loss / ct)
                    
                    for k in range(bt_1s_count[j * suppl_count + m]):
                        v = bin_types[j].getVolume()
                        if test[k] == 0: # == 1
                            if bin_types[j].getLossType() == "U":
                                v -= min(sbin_loss, bv)
                            elif bin_types[j].getLossType() == "L":
                                v = 0
                            else:
                                raise Exception("Unknown loss type for bin")
                            
                            tot_loss_p += bv - v
                        self._bin_type_suppl_1st_stage_volumes[j * suppl_count + m][k] = v
                        
                #print("F ::: m " + str(m) + " j " + str(j) + " :: " + str(sbin_loss) + ": " + str(tot_loss_p))
        #print(self._bin_type_suppl_1st_stage_volumes)
    
    def get1stStageBinVolumes(self):
        retv = []
        return self._bin_type_suppl_1st_stage_volumes
    
    def generate2ndStageBins(self, bin_types, bxtype_count, suppl_count, bin_type_suppl_costs, beta_inf, beta_sup):
        T = len(bxtype_count)
        for j in range(T):
            for m in range(suppl_count):
                btcst = bin_type_suppl_costs[j][m]
                bt2scst = btcst * (1.0 + self._rng.uniform(beta_inf, beta_sup))
                self._bin_type_suppl_2nd_stage_costs.append( round(bt2scst) ) # , 4
                
                b2sc = self._rng.randint( math.floor(bin_types[j].get2ndStageInf() * bxtype_count[j]),
                                          math.ceil(bin_types[j].get2ndStageSup() * bxtype_count[j]) )
                self._bt_2s_count.append(b2sc)
        
        # test feasibility
        totvol = 0
        for j in range(T):
            for m in range(suppl_count):
                totvol += sum(self._bin_type_suppl_1st_stage_volumes[j * suppl_count + m])
                totvol += bin_types[j].getVolume() * self._bt_2s_count[j * suppl_count + m]
        # rise exception if infeasible
        if totvol < self._tot_item_vol:
            raise Exception("Unknown loss type for bin")
    
    def get2ndStageBinCost(self):
        return self._bin_type_suppl_2nd_stage_costs
    
    def get2ndStageBinCount(self):
        return self._bt_2s_count


    






















