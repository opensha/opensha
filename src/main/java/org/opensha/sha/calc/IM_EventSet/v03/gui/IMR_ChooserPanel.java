package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.*;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel;
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
	private final IMT_ChooserPanel imtChooser;
	private final ParameterListEditor imrSiteParamsEdit;
    private final HashMap<String, ScalarIMR> imrNameMap = new HashMap<>(); // Lookup IMR by name in O(1)
    private List<? extends ScalarIMR> allIMRs;
	
	public IMR_ChooserPanel(IMT_ChooserPanel imtChooser) {
		super(null, "Selected IMR(s):");

		imtChooser.setForceDisableAddButton(true);
		this.imtChooser = imtChooser;

        buildValidIMRs(/*excludedIMRs=*/List.of(Abrahamson_2000_AttenRel.NAME));
        imrGuiBean = new IMR_MultiGuiBean(allIMRs);
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
     * Include all available IMRs
     */
    private void buildValidIMRs() {
       buildValidIMRs(null);
    }

    /**
     * Creates a list of IMRs valid for selection and sets allIMRs and imrNameMap.
     * Only considers IMRs not in excluded list
     * @param excluded Names of IMRs to exclude from the list of selectable IMRs
     */
    private void buildValidIMRs(List<String> excluded) {
        this.allIMRs = AttenRelRef.instanceList(
                null, true, ServerPrefUtils.SERVER_PREFS);
        if (excluded != null && !excluded.isEmpty()) {
            allIMRs.removeIf(imr -> excluded.contains(imr.getName()));
        }
            for (ScalarIMR imr : allIMRs) {
                imr.setParamDefaults();
                imrNameMap.put(imr.getName(), imr);
            }
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
		ListModel<?> model = namesList.getModel();
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
        updateSiteParams();
	}

	@Override
	public void addButton_actionPerformed() {
        // Select the new IMR
		ListModel<String> model = namesList.getModel();
		ScalarIMR imr = imrGuiBean.getSelectedIMR();
		String[] names = new String[model.getSize()+1];
		for (int i=0; i<model.getSize(); i++) {
			names[i] = model.getElementAt(i);
		}
		names[names.length - 1] = imr.getName();
		namesList.setListData(names);
		addButton.setEnabled(false);
        // Get names of IMTs we would need to remove
        List<String> removedIMTs = imtChooser.getIMTsRemoveFor(getSelectedIMRs());
        int confirmation = 0;
        if (!removedIMTs.isEmpty()) {
           // If there are IMTs that need to be removed from adding this IMR, prompt the user to confirm.
            confirmation = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to select IMR: " + imr.getName() + "?\n"
                              + "This will result in the deselection of IMT"
                              + (removedIMTs.size() > 1 ? "s: \n" : ": ")
                              + String.join(", ", removedIMTs),
                    "Confirm IMR Selection",
                    JOptionPane.YES_NO_OPTION);
        }
        // Proceed with IMR selection
        if (confirmation == 0) {
            // Update IMTs accordingly
            updateIMTs();
        // If user selected "No" or closed dialog without choosing
        } else {
            // Deselect the new IMR and don't touch selected IMTs.
           namesList.setListData(Arrays.copyOf(names, names.length-1));
        }
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
		namesList.setListData(new String[0]);
		updateIMTs();
	}

	public ArrayList<ScalarIMR> getSelectedIMRs() {
		ListModel<String> model = namesList.getModel();
		ArrayList<ScalarIMR> imrs = new ArrayList<>();
		for (int i=0; i<model.getSize(); i++) {
            String name = model.getElementAt(i);
			imrs.add(imrNameMap.get(name));
		}
		return imrs;
	}

    /**
     * The list of eligible IMTs in the IMT Chooser should reflect the
     * intersection of selected IMRs. updateIMTs is invoked whenever an IMR
     * is added or removed.
     */
	private void updateIMTs() {
		ArrayList<ScalarIMR> imrs = getSelectedIMRs();
        if (imrs.isEmpty()) {
            imrs = (ArrayList<ScalarIMR>) allIMRs;
        }
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
            String name = namesList.getSelectedValue();
            imrGuiBean.setSelectedSingleIMR(name);
		}
		super.valueChanged(e);
	}
}
