function [] = RTGM_ResultReader()

% USE THIS TO READ CURVES FROM THE *.MAT FILES IN THIS DIRECTORY
%
% The *.mat curves are the ones actually used in the USGS design maps
% and differ slightly from those pulled from the hazard webservice
%
% gets NEHRP city info from Cities struct, reads curve from *.mat
% files, and calculates rtgm
Cities;

% urlBase = 'http://ehpd-earthquake.cr.usgs.gov/hazardtool/';
% urlQuery = 'curves.php?format=2&site=760';
% SA_1HZ = load('34cities.20081229.1hz.mat');
% SA_5HZ = load('34cities.20081229.5hz.mat');

files = {'34cities.20081229.5hz.mat','34cities.20081229.1hz.mat'};
periods = {'0p20','1p00'};

% clean previous results and set up output file
f = 'results.txt';
if exist(f, 'file')
	delete(f);
end
fid = fopen(f, 'a');

for i=1:numel(periods)
	load(files{i}); % loads HazCurves
	SAvals = HazCurves.SA';
	SAstr = num2str(SAvals,'%10.5e,');
	SAstr = SAstr(1:length(SAstr)-1); % strip last comma
	
	
	for j=1:numel(cities)
% 	 for j=1:1
		city = cities(j);
		disp(['Reading: ',city.name]);
		lat = str2double(city.lat);
		lon = str2double(city.lon);
		
		% cities are ordered identically but check to be sure
		fLat = HazCurves.lat(1,j);
		fLon = HazCurves.lon(1,j);
		if ((fLat ~= lat) || (fLon ~= lon))
			error('LatLon mismatch in %s: %f %f %f %f', ...
				city.name, fLat, lat, fLon, lon);
		end
	
		city.period = periods{i};
		city.saStr = SAstr;
		city.saVal = SAvals;

		AFEvals = HazCurves.MAFE(:,j)';
		AFEstr = num2str(AFEvals,'%10.5e,');
		AFEstr = AFEstr(1:length(AFEstr)-1); % strip last comma
		city.afeStr = AFEstr;
		city.afeVal = AFEvals;

		% geo-mean to maxHoriz ground motion conversion
		corr = 1.1;
		if (strcmp(periods{i},periods{2})) 
			corr = 1.3;
		end
		%SAcorr = SAvals * corr;

		HazardCurve = struct('SAs', SAvals, 'AFEs', AFEvals);
		[rtgm, riskCoeff] = RTGM_Calculator(HazardCurve);
		city.rtgm = rtgm * corr;
		city.rc = riskCoeff
		
 		exportEntry(city, fid);
	end

end
end

function exportEntry(city, fid)
	fprintf(fid, ...
		'\ncity: %s\nlat: %s\nlon: %s\nperiod: %s\nsa: %s\nafe: %s\nrtgm: %e\nrc: %e\n', ...
		city.name, city.lat, city.lon, city.period, city.saStr, city.afeStr, city.rtgm, city.rc);
end

