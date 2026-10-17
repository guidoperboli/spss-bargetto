-- sed -i -e "s/_GRB_TIME_LIMIT//g" -e "s/M//" -e "s/_SN1//" -e "s/_/ /g" -e 's/[[:space:]]*$//'
create table :tabname
(
    ptag                varchar(8),
    suppliers           integer,
    minsuppl            integer,
    nrtems              integer,
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
    grbexitcode         integer
);
