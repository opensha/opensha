The IM Event Set Calculator CLT is a command-line application that gives the mean and standard deviation at various site(s) for specified IMT(s), attenuation relationship(s), and for each earthquake rupture of selected Earthquake Rupture Forecasts (ERFs).  Other information is also given in other files (details below).

This application is run by typing the following on the command line:

	java -jar -Xmx500M IMEventSetCalculatorCLT.jar  [-h] [--list-erfs] [--haz01] [-d] [-dd] [-ddd] [-q] [-e <name>]
	                                                [-b <type>] [-r <km>] [-t <years>] [-a <IMR1,IMR2,...> | -f <file>]
	                                                [-m <IMT1,IMT2,...>] [-s <csv-file>] [-o <dir>]

     IM Event Set Calculator - Compute Mean and Sigma for Attenuation
     Relationships and IMTs

      -h,--help                           Show this help and exit.
         --list-erfs                      List available ERF short names and
                                          long names
         --haz01                          Use HAZ01 output file format instead
                                          of default
      -d                                  Set logging level to CONFIG (verbose)
      -dd                                 Set logging level to FINE (very
                                          verbose)
      -ddd                                Set logging level to ALL (debug)
      -q,--quiet                          Set logging level to OFF (quiet)
      -e,--erf <name>                     Earthquake Rupture Forecast (ERF) -
                                          short code or full name in quotes
      -b,--background-seismicity <type>   Include | Exclude | Only-Background
      -r,--rupture-offset <km>            Rupture offset for floating ruptures
                                          (1-100 km; 5 km is generally best).
      -t,--duration <years>               Duration in years. Defaults to 1 if
                                          not provided.
      -a,--atten-rels <IMR1,IMR2,...>     Comma-separated in quotation
                                          attenuation relations
      -f,--atten-rels-file <file>         Newlines-separated IMR list (mutually
                                          exclusive with --atten-rels)
      -m,--imts <IMT1,IMT2,...>           Comma-separated intensity-measure
                                          types
      -s,--sites <csv-file>               CSV of Lat, Lon, [Vs30/Wills] (column
                                          optional)
      -o,--output-dir <dir>               Where to write results (defaults to
                                          current dir)

     Example:
       imcalc -e "WGCEP (2007) UCERF2 - Single Branch" \
              -b Exclude \
              -a "Boore & Atkinson (2008)","Chiou & Youngs (2008)" \
              -m "PGA,SA20,SA 1.0" \
              -r 5 \
              -s sites.csv \
              -o results/

     Note: Site data values are limited to Vs30, Z1.0, Z2.5 in CSV file.
           Use IMEventSetCalculatorGUI for more refined control of Site
     Parameters.

Required parameters are:
  * One of `atten-rels` or `atten-rels-file`
  * `erf`
  * `imts`
  * `output-dir`


If the --HAZ01 flag is specified, the output files will follow the HAZ01 as specified in "Data Interchange Formats for the Global Earthquake Model (GEM)", 4 May 2009.

To turn on debug messages, supply the --d flag for some information messages, --dd for more finely tuned messages, and --ddd for highly detailed tracing information. 

For Example :
java -jar -Xmx500M IMEventSetCalculatorCLT.jar \
                        --erf MeanUCERF2 \
                        --background-seismicity Exclude \
                        --rupture-offset 5 \
                        --imts "PGA,SA02,SA 1.0" \
                        --atten-rels-file ExampleAttenRelsInputFileCLT.txt \
                        --sites ExampleSitesInputFileCLT.csv \
                        --output-dir ExampleOutputDir


It will run the application IMEventSetCalculatorCLT.jar with the input file ExampleInputFileCLT.txt, which must exist in the same location as the IMEventSetCalculatorCLT.jar application, and will create the output files in the directory ExampleOutputDir.

NOTE: If you see an error message mentioning Java heap space, GC Overhead, or anything with "Memory" in the message, increase the memory to java by replacing the default "-Xmx500M" argument in the above example with "-Xmx2G". This allocates 2 GB of memory. Increase further (-Xmx3G, ...) if that still fails.

The input format uses a Sites CSV file, "ExampleSitesInputFileCLT.csv" without headers.
You can also pass an IMR input File, "ExampleAttenRelsInputFileCLT.txt" with an IMR on each line and "#" line-comments.

Each line of the input file that starts with "#" is a comment that gets ignored by the program (they are just for user's information).

The first choice is which of the following Earthquake Rupture Forecasts to use:

    WGCEP (2007) UCERF2 - Single Branch
    WGCEP Eqk Rate Model 2 ERF
    NSHM23-WUS (crustal only, excl. Cascadia) Branch Avg ERF
    USGS/CGS 2002 Adj. Cal. ERF
    UCERF3 Single Branch ERF
    USGS/CGS 1996 Cal. ERF
    WGCEP (2007) UCERF2 - Single Branch, Modified, Fault Model 2.1 only
    WGCEP UCERF 1.0 (2005)
    Fault System Solution ERF
    USGS/CGS 1996 Adj. Cal. ERF
    Mean UCERF3

You could alternatively specify their corresponding short names as follows:
    MeanUCERF2
    UCERF2
    NSHM23_WUS_BranchAveragedERF
    Frankel02_AdjustableEqkRupForecast
    UCERF3_CompoundSol_ERF
    Frankel96_EqkRupForecast
    ModMeanUCERF2_FM2pt1
    WGCEP_UCERF1_EqkRupForecast
    FaultSystemSolutionERF
    Frankel96_AdjustableEqkRupForecast
    MeanUCERF3


For example, you could pass either
Mean UCERF3
or
MeanUCERF3
as the first choice. Either long or short name formats will result in the same choice.

Use `java -jar IMEventSetCalculatorCLT.jar --list-erfs` to see an updated list of available ERFs.


All adjustable parameters for these ERFs are hard-wired in the code to default/official settings; only whether to include the background/grid sources and the offset for floating ruptures are set in the input file.
Use the graphical application, IMEventSetCalculatorGUI.jar, for more refined control over the ERF parameters.

The next choices are which AttenuationRelationships and IMTs to support (see ExampleAttenRelsInputFileCLT.txt file for the many options). If user chooses an IMT that is not supported by one or more of the chosen AttenuationRelationships then the program will terminate with an error message.

Lastly, the input file specifies a list of sites for which Mean and Sigma will to be computed.
If a site file is not provided, defaults to a single site for Los Angeles (34.1,-118.1) with Vs30 760m/s.

Earthquake Ruptures that are at a distance greater than ~200 km to each site are not listed in the output files (details below).

There are 3 types of output files generated:

1) The Mean and Sigma for each site for a given AttenuationRelationship and IMT, structured in the following manner:

	SourceId RuptureId Mean(1) Total-Std-Dev.(1) Inter-Event-Std-Dev.(1) ......... Mean(i) Total-Std-Dev.(i) Inter-Event-Std-Dev.(i)

where i- corresponds to the ith site, and RuptureId and SourceId tell which rupture and source the line corresponds to. If the selected IMR doesn't support Inter Event Std Dev, then the value will be -1.

Different files are produced for each IMT/AttenuationRelationship pair (e.g., "AS1997_SA200.csv" would be the result for spectral acceleration at 0.2 sec period using the Abrahamson and Silva (1997) attenuation relationship).

2) A file giving Rupture-Distance (shortest distance to a point on the rupture surface) for each site called 'rup_dist_info.csv' in the following format:

	SourceId RuptureId RupDist(1) RupDist(2) ............... RupDist(i)

where i- corresponds to the ith Site. A similar file, 'rup_dist_jb_info.csv,' is also generated with JB distances.
  
3) a Src-Rup Metadata file structured in the following way:

	SourceId RuptureId annualizedRate Mag Src-Name

where annualizedRate is the annualized rate for the rupture.


How Source-Site Cutoff distance is computed
--------------------------------------------

Here is the algorithm for calculating the source-site cutoff:

First find the min and max lat and lon among the sites given in the input file. Then find the middle lat and lon as: 

	middleLat =  (minLat + maxLat)/2
 	middleLon =  (minLon + maxLon)/2

Find the distance (km) from the this middleLat & middleLon to maxLat & maxLon, and add this distance to 200 km.

During the calculation, ignore (skip) any ruptures that our outside the circle defined by this combined distance from middleLat and middleLon.

This procedure ensures a uniform set of ruptures for all sites.
