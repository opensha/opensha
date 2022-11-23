# Fault Style Regions

Polygons that define the extent of focal mechanism ratios (a.k.a 'style-of-faulting' in GMM
terminology) used for gridded seismicity sousrces. The polygons define three tectonic domains:

- Western U.S. compressional (50% strike-slip; %50 reverse)
- Western U.S. extensional (50% strike-slip; %50 normal)
- Central and Eastern U.S. stable craton  (100% strike-slip; CEUS GMMs are not sensitive to
  style-of-faulting)

The boundary between western U.S. compressional and extensional domains roughly coincides with
the boundary used by Chuck Mueller in prior NSHMs (2002, 2008). These NSHMs defined strict catalog
regions for each domain, but seismicity rate was then smoothed outside those regions. The 2014 and 
2018 NSHMs required merging in UCERF3 and the prior approach was preserved, albeit inelegantly.
For the 2023 NSHM we are using WUS- and CEUS-wide to spatial PDFs and then using the strict regions
defined here to assign focal mechanisms for gridded seismicity (point) sources.

The boundary between the WUS extansional domain and the CEUS stable craton is the updated (2023)
attenuation boundary.

Created by: @pmpowers  
11/3/2022
