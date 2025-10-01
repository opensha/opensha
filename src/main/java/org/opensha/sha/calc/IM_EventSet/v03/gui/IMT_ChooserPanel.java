package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.JFrame;
import javax.swing.ListModel;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

public class IMT_ChooserPanel extends NamesListPanel implements ParameterChangeListener {

    // The IMT Gui bean shows IMTs that can be selected
	private final IMT_NewGuiBean imtGuiBean;
    // This IMTs list shows what have been selected
    private final ArrayList<Parameter<?>> imts;
	private boolean masterDisable = false;

	public IMT_ChooserPanel() {
		super("Selected IMT(s):");

        // Initially shows no IMTs eligible by taking intersection of params from all IMRs
        List<? extends ScalarIMR> imrs =
                AttenRelRef.instanceList(null, true, ServerPrefUtils.SERVER_PREFS);
        for (ScalarIMR imr : imrs) {
            imr.setParamDefaults();
        }

        // Initialize the IMT GUI Bean
		imtGuiBean = new IMT_NewGuiBean(imrs, /*commonParamsOnly=*/true);
//        imtGuiBean.setSelectedIMT(SA_Param.NAME);
//        imtGuiBean.addIMTChangeListener(this);
		imts = new ArrayList<Parameter<?>>();
		setLowerPanel(imtGuiBean);
	}
	
	public void setForceDisableAddButton(boolean disable) {
		masterDisable = disable;
		this.addButton.setEnabled(shouldEnableAddButton());
	}
	
	protected void rebuildList() {
		String[] names = new String[imts.size()];
		for (int i=0; i<imts.size(); i++) {
			Parameter<?> imt = imts.get(i);
			names[i] = IM_EventSetOutputWriter.getRegularIMTString(imt) + " (HAZ01 code: " + IM_EventSetOutputWriter.getHAZ01IMTString(imt) + ")";
		}
		namesList.setListData(names);
	}

	@Override
	public void addButton_actionPerformed() {
		ListModel<String> model = namesList.getModel();
		Parameter<?> newIMT = (Parameter<?>) imtGuiBean.getSelectedIM();
		Parameter<?> clone = (Parameter<?>) newIMT.clone();
		for (Parameter<?> param : newIMT.getIndependentParameterList()) {
            if (!clone.containsIndependentParameter(param.getName())) {
                clone.addIndependentParameter((Parameter<?>) param.clone());
            }
        }
		imts.add(clone);
		
		rebuildList();
	}

	@Override
	public void removeButton_actionPerformed() {
		ListModel<String> model = namesList.getModel();
		String[] names = new String[model.getSize()-1];
		int selected = namesList.getSelectedIndex();
		int cnt = 0;
		for (int i=0; i<model.getSize(); i++) {
			if (selected == i) {
				// remove it
				imts.remove(i);
				continue;
			} else {
				names[cnt] = model.getElementAt(i);
				cnt++;
			}
		}
		namesList.setListData(names);
	}

	@Override
	public boolean shouldEnableAddButton() {
		if (masterDisable || !imtGuiBean.areIMTsAvailable())
			return false;
		Parameter<?> imt = imtGuiBean.getSelectedIM();
		for (Parameter<?> oldIMT : imts) {
			if (imt.getName().equals(oldIMT.getName())) {
                // TODO: This allows selecting multiple SA IMTs with unique periods.
                //       Is this a good idea? Why only with SA and not other IMTs?
				if (imt.getName().equals(SA_Param.NAME)) {
					Parameter<?> oldParam = (Parameter<?>) oldIMT;
					Parameter<?> newParam = (Parameter<?>) imt;
					double oldPeriod = (Double) oldParam.getIndependentParameter(PeriodParam.NAME).getValue();
					double newPeriod = (Double) imtGuiBean.getParameterList().getParameter(PeriodParam.NAME).getValue();
					if (newPeriod == oldPeriod) {
//						System.out.println("Returning a SA false: " + newPeriod + ", " + oldPeriod);
						return false;
					}
				} else {
//					System.out.println("Returning a non SA false");
					return false;
				}
			}
		}
		return true;
	}

    /**
     * Set the IMRs in the IMT GUI bean to show only IMTs that work for all IMRs.
     * Also unsets any already selected IMTs that are no longer valid.
     * @param imrs
     */
	public void setIMRs(ArrayList<ScalarIMR> imrs) {
		imtGuiBean.setIMRs(imrs);
		
		if (imrs == null || imrs.isEmpty()) {
//			System.err.println("WARNING: empty IMR array!");
			this.setForceDisableAddButton(true);
			return;
		}
		
		// now we need to see if we have already selected any IMTs that don't work
		// for one of the new IMRs
		ArrayList<Integer> toRemove = new ArrayList<Integer>();
		for (int i=imts.size()-1; i>=0; i--) {
			Parameter<?> imt = imts.get(i);
			for (ScalarIMR imr : imrs) {
				if (!imr.isIntensityMeasureSupported(imt)) {
					toRemove.add(i);
					break;
				}
			}
		}
		if (!toRemove.isEmpty()) {
			for (int index : toRemove) {
//				System.out.println("Removing a now-invalid IMT!");
				imts.remove(index);
			}
			rebuildList();
		}
		this.setForceDisableAddButton(imrs.isEmpty());
		imtGuiBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME).addParameterChangeListener(this);
		imtGuiBean.refreshParamEditor();
		imtGuiBean.invalidate();
		imtGuiBean.validate();
		this.validate();
	}

	/**
     * Demo
	 * @param args
	 */
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 600);
		
		IMT_ChooserPanel choose = new IMT_ChooserPanel();

		frame.setContentPane(choose);
		frame.setVisible(true);
	}
	
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getNewValue().equals(SA_Param.NAME)) {
			Parameter<?> periodParam = imtGuiBean.getParameterList().getParameter(PeriodParam.NAME);
			periodParam.addParameterChangeListener(this);
		}
		addButton.setEnabled(shouldEnableAddButton());
	}
	
	public ArrayList<String> getIMTStrings() {
		ArrayList<String> strings = new ArrayList<String>();
		for (Parameter<?> param : imts) {
			strings.add(IM_EventSetOutputWriter.getRegularIMTString(param));
		}
		return strings;
	}
	
	public void setIMTs(ArrayList<String> imts) {
		for (String imt : imts) {
			StringTokenizer tok = new StringTokenizer(imt.trim());
			String imtName = tok.nextToken();
			
			this.imtGuiBean.getParameterList();
			this.imtGuiBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME);
			this.imtGuiBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME).setValue(imtName);
			if (tok.hasMoreTokens()) {
				Double period = Double.parseDouble(tok.nextToken());
				this.imtGuiBean.getParameterList().getParameter(PeriodParam.NAME).setValue(period);
			}
			this.addButton_actionPerformed();
		}
	}

}
