package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.event.ScalarIMRChangeListener;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Select multiple IMRs from a list of IMRs and set corresponding IMR parameters and site params.
 * As an extension of the NamesListPanel, our namesList represents selected IMRs.
 */
public class IMR_ChooserPanel extends NamesListPanel implements ScalarIMRChangeListener {
	
	private final IMR_MultiGuiBean imrGuiBean;
	private final IMT_ChooserPanel imtChooser; // TODO: Finish migrating IMT_GuiBean to IMT_NewGuiBean
	private final ParameterListEditor imrSiteParamsEdit;
    private final HashMap<String, ScalarIMR> imrNameMap = new HashMap<>(); // Lookup IMR by name in O(1)
	
	public IMR_ChooserPanel(IMT_ChooserPanel imtChooser) {
		super(null, "Selected IMR(s):");
		
		imtChooser.setForceDisableAddButton(true);
		this.imtChooser = imtChooser;

        List<? extends ScalarIMR> imrs =
                AttenRelRef.instanceList(null, true, ServerPrefUtils.SERVER_PREFS);
        for (ScalarIMR imr : imrs) {
            imr.setParamDefaults();
            imrNameMap.put(imr.getName(), imr);
        }
        imrGuiBean = new IMR_MultiGuiBean(imrs);
        // TODO: What is multiple IMR mode w/ multiple tectonic regions?
        // imrGuiBean.setMultipleIMRs(true);
		imrGuiBean.addIMRChangeListener(this);
		
		imrSiteParamsEdit = new ParameterListEditor();
		imrSiteParamsEdit.setTitle("Default Site Params");
		
		JPanel imPanel = new JPanel();
		imPanel.setLayout(new BoxLayout(imPanel, BoxLayout.Y_AXIS));
		imPanel.add(imrGuiBean);
		imPanel.add(imrSiteParamsEdit);
		
		updateSiteParams();
		
		setLowerPanel(imPanel);
		updateIMTs();
	}

    /**
     * Gets default parameters for first IMR in list (AS1997)
     */
	public void updateSiteParams() {
        // TODO: * Why is this only invoked once in constructor for AS1997?
        //       * Shouldn't we get the params for each selected IMR?
        //       * We could invoke this again in imrChange()

        // We will eventually need to build a ParameterList from all
        // selected IMRs for use in the AddSitePanel
        // TODO: Confirm if we need the Union of IMR site data parms uniquely per
        //       site location or if these params are applied across all added
        ScalarIMR imr = imrGuiBean.getSelectedIMR();
        updateSiteParams(imr);
	}
	
	private void updateSiteParams(ScalarIMR imr) {
		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator(); // TODO: Why is this deprecated?
		ParameterList list = new ParameterList();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
//			System.out.println("adding: " + param.getName());
			list.addParameter(param);
		}
		imrSiteParamsEdit.setParameterList(list);
		imrSiteParamsEdit.refreshParamEditor();
		imrSiteParamsEdit.validate();
		this.validate();
	}

    /**
     * The add button should be disabled if the IMR is already in the selected IMRs list
     * @param imr The IMR selected in the "Set IMR" box
     * @return if the add button should be clickable or greyed out
     */
	private boolean shouldEnableAddButton(ScalarIMR imr) {
		ListModel model = namesList.getModel();
		boolean match = false;
		for (int i=0; i<model.getSize(); i++) {
			if (model.getElementAt(i).toString().equals(imr.getName())) {
				match = true;
				break;
			}
		}
		return !match;
	}

	/**
	 * tester main method
	 * @param args
	 */
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 600);
		
		frame.setContentPane(new IMR_ChooserPanel(new IMT_ChooserPanel()));
		frame.setVisible(true);
	}

	public void imrChange(ScalarIMRChangeEvent event) {
		HashMap<TectonicRegionType, ScalarIMR> imrMap = event.getNewIMRs();
		ScalarIMR imr = TRTUtils.getFirstIMR(imrMap);
		addButton.setEnabled(shouldEnableAddButton(imr));
	}

	@Override
	public void addButton_actionPerformed() {
		ListModel model = namesList.getModel();
		ScalarIMR imr = imrGuiBean.getSelectedIMR();
		Object names[] = new Object[model.getSize()+1];
		for (int i=0; i<model.getSize(); i++) {
			names[i] = model.getElementAt(i);
		}
		names[names.length - 1] = imr.getName();
		namesList.setListData(names);
		addButton.setEnabled(false);
		updateIMTs();
	}

	@Override
	public void removeButton_actionPerformed() {
		ListModel model = namesList.getModel();
		ScalarIMR imr = imrGuiBean.getSelectedIMR();
		Object names[] = new Object[model.getSize()-1];
		int selected = namesList.getSelectedIndex();
		int cnt = 0;
		for (int i=0; i<model.getSize(); i++) {
			if (selected == i) {
				// remove it
				continue;
			} else {
				names[cnt] = model.getElementAt(i);
				cnt++;
			}
		}
		namesList.setListData(names);
		updateIMTs();
	}
	
	public void clear() {
		namesList.setListData(new Object[0]);
		updateIMTs();
	}

    // TODO: Delete
    // This method sets the parameters for each of the IMRs passed in the Gui Bean parameter list
    // This list in IMR_GuiBean does not exist in IMR_MultiGuiBean, making this unnecessary
//	public void setForIMRS(ArrayList<ScalarIMR> imrs) {
//		this.clear();
//		String names[] = new String[imrs.size()];
//		for (int i=0; i<imrs.size(); i++) {
//			ScalarIMR imr = imrs.get(i);
//			ScalarIMR myIMR = imrGuiBean.getIMR_Instance(imr.getName());
//			ListIterator<Parameter<?>> paramIt = myIMR.getOtherParamsIterator();
//			while (paramIt.hasNext()) {
//				Parameter param = paramIt.next();
//				param.setValue(imr.getParameter(param.getName()).getValue());
//			}
//			names[i] = imr.getName();
//		}
//		this.namesList.setListData(names);
//		this.imrGuiBean.refreshParamEditor();
//	}

	public ArrayList<ScalarIMR> getSelectedIMRs() {
		ListModel model = namesList.getModel();
		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();
		for (int i=0; i<model.getSize(); i++) {
            String name = (String)model.getElementAt(i);
			imrs.add(imrNameMap.get(name));
		}
		return imrs;
	}
	
	private void updateIMTs() {
		ArrayList<ScalarIMR> imrs = getSelectedIMRs();
		imtChooser.setIMRs(imrs);
	}

	@Override
	public boolean shouldEnableAddButton() {
		ScalarIMR imr = imrGuiBean.getSelectedIMR();
		return shouldEnableAddButton(imr);
	}

    /**
     * Executes on clicking one of the IMRs in the list of "Selected IMR(s)"
     */
	@Override
	public void valueChanged(ListSelectionEvent e) {
		int index = namesList.getSelectedIndex();
		if (index >= 0) {
            String name = namesList.getSelectedValue().toString();
            imrGuiBean.setSelectedSingleIMR(name);
		}
		super.valueChanged(e);
	}
}
