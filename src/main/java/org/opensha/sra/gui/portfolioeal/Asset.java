package org.opensha.sra.gui.portfolioeal;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.ListIterator;

import javax.swing.JOptionPane;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.SiteTranslator;
import org.opensha.sra.calc.EALCalculator;
import org.opensha.sra.gui.portfolioeal.gui.PortfolioEALCalculatorView;
import org.opensha.sra.vulnerability.Vulnerability;

import com.google.common.base.Preconditions;



/**
 * This class defines an asset.  Each asset has a ParameterList.  The parameters in the
 * list can be arbitrary, and will be defined based on the parameters name.  Each asset
 * is responsible for calculating its own EAL as well.
 * 
 * @author Jeremy Leakakos
 * @see PortfolioParser
 */
public class Asset implements Cloneable {

	private ParameterList paramList;
	private String errorMessage = "";
	private double EAL;
	private Site assetSite;
	private HazardCurveCalculator calc;
	private boolean calculationDone = false;
	private ArbitrarilyDiscretizedFunc hazFunction;
	
	private SiteTranslator trans = new SiteTranslator();

	/**
	 * This constructor takes a comma separated value String
	 * 
	 * @param asset The csv String from the portfolio file
	 */
	public Asset( String asset ) {
		String[] parameters = asset.split(",");
		paramList = new ParameterList();
		initParameterMap( parameters );
		EAL = 0.0;
	}

	/**
	 * This method sets the Parameter objects in the ParameterList from the parameters 
	 * in the string array.  It is only used on the first "asset", or first line of a'
	 * portfolio.  This line is a header, and used to define which parameters are in
	 * each asset.
	 * 
	 * @param parameters An string array to be turned into parameters.
	 */
	private void initParameterMap( String[] parameters ) {
		for( int i = 0; i < parameters.length; i ++ ) {
			paramList.addParameter( createParameter(parameters[i]) );
		}
	}

	/**
	 * This method will start a calculation progress bar is the progress bar
	 * CheckBox is checked.
	 */
	private void startCalcProgressBar() {
		if(PortfolioEALCalculatorView.isViewInitialized() && 
				PortfolioEALCalculatorView.getView().getProgressBarChecked()) {
			CalcProgressListener progressBar = new CalcProgressListener(this);
			progressBar.start();
		}
	}

	/**
	 * Set the parameters for the asset.  It takes an array of strings, which are the
	 * parameter values.
	 * 
	 * @param assetList The list of parameters
	 */
	public void setAssetParameters( String[] assetList ) {
		ParameterList list = getParameterList();
		ListIterator<Parameter<?>> iter = list.getParametersIterator();
		Integer i = 0;
		Object val = null;
		while( iter.hasNext() ) {
			Parameter param = iter.next();
			if ( param.getType().equals("IntegerParameter") ) {
				val = Integer.parseInt(assetList[i]);
			}
			else if ( param.getType().equals("StringParameter") ) {
				val = assetList[i];
			}
			else if ( param.getType().equals("DoubleParameter") ) {
				val = Double.parseDouble(assetList[i]);
			}
			param.setValue(val);
			i++;
		}
	}

	/**
	 * This method creates a parameter based on the name of the parameter from the
	 * portfolio file, using reflection. This method is only used when the first "asset"
	 * needs to be created.  It is called from <code>initParameterMap</code>, once for
	 * each parameter to be created.  It initializes the base asset that the other
	 * assets with be cloned from.
	 * 
	 * @param paramName The name of the parameter to be created
	 * @return The created parameter, based on the name
	 * @see ParameterParser
	 */
	private AbstractParameter createParameter( String paramName ) {
		AbstractParameter param = null;
		ParameterParser parameterParser = ParameterParser.getParameterParser();
		Class<?> c = null;
		try {
			String className = "org.opensha.commons.param.impl." + parameterParser.getParameterType(paramName);
			c = Class.forName( className );
			Class<?>[] paramTypes = {String.class};
			Constructor<?> cons = c.getConstructor(paramTypes);
			param = (AbstractParameter) cons.newInstance(paramName);
		} catch( Exception e ) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Parameter type " + paramName + " in file not recognized!", "Error", JOptionPane.ERROR_MESSAGE );
			if (PortfolioEALCalculatorView.isViewInitialized())
				PortfolioEALCalculatorView.getView().setButtonsOnCancel();
		}
		return param;
	}

	/**
	 * Sets up the site with the name and location from the asset
	 * 
	 * @param site The site to have its values changed
	 * @return The updated site
	 */
	public void siteSetup( Site site ) {
		if (getParameterList().containsParameter("SiteName"))
			site.setName((String) getParameterList().getParameter("SiteName").getValue());
		site.setLocation(getLocation());
		assetSite = (Site)site.clone();
		
		double vs30 = (Double)getParameterList().getParameter("Vs30").getValue();
		SiteDataValue<Double> vs30val = new SiteDataValue<Double>(SiteData.TYPE_VS30, null, vs30);
		
		Iterator<Parameter<?>> it = assetSite.getParametersIterator();
		while (it.hasNext()) {
			trans.setParameterValue(it.next(), vs30val);
		}
	}
	
	public Location getLocation() {
		return new Location((Double) getParameterList().getParameter("Lat").getValue(), (Double) paramList.getParameter("Lon").getValue());
	}

	/**
	 * Sets up the vulnerability model based on the name from the asset.  It uses
	 * reflection to create the class at runtime.
	 * @return The vulnerability model
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private Vulnerability getVulnModel() throws ClassNotFoundException,
	InstantiationException,
	IllegalAccessException {
		String vulnName = getVulnModelName();

//		System.out.println("looking for vuln: '" + vulnName + "'");

		return PortfolioEALCalculatorController.getVulnerabilities().get(vulnName);
	}
	
	public String getVulnModelName() {
		return paramList.getParameter(String.class, "VulnModel").getValue();
	}

	/**
	 * This resets the X values of the hazard function.
	 * @param hazFunction The hazard function to be reset
	 * @param vulnModel The vulnerability model where the X values come fro
	 * @return The reset hazard function
	 */
	private DiscretizedFunc resetHazardXValues( DiscretizedFunc hazFunction, Vulnerability vulnModel ) {
		XY_DataSet tempFunc = hazFunction.deepClone();
		Preconditions.checkState(tempFunc.size() == hazFunction.size());
		hazFunction = new ArbitrarilyDiscretizedFunc();
		double imlvals[] = vulnModel.getIMLValues();
		Preconditions.checkState(imlvals.length == tempFunc.size(), "IML val length inconsistant: "
				+imlvals.length+" != "+tempFunc.size());
		for( int i = 0; i < tempFunc.size(); ++i ) {
			hazFunction.set(imlvals[i],tempFunc.getY(i));
		}
		return hazFunction;
	}
	/**
	 * This calculates the EAL for a given asset
	 * 
	 * @return The EAL for the asset.  This will be summed up with all of the EAL's
	 * for the other assets in the list.
	 */
	public double calculateEAL( ScalarIMR imr, double distance, Site site, BaseERF erf, CalculationExceptionHandler controller ) {
		return calculateEAL(imr, distance, null, site, erf, controller);
	}
	
	public double calculateEAL( ScalarIMR imr, double distance, ArbitrarilyDiscretizedFunc magThreshFunc, Site site, BaseERF erf, CalculationExceptionHandler controller ) {
		// Edit the site with the asset values
		siteSetup(site);
		Site newSite = getSite();
		boolean error = false;

		errorMessage = "";

		// Create a new hazard function, which will will be used to make calculation
		hazFunction = new ArbitrarilyDiscretizedFunc();

		// Setup for the HazardCurveCalculator
		try {
			calc = new HazardCurveCalculator();
			wait(5000);
		} catch( Exception e ) {
		}

		startCalcProgressBar();

		// Setup for the forcast gotten from the ERF
		BaseERF forecast = null;

		// Setup for the annualized rates gotten from the hazard function with the HazardCurveCalculator
		ArbitrarilyDiscretizedFunc annualizedRates = null;

		// The vulnerability model, which is hard coded for now
		Vulnerability vulnModel = null;

		try {
			vulnModel = getVulnModel();
		} catch( Exception e ) {
			e.printStackTrace();
			errorMessage += e.getMessage();
			error = true;
		}

		Preconditions.checkNotNull(vulnModel, "Vulnerability model '"+getVulnModelName()+"' is null!");
		String imt = vulnModel.getIMT();
		double imls[] = vulnModel.getIMLValues();

		// Sets the intensity measure for the imr instance
		try {				
			//			((AttenuationRelationship)imr).setIntensityMeasure(imt, period);
//			System.out.println("IMT: " + imt);
			imr.setIntensityMeasure(imt);
			if (imt.equals(SA_Param.NAME))
				SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), vulnModel.getPeriod());
			//			((AttenuationRelationship)imr).setIntensityMeasure(imt);
		} catch( ParameterException e ) {
			e.printStackTrace();
			controller.calculationException( e.getMessage() );
		}

		// Take the log of the x values of the hazard function
		// Used to make calculations
		for( int i = 0; i < imls.length; ++i ) {
			
//			System.out.println(i+". "+imls[i]+", log: "+Math.log(imls[i]));
			hazFunction.set( Math.log( imls[i] ), 1 );
		}
		
		Preconditions.checkState(imls.length == hazFunction.size());

		// Create a HazardCurveCalculator with a site, imr, and erf
		try {
			if (magThreshFunc != null)
				calc.setMagDistCutoffFunc(magThreshFunc);
			else
				calc.setMaxSourceDistance( distance );
			forecast = erf;
			hazFunction = (ArbitrarilyDiscretizedFunc)calc.getHazardCurve(hazFunction, newSite, imr, (ERF) forecast);
		} catch( Exception e ) {
			e.printStackTrace();
			errorMessage += e.getMessage();
			error = true;
		}
		
		Preconditions.checkState(imls.length == hazFunction.size());

		// Reset the x values of the hazard function
		hazFunction = (ArbitrarilyDiscretizedFunc) resetHazardXValues( hazFunction, vulnModel );

		// Create the annualized rates function to be used in the EAL calculator
		try {
			annualizedRates = (ArbitrarilyDiscretizedFunc)calc.getAnnualizedRates(hazFunction, forecast.getTimeSpan().getDuration());
		} catch( Exception e ) {
			e.printStackTrace();
			errorMessage += e.getMessage();
			error = true;
		}

		if ( error ) controller.calculationException( errorMessage );

		EALCalculator currentCalc = new EALCalculator( annualizedRates, vulnModel.getVulnerabilityFunc(), getValue() );
		EAL = currentCalc.computeEAL();
		calculationDone();
		calc = null;
		return EAL;
	}
	
	/**
	 * This calculates the expected loss for each rupture in the given forecast individually.
	 * 
	 * @return The EAL for the asset.  This will be summed up with all of the EAL's
	 * for the other assets in the list.
	 */
	public double[][] calculateExpectedLossPerRup(
			ScalarIMR imr, ArbitrarilyDiscretizedFunc magThreshFunc, Site site, ERF erf,
			CalculationExceptionHandler controller ) {
		// Edit the site with the asset values
		siteSetup(site);
		Site newSite = getSite();
		boolean error = false;

		errorMessage = "";

		// Create a new hazard function, which will will be used to make calculation
		ArbitrarilyDiscretizedFunc logHazFunction = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc hazFunction = new ArbitrarilyDiscretizedFunc();

		// Setup for the HazardCurveCalculator
		try {
			calc = new HazardCurveCalculator();
			wait(5000);
		} catch( Exception e ) {
		}

		startCalcProgressBar();

		// The vulnerability model, which is hard coded for now
		Vulnerability vulnModel = null;

		try {
			vulnModel = getVulnModel();
		} catch( Exception e ) {
			e.printStackTrace();
			errorMessage += e.getMessage();
			error = true;
		}

		Preconditions.checkNotNull(vulnModel, "Vulnerability model '"+getVulnModelName()+"' is null!");
		String imt = vulnModel.getIMT();
		double imls[] = vulnModel.getIMLValues();

		// Sets the intensity measure for the imr instance
		try {				
			//			((AttenuationRelationship)imr).setIntensityMeasure(imt, period);
//			System.out.println("IMT: " + imt);
			imr.setIntensityMeasure(imt);
			if (imt.equals(SA_Param.NAME))
				SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), vulnModel.getPeriod());
			//			((AttenuationRelationship)imr).setIntensityMeasure(imt);
		} catch( ParameterException e ) {
			System.out.println("imt: "+imt);
			System.out.println("Vuln class: "+vulnModel.getClass().getName());
			System.out.println("Vuln name: "+vulnModel.getName());
			e.printStackTrace();
			controller.calculationException( e.getMessage() );
		}

		// Take the log of the x values of the hazard function
		// Used to make calculations
		for( int i = 0; i < imls.length; ++i ) {
			
//			System.out.println(i+". "+imls[i]+", log: "+Math.log(imls[i]));
			logHazFunction.set( Math.log( imls[i] ), 1 );
			hazFunction.set(imls[i], 1);
		}
		
		Preconditions.checkState(imls.length == hazFunction.size());
		
		double[][] results = new double[erf.getNumSources()][];
		
		EAL = 0d;
		double duration = erf.getTimeSpan().getDuration();
		
		EALCalculator currentCalc = new EALCalculator(hazFunction, vulnModel.getVulnerabilityFunc(), getValue() );
		
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			double distance = source.getMinDistance(site);
			
			if (distance > magThreshFunc.getMaxX()) {
//				System.out.println("Distance thresh fail (dist="+distance+")");
				continue;
			}
			double magThresh = magThreshFunc.getInterpolatedY(distance);
			results[sourceID] = new double[source.getNumRuptures()];
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				ProbEqkRupture rupture = source.getRupture(rupID);
				
				if (rupture.getMag() < magThresh) {
//					System.out.println("Mag thresh fail (mag="+rupture.getMag()+", thresh="+magThresh+")");
					continue;
				}
				
				// calc deterministic hazard curve
				calc.getHazardCurve(logHazFunction, newSite, imr, rupture);
				
				Preconditions.checkState(imls.length == logHazFunction.size());

				// populate the linear func with the y values
				for (int i=0; i<logHazFunction.size(); i++)
					hazFunction.set(i, logHazFunction.getY(i));

//				// Create the annualized rates function to be used in the EAL calculator
//				try {
//					hazFunction = (ArbitrarilyDiscretizedFunc)calc.getAnnualizedRates(hazFunction, 1d);
//				} catch( Exception e ) {
//					e.printStackTrace();
//					errorMessage += e.getMessage();
//					error = true;
//				}

				if ( error ) controller.calculationException( errorMessage );

				currentCalc.setMAFE(hazFunction);
				double rupEL = currentCalc.computeEAL();
				
				results[sourceID][rupID] = rupEL;
				EAL += rupEL * rupture.getMeanAnnualRate(duration);
			}
		}
		calculationDone();
		return results;
	}
	
	public double getValue() {
		return (Double)paramList.getParameter("Value").getValue();
	}

	/**
	 * Set the calculationDone boolean to true when the calculation finishes.
	 */
	private void calculationDone() {
		calculationDone = true;
	}

	/**
	 * @return The boolean representing whether the calculation is done or not.
	 */
	public boolean isCalculationDone() {
		return calculationDone;
	}

	/**
	 * @return The total amount of ruptures in a hazard calculation.
	 */
	public int getTotalRuptures() {
		return calc.getTotRuptures();
	}

	/**
	 * @return The current amount of ruptures in a hazard calculation.
	 */
	public int getCurrentRuptures() {
		return calc.getCurrRuptures();
	}

	/**
	 * @return The ParameterList storing the parameters for a given Asset.
	 */
	public ParameterList getParameterList() {
		return paramList;
	}

	/**
	 * @return The EAL for the asset
	 */
	public double getAssetEAL() {
		return EAL;
	}

	/**
	 * This method sets the parameter list for an asset
	 * @param paramList The ParameterList to be set to
	 */
	private void setParamList( ParameterList paramList ) {
		this.paramList = paramList;
	}

	/**
	 * Get the site associated with an asset.
	 * @return The site for the asset
	 */
	public Site getSite() {
		return assetSite;
	}

	/**
	 * @return The hazard function associated with an asset
	 */
	public ArbitrarilyDiscretizedFunc getHazardFunction() {
		return hazFunction;
	}

	/**
	 * The clone method for Asset.  It overrides the default clone operation in Object.
	 * It creates an a shallow clone of the base asset, and then it creates a clone of
	 * the base asset's ParameterList.  The new asset then has its ParameterList set
	 * to the cloned one.
	 */
	@Override
	public Asset clone() throws CloneNotSupportedException {
		Asset asset = (Asset) super.clone();
		asset.setParamList((ParameterList)this.getParameterList().clone());
		return asset;
	}
}