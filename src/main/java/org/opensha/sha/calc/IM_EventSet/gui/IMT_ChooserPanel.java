package org.opensha.sha.calc.IM_EventSet.gui;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.JFrame;
import javax.swing.ListModel;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.IM_EventSet.IM_EventSetOutputWriter;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * Select multiple IMTs from a list of IMTs and set corresponding IMT parameters (i.e., Period/Damping).
 * As an extension of the NamesListPanel, our namesList represents selected IMTs.
 */
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
		imts = new ArrayList<>();
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
        Parameter<?> newIMT = imtGuiBean.getSelectedIM();
        Parameter<?> clone = (Parameter<?>) newIMT.clone();
        for (Parameter<?> param : newIMT.getIndependentParameterList()) {
            if (!clone.containsIndependentParameter(param.getName())) {
                Parameter<?> independentParamClone = (Parameter<?>) param.clone();
                independentParamClone.addParameterChangeListener(this);
                clone.addIndependentParameter(independentParamClone);
            }
        }
        clone.addParameterChangeListener(this);
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
				if (imt.getName().equals(SA_Param.NAME)) {
					double oldPeriod = (Double) oldIMT.getIndependentParameter(PeriodParam.NAME).getValue();
//					double newPeriod = (Double) imtGuiBean.getParameterList().getParameter(PeriodParam.NAME).getValue();
                    double newPeriod = (Double) imt.getIndependentParameter(PeriodParam.NAME).getValue();
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
     * This method returns a list of IMTs would be deselected if the given IMRs were selected.
     * This considers the current state of IMTs and IMRs selected without modifying state or actually selecting the IMRs.
     * This allows us to prompt a user "Are you sure?" before they add an IMR that could potentially deselect IMTs.
     * @param imrs Potential new IMR list
     * @return list of names of IMTs to remove if the given IMRs were selected
     */
    public List<String> getIMTsRemoveFor(List<ScalarIMR> imrs) {
        List<String> toRemove = new ArrayList<>();
        for (int i=imts.size()-1; i>=0; i--) {
            Parameter<?> imt = imts.get(i);
            for (ScalarIMR imr : imrs) {
                if (!imr.isIntensityMeasureSupported(imt)) {
                    String name = imt.getName();
                    if (name.equals(SA_Param.NAME)) {
                        // Add period to differentiate multiple SA IMTs.
                        name += "(" + imt.getIndependentParameter(PeriodParam.NAME).getValue() + ")";
                    }
                    toRemove.add(name);
                    break;
                }
            }
        }
        Collections.reverse(toRemove);
        return toRemove;
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
		ArrayList<Integer> toRemove = new ArrayList<>();
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
		ArrayList<String> strings = new ArrayList<>();
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
