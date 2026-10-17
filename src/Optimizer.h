#ifndef OPTM_H
#define OPTM_H

#include <memory>

#include "utility/optresult.h"
#include "utility/optcfg.h"

#include "CpsspInstance.h"


namespace cpssp
{
    class Optimizer
    {
        bool is_verbose = false;
        
    public:
        optresult operator()(const std::shared_ptr<CpsspInstance>&, const optcfg& ocfg, std::string);
        
        inline void setVerbose() { is_verbose = true; }
    };
}

#endif // OPTM_H