copy
(
    select
        instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems as inst,
        scenarios, 
        rep,
        
        ev,
        isevopt,

        ws,
        iswsopt,

        rp,        
        israbopt,
        
        eev,
        iseevopt,

        round(100.0 * (rp - ws) / ws, 2) as evpi,
        round(100.0 * (eev - rp) / rp, 2) as vss,

        raof as modelof,
        raogap as modelgap,
                    
        -- objf1,
        objf2 as intsoleval,
        optgap as intsolevalgap

        -- fmbb, fmogap, fmtm
        
    from :tab
    order by 
        instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems,
        scenarios,
        rep
)
to stdout with delimiter ';' header csv;
