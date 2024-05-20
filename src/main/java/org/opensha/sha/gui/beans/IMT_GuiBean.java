package org.opensha.sha.gui.beans;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * <p>Title: IMT_GuiBean </p>
 * <p>Description: this dispalys the various IMTs supported by the selected IMR</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Nitin Gupta and Vipin Gupta
 * @version 1.0
 */
@Deprecated
public class IMT_GuiBean extends ParameterListEditor implements ParameterChangeListener{

	// IMT GUI Editor & Parameter names
	public final static String IMT_PARAM_NAME =  "IMT";
	// IMT Panel title
	public final static String IMT_EDITOR_TITLE =  "Set IMT";

	//stores the IMT Params for the choosen IMR
	private ArrayList<Parameter> imtParam;

	// imr for which IMT is to be displayed
	private ScalarIMR imr;

	private ArrayList<ScalarIMR> multipleIMRs;

	/**
	 * constructor: accepts AttenuationRelationshipAPI and supprtedIntensity Measure type
	 * @param imr Choosen AttenuationRelationship
	 * @param supportedIntensityMeasureIt Supported Intensity Measure Iterator
	 */
	public IMT_GuiBean(ScalarIMR imr,Iterator<Parameter<?>> supportedIntensityMeasureIt) {
		setIM(imr,supportedIntensityMeasureIt );
	}
	
	/**
	 * constructor: accepts AttenuationRelationshipAPI and supprtedIntensity Measure type
	 * @param imr Choosen AttenuationRelationship
	 * @param supportedIntensityMeasureIt Supported Intensity Measure Iterator
	 */
	public IMT_GuiBean(ArrayList<ScalarIMR> multipleIMRs) {
		setIM(multipleIMRs);
	}

	/**
	 * 
	 * This function accepts AttenuationRelationshipAPI and supprtedIntensity Measure type
	 *
	 * @param imr Choosen AttenuationRelationship
	 * @param supportedIntensityMeasureIt Supported Intensity Measure Iterator
	 */
	public void setIM(ScalarIMR imr,Iterator<Parameter<?>> supportedIntensityMeasureIt){
		this.imr = imr;
		init_imtParamListAndEditor(imr.getSupportedIntensityMeasures());
	}
	
	/**
	 * 
	 * This function accepts AttenuationRelationshipAPI and supprtedIntensity Measure type
	 *
	 * @param imr Choosen AttenuationRelationship
	 * @param supportedIntensityMeasureIt Supported Intensity Measure Iterator
	 */
	public void setIM(ArrayList<ScalarIMR> multipleIMRs) {
		if (multipleIMRs == null || multipleIMRs.size() == 0)
			return;
		this.imr = null;
		
		// first get a master list of all of the supported Params
		// this is hardcoded to allow for checking of common SA period
		ArrayList<Double> saPeriods = new ArrayList<Double>();
		double defaultPeriod = -1;
		ParameterList paramList = new ParameterList();
		for (ScalarIMR imr : multipleIMRs) {
			for (Parameter param : imr.getSupportedIntensityMeasures()) {
				if (paramList.containsParameter(param.getName())) {
					// it's already in there
				} else {
					paramList.addParameter(param);
				}
				// get all of the periods in there
				if (param.getName().equals(SA_Param.NAME)) {
					SA_Param saParam = (SA_Param)param;
					PeriodParam periodParam = saParam.getPeriodParam();
					if (defaultPeriod < 0)
						defaultPeriod = periodParam.getValue();
					List<Double> periods = periodParam.getSupportedPeriods();
//					System.out.println("Located " + periods.size() + " supported periods for " + imr.getShortName());
					for (double period : periods) {
						if (!saPeriods.contains(period)) {
//							System.out.println("Adding a period: " + period);
							saPeriods.add(period);
						}
					}
				}
			}
		}
		
		// now we weed out the ones that aren't supported by everyone
		ParameterList toBeRemoved = new ParameterList();
		SA_Param replaceSA = null;
		for (Parameter param : paramList) {
			boolean remove = false;
			for (ScalarIMR imr : multipleIMRs) {
				if (!imr.getSupportedIntensityMeasures().containsParameter(param.getName())) {
					remove = true;
					break;
				}
			}
			if (remove) {
				if (!toBeRemoved.containsParameter(param.getName())) {
					toBeRemoved.addParameter(param);
				}
				// if SA isn't supported, we can skip the below logic
				continue;
			}
			ArrayList<Double> badPeriods = new ArrayList<Double>();
			if (param.getName().equals(SA_Param.NAME)) {
				for (ScalarIMR imr : multipleIMRs) {
					SA_Param saParam = (SA_Param) imr.getSupportedIntensityMeasures().getParameter(SA_Param.NAME);
					PeriodParam periodParam = saParam.getPeriodParam();
					List<Double> periods = periodParam.getSupportedPeriods();
					for (double period : saPeriods) {
						if (!periods.contains(period)) {
							// this period is not supported by this IMR
							if (!badPeriods.contains(period))
								badPeriods.add(period);
						}
					}
				}
				for (double badPeriod : badPeriods) {
//					System.out.println("Removing a period: " + badPeriod);
					saPeriods.remove(badPeriod);
				}
				SA_Param oldSA = (SA_Param)param;
				DoubleDiscreteConstraint pConst = new DoubleDiscreteConstraint(saPeriods);
				PeriodParam periodParam = new PeriodParam(pConst, defaultPeriod, true);
				replaceSA = new SA_Param(periodParam, oldSA.getDampingParam());
				replaceSA.setValue(defaultPeriod);
			}
		}
		if (replaceSA != null)
			paramList.replaceParameter(replaceSA.getName(), replaceSA);
		// now we remove them
		for (Parameter badParam : toBeRemoved) {
			paramList.removeParameter(badParam.getName());
		}
		
		init_imtParamListAndEditor(paramList);
	}


	/**
	 *  Create a list of all the IMTs
	 */
	private void init_imtParamListAndEditor(ParameterList newIMTParams) {

		parameterList = new ParameterList();

		//vector to store all the IMT's supported by an IMR
		ArrayList<String> imt=new ArrayList<String>();
		imtParam = new ArrayList<Parameter>();




		//loop over each IMT and get their independent parameters
		for (Parameter param : newIMTParams) {
//		while ( it.hasNext() ) {
//			ParameterAPI param = ( ParameterAPI ) it.next();
			DoubleDiscreteParameter param1=new DoubleDiscreteParameter(param.getName());

			// add all the independent parameters related to this IMT
			// NOTE: this will only work for DoubleDiscrete independent parameters; it's not general!
			// this also converts these DoubleDiscreteParameters to StringParameters
			if (param instanceof Parameter) {
				Parameter depParam = (Parameter)param;
				if(D) System.out.println("IMT is:"+param.getName());
				for (Parameter<?> param2 : depParam.getIndependentParameterList()) {
					DoubleDiscreteConstraint values = ( DoubleDiscreteConstraint )param2.getConstraint();
					// add all the periods relating to the SA
					DoubleDiscreteParameter independentParam = new DoubleDiscreteParameter(param2.getName(),
							values, (Double)values.getAllowedValues().get(0));

					// added by Ned so the default period is 1.0 sec (this is a hack).
					if( ((String) independentParam.getName()).equals("SA Period") ) {
						if (independentParam.isAllowed(Double.valueOf(1.0)))
							independentParam.setValue(Double.valueOf(1.0));
					}

					param1.addIndependentParameter(independentParam);
				}
			}
			imtParam.add(param1);
			imt.add(param.getName());
		}

		// add the IMT paramter
		StringParameter imtParameter = new StringParameter (IMT_PARAM_NAME,imt,
				(String)imt.get(0));
		imtParameter.addParameterChangeListener(this);
		parameterList.addParameter(imtParameter);

		/* gets the iterator for each supported IMT and iterates over all its indepenedent
		 * parameters to add them to the common ArrayList to display in the IMT Panel
		 **/

		for (Parameter param : imtParam) {
			if (param instanceof Parameter) {
				Parameter depParam = (Parameter)param;
				for (Parameter<?> indepParam : depParam.getIndependentParameterList())
					parameterList.addParameter(indepParam);
			}
		}
		this.editorPanel.removeAll();
		// now make the editor based on the paramter list
		addParameters();
		setTitle( IMT_EDITOR_TITLE );
		// update the current IMT
		updateIMT((String)imt.get(0));
	}

	/**
	 * This function updates the IMTeditor with the independent parameters for the selected
	 * IMT, by making only those visible to the user.
	 * @param imtName : It is the name of the selected IMT, based on which we make
	 * its independentParameters visible.
	 */

	private void updateIMT(String imtName) {
		Iterator it= parameterList.getParametersIterator();

		//making all the IMT parameters invisible
		while(it.hasNext())
			setParameterVisible(((Parameter)it.next()).getName(),false);

		//making the choose IMT parameter visible
		setParameterVisible(IMT_PARAM_NAME,true);

		it=imtParam.iterator();
		//for the selected IMT making its independent parameters visible
		while(it.hasNext()){
			Parameter param=(Parameter)it.next();
			if(param.getName().equalsIgnoreCase(imtName)){
				for (Parameter<?> indepParam : param.getIndependentParameterList())
					setParameterVisible(indepParam.getName(),true);
			}
		}
	}

	/**
	 *  This is the main function of this interface. Any time a control
	 *  paramater or independent paramater is changed by the user in a GUI this
	 *  function is called, and a paramater change event is passed in. This
	 *  function then determines what to do with the information ie. show some
	 *  paramaters, set some as invisible, basically control the paramater
	 *  lists.
	 *
	 * @param  event
	 */
	public void parameterChange( ParameterChangeEvent event ) {

		String S = C + ": parameterChange(): ";
		if ( D )
			System.out.println( "\n" + S + "starting: " );

		String name1 = event.getParameterName();

		// if IMT selection then update
		if (name1.equalsIgnoreCase(IMT_PARAM_NAME)) {
			updateIMT((String)event.getNewValue());
		}

	}

	/**
	 * It will return the IMT selected by the user
	 * @return : IMT selected by the user
	 */
	public String getSelectedIMT() {
		return this.parameterList.getValue(IMT_PARAM_NAME).toString();
	}

	/**
	 * set the IMT parameter in IMR
	 */
	public void setIMT() {
		Parameter param = getIntensityMeasure();
		if (imr != null) {
			imr.setIntensityMeasure(param);
		} else {
			for (ScalarIMR imr : multipleIMRs) {
				imr.setIntensityMeasure(param.getName());
				if (param instanceof SA_Param) {
					SA_Param saParam = (SA_Param) param;
					double period = saParam.getPeriodParam().getValue();
					SA_Param imt = (SA_Param) imr.getIntensityMeasure();
					imt.getPeriodParam().setValue(period);
				}
			}
		}
	}


	/**
	 * gets the selected Intensity Measure Parameter and its dependent Parameter
	 * @return
	 */
	public Parameter getIntensityMeasure(){
		String selectedImt = parameterList.getValue(IMT_PARAM_NAME).toString();
		//set all the  parameters related to this IMT
		Iterator it= imtParam.iterator();
		while(it.hasNext()){
			Parameter param=(Parameter)it.next();
			if(param.getName().equalsIgnoreCase(selectedImt))
				return param;
		}
		return null;
	}



	/**
	 *
	 * @return the Metadata string for the IMT Gui Bean
	 */
	public String getParameterListMetadataString(){
		String metadata=null;
		ListIterator it = getVisibleParameters().getParametersIterator();
		int paramSize = getVisibleParameters().size();
		while(it.hasNext()){
			//iterates over all the visible parameters
			Parameter tempParam = (Parameter)it.next();
			//if the param name is IMT Param then it is the Dependent param
			if(tempParam.getName().equals(this.IMT_PARAM_NAME)){
				metadata = tempParam.getName()+" = "+(String)tempParam.getValue();
				if(paramSize>1)
					metadata +="[ ";
			}
			else{ //rest all are the independent params
				metadata += tempParam.getName()+" = "+((Double)tempParam.getValue()).doubleValue()+" ; ";
			}
		}
		if(paramSize>1)
			metadata = metadata.substring(0,metadata.length()-3);
		if(paramSize >1)
			metadata +=" ] ";
		return metadata;
	}

}
