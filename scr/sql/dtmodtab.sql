copy
(
select
    suppliers,
    minsuppl,
    nrtems,
    
    count(case when status = 'Optimal' then 1 else null end) as opt,
    
    round(avg(objf1), 1) as ub, 
    round(avg(objf2), 1) as lb,
    
    round(100.0 * avg(case when status != 'Optimal' then optgap else null end), 2) as optgap,
    round(avg(case when status = 'Optimal' then time1sec else null end) , 1) as opttime

from :tab
group by suppliers, minsuppl, nrtems
order by suppliers, minsuppl, nrtems
)
to stdout with delimiter ';' header csv;

