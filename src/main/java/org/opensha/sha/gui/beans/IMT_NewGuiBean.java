package org.opensha.sha.gui.beans;

import java.util.*;

import com.google.common.base.Preconditions;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.event.ScalarIMRChangeListener;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

import javax.swing.*;

/**
 * This is a GUI bean for selecting IMTs with an IMT-First approach. It takes a list
 * of IMRs and lists every IMT supported by at least one IMR.
 * 
 * @author kevin
 *
 */
public class IMT_NewGuiBean extends ParameterListEditor
implements ParameterChangeListener, ScalarIMRChangeListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static double default_period = 1.0;
	
	public final static String IMT_PARAM_NAME =  "IMT";
	
	public final static String TITLE =  "Set IMT";

    // Can be set at construction, determines if we should use union or intersect on params
	private boolean commonParamsOnly = false;

    private boolean imtsAvailable;
	
	private ParameterList imtParams;
	
	private StringParameter imtParameter;
	
	private List<? extends ScalarIMR> imrs;
	
	private ArrayList<IMTChangeListener> listeners = new ArrayList<IMTChangeListener>();
	
	private List<Double> allPeriods;
	private List<Double> currentSupportedPeriods;

    /**
     * Init with single IMR and set commonParamsOnly
     * @param imr
     * @param commonParamsOnly
     */
    public IMT_NewGuiBean(ScalarIMR imr, boolean commonParamsOnly) {
        this(wrapInList(imr), commonParamsOnly);
    }

	/**
	 * Init with single IMR
	 * @param imr
	 */
	public IMT_NewGuiBean(ScalarIMR imr) {
		this(wrapInList(imr));
	}

	/**
	 * Init with an IMR gui bean. Listeners will be set up so that the IMT GUI will be updated
	 * when the IMRs change, and visa-versa.
	 * @param imrGuiBean
	 */
	public IMT_NewGuiBean(IMR_MultiGuiBean imrGuiBean) {
        this(imrGuiBean, false);
	}

    /**
     * Init with GUI bean and set commonParamsOnly
     * @param imrGuiBean
     * @param commonParamsOnly
     */
    public IMT_NewGuiBean(IMR_MultiGuiBean imrGuiBean, boolean commonParamsOnly) {
        this(imrGuiBean.getIMRs(), commonParamsOnly);
        this.addIMTChangeListener(imrGuiBean);
        imrGuiBean.addIMRChangeListener(this);
        fireIMTChangeEvent();
    }

	/**
	 * Init with a list of IMRs
	 * @param imrs
	 */
	public IMT_NewGuiBean(List<? extends ScalarIMR> imrs) {
        this(imrs, false);
	}

    /**
     * Init with a list of IMRs and set commonParamsOnly
     * @param imrs
     * @param commonParamsOnly
     */
    public IMT_NewGuiBean(List<? extends ScalarIMR> imrs, boolean commonParamsOnly) {
        this.commonParamsOnly = commonParamsOnly;
        this.setTitle(TITLE);
        setIMRs(imrs);
    }

	private static ArrayList<ScalarIMR> wrapInList(
			ScalarIMR imr) {
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		imrs.add(imr);
		return imrs;
	}

    /**
     * If there is a valid IMT available for selection or just the "No IMTs available" placeholder.
     * Is used in IMT_Chooser to determine if Add button should be enabled.
     * @return if IMTs can be selected
     */
    public boolean areIMTsAvailable() {
        return imtsAvailable;
    }
	
	/**
	 * Setup IMT GUI for single IMR
	 * 
	 * @param imr
	 */
	public void setIMR(ScalarIMR imr) {
		this.setIMRs(wrapInList(imr));
	}
	
	/**
	 * Set IMT GUI for multiple IMRs to show supported IMTs.
	 * 
	 * @param imrs
	 */
	public void setIMRs(List<? extends ScalarIMR> imrs) {
        this.imrs = imrs;
        Preconditions.checkNotNull(imrs, "IMR list cannot be null!");
        Preconditions.checkArgument(!imrs.isEmpty(), "IMR list cannot be empty!");

        // Build ParameterList of supported params per IMR
        ParameterList[] supportedParamsPerIMR = new ParameterList[imrs.size()];
        for (int i = 0; i < supportedParamsPerIMR.length; i++) {
            supportedParamsPerIMR[i] = imrs.get(i).getSupportedIntensityMeasures();
        }

        // Build supported params and SA periods
        ParameterList paramList;
        ArrayList<Double> saPeriods;
        if (commonParamsOnly) {
            paramList = ParameterList.intersection(supportedParamsPerIMR);
            saPeriods = getCommonPeriods(imrs);
        } else {
            paramList = ParameterList.union(supportedParamsPerIMR);
            saPeriods = getAllSupportedPeriods(imrs);
        }

        // Get SA param if it is supported
        SA_Param oldSAParam = null;
        if (paramList.containsParameter(SA_Param.NAME)) {
            oldSAParam = (SA_Param) paramList.getParameter(SA_Param.NAME);
        }
        // Update the periods in the SA parameter
		if (oldSAParam != null && paramList.containsParameter(oldSAParam.getName())) {
			Collections.sort(saPeriods);
			this.allPeriods = saPeriods;
			DoubleDiscreteConstraint pConst = new DoubleDiscreteConstraint(saPeriods);
			double defaultPeriod = default_period;
			if (!pConst.isAllowed(defaultPeriod))
				defaultPeriod = saPeriods.get(0);
			PeriodParam periodParam = new PeriodParam(pConst, defaultPeriod, true);
			periodParam.addParameterChangeListener(this);
//			System.out.println("new period param with " + saPeriods.size() + " periods");
			SA_Param replaceSA = new SA_Param(periodParam, oldSAParam.getDampingParam());
			replaceSA.setValue(defaultPeriod);
			paramList.replaceParameter(replaceSA.getName(), replaceSA);
		}

		this.imtParams = paramList;

        imtsAvailable = !paramList.isEmpty();
        if (!imtsAvailable) {
            paramList.addParameter(new StringParameter("No IMTs available for selected IMRs"));
        }

		ArrayList<String> imtNames = new ArrayList<String>();
		for (Parameter<?> param : paramList) {
			imtNames.add(param.getName());
		}
		// add the IMT parameter
        ParameterList finalParamList = new ParameterList();
		imtParameter = new StringParameter(IMT_PARAM_NAME, imtNames, imtNames.get(0));
		imtParameter.addParameterChangeListener(this);
		finalParamList.addParameter(imtParameter);
        finalParamList.addParameterList(paramList);
        // Update GUI
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
				List<Double> periods = currentSupportedPeriods;
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
		return imtParameter.getValue();
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
		IMTChangeEvent event = new IMTChangeEvent(this, getSelectedIM());
		for (IMTChangeListener listener : listeners) {
			listener.imtChange(event);
		}
	}
	
	/**
	 * Sets the current IM in the given IMR
	 * 
	 * @param imr
	 */
	public void setIMTinIMR(ScalarIMR imr) {
		setIMTinIMR(getSelectedIM(), imr);
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
	 * This will set the IMT (and it's independent params) in the given IMR. If you simply called
	 * <code>setIntensityMeasure</code> for the IMR, it might throw an error because the IMT could
	 * have been cloned or could be from another IMR. This gets around that problem by setting
	 * the IMR by name, then setting the value of each independent parameter.
	 * 
	 * @param imt
	 * @param imr
	 */
	@SuppressWarnings("unchecked")
	public static void setIMTinIMR(
			Parameter<Double> imt,
			ScalarIMR imr) {
		imr.setIntensityMeasure(imt.getName());
		Parameter<Double> newIMT = (Parameter<Double>) imr.getIntensityMeasure();
		
		for (Parameter toBeSet : newIMT.getIndependentParameterList()) {
			Parameter newVal = imt.getIndependentParameter(toBeSet.getName());
			
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
	 * Sets the periods that should be displayed...by default all periods will be displayed,
	 * even those only supported by a single IMR.
	 * 
	 * @param supportedPeriods
	 */
	public void setSupportedPeriods(List<Double> supportedPeriods) {
		// this list might be immutable, so create a new one
		currentSupportedPeriods = new ArrayList<Double>(supportedPeriods.size());
		currentSupportedPeriods.addAll(supportedPeriods);
		Collections.sort(currentSupportedPeriods);
		updateGUI();
	}
	
	/**
	 * Creates a list of periods common to all given IMRs.
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
                if (!imr.isIntensityMeasureSupported(SA_Param.NAME)) {
                    // Return an empty list of there is an IMR that can't support SA periods
                   return new ArrayList<Double>();
                }
                imr.setIntensityMeasure(SA_Param.NAME);
                SA_Param saParam = (SA_Param) imr.getIntensityMeasure();
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
	}

    /**
     * IMT gui bean demo
     * @param args
     */
    public static void main(String[] args) {

        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 800);

        List<? extends ScalarIMR> imrs =
                AttenRelRef.instanceList(null, true, ServerPrefUtils.SERVER_PREFS);
        for (ScalarIMR imr : imrs) {
            imr.setParamDefaults();
        }
        IMT_NewGuiBean choose = new IMT_NewGuiBean(imrs);


        frame.setContentPane(choose);
        frame.setVisible(true);

    }
}
