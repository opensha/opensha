

This directory contains various files used to generate rtgm result sets
used for testing. RTGM_ResultFetcher/Reader.m generate hazard curve data and
computes risk-targeted ground motions for all NEHRP cities at 5Hz and 1sec
spectral accelerations.

34cities.20081229.*hz.mat contain the curves used for the 34 NEHRP cities
when the USGS design maps were created.

RTGM_Calculator.m		: original m-file from Nico Luco

RTGM_ResultFetcher.m	: builds results.txt using webservice

RTGM_ResultReader.m		: builds results.txt by reading *.mat files

Cities.m				: MatLab struct of NEHRP test city locations

results.txt				: result file that is created by either RTGM_Result*.m
						  this file is overwritten every time RTGM_Result runs
						  
resultsFINAL.txt		: result file that is ingested by RTGM_Tests.java
						  in parent directory

						  