-- data preprocessing with sed: sed -e "s/_M/ /" -e "s/_II/ /" -e "s/_IS/ /" -e "s/_SN/ /" -e "s/_/ /g"
create table :tabname
(
    ptag                varchar(8),
    suppliers           integer,
    minitems            integer,
    maxitems            integer,
    instttag            varchar(8),
    scenarios           integer,
    rep                 integer,  
   
    alg                 varchar(16),
    algmode             integer,
    status              varchar(16),
    
    objf0               decimal,
    time0sec            decimal,
    objf1               decimal,
    objf2               decimal,
    optgap              decimal,
    time1sec            decimal,
    -- integers
    isevopt             integer, -- 0
    iseevopt            integer, -- 1
    iswsopt             integer, -- 2
    israbopt            integer, -- 3
    isintopt            integer, -- 4
    -- doubles
    ev                  decimal, -- 0
    ws                  decimal, -- 1
    rp                  decimal, -- 2
    eev                 decimal, -- 3
    
    evbb                decimal, -- 4
    evof                decimal, -- 5
    evtm                decimal, -- 6
    
    eevbb               decimal, -- 7
    eevof               decimal, -- 8
    eevtm               decimal, -- 9
    
    wsbb                decimal, -- 10
    wsof                decimal, -- 11
    wstm                decimal, -- 12
    
    raof                decimal, -- 13
    raogap              decimal, -- 14
    ratm                decimal, -- 15

    fmbb                decimal, -- 16
    fmogap              decimal, -- 17
    fmtm                decimal  -- 18
);
