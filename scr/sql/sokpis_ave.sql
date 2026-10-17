copy
(
    with kpidata as
    (
        select
            --instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems as inst,
            instttag,
            suppliers,
            minitems,
            maxitems,
            algmode,
            scenarios, 
            rep,
            
            ev,
            isevopt,
            (evof - evbb) / evbb as evgap,

            ws,
            iswsopt,
            (wsof - wsbb) / wsbb as wsgap,

            rp,        
            israbopt,
            (raof - objf1) / objf1 as rpgap,
            
            eev,
            iseevopt,
            (eevof - eevbb) / eevbb as eevgap,


            round((rp - ws) / ws, 4) as evpi,
            round((eev - rp) / rp, 4) as vss,

            raof as modelof,
            raogap as modelgap,
                        
            -- objf1,
            objf2 as intsolofeval,
            optgap as intsolevalgap
            
        from :tab
        order by 
            instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems,
            algmode,
            scenarios,
            rep
    )
select
    algmode,
    scenarios,
    instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems as inst,
    count(*) as count,
    count(case when isevopt > 0 then 1 else null end) as isevopt,
    round(avg(evgap), 4) as evgap,
    count(case when iswsopt > 0 then 1 else null end) as iswsopt,
    round(avg(wsgap), 4) as wsgap,
    count(case when israbopt > 0 then 1 else null end) as isrpopt,
    round(avg(modelgap), 4) as rpgap,
    count(case when iseevopt > 0 then 1 else null end) as iseevopt,
    round(avg(eevgap), 4) as eevgap,

    round(avg(ev), 1) as ev,
    round(avg(ws), 1) as ws,
    round(avg(rp), 1) as rp,
    round(avg(eev), 1) as eev,

    round(avg(evpi), 4) as evpi,
    round(avg(vss), 4) as vss,

    round(avg(modelof), 1) as modelof,
    round(avg(modelgap), 4) as gap,
    
    round(avg(intsolofeval), 1) as intsolofeval,
    round(avg(intsolevalgap), 4) as intsolevalgap

from kpidata
group by
    algmode,
    instttag,
    suppliers,
    minitems,
    maxitems,
    scenarios
order by 
    algmode,
    scenarios,
    minitems,
    maxitems,
    instttag
)
to stdout with delimiter ';' header csv;
























