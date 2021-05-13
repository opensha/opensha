This program, IM_EventSetCalc, is a command-line application that gives the mean and standard deviation at various site(s) for specified IMT(s), attenuation relationship(s), and for each earthquake rupture in one of two optional Earthquake Rupture Forecasts (ERFs).  Other information is also given in other files (details below).

This application is run by typing the following on the command line:

	java -jar -Xmx500M IM_EventSetCalc.jar [name of the inputfile] [output-files directory]

where [name of the inputfile] is the input file and 
[output-files directory] is a directory where the output files go. The user just needs to provide the relative path to the Jar file where user wants output-files directory to be created.

For Example :

java -jar -Xmx500M IM_EventSetCalc.jar ExampleInputFile.txt SoCalEdison

It will run the application IM_EventSetCalc.jar with the inputfile ExampleInputFile.txt, which exists in the same location as the IM_EventSetCalc.jar application and create the output-files in the directory SoCalEdison, which too will be created in the same level as application IM_EventSetCalc.jar.

An example input file is included in this directory as "ExampleInputFile.txt" (which happens to be identical to the input file in the "SoCalEdison" directory).

Each line of the input file that starts with "#" is a comment that gets ignored by the program, it is just for user's information.

The first choice is whether to use NSHMP-2002 Earthquake Rupture Forecast (ERF) or WGCEP-UCERF-1.0 (2005). All adjustable parameters for these ERFs are hardwired (in the code) to default/official settings; only whether to include the background/grid sources and the offset for floating ruptures are set in the input file.  If "WGCEP-UCERF-1.0 (2005)" is chosen the timespan duration is hard coded as 1 year and this model will be treated as Time independent model.

The next choices are which AttenuationRelationships and IMTs to support. If user chooses an IMT not supported by a AttenuationRelationship then the program will terminate with an error message.

Lastly, the input file specifies a list of sites for which Mean and Sigma will to be computed. If Vs30 is provided (third column, after lat and lon) that value will be used for each attenuation relationship (after conversion to the specific site type for each attenuation relationship).  If Vs30 is not given on the third column, then the program will get the value from a digitized version of the Wills et al. classification map for california  (Wills et al., 2000, BSSA, pages S187-S208).  This feature requires an internet connection in order to obtain the Vs30 values; an error message is given if access fails.

Earthquake Ruptures that are at a distance greater than ~200 km to each site are not listed in the output files (details below).

There are 3 types of output files generated:

1) The Mean and Sigma for each site for a given AttenuationRelationship and IMT, structured in the following manner:

	SourceId RuptureId Mean(1) Std-Dev.(1) ......... Mean(i) Std-Dev.(i)

where i- corresponds to the ith site, and RuptureId and SourceId tell which rupture and source the line corresponds to.

Different files are produced for each IMT/AttenuationRelationship pair.

2) A file giving Rupture-Distance (shortest distance to a point on the rupture surface) for each site in the following format:

	SourceId RuptureId RupDist(1) RupDist(2) ............... RupDist(i)

where i- corresponds to the ith Site.
  
3) a Src-Rup Metadata file structured in the following way:

	SourceId RuptureId annualizedRate Mag Src-Name

where annualized rate is the annualized rate for the rupture.

The directory "SoCalEdison" has the input and output files for an example application.


How Source-Site Cuttoff distance is computed
--------------------------------------------

Here is the Algorithm for calculating the source-site cuttoff:

First find the min and max lat and lon among the sites given in the input file. Then find the middle lat and lon as: 

	middleLat =  (minLat + maxLat)/2
 	middleLon =  (minLon + maxLon)/2

Find the distance (km) from the this middleLat & middleLon to maxLat & maxLon, and add this distance to 200 km.

During the calculation, ignore (skip) any ruptures that our outside the circle defined by this combined distance from middleLat and middleLon.

This procedure insures a uniform set of ruptures for all sites.


Hazard Curve Summation Code
----------------
This package includes a program "IM_EventSetCalcTest.jar" that reads the Event Set files for a given IMT and Site (given as the dir name), and produces annualized rate curves for each Attenuation Relationships used.

This was created to test results against our hazard curve calculator and official USGS/NSHMP results.

This annualized rate-curve calculator hard-codes the following x-axis (IML) values: 0.005, 0.007, 0.0098, 0.0137, 0.0192, 0.0269, 0.0376, 0.0527, 0.0738, 0.103, 0.145, 0.203, 0.284, 0.397, 0.556, 0.778, 1.09, 1.52, 2.13

The output curve is produced on the terminal window.

Run this using the following on the command line:

	java -jar IM_EventSetCalcTest.jar [input directory name]

where input directory name is where the EventSet files are located.
For example :

  java -jar IM_EventSetCalcTest.jar  SoCalEdison

where SoCalEdison is the input directory name where all the EventSet files are located for a given site and IMT.
This tester application reads the EventSet files and generate a averaged annualized rate curve for the selected AttenuationRelationships.

This calculation assumes all sources are Poissonian.


Code Tests:
-----------

The directory "Test_BJFSRL" tests a hazard curve computed here against both one computed from our hazard curve calculator and one from using Frankel's USGS/NSHMP Fortran code (the latter two were published as a validation exercise in Field et al. (Seismological Research Letters, Vol 76,Number 5, Sept/Oct 2005). See the "readme.txt" file in that directory for more details.

The directory "Test_USGS" tests various curves computed here against two "official" points on the curves obtained from the USGS/NSHMP web site.  See the "readme.txt" in that directory details.


Future enhancements to this application
----------------------------------------

1) Calculation of Inter/Intra event Sigma if the attenuation relationship provides this info.

2) Allow the selection of any ERF (that OpenSHA supports), and the setting of any adjustable parameters (including start time and duration) for the chosen ERF (rather than the present hard coding of most of these).

3) Make a GUI-based version of the application, which would both avoid the user from having to hand edit the input file, and more easily allow other parameters to be adjustable.