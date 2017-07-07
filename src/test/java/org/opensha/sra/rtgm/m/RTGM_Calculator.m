function [ RTGM, RiskCoefficient ] = RTGM_Calculator( HazardCurve )

%
% Author:  Nicolas Luco (nluco@usgs.gov)
% Last Revised:  23 February 2012
% References:
% [1] Luco N, Ellingwood BR, Hamburger RO, Hooper JD, Kimball JK & Kircher CA (2007),
%     "Risk-Targeted versus Current Seismic Design Maps for the Conterminous United States,"
%     Proceedings of the 2007 Structural Engineers Association of California (SEAOC) Convention.
% [2] Building Seismic Safety Council (2009), "NEHRP Recommended Seismic Provisions 
%     for New Buildings and Other Structures (FEMA P-750): Part I, Provisions," 
%     Federal Emergency Management Agency, Washington D.C., pp. 5-8, 10-18, 67-71 & 92-93.
%
% INPUT:
% ======
% HazardCurve.SAs  = Spectral Response Accelerations  ( column vector )
% HazardCurve.AFEs = Annual Frequencies of Exceedance ( column vector )
%
% OUTPUT:
% =======
% RTGM            = Risk-Targeted Ground Motion         ( scalar )
% RiskCoefficient = RTGM / Uniform-Hazard Ground Motion ( scalar )
%


% Display time stamp
disp( strvcat( ' ' , datestr(now) ) )


% MODEL PARAMETERS
% ================

disp( strvcat( ' ' , 'Model Parameters', '----------------' ) )

% Target risk in terms of annual frequency
TARGET_RISK = - log( 1 - 0.01 )/ 50

% Probability on fragility curve at RTGM
FRAGILITY_AT_RTGM = 0.10

% Logarithmic standard deviation of fragility curve
BETA = 0.8

% Annual frequency of exceedance for Uniform-Hazard Ground Motion (UHGM)
% UHGM is both denominator of risk coefficient and initial guess for RTGM 
AFE4UHGM = - log( 1 - 0.02 )/ 50
%AFE4UHGM = 2 * TARGET_RISK

% See also "UPSAMPLING_FACTOR" and "TOLERANCE" in subfunctions below


% CALCULATIONS
% ============

disp( strvcat( ' ' , 'Calculated RTGM', '---------------' ) )

% Uniform-Hazard Ground Motion
UHGM = LogLog_Interp1( HazardCurve.AFEs, HazardCurve.SAs, AFE4UHGM )

% For adequate discretization of fragility curves ...
[ UpsampledHC ] = Upsample_Hazard_Curve( HazardCurve );
% UpsampledHC.SAs
% UpsampledHC.AFEs
% UpsampledHC.Iextrap


% Iterative calculation of RTGM
% -----------------------------
MAX_N_ITERATIONS = 6;
for i = 1:MAX_N_ITERATIONS
   
    if i == 1
        RTGMi(i) = UHGM;
    elseif i ==2 
        RTGMi(i) = RTGMi(1) * Error_Ratio;
    else
        RTGMi(i) = LogLog_Interp1( RiskValues, RTGMi, TARGET_RISK );
    end
    
    % Generate fragility curve corresponding to current guess for RTGM
    FragilityCurves(i) = Generate_Fragility_Curve( RTGMi(i), FRAGILITY_AT_RTGM, BETA, UpsampledHC.SAs );

    % Calculate risk using fragility curve generated above & upsampled
    % hazard curve
    RiskValues(i) = Risk_Integral( FragilityCurves(i).PDF, UpsampledHC );
        
    % Check risk calculated above against target risk
    Error_Ratio = Check_Risk_against_Target( RiskValues(i), TARGET_RISK );

    % If ratio of calculated and target risks is 1 (within tolerance), exit loop
    if Error_Ratio == 1
        break
    end
    
    % If number of iterations has reached specified maximum, exit loop
    if i == MAX_N_ITERATIONS
        disp( 'MAX # ITERATIONS REACHED' )
    end
    
end

% Specify output
if Error_Ratio ~= 1
    RTGM = NaN
else
    RTGM = RTGMi(end)
end
RiskCoefficient = RTGM / UHGM


% ADDITIONAL OUTPUT
% =================

disp( strvcat( ' ' , 'Iteration Summary', '-----------------' ) )

% Display RTGM iterations
RTGMi
RISKiDivideByTARGET = RiskValues / TARGET_RISK

% Plot RTGM iterations
%Plot_RTGM_Calculation_Iterations( UpsampledHC, AFE4UHGM, UHGM, RTGMi, ...
%                                  FragilityCurves, FRAGILITY_AT_RTGM, TARGET_RISK );

                              

% SUBFUNCTION for upsampling the hazard curve
% ===========================================

function [ upsampledHC ] = Upsample_Hazard_Curve( originalHC )

UPSAMPLING_FACTOR = 1.05;
SMALLEST_SA = 0.001;
LARGEST_SA = max(originalHC.SAs);

upsampledHC.SAs = exp( log(SMALLEST_SA) : log(UPSAMPLING_FACTOR) : log(LARGEST_SA) )';
if upsampledHC.SAs(end) ~= LARGEST_SA
    upsampledHC.SAs(end+1) = LARGEST_SA;
end

upsampledHC.AFEs = LogLog_Interp1( originalHC.SAs, originalHC.AFEs, upsampledHC.SAs );

upsampledHC.Iextrap = ( upsampledHC.SAs < min(originalHC.SAs) ...
                        | upsampledHC.SAs > max(originalHC.SAs) );

                    
% SUBFUNCTION for log-log interpolation
% =====================================

function [ YI ] = LogLog_Interp1( X, Y, XI )

YI = exp( interp1( log(X), log(Y), log(XI), 'linear', 'extrap' ) );


% SUBFUNCTION for generating a fragility curve
% ============================================

function [ FragilityCurve ] = Generate_Fragility_Curve( RTGM, FRAGILITY_AT_RTGM, BETA, SAs )

FragilityCurve.Median = RTGM / exp( norminv( FRAGILITY_AT_RTGM ) * BETA );
FragilityCurve.PDF = lognpdf( SAs, log(FragilityCurve.Median), BETA );
FragilityCurve.CDF = logncdf( SAs, log(FragilityCurve.Median), BETA );
FragilityCurve.SAs = SAs;
FragilityCurve.Beta = BETA;


% SUBFUNCTION for evaluationg the Risk Integral
% =============================================

function [ Risk ] = Risk_Integral( FragilityCurvePDF, HazardCurve )

% This function assumes that FrigilityCurvePDF is defined at the same
% spectral accelerations as the hazard curve (i.e. at HazardCurve.SAs)
Integrand = FragilityCurvePDF .* HazardCurve.AFEs;
Risk = trapz( HazardCurve.SAs, Integrand );


% SUBFUNCTION for checking calculated risk against target risk
% ============================================================

function [ Error_Ratio ] = Check_Risk_against_Target( Risk, TARGET_RISK )

TOLERANCE = 0.01;

Error_Ratio = Risk / TARGET_RISK;
% If error is within tolerance, round error to integer value of 1
if abs( Error_Ratio - 1 ) <= TOLERANCE
    Error_Ratio = round(1);
end
