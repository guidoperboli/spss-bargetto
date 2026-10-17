copy
(
select
    suppliers,
    minsuppl,
    --nrtems,
    
    round(avg(dmsuplshare), 0) as dmsuplshare,
    round(avg(dmsuplhh), 2) as dmsuplhh,
    round(avg(usedbtypes), 1) as usedbtypes,
    round(avg(dmbtypeshare), 0) as dmbtypeshare,
    
    count(case when dmisuplisdmbin then 1 else null end) as dmisuplisdmbin

from :tab
where nrtems=1000
group by suppliers, minsuppl, nrtems
order by suppliers, minsuppl, nrtems
)
to stdout with delimiter ';' header csv;

