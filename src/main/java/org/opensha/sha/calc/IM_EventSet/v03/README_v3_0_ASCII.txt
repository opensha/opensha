This program, IM_EventSetCalc, is a command-line application that gives the mean and standard deviation at various site(s) for specified IMT(s), attenuation relationship(s), and for each earthquake rupture in one of three Earthquake Rupture Forecasts (ERFs).  Other information is also given in other files (details below).

This application is run by typing the following on the command line:

	java -jar -Xmx500M IM_EventSetCalc_v3_0_ASCII.jar [--HAZ01] [--d] <name of the inputfile> <output-files directory>

where <name of the inputfile> is the input file and <output-files directory> is a directory where the output files go. The location of the output directory is relative to the location of the jar file.

If the --HAZ01 flag is specified, the output files will follow the HAZ01 as specified in "Data Interchange Formats for the Global Earthquake Model (GEM)", 4 May 2009.

To turn on debug messages, supply the --d flag for some information messages, --dd for more finely tuned messages, and --ddd for highly detailed tracing information. 

For Example :

java -jar -Xmx500M IM_EventSetCalc_v3_0_ASCII.jar ExampleInputFile_v3_0_ASCII.txt ExampleOutputDir

It will run the application IM_EventSetCalc_v2_1.jar with the inputfile ExampleInputFile_v2_1.txt, which must exists in the same location as the IM_EventSetCalc_v2_1.jar application, and will create the output-files in the directory ExampleOutputDir.

NOTE: If you see an error message mentioning Java heap space, GC Overhead, or anything with "Memory" in the message, increase the memory to java by replacing the default "-Xmx500M" argument in the above example with "-Xmx2G". This allocates 2 GB of memory. Increase further (-Xmx3G, ...) if that still fails.

An example input file is included in this directory as "ExampleInputFile_v3_0_ASCII.txt".

Each line of the input file that starts with "#" is a comment that gets ignored by the program (they are just for user's information).

The first choice is which of the following Earthquake Rupture Forecasts to use: 

	NSHMP-2002 Earthquake Rupture Forecast (ERF)
	WGCEP-UCERF-1.0 (2005)
	WGCEP (2007) UCERF2 - Single Branch
	GEM1 CEUS ERF

All adjustable parameters for these ERFs are hard wired in the code to default/official settings; only whether to include the background/grid sources and the offset for floating ruptures are set in the input file.  

If "WGCEP-UCERF-1.0 (2005)" or "WGCEP (2007) UCERF2 - Single Branch" is chosen the timespan duration is hard coded as 1 year and this model will be treated as Time independent model.

The next choices are which AttenuationRelationships and IMTs to support (see ExampleInputFile.txt file for the many options). If user chooses an IMT that is not supported by one or more of the chosen AttenuationRelationships then the program will terminate with an error message.

Lastly, the input file specifies a list of sites for which Mean and Sigma will to be computed. If Vs30 is provided (third column, after lat and lon) that value will be used for each attenuation relationship (after conversion to the specific site type for each attenuation relationship).  If Vs30 is not given on the third column, then the program will get the value from a digitized version of the Wills et al. classification map for california  (Wills et al., 2000, BSSA, pages S187-S208).  This feature requires an internet connection in order to obtain the Vs30 values; an error message is given if access fails.

Earthquake Ruptures that are at a distance greater than ~200 km to each site are not listed in the output files (details below).

There are 3 types of output files generated:

1) The Mean and Sigma for each site for a given AttenuationRelationship and IMT, structured in the following manner:

	SourceId RuptureId Mean(1) Total-Std-Dev.(1) Inter-Event-Std-Dev.(1) ......... Mean(i) Total-Std-Dev.(i) Inter-Event-Std-Dev.(i)

where i- corresponds to the ith site, and RuptureId and SourceId tell which rupture and source the line corresponds to. If the selected IMR doesn't support Inter Event Std Dev, then the value will be -1.

Different files are produced for each IMT/AttenuationRelationship pair (e.g., "AS1997_SA02.txt" would be the result for spectral acceleration at 0.2 sec period using the Abrahamson and Silva (1997) attenuation relationship).

2) A file giving Rupture-Distance (shortest distance to a point on the rupture surface) for each site called 'rup_dist_info.txt' in the following format:

	SourceId RuptureId RupDist(1) RupDist(2) ............... RupDist(i)

where i- corresponds to the ith Site. An similar file, 'rup_dist_jb_info.txt,' is also generated with JB distances.
  
3) a Src-Rup Metadata file structured in the following way:

	SourceId RuptureId annualizedRate Mag Src-Name

where annualized rate is the annualized rate for the rupture.


How Source-Site Cuttoff distance is computed
--------------------------------------------

Here is the algorithm for calculating the source-site cuttoff:

First find the min and max lat and lon among the sites given in the input file. Then find the middle lat and lon as: 

	middleLat =  (minLat + maxLat)/2
 	middleLon =  (minLon + maxLon)/2

Find the distance (km) from the this middleLat & middleLon to maxLat & maxLon, and add this distance to 200 km.

During the calculation, ignore (skip) any ruptures that our outside the circle defined by this combined distance from middleLat and middleLon.

This procedure insures a uniform set of ruptures for all sites.


Future enhancements to this application
----------------------------------------

1) (COMING SOON IN XML VERSION) Allow the selection of any ERF (that OpenSHA supports), and the setting of any adjustable parameters (including start time and duration) for the chosen ERF (rather than the present hard coding of most of these).  Right now we only compute annualized rates for time-independent models.

2) Make a GUI-based version of the application, which would both avoid the user from having to hand edit the input file, and more easily allow other parameters to be adjustable.

3) Write mean and stddevs to separate output files.

4) Provide more metadata