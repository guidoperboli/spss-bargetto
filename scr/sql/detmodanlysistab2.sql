copy
(
select
    suppliers,
    minsuppl,
    -- nrtems,
    
    round(avg(dmbtypevol), 1) as dmbtypevol,
    round(avg(itemsindmbins), 0) as itemsindmbins,
    round(avg(dmbtypehh), 2) as dmbtypehh

from :tab
where nrtems=1000
group by suppliers, minsuppl, nrtems
order by suppliers, minsuppl, nrtems
)
to stdout with delimiter ';' header csv;

