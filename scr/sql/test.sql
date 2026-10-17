copy
(
    select
        instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems as inst,
        scenarios, 
        rep,
        
        evobjf,
        isevopt,

        eevobjf,
        iseevopt,

        wsobjf,
        iswsopt,

        rabobjf,        
        israbopt,
        
        intobjf,
        isintopt
        
    from :tab
    where evobjf > wsobjf
    order by 
        instttag ||'-' || suppliers || '-' || minitems || '-' || maxitems,
        scenarios,
        rep
)
to stdout with delimiter ';' header csv;
