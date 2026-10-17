#include <stdio.h>

#include "baseoptm/OptMaster.h"
#include "optlogger/MyLog.h"

#include "CpsspInstance.h"
#include "Optimizer.h"


int main(int argc, char **argv)
{
    /// app. arguments
    unsigned int i = 1;
    std::string ifname(argv[i++]);
    std::string cfgname(argv[i++]);
    
    /// try/catch
    try
    {
        /// data structure
        OptMaster<cpssp::CpsspInstance, cpssp::Optimizer> optmaster(ifname, cfgname);
        if(argc > 3)
            optmaster.setLogFileName("");
        
        /// run optimization
        cpssp::Optimizer myopt;
        if(argc > 4)
            myopt.setVerbose();
        optresult optres = optmaster.runOpt(myopt);
    }
    catch(std::exception& e)
    {
        FILE_LOG(logINFO) << "Program thrown an exception, msg: " << e.what();
    }

    return 0;
}
