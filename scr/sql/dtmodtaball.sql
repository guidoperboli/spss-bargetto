select
    suppliers,
    minsuppl,
    nrtems,
    
    status as opt,
    
    objf1  as ub, 
    objf2 as lb,
    
    round(100.0 * optgap, 2) as optgap,
    round(time1sec, 1) as opttime

from :tab
order by suppliers, minsuppl, nrtems

