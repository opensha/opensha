function [] = RTGM_ResultFetcher()

% USE THIS TO FETCH CURVES FROM USGS WEBSERVICE
%
% gets NEHRP city info from Cities struct, fetches curve from USGS
% webservice, and calculates rtgm
Cities;

urlBase = 'http://ehpd-earthquake.cr.usgs.gov/hazardtool/';
urlQuery = 'curves.php?format=2&site=760';

periods = {'0p20','1p00'};

% clean previous results and set up output file
f = 'results.txt';
if exist(f, 'file')
	delete(f);
end
fid = fopen(f, 'a');

for i=1:numel(cities)
% for i=1:1
	city = cities(i);
	disp(['Fetching: ',city.name]);
	urlLatLon = ['&lat=', city.lat,'&lon=',city.lon];
	for j=1:numel(periods)
		
		urlPeriod = ['&period=',periods{j}];
		city.period = periods{j};
		
		URL = [urlBase, urlQuery, urlLatLon, urlPeriod];
		disp(['     url: ',URL]);
		result = urlread(URL);
		
		R = strread(result, '%s', 'delimiter', sprintf('\n'));

		saIdx = strfind(R(2),',');
		SAstr = char(R(2));
		SAstr = SAstr((saIdx{1}(5) + 1):end);
		SAvals = strread(SAstr, '%f', 'delimiter', ',');
		city.saStr = SAstr;
		city.saVal = SAvals;
		
		afeIdx = 6;
		AFEstr = char(R(3));
		AFEstr = AFEstr(afeIdx:end);
		AFEvals = strread(AFEstr, '%f', 'delimiter', ',');
		city.afeStr = AFEstr;
		city.afeVal = AFEvals;
		
		% geo-mean to maxHoriz ground motion conversion
		corr = 1.1;
		if (strcmp(periods{j},periods{2})) 
			corr = 1.3;
		end
		%SAcorr = SAvals * corr;

		HazardCurve = struct('SAs', SAvals, 'AFEs', AFEvals)
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

