%% README for NSHM2023 Geologic Deformation Model, v1.0
September 3, 2021
contact: ahatem@usgs.gov

PROVISIONAL DATASET DISCLAIMER:
"These data are preliminary or provisional and are subject to revision. They are being provided to meet the need for timely best science. The data have not received final approval by the U.S. Geological Survey (USGS) and are provided on the condition that neither the USGS nor the U.S. Government shall be held liable for any damages resulting from the authorized or unauthorized use of the data."


%%OUTPUT RESULTS OF GEOLOGIC DEFORMATION MODEL%%
FILE NAME: NSHM23_GeologicDeformationModel_v1
File formats included:
Shapefile, GeoJSON, CSV

Coordinate reference system:
WGS84

Fields included:
FaultID: ID number corresponds to the NSHM2023 Fault Sections Database
FaultName: Fault name as listed in NSHM2023 Fault Sections Database
SubRegion: Subregion identifier
Treat: Distribution choice selected for geologic slip rate; options are ‘tribox’, ‘boxcar’, ‘lowbox’, ‘highbox’, ‘logn-low’, or ‘logn-high’
PrefRate: Preferred output slip rate (mm/yr); median rate of applied distributions
LowRate: Low output slip rate (mm/yr); if ‘tribox’, this rate is the low reported value from original authors; if not ‘tribox’, this rate is the lower boundary of evaluation window of the distribution
HighRate: High output slip rate (mm/yr); if ‘tribox’, this rate is the high reported value from original authors; if not ‘tribox’, this rate is the upper boundary of evaluation window of the distribution
RateType: states what type of slip rate Pref, Low and HighRate represent. Ex: unprojected (vertical) denotes a vertical slip rate, and is not projected onto a given fault dip.


%%ADDITIONAL FILES%%
FILE NAME: SmallRegions_StrainRateComparisons
File formats included:
GeoJSON

Coordinate reference system:
WGS84

File contains polygons used in strain rate comparisons when selecting the general distribution shape of geologic slip rates in a given region


FILE NAME: LargeRegions_MomentRateComparions
File formats included:
GeoJSON

Coordinate reference system:
WGS84

File contains polygons used in moment rate comparisons following geologic slip rate assignments.


