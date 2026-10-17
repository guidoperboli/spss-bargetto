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
    samples             integer, -- 0
    insamplescs         integer, -- 1
    
    rpave               decimal, -- 0
    rpstdv              decimal, -- 1
    rpmin               decimal, -- 2
    rpmax               decimal,  -- 3
    
    dum1                decimal, -- 4
    dum2                decimal  -- 5
);
