package org.opensha.sha.gcim.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.event.ScalarIMRChangeListener;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

/**
 * This is a GUI bean for selecting IMTs with an IMT-First approach. It takes a list
 * of IMRs and lists every IMT supported by at least one IMR.
 * 
 * @author kevin, modified by Brendon for GCIM specifics
 *
 */
public class IMT_GcimGuiBean extends ParameterListEditor
implements ParameterChangeListener, ScalarIMRChangeListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final static boolean D = false; //Debugging
	
	private static double default_period = 1.0;
	
	public final static String IMT_PARAM_NAME =  "IMT";
	
	public final static String TITLE =  "Set IMT";
	
	private boolean commonParamsOnly = false;
	
	private ParameterList imtParams;
	
	private StringParameter imtParameter;
	private Parameter<Double> imj; //The IMj parameter from the main hazard calc
	private ArrayList<String> currentIMiList; //The currently assigned list of IMi's
	
	private List<? extends ScalarIMR> imrs;
	private ArrayList<ImCorrelationRelationship> imCorrRels;
	
	private ArrayList<IMTChangeListener> listeners = new ArrayList<IMTChangeListener>();

	private ArrayList<Double> allPeriods;
	private ArrayList<Double> currentSupportedPeriods;
	
	/**
	 * Init with single IMR and IMCorrRel
	 * 
	 * @param imr
	 */
	public IMT_GcimGuiBean(ScalarIMR imr, ImCorrelationRelationship imCorrRel,
			Parameter<Double> imj, ArrayList<String> currentIMiList) {
		this(wrapInList(imr), wrapInList(imCorrRel), imj, currentIMiList);
	}
	
	/**
	 * Init with an IMR and IMCorrRel gui bean. Listeners will be set up so that the IMT GUI will be updated
	 * when the IMRs and IMCorrRels change, and visa-versa.
	 * 
	 * @param imrGuiBean
	 * @param imCorrRelGuiBean
	 */
	public IMT_GcimGuiBean(IMR_MultiGuiBean imrGuiBean, IMCorrRel_MultiGuiBean imCorrRelGuiBean,
			Parameter<Double> imj, ArrayList<String> currentIMiList) {
		this(imrGuiBean.getIMRs(),imCorrRelGuiBean.getIMCorrRels(), imj, currentIMiList);
		this.addIMTChangeListener(imrGuiBean);
		this.addIMTChangeListener(imCorrRelGuiBean);
		imrGuiBean.addIMRChangeListener(this);
		fireIMTChangeEvent();
	}
	
	/**
	 * Init with a list of IMRs and IMCorrRels
	 * 
	 * @param imrs
	 */
	public IMT_GcimGuiBean(List<? extends ScalarIMR> imrs, 
			ArrayList<ImCorrelationRelationship> imCorrRels, Parameter<Double> imj,
			ArrayList<String> currentIMiList) {
		this.setTitle(TITLE);
		this.imj = imj;
		this.currentIMiList = currentIMiList;
		setIMRsIMCorrRels(imrs, imCorrRels);
	}
	
	private static ArrayList<ScalarIMR> wrapInList(
			ScalarIMR imr) {
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		imrs.add(imr);
		return imrs;
	}
	
	private static ArrayList<ImCorrelationRelationship> wrapInList(
			ImCorrelationRelationship imCorrRel) {
		ArrayList<ImCorrelationRelationship> imCorrRels =
			new ArrayList<ImCorrelationRelationship>();
		imCorrRels.add(imCorrRel);
		return imCorrRels;
	}
	
	
	/**
	 * Setup IMT GUI for single IMR and IMCorrRel
	 * 
	 * @param imr
	 */
	public void setIMRIMCorrRel(ScalarIMR imr,
			ImCorrelationRelationship imCorrRel) {
		this.setIMRsIMCorrRels(wrapInList(imr),wrapInList(imCorrRel));
	}
	
	/**
	 * Set IMT GUI for multiple IMRs and IMCorrRels. All IMTs supported by at least one IMCorrRel
	 * and IMR will be displayed.
	 * 
	 * @param imrs, imCorrRels
	 */
	public void setIMRsIMCorrRels(List<? extends ScalarIMR> imrs,
			ArrayList<ImCorrelationRelationship> imCorrRels) {
		this.imrs = imrs;
		this.imCorrRels = imCorrRels;
		
		// first get a master list of all of the supported IM params  
		ArrayList<Double> saPeriods;
		ParameterList imiParamList_IMjSupport = new ParameterList();
		//Loop over all of the ImCorrRels
		for (ImCorrelationRelationship imCorrRel : imCorrRels) {
			//For each IMCorrRel loop over the supported IMjs
			ArrayList<Parameter<?>> imjParamList = imCorrRel.getSupportedIntensityMeasuresjList();
			for (int i = 0; i<imjParamList.size(); i++) {
				Parameter<?> imjParam = imjParamList.get(i);
				//Check if the imjParam is the imjName
				if (imjParam.getName()==imj.getName()) {
					//Now get the IMi pair that goes with this IMj
					Parameter<?> imiParam = imCorrRel.getSupportedIntensityMeasuresiList().get(i);
					//Hence this correlation relationship supports both imjName and imiParamName, now
					//check to see if any IMRs support this imiParamName
					for (ScalarIMR imr : imrs) {
						//Loop over the IMR supported IMis
						ParameterList imiIMRParamList = imr.getSupportedIntensityMeasures();
						for (Parameter<?> imiIMRParam : imiIMRParamList) {
						//for (int j = 0; j<imiIMRParamList.size(); j++) {
							//ParameterAPI<?> imiIMRParam = imiIMRParamList.getParameter(j);
							if (imiIMRParam.getName()==imiParam.getName()) {
								//Hence this imiParamName is supported by this IMR so store if not stored already
								if (imiParamList_IMjSupport.containsParameter(imiParam.getName())) {
									// it's already in there, do nothing
								} else {
									// add it to the list while putting a param listerner to SA period
									if (imiIMRParam.getName()==SA_InterpolatedParam.NAME) {
										PeriodInterpolatedParam interpPeriod = ((SA_InterpolatedParam)imiIMRParam).getPeriodInterpolatedParam();
										interpPeriod.addParameterChangeListener(this);
									}
									imiParamList_IMjSupport.addParameter(imiIMRParam);
								}
							}
						}
					}
				}
			}
		}
		//At this point the imiParamList contains all the IMT's for which an IMR and a correlation relation
		//between IMi and IMj exists, however it hasnt been checked yet whether there are correlation
		//relations between the IMi and all of the previously defined IMi's (refered to as IMk's below)
		//So perform this check now
		ParameterList imiParamList = new ParameterList();
		int numIMiWithIMjSupport = imiParamList_IMjSupport.size();
		int numCurrentIMis = currentIMiList.size();
		imiParamList = imiParamList_IMjSupport;
		ArrayList<String> imiParamList_IMkNoSupport = new ArrayList<String>();
		if (numCurrentIMis>0) {
			for (Parameter<?> imiParam : imiParamList_IMjSupport) {
			//for (int i=0; i<numIMiWithIMjSupport; i++){
				String imiName = imiParamList_IMjSupport.getParameterName(imiParam.getName());
				//String imiName = imiParamList_IMjSupport.getParameterName(i);
				//For each IMi check, for each IMk if there are IMCorrRels avaliable
				for (int k=0; k<numCurrentIMis; k++) {
					boolean imiHasIMkSupport = false;
					String imkName = currentIMiList.get(k);
					//Loop over the IMCorrRels to see if any support IMi and IMk
					for (ImCorrelationRelationship imCorrRel : imCorrRels) {
						//For each IMCorrRel loop over the supported IMks
						ArrayList<Parameter<?>> imkParamList = imCorrRel.getSupportedIntensityMeasuresjList();
						for (int m = 0; m<imkParamList.size(); m++) {
							Parameter<?> imkParam = imkParamList.get(m);
							//Check if the imkParam is the imkName
							if (imkParam.getName()==imkName) {
								//If so then check if the imiParam is the imiName
								Parameter<?> imiParamM = imCorrRel.getSupportedIntensityMeasuresiList().get(m);
								if (imiParamM.getName()==imiName) {
									imiHasIMkSupport = true;
								}
							}
						}
					}
					if (!imiHasIMkSupport) {
						imiParamList_IMkNoSupport.add(imiName);
					}
				}
			}
			//Now for all the imi's which dont have imk support remove them from the list
			for (int k=0; k<imiParamList_IMkNoSupport.size(); k++) {
				imiParamList.removeParameter(imiParamList_IMkNoSupport.get(k));
			}
		}
		
		SA_Param oldSAParam = null;
		SA_InterpolatedParam oldSAInterpolatedParam = null;
		if (commonParamsOnly) {
			// now we weed out the ones that aren't supported by everyone
			ParameterList toBeRemoved = new ParameterList();
			for (Parameter param : imiParamList) {
				boolean remove = false;
				for (ScalarIMR imr : imrs) {
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
					oldSAParam = (SA_Param)param;
				}
			}
			// now we remove them
			for (Parameter badParam : toBeRemoved) {
				imiParamList.removeParameter(badParam.getName());
			}
			saPeriods = getCommonPeriods(imrs);
		} else {
			for (Parameter<?> param : imiParamList) {
				if (param.getName().equals(SA_Param.NAME)) {
					oldSAParam = (SA_Param) param;
					break;
				} else if (param.getName().equals(SA_InterpolatedParam.NAME)) {
					oldSAInterpolatedParam = (SA_InterpolatedParam) param;
					break;
				}
			}
			saPeriods = getAllSupportedPeriods(imrs);
		}
		if (oldSAParam != null && imiParamList.containsParameter(oldSAParam.getName())) {
			Collections.sort(saPeriods);
			allPeriods = saPeriods;
			DoubleDiscreteConstraint pConst = new DoubleDiscreteConstraint(saPeriods);
			double defaultPeriod = default_period;
			if (!pConst.isAllowed(defaultPeriod))
				defaultPeriod = saPeriods.get(0);
			PeriodParam periodParam = new PeriodParam(pConst, defaultPeriod, true);
			periodParam.addParameterChangeListener(this);
//			System.out.println("new period param with " + saPeriods.size() + " periods");
			SA_Param replaceSA = new SA_Param(periodParam, oldSAParam.getDampingParam());
			replaceSA.setValue(defaultPeriod);
			imiParamList.replaceParameter(replaceSA.getName(), replaceSA);
		}
		
		this.imtParams = imiParamList;
		
		ParameterList finalParamList = new ParameterList();
		
		ArrayList<String> imtNames = new ArrayList<String>();
		for (Parameter<?> param : imiParamList) {
			imtNames.add(param.getName());
		}
		
		// add the IMT paramter
		imtParameter = new StringParameter (IMT_PARAM_NAME,imtNames,
				(String)imtNames.get(0));
		imtParameter.addParameterChangeListener(this);
		finalParamList.addParameter(imtParameter);
		for (Parameter<?> param : imiParamList) {
			finalParamList.addParameter(param);
		}
		updateGUI();
		fireIMTChangeEvent();
	}
	
	private void updateGUI() {
		ParameterList params = new ParameterList();
		params.addParameter(imtParameter);
		
		// now add the independent params for the selected IMT
		String imtName = imtParameter.getValue();
//		System.out.println("Updating GUI for: " + imtName);
		Parameter<?> imtParam = (Parameter<?>) imtParams.getParameter(imtName);
		for (Parameter<?> param : imtParam.getIndependentParameterList()) {
			if (param.getName().equals(PeriodParam.NAME)) {
				PeriodParam periodParam = (PeriodParam) param;
				ArrayList<Double> periods = currentSupportedPeriods;
				if (periods == null)
					periods = allPeriods;
				DoubleDiscreteConstraint pConst = new DoubleDiscreteConstraint(periods);
				periodParam.setConstraint(pConst);
				if (periodParam.getValue() == null) {
					if (periodParam.isAllowed(default_period))
						periodParam.setValue(default_period);
					else
						periodParam.setValue(periods.get(0));
				}
				periodParam.getEditor().setParameter(periodParam);
			}
			params.addParameter(param);
		}
		
		this.setParameterList(params);
		this.refreshParamEditor();
		this.revalidate();
		this.repaint();
	}
	
	/**
	 * Returns the name of the selected Intensity Measure
	 * 
	 * @return
	 */
	public String getSelectedIMT() {
		return ((StringParameter)imtParameter.clone()).getValue();
	}
	
	/**
	 * Set the selected Intensity Measure by name
	 * 
	 * @param imtName
	 */
	public void setSelectedIMT(String imtName) {
		if (!imtName.equals(getSelectedIMT())) {
			imtParameter.setValue(imtName);
		}
	}
	
	/**
	 * Set the selected Intensity measure period
	 * 
	 */
	public void setSelectedIMTPeriod(double period) {
		
		ParameterList params = new ParameterList();
		params.addParameter(imtParameter);
		
		// now add the independent params for the selected IMT
		String imtName = imtParameter.getValue();
		Parameter<?> imtParam = (Parameter<?>) imtParams.getParameter(imtName);
		for (Parameter<?> param : imtParam.getIndependentParameterList()) {
			if (param.getName().equals(PeriodParam.NAME)) {
				PeriodParam periodParam = (PeriodParam) param;
				periodParam.setValue(period);
				periodParam.getEditor().setParameter(periodParam);
			} else if (param.getName().equals(PeriodInterpolatedParam.NAME)) {
				PeriodInterpolatedParam periodParam = (PeriodInterpolatedParam) param;
				periodParam.setValue(period);
				periodParam.getEditor().setParameter(periodParam);
				
			}
			params.addParameter(param);
		}
		updateGUI();
		
	}
	
	public ArrayList<String> getSupportedIMTs() {
		return imtParameter.getAllowedStrings();
	}
	
	/**
	 * 
	 * @return The selected intensity measure parameter
	 */
	@SuppressWarnings("unchecked")
	public Parameter<Double> getSelectedIM() {
		return (Parameter<Double>) imtParams.getParameter(getSelectedIMT());
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		
		if (paramName.equals(IMT_PARAM_NAME)) {
			fireIMTChangeEvent();
			updateGUI();
		} else if (paramName.equals(PeriodParam.NAME)) {
			if (getSelectedIMT().equals(SA_Param.NAME))
				fireIMTChangeEvent();
		} else if (paramName.equals(PeriodInterpolatedParam.NAME)) {
			if (getSelectedIMT().equals(SA_InterpolatedParam.NAME))
				fireIMTChangeEvent();
		}
			
	}
	
	public void addIMTChangeListener(IMTChangeListener listener) {
		listeners.add(listener);
	}
	
	public void removeIMTChangeListener(IMTChangeListener listener) {
		listeners.remove(listener);
	}
	
	public void clearIMTChangeListeners(IMTChangeListener listener) {
		listeners.clear();
	}
	
	private void fireIMTChangeEvent() {
//		System.out.println("firing change event!");
		IMTChangeEvent event = new IMTChangeEvent(this, getSelectedIM());
		
		for (IMTChangeListener listener : listeners) {
			listener.imtChange(event);
		}
	}
	
	/**
	 * Sets the current IMT in the given IMR
	 * 
	 * @param imr
	 */
	public void setIMTinIMR(ScalarIMR imr) {
		setIMTinIMR(getSelectedIM(), imr);
	}
	
	/**
	 * Sets the current IMi and IMj in the given IMCorrRel
	 * 
	 * @param imr
	 */
	public void setIMTsInIMCorrRel(ImCorrelationRelationship imCorrRel, 
			Parameter<Double> imj) {
		setIMTsinIMCorrRel(getSelectedIM(), imj, imCorrRel);
	}
	
	/**
	 * Sets the current IM in each IMR in the given IMR map
	 * 
	 * @param imrMap
	 */
	public void setIMTinIMRs(Map<TectonicRegionType, ScalarIMR> imrMap) {
		setIMTinIMRs(getSelectedIM(), imrMap);
	}
	
	/**
	 * Sets the current IMTs in each IMCorrRel in the given IMCorrRel map
	 * 
	 * @param imrMap
	 */
	public void setIMTsinIMCorrRels(Map<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap,
			Parameter<Double> imj) {
		setIMTsInIMCorrRels(getSelectedIM(), imj, imCorrRelMap);
	}
	
	
	
	/**
	 * This will set the IMT (and it's independent params) in the given IMR. If you simply called
	 * <code>setIntensityMeasure</code> for the IMR, it might throw an error because the IMT could
	 * have been cloned or could be from another IMR. This gets around that problem by setting
	 * the IMT by name, then setting the value of each independent parameter.
	 * 
	 * @param imt
	 * @param imr
	 */
	@SuppressWarnings("unchecked")
	public static void setIMTinIMR(Parameter<Double> imt,ScalarIMR imr) {
		if (D) 
			System.out.println("Setting the imr (" + imr + ") with imt (" + imt + ")");
		
		imr.setIntensityMeasure(imt.getName());
		Parameter<Double> newIMT = (Parameter<Double>) imr.getIntensityMeasure();
		
		for (Parameter toBeSet : newIMT.getIndependentParameterList()) {
			Parameter newVal = imt.getIndependentParameter(toBeSet.getName());
			
			toBeSet.setValue(newVal.getValue());
		}
	}
	
	/**
	 * This will set the IMTs (both IMi and IMj and their independent params) in the given IMCorrRel. If you simply called
	 * <code>setIntensityMeasure</code> for the IMCorrRel, it might throw an error because the IMT could
	 * have been cloned or could be from another IMCorrRel. This gets around that problem by setting
	 * the IMCorrRel by name, then setting the value of each independent parameter.
	 * 
	 * @param imti, imtj
	 * @param imr
	 */
	@SuppressWarnings("unchecked")
	public static void setIMTsinIMCorrRel(
			Parameter<Double> imti, Parameter<Double> imtj,
			ImCorrelationRelationship imCorrRel) {
		
		//IMi
		imCorrRel.setIntensityMeasurei(imti.getName());
		Parameter<Double> newIMTi = (Parameter<Double>) imCorrRel.getIntensityMeasurei();
		
		for (Parameter toBeSet : newIMTi.getIndependentParameterList()) {
			Parameter newVal = imti.getIndependentParameter(toBeSet.getName());
			toBeSet.setValue(newVal.getValue());
		}
		
		//IMj
		imCorrRel.setIntensityMeasurej(imtj.getName());
		Parameter<Double> newIMTj = (Parameter<Double>) imCorrRel.getIntensityMeasurej();
		
		for (Parameter toBeSet : newIMTj.getIndependentParameterList()) {
			Parameter newVal = imtj.getIndependentParameter(toBeSet.getName());
			toBeSet.setValue(newVal.getValue());
		}
	}
	
	/**
	 * Set the IMT in each IMR contained in the IMR map
	 * 
	 * @param imt
	 * @param imrMap
	 */
	public static void setIMTinIMRs(
			Parameter<Double> imt,
			Map<TectonicRegionType, ScalarIMR> imrMap) {
		for (TectonicRegionType trt : imrMap.keySet()) {
			ScalarIMR imr = imrMap.get(trt);
			setIMTinIMR(imt, imr);
		}
	}
	
	/**
	 * Set the IMTs in each IMCorrRel contained in the IMCorrRel map
	 * 
	 * @param imti, imtj
	 * @param imrMap
	 */
	public static void setIMTsInIMCorrRels(
			Parameter<Double> imti, Parameter<Double> imtj,
			Map<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap) {
		for (TectonicRegionType trt : imCorrRelMap.keySet()) {
			ImCorrelationRelationship imCorrRel = imCorrRelMap.get(trt);
			setIMTsinIMCorrRel(imti, imtj, imCorrRel);
		}
	}
	
	/**
	 * Sets the periods that should be displayed...by default all periods will be displayed,
	 * even those only supported by a single IMR.
	 * 
	 * @param supportedPeriods
	 */
	public void setSupportedPeriods(ArrayList<Double> supportedPeriods) {
		this.currentSupportedPeriods = supportedPeriods;
		Collections.sort(currentSupportedPeriods);
		updateGUI();
	}
	
	/**
	 * Creates a list of periods common to all of the given IMRs
	 * 
	 * @param imrs
	 * @return
	 */
	public static ArrayList<Double> getCommonPeriods(Collection<? extends ScalarIMR> imrs) {
		ArrayList<Double> allPeriods = getAllSupportedPeriods(imrs);
		
		ArrayList<Double> commonPeriods = new ArrayList<Double>();
		for (Double period : allPeriods) {
			boolean include = true;
			for (ScalarIMR imr : imrs) {
				imr.setIntensityMeasure(SA_Param.NAME);
				SA_Param saParam = (SA_Param)imr.getIntensityMeasure();
				PeriodParam periodParam = saParam.getPeriodParam();
				if (!periodParam.isAllowed(period)) {
					include = false;
					break;
				}
			}
			
			if (include)
				commonPeriods.add(period);
		}
		
		return commonPeriods;
	}
	
	/**
	 * Creates a list of all periods found in any of the given IMRs
	 * 
	 * @param imrs
	 * @return
	 */
	public static ArrayList<Double> getAllSupportedPeriods(Collection<? extends ScalarIMR> imrs) {
		ArrayList<Double> periods = new ArrayList<Double>();
		for (ScalarIMR imr : imrs) {
			if (imr.isIntensityMeasureSupported(SA_Param.NAME)) {
				imr.setIntensityMeasure(SA_Param.NAME);
				SA_Param saParam = (SA_Param)imr.getIntensityMeasure();
				PeriodParam periodParam = saParam.getPeriodParam();
				for (double period : periodParam.getAllowedDoubles()) {
					if (!periods.contains(period))
						periods.add(period);
				}
			}
		}
		return periods;
	}

	@Override
	public void imrChange(ScalarIMRChangeEvent event) {
		this.setSupportedPeriods(getCommonPeriods(event.getNewIMRs().values()));
		//Make sure the imr has the most up to date IMT
		setIMTinIMRs(event.getNewIMRs());
	}
	
	/**
	 * This method gets the a single 
	 */
//	public ParameterAPI<?> getIMtParams(int index){
//		return imtParams.getParameter(index);
//	}
	public ParameterList getIMTParams() {
		return imtParams;
	}
}

