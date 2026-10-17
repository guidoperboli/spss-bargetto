
import sys
import random
import math
from sspscenario import SupplierReliabDistr
from sspinstance import SSPInstance
from sspscenario import SSPScenario
from sspinstbasetypes import ItemType, BinType, Bin, RNDINTLB, RNDINTUB


class SSPInstGenerator:
    #class constructor
    def __init__(self, dfname, rseed, ic, sc, ofn):
        # root rng
        self._root_rng           = random.Random()
        self._instances          = []
        self._rootseed           = int(rseed)
        self._inst_count         = int(ic)
        self._scenarios_count    = int(sc)
        self._instag             = ofn
        self._root_rng.seed(self._rootseed)
        
        print("Read data from file " + dfname + " ...")
        with open(dfname, 'r') as f:
            # read file
            data = f.read().split()
            values = [(x) for x in data]
            
            # store values
            a = 0
            self._suppliers_count  = int(values[0])
            self._supplier_r_distr = SupplierReliabDistr(self._root_rng.randint(RNDINTLB, RNDINTUB),
                                                         float(values[1]), 
                                                         float(values[2]), 
                                                         float(values[3]))
            self._min_suppliers         = int(values[4])
            self._min_vol_x_supplier    = float(values[5])
            self._min_avg_supplier_R    = float(values[6])
            
            self._items_count_min       = int(values[7])
            self._items_count_max       = int(values[8])
            self._item_types_count      = int(values[9])
            self._item_types            = []
            
            # read item types
            b = 10
            for i in range(self._item_types_count):
                self._item_types.append(ItemType(int(values[b + i * 3]),
                                                 int(values[b + i * 3 + 1]),
                                                 float(values[b + i * 3 + 2])))
                a = b + i * 3 + 2
            
            s = 0.0
            for i in range(self._item_types_count):
                s += self._item_types[i].getProb()

            for i in range(self._item_types_count):
                self._item_types[i].setProb(self._item_types[i].getProb() / s)
            
            # read bin types
            a += 1
            self._bin_types_count       = int(values[a])
            self._bin_types             = []
            
            b = a + 1
            for i in range(self._bin_types_count):
                self._bin_types.append(BinType(values[b + i * 6],
                                               values[b + i * 6 + 1],
                                               values[b + i * 6 + 2],
                                               values[b + i * 6 + 3],
                                               values[b + i * 6 + 4],
                                               values[b + i * 6 + 5]))
                a = b + i * 6 + 5
                
            s = 0.0
            #for i in range(self._bin_types_count):
                #s += self._bin_types[i].getProb()
            
            #for i in range(self._bin_types_count):
                #self._bin_types[i].setProb(self._bin_types[i].getProb() / s)
            
            # read scenario data
            self._loss_scenario_prob = float(values[a + 1])
            # gap loss cost/compensation
            self._alfa               = float(values[a + 2]) 
            #self._beta_count         = int(values[a + 3])
            a += 3
            # Unif. distr. of spot rates on contract rates
            self._beta_inf           = float(values[a])
            self._beta_sup           = float(values[a + 1])
            # contract rates unfiorm distribution
            self._gamma_inf          = float(values[a + 2])
            self._gamma_sup          = float(values[a + 3])
            # contract costs unfiorm distribution
            self._zeta_inf           = float(values[a + 4])
            self._zeta_sup           = float(values[a + 5])
            
        print("Done")
        
    # to string
    def __str__(self):
        string = str(self._rootseed)
        string += "," + str(self._inst_count)
        string += "," + str(self._suppliers_count)
        string += "," + str(self._supplier_r_distr)
        string += "," + str(self._min_suppliers)
        string += "," + str(self._min_vol_x_supplier)
        string += "," + str(self._min_avg_supplier_R)
        string += "," + str(self._items_count_min)
        string += "," + str(self._items_count_max)
        string += "," + str(self._item_types_count)
        
        for i in range(self._item_types_count):
            string += "," + str(self._item_types[i])
        
        string += "," + str(self._bin_types_count)
        
        for i in range(self._bin_types_count):
            string += "," + str(self._bin_types[i])
        
        string += "," + str(self._scenarios_count)
        string += "," + str(self._loss_scenario_prob)
        string += "," + str(self._alfa)        
        string += "," + str(self._beta_inf)
        string += "," + str(self._beta_sup)
        string += "," + str(self._gamma_inf)
        string += "," + str(self._gamma_sup)
        string += "," + str(self._zeta_inf)
        string += "," + str(self._zeta_sup)
        
        return string
    
    
    def generate(self):        
        print("Generate " + str(self._inst_count) + " instances ...")
        for i in range(self._inst_count):
            # generate instance
            inst_root_seed = self._root_rng.randint(RNDINTLB, RNDINTUB)
            sspinst = SSPInstance(inst_root_seed,
                                  self._suppliers_count,
                                  self._min_suppliers,
                                  self._min_vol_x_supplier,
                                  self._min_avg_supplier_R,
                                  self._supplier_r_distr,
                                  self._scenarios_count,
                                  [self._items_count_min, self._items_count_max],
                                  self._item_types,
                                  self._bin_types,
                                  self._alfa,
                                  [self._beta_inf, self._beta_sup],
                                  [self._gamma_inf, self._gamma_sup],
                                  [self._zeta_inf, self._zeta_sup])
            
            ofname = self._instag + "_" + str(i + 1) + ".txt"
            print("Generate instance " + str(i + 1) + " with seed " + str(inst_root_seed) + " and write to file " + ofname + " ...", end = " ")
            sspinst.generate()
            
            sspinst.write2file(ofname)
            print("OK")
            #self._instances.append(sspinst)


































