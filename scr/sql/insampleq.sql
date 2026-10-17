copy
(
select
   scenarios / samples,
   -- round(avg(rpave), 1),
   -- round(avg(rpstdv), 1),
   round(100.0 * min((:z) / rpave * rpstdv / sqrt(samples::decimal)), 6),
   round(100.0 * avg((:z) / rpave * rpstdv / sqrt(samples::decimal)), 6),
   round(100.0 * max((:z) / rpave * rpstdv / sqrt(samples::decimal)), 6)
from :tab
where instttag = :tag
group by scenarios, samples
order by scenarios
)
to stdout with delimiter ' ' csv;
