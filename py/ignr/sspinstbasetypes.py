"""
    Instance generator of the supplier selection problem
"""

import sys
import random
import copy as cp


RNDINTLB=999999
RNDINTUB=999999999


# 
class ItemType:
    def __init__(self, vlb, vub, pv=0.0):
        self._volume_LB = int(vlb)
        self._volume_UB = int(vub)
        self._prob = float(pv)
    
    def getProb(self):
        return cp.copy(self._prob)
    
    def setProb(self, pv):
        self._prob = float(pv)

    def getVolLB(self):
        return cp.copy(self._volume_LB)
    
    def getVolUB(self):
        return cp.copy(self._volume_UB)

    def __str__(self):
        return str(self._volume_LB) + "," + str(self._volume_UB) + "," + str(self._prob)

class Item:
    def __init__(self, vol):
        self._volume = int(vol)
    
    def getVolume(self):
        return cp.copy(self._volume)
    
    def __str__(self):
        return str(self._volume)

class BinType:
    def __init__(self, vol, d_inf, d_sup, loss_t, loss_p, ovra_loss_prc):
        self._volume = int(vol)
        self._2nd_stage_inf = float(d_inf)
        self._2nd_stage_sup = float(d_sup)
        self._loss_type = str(loss_t)
        self._loss_prob = float(loss_p)
        self._loss_perc = float(ovra_loss_prc)

    def get2ndStageInf(self):
        return cp.copy(self._2nd_stage_inf)

    def get2ndStageSup(self):
        return cp.copy(self._2nd_stage_sup)
    
    def setProb(self, pv):
        self._prob = float(pv)
    
    def getVolume(self):
        return cp.copy(self._volume)

    def getLossType(self):
        return cp.copy(self._loss_type)
    
    def getTypeLossProb(self):
        return cp.copy(self._loss_prob)

    def getLossPerc(self):
        return cp.copy(self._loss_perc)

    def __str__(self):
        string = str(self._volume)
        string += "," + str(self._2nd_stage_inf)
        string += "," + str(self._2nd_stage_sup)
        string += "," + str(self._loss_type)
        string += "," + str(self._loss_prob)
        string += "," + str(self._loss_perc)
        
        return string

class Bin:
    def __init__(self, btype, vol, cost, lsscost, cmpcost):
        self.bin_type = btype
        self.volume = vol
        self.cost = cost
        self.loss_cost= lsscost
        self.compensation_cost = cmpcost


class Supplier:
    def __init__(self, theta, fcost, fbin_costs, loss_costs, comp_costs):
        self.theta = theta 
        self.contract_cost = fcost
        self.bin_cost_x_type = fbin_costs
        self.loss_cost_x_type = loss_costs
        self.compensation_cost_x_type = comp_costs























