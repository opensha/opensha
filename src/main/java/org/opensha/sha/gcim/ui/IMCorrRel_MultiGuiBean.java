package org.opensha.sha.gcim.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import org.opensha.commons.gui.LabeledBoxPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.ListUtils;
import org.opensha.commons.util.NtoNMap;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.event.IMCorrRelChangeEvent;
import org.opensha.sha.gcim.imCorrRel.event.IMCorrRelChangeListener;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

/**
 * This is a IMCorrRel selection GUI which allows for multiple IM Correlation Relations to be selected
 * and edited, one for each Tectonic Region Type.
 * 
 * @author Brendon Bradley 
 * @version 1.0 5 July 2010
 * @version 2.0 4 Jan 2011 - added HARD CODED lines to store default IMikj correlation relations
 * which are needed for GCIM simulations
 *
 */
public class IMCorrRel_MultiGuiBean extends LabeledBoxPanel implements ActionListener, IMTChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static final Font trtFont = new Font("TRTFont", Font.BOLD, 16);

	private ArrayList<IMCorrRelChangeListener> imCorrRelChangeListeners = new ArrayList<IMCorrRelChangeListener>();

	private JPanel checkPanel;
	protected JCheckBox singleIMCorrRelBox = new JCheckBox("Single IMCorrRel For All Tectonic Region Types");

	private ArrayList<ImCorrelationRelationship> imCorrRels;
	private ArrayList<Boolean> imCorrRelEnables;

	private ArrayList<TectonicRegionType> regions = null;
	
	private IMCorrRel_ParamEditor paramEdit = null;
	private int chooserForEditor = 0;
	
	private ArrayList<ShowHideButton> showHideButtons = null;
	private ArrayList<ChooserComboBox> chooserBoxes = null;

	private HashMap<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap;
	private ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> imikCorrRelMap =
		new ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>>();

	private Parameter<Double> imti = null;
	private Parameter<Double> imtj = null;
	
	private int maxChooserChars = Integer.MAX_VALUE;
	
	private int defaultIMCorrRelIndex = 0;

	/**
	 * Initializes the GUI with the given list of IMCorrRels
	 * 
	 * @param imCorrRels
	 */
	public IMCorrRel_MultiGuiBean(ArrayList<ImCorrelationRelationship> imCorrRels, 
			Parameter<Double> imtj) {
		this.imCorrRels = imCorrRels;
		this.imtj = imtj;
		imCorrRelEnables = new ArrayList<Boolean>();
		for (int i=0; i<imCorrRels.size(); i++) {
			imCorrRelEnables.add(new Boolean(true));
		}

		initGUI();
		updateIMCorrRelMap();
	}

	private void initGUI() {
		setLayout(new BoxLayout(editorPanel, BoxLayout.Y_AXIS));
		singleIMCorrRelBox.setFont(new Font("My Font", Font.PLAIN, 10));
		singleIMCorrRelBox.addActionListener(this);
		paramEdit = new IMCorrRel_ParamEditor();
		this.setTitle("Set IMCorrRel");

		rebuildGUI();
	}

	/**
	 * This rebuilds all components of the GUI for display
	 */
	public void rebuildGUI() {
		rebuildGUI(false);
	}

	/**
	 * This rebuilds all components of the GUI for display. If refreshOnly is true,
	 * then the GUI will just be refreshed with editor panels updated, otherwise it will
	 * be rebuilt from the ground up. You can only refresh on a show/hide button click,
	 * otherwise you should rebuild for all events
	 */
	private void rebuildGUI(boolean refreshOnly) {
//		System.out.println("rebuildGUI...refreshOnly? " + refreshOnly);
		// even for a refresh, we remove all components and re-add
		this.removeAll();
		if (regions == null || regions.size() <= 1) {
			// if we don't have enough regions for multiple IMCorrRels, make sure the
			// single IMCorrRel box is selected, but don't show it
			singleIMCorrRelBox.setSelected(true);
		} else {
			// this means there's the possibility of multiple IMCorrRels...show the box
			checkPanel = new JPanel();
			checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.X_AXIS));
			checkPanel.add(singleIMCorrRelBox);
			this.add(checkPanel);
		}
		if (!refreshOnly) {
			// if we're rebuilding, we have to re-init the GUI elements
			chooserBoxes = new ArrayList<ChooserComboBox>();
			showHideButtons = null;
		}
		if (!singleIMCorrRelBox.isSelected()) {
			// this is for multiple IMCorrRels
			if (!refreshOnly)
				showHideButtons = new ArrayList<ShowHideButton>();
			for (int i=0; i<regions.size(); i++) {
				// create label for tectonic region
				TectonicRegionType region = regions.get(i);
				JLabel label = new JLabel(region.toString());
				label.setFont(trtFont);
				this.add(wrapInPanel(label));
				
				// get the chooser and button. if we're rebuilding, chooser
				// and button need to be recreated
				ChooserComboBox chooser;
				ShowHideButton button;
				if (refreshOnly) {
					chooser = chooserBoxes.get(i);
					button = showHideButtons.get(i);
				} else {
					chooser = new ChooserComboBox(i);
//					chooser.addActionListener(this);
					chooserBoxes.add(chooser);
					button = new ShowHideButton(false);
					button.addActionListener(this);
					showHideButtons.add(button);
				}

				//				JPanel panel = new JPanel();
				//				panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
				this.add(wrapInPanel(chooser));
				this.add(wrapInPanel(button));

				//				this.add(wrapInPanel(panel));
				if (button.isShowing()) {
					// if the show params button is selected, update and add the parameter editor
					// to the GUI
					chooserForEditor = i;
					updateParamEdit(chooser);
					this.add(paramEdit);
				}
			}
		} else {
			// this is for single IMCorrRel mode
			ChooserComboBox chooser;
			if (refreshOnly) {
				chooser = chooserBoxes.get(0);
			} else {
				chooser = new ChooserComboBox(0);
				chooser.setBackground(Color.WHITE);
//				chooser.addActionListener(this);
				chooserBoxes.add(chooser);
			}
			// we simply add chooser 0 to the GUI, and show the params for the selected IMCorrRel
			this.add(wrapInPanel(chooser));
			chooserForEditor = 0;
			updateParamEdit(chooser);
			this.add(paramEdit);
		}
		this.validate();
		this.paintAll(getGraphics());
	}

	private static JPanel wrapInPanel(JComponent comp) {
		JPanel panel = new JPanel();
		panel.add(comp);
		return panel;
	}
	
	/**
	 * 
	 * @return true if the single IMCorrRel check box is visible in the GUI
	 */
	public boolean isCheckBoxVisible() {
		if (checkPanel == null)
			return false;
		else
			return checkPanel.isAncestorOf(singleIMCorrRelBox) && this.isAncestorOf(checkPanel);
	}

	/**
	 * This sets the tectonic regions for the GUI. If regions is not null and contains multiple,
	 * TRTs, then the user can select multiple IMCorrRels
	 * 
	 * This triggers a GUI rebuild, and will fire an IMCorrRel Change Event if necessary
	 * 
	 * @param regions
	 */
	public void setTectonicRegions(ArrayList<TectonicRegionType> regions) {
		// we can refresh only if there are none or < 2 regions, and the check box isn't showing
		boolean refreshOnly = (regions == null || regions.size() < 2) && !isCheckBoxVisible();
		this.regions = regions;
		boolean prevSingle = !isMultipleIMCorrRels();
		this.rebuildGUI(refreshOnly);
		boolean newSingle = !isMultipleIMCorrRels();
		// update the IMCorrRel map if we rebuilt the GUI, and it didn't both start and end as single IMCorrRel.
		// we dont' want to fire an event if we changed TRTs from null to something, but still have single
		// IMCorrRel selected.
		if (!refreshOnly && !(prevSingle && newSingle))
			fireUpdateIMCorrRelMap();
	}
	
	/**
	 * 
	 * @return the list Tectonic Regions from the GUI
	 */
	public ArrayList<TectonicRegionType> getTectonicRegions() {
		return regions;
	}

	private static String showParamsTitle = "Edit IMCorrRel Params";
	private static String hideParamsTitle = "Hide IMCorrRel Params";
	/**
	 * This is an internal class for a button that shows/hides parameter editors in the multi-IMCorrRel GUI
	 * @author Brendon
	 *
	 */
	private class ShowHideButton extends JButton {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private boolean showing;

		public ShowHideButton(boolean initial) {
			this.showing = initial;

			updateText();
		}

		private void updateText() {
			if (showing)
				this.setText(hideParamsTitle);
			else
				this.setText(showParamsTitle);
		}

		private void hideParams() {
			showing = false;
			updateText();
		}

		public void toggle() {
			showing = !showing;
			updateText();
		}

		public boolean isShowing() {
			return showing;
		}
	}

	protected static final Font supportedTRTFont = new Font("supportedFont", Font.BOLD, 12);
	protected static final Font unsupportedTRTFont = new Font("supportedFont", Font.ITALIC, 12);
	
	/**
	 * This class is the cell renderer for the drop down chooser boxes. It adds
	 * the ability to enable/disable a selected item.
	 * 
	 * If a Tectonic Region Type is given, it will render IMCorrRels the support the TRT
	 * bold, and those that don't in italics.
	 * 
	 * @author Brendon - modified from kevins IMR version
	 *
	 */
	public class EnableableCellRenderer extends BasicComboBoxRenderer {
		
		protected ArrayList<Boolean> trtSupported = null;
		
		public EnableableCellRenderer(TectonicRegionType trt) {
			if (trt != null) {
				trtSupported = new ArrayList<Boolean>();
				for (ImCorrelationRelationship imCorrRel : imCorrRels) {
					trtSupported.add(imCorrRel.isTectonicRegionSupported(trt));
				}
			}
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (!isSelected)
				comp.setBackground(Color.white);
			if (index >= 0) {
				comp.setEnabled(imCorrRelEnables.get(index));
				setFont(comp, index);
			} else {
				int selIndex = list.getSelectedIndex();
				setFont(comp, selIndex);
				comp.setEnabled(true);
			}
			return comp;
		}
		
		public void setFont(Component comp, int index) {
			if (trtSupported != null) {
				if (trtSupported.get(index))
					comp.setFont(supportedTRTFont);
				else
					comp.setFont(unsupportedTRTFont);
			}
		}

	}

	/**
	 * Internal sub-class for IMCorrRel chooser combo box
	 * 
	 * @author Brendon
	 *
	 */
	public class ChooserComboBox extends JComboBox {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private int comboBoxIndex;
		public ChooserComboBox(int index) {
			for (ImCorrelationRelationship imCorrRel : imCorrRels) {
				String name = imCorrRel.getName();
				if (name.length() > maxChooserChars) {
					name = name.substring(0, maxChooserChars);
				}
				this.addItem(name);
			}
			
			if (!imCorrRelEnables.get(defaultIMCorrRelIndex)) {
				for (int i=0; i<imCorrRelEnables.size(); i++) {
					if (imCorrRelEnables.get(i).booleanValue()) {
//						System.out.println("Const...set imr to " + imrs.get(i).getName());
						defaultIMCorrRelIndex = i;
						break;
					}
				}
			}
			this.setSelectedIndex(defaultIMCorrRelIndex);

			TectonicRegionType trt = null;
			if (isMultipleIMCorrRels())
				trt = regions.get(index);
			this.setRenderer(new EnableableCellRenderer(trt));
			
			this.comboBoxIndex = index;
			this.addActionListener(new ComboListener(this));
			this.setMaximumSize(new Dimension(15, 150));
		}

		public int getIndex() {
			return comboBoxIndex;
		}
	}
	
	protected ChooserComboBox getChooser(TectonicRegionType trt) {
		if (!isMultipleIMCorrRels())
			return chooserBoxes.get(0);
		for (int i=0; i<regions.size(); i++) {
			if (regions.get(i).toString().equals(trt.toString()))
				return chooserBoxes.get(i);
		}
		return null;
	}

	/**
	 * This internal class makes sure that the user selected an enabled IMCorrRel in the list.
	 * If the selected one is disabled, it reverts to the previous selection.
	 * 
	 * @author Brendon
	 *
	 */
	class ComboListener implements ActionListener {
		ChooserComboBox combo;

		Object currentItem;

		ComboListener(ChooserComboBox combo) {
			this.combo = combo;
			currentItem = combo.getSelectedItem();
		}

		public void actionPerformed(ActionEvent e) {
			Object tempItem = combo.getSelectedItem();
			// if the selected one isn't enabled, then go back to the old one
			if (!imCorrRelEnables.get(combo.getSelectedIndex())) {
//				System.out.println("Just selected a bad IMCorrRel: " + combo.getSelectedItem());
				combo.setSelectedItem(currentItem);
				updateParamEdit(combo);
//				System.out.println("reverted to: " + combo.getSelectedItem());
			} else {
				currentItem = tempItem;
				updateParamEdit(combo);
				fireUpdateIMCorrRelMap();
			}
		}
	}
	
	private void updateParamEdit(ChooserComboBox chooser) {
		if (chooser.getIndex() == 0 && !isMultipleIMCorrRels()) {
			// if we're in single mode, and this is the single chooser, then 
			// the default IMCorrRel index should be this chooser's value
			defaultIMCorrRelIndex = chooser.getSelectedIndex();
		}
		if (chooserForEditor == chooser.getIndex()) {
			// this is a check to make sure that we're updating the param editor for the
			// currently showing chooser
			
			ImCorrelationRelationship imCorrRel = imCorrRels.get(chooser.getSelectedIndex());
//			System.out.println("Updating param edit for chooser " + chooserForEditor + " to : " + imCorrRel.getName());
			paramEdit.setIMCorrRel(imCorrRel);
			// we only want to show the TRT param if it's in single mode
			paramEdit.setTRTParamVisible(!this.isMultipleIMCorrRels());
			paramEdit.setTitle(IMCorrRel_ParamEditor.DEFAULT_NAME + ": " + imCorrRel.getShortName());
			paramEdit.validate();
		}
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source instanceof ShowHideButton) {
			// one of the buttons to show or hide parameters in the GUI was clicked
			
			ShowHideButton button = (ShowHideButton)source;
			// toggle the button that was clicked
			button.toggle();
			// make sure that every other button has params hidden
			for (ShowHideButton theButton : showHideButtons) {
				if (theButton == button)
					continue;
				theButton.hideParams();
			}
			// since we're just showing params, we can rebuild with
			// a simple refresh instead of re-creating each GUI element
			rebuildGUI(true);
		} else if (source == singleIMCorrRelBox) {
			// this means the user either selected or deselected the
			// single/multiple IMCorrRel check box...full GUI rebuild
			rebuildGUI();
			fireUpdateIMCorrRelMap();
//		} else if (source instanceof ChooserComboBox) {
//			// this means the user changed one of the selected IMCorrRels in a
//			// chooser list
//			ChooserComboBox chooser = (ChooserComboBox)source;
//			updateParamEdit(chooser);
//		}
//		if (source == singleIMCorrRelBox || source instanceof ChooserComboBox) {
//			// if we switched between single/multiple, or changed a selected
//			// imCorr relationship, then we have to update the in-memory
//			// IMCorrRel map and fire an IMCorrRel Change event
//			fireUpdateIMCorrRelMap();
		}
	}

	private ImCorrelationRelationship getIMCorrRelForChooser(int chooserID) {
		ChooserComboBox chooser = chooserBoxes.get(chooserID);
		return imCorrRels.get(chooser.getSelectedIndex());
	}

	/**
	 * 
	 * @return true if multiple IMCorrRels are both enabled, and selected
	 */
	public boolean isMultipleIMCorrRels() {
		return !singleIMCorrRelBox.isSelected();
	}
	
	/**
	 * this enables/disables the multiple IMCorrRel check box.
	 * @param enabled
	 */
	public void setMultipleIMCorrRelsEnabled(boolean enabled) {
		if (!enabled)
			setMultipleIMCorrRels(false);
		singleIMCorrRelBox.setEnabled(enabled);
	}

	/**
	 * This returns the selected IMCorrRel if only a single one is selected. Otherwise, a
	 * <code>RuntimeException</code> is thrown.
	 * 
	 * @return
	 */
	public ImCorrelationRelationship getSelectedIMCorrRel() {
		if (isMultipleIMCorrRels())
			throw new RuntimeException("Cannot get single selected IMCorrRel when multiple selected!");
		return getIMCorrRelForChooser(0);
	}
	
	/**
	 * In multiple IMCorrRel mode, shows the parameter editor for the IMCorrRel associated with the
	 * given tectonic region type.
	 * 
	 * @param trt
	 */
	public void showParamEditor(TectonicRegionType trt) {
		if (!isMultipleIMCorrRels())
			throw new RuntimeException("Cannot show param editor for TRT in single IMCorrRel mode!");
		for (int i=0; i<regions.size(); i++) {
			if (regions.get(i).toString().equals(trt.toString())) {
				ShowHideButton button = showHideButtons.get(i);
				if (button.isShowing())
					button.doClick();
				return;
			}
		}
		throw new RuntimeException("TRT '" + trt.toString() + "' not found!");
	}
	
	protected IMCorrRel_ParamEditor getParamEdit() {
		return paramEdit;
	}

	/**
	 * This returns a clone of the current IMCorrRel map in the GUI. This internal IMCorrRel map is updated
	 * when certain actions are preformed, and should always be up to date.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public HashMap<TectonicRegionType, ImCorrelationRelationship> getIMCorrRelMap() {
		return (HashMap<TectonicRegionType, ImCorrelationRelationship>) imCorrRelMap.clone();
	}
	
	/**
	 * This returns a clone of the current IMikCorrRel map in the GUI. This internal IMikCorrRel map is updated
	 * when certain actions are preformed, and should always be up to date.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> getIMikCorrRelMap() {
		return (ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>>) imikCorrRelMap.clone();
	}

	/**
	 * This updates the current in-memory IMCorrRel map (the one returned by <code>getIMCorrRelMap()</code>)
	 */
	public void updateIMCorrRelMap() {
		HashMap<TectonicRegionType, ImCorrelationRelationship> map =
			new HashMap<TectonicRegionType, ImCorrelationRelationship>();

		if (!isMultipleIMCorrRels()) {
			ImCorrelationRelationship imCorrRel = getIMCorrRelForChooser(0);
			map.put(TectonicRegionType.ACTIVE_SHALLOW, imCorrRel);
		} else {
			for (int i=0; i<regions.size(); i++) {
				TectonicRegionType region = regions.get(i);
				map.put(region, getIMCorrRelForChooser(i));
			}
		}

		this.imCorrRelMap = map;
		
		//For now HARD-CODE the off-diagonal correlation terms until the GUI to handle these is developed
		//These are hard coded in the IMT change near the base of this method
	}

	/**
	 * Sets the GUI to multiple/single IMCorrRel mode. If setting to multiple, but multiple isn't
	 * supported, a <code>RundimeException</code> is thrown.
	 * 
	 * The GUI will be updated, and IMCorrRel an change event will be fired as needed.
	 * 
	 * @param multipleIMCorrRels
	 */
	public void setMultipleIMCorrRels(boolean multipleIMCorrRels) {
		// if they're trying to set it to multiple, but we don't have multiple tectonic regions
		// then throw an exception
		if (multipleIMCorrRels && (regions == null || regions.size() <= 1))
			throw new RuntimeException("Cannot be set to multiple IMCorrRels if < 2 tectonic regions" +
			" sepcified");
		boolean previous = isMultipleIMCorrRels();
		if (previous != multipleIMCorrRels) {
			singleIMCorrRelBox.setSelected(!multipleIMCorrRels);
//			System.out.println("changing singleIMCorrRelBox to " + (!multipleIMCorrRels));
			rebuildGUI(false);
			fireUpdateIMCorrRelMap();
		}
	}
	
	/**
	 * Sets the GUI to multiple/single IMikCorrRel mode. If setting to multiple, but multiple isn't
	 * supported, a <code>RundimeException</code> is thrown.
	 * 
	 * The GUI will be updated, and IMCorrRel an change event will be fired as needed.
	 * 
	 * @param multipleIMCorrRels
	 */
	public void setMultipleIMikCorrRels(boolean multipleIMikCorrRels) {
		// if they're trying to set it to multiple, but we don't have multiple tectonic regions
		// then throw an exception
		if (multipleIMikCorrRels && (regions == null || regions.size() <= 1))
			throw new RuntimeException("Cannot be set to multiple IMCorrRels if < 2 tectonic regions" +
			" sepcified");
		boolean previous = isMultipleIMCorrRels();
		if (previous != multipleIMikCorrRels) {
			singleIMCorrRelBox.setSelected(!multipleIMikCorrRels);
//			System.out.println("changing singleIMCorrRelBox to " + (!multipleIMCorrRels));
			rebuildGUI(false);
			fireUpdateIMCorrRelMap();
		}
	}

	/**
	 * Sets the GUI to single IMCorrRel mode, and sets the selected IMCorrRel to the given name.
	 * 
	 * @param imCorrRelName
	 */
	public void setSelectedSingleIMCorrRel(String imCorrRelName) {
		setMultipleIMCorrRels(false);
		ChooserComboBox chooser = chooserBoxes.get(0);
		int index = ListUtils.getIndexByName(imCorrRels, imCorrRelName);
		if (index < 0)
			throw new NoSuchElementException("IMR '" + imCorrRelName + "' not found");
		ImCorrelationRelationship imCorrRel = imCorrRels.get(index);
		if (!shouldEnableIMCorrRel(imCorrRel))
			throw new RuntimeException("IMCorrRel '" + imCorrRelName + "' cannot be set because it is not" +
					" supported by the current IMTs, '" + imti.getName() + "', and, '" + imtj.getName() + "'.");
		chooser.setSelectedIndex(index);
	}
	
	public void setIMCorrRel(String imCorrRelName, TectonicRegionType trt) {
		if (!isMultipleIMCorrRels())
			throw new RuntimeException("IMCorrRel cannot be set for a Tectonic Region in single IMCorrRel mode");
		if (trt == null)
			throw new IllegalArgumentException("Tectonic Region Type cannot be null!");
		for (int i=0; i<regions.size(); i++) {
			if (trt.toString().equals(regions.get(i).toString())) {
				int index = ListUtils.getIndexByName(imCorrRels, imCorrRelName);
				if (index < 0)
					throw new NoSuchElementException("IMCorrRel '" + imCorrRelName + "' not found");
				ImCorrelationRelationship imCorrRel = imCorrRels.get(index);
				if (!shouldEnableIMCorrRel(imCorrRel))
					throw new RuntimeException("IMCorrRel '" + imCorrRelName + "' cannot be set because it is not" +
							" supported by the current IMTs, '" + imti.getName() + "', and, '" + imti.getName() + "'.");
				chooserBoxes.get(i).setSelectedIndex(index);
				return;
			}
		}
		throw new RuntimeException("TRT '" + trt.toString() + "' not found!");
	}
	
	/**
	 * Sets the GUI to single IMikCorrRel mode, and sets the selected IMikCorrRel to the given name.
	 * THIS IS HARD CODED - NEEDS TO BE CODED CORRECTLY AT A LATER DATE
	 * @param imCorrRelName
	 */
	public void setSelectedSingleIMikCorrRel(int corrRelIndex, ImCorrelationRelationship imikCorrRel) {
		HashMap<TectonicRegionType, ImCorrelationRelationship> map =
			new HashMap<TectonicRegionType, ImCorrelationRelationship>();

		if (!isMultipleIMCorrRels()) {
			ImCorrelationRelationship imCorrRel = imikCorrRel; //Hard coded
			map.put(TectonicRegionType.ACTIVE_SHALLOW, imCorrRel);
		} else {
			for (int i=0; i<regions.size(); i++) {
				TectonicRegionType region = regions.get(i);
				map.put(region, imikCorrRel);  //Hard coded
			}
		}
		
		int A = imikCorrRelMap.size();
		if ((corrRelIndex+1)>imikCorrRelMap.size()) {
			imikCorrRelMap.add(map);
		}
		else {
			imikCorrRelMap.set(corrRelIndex, map);
		}
			
		
	}
	

	public void addIMCorrRelChangeListener(IMCorrRelChangeListener listener) {
		imCorrRelChangeListeners.add(listener);
	}

	public void removeIMCorrRelChangeListener(IMCorrRelChangeListener listener) {
		imCorrRelChangeListeners.remove(listener);
	}

	private void fireUpdateIMCorrRelMap() {
		HashMap<TectonicRegionType, ImCorrelationRelationship> oldMap = imCorrRelMap;
		updateIMCorrRelMap();
		fireIMCorrRelChangeEvent(oldMap, imCorrRelMap);
	}

	private void fireIMCorrRelChangeEvent(
			HashMap<TectonicRegionType, ImCorrelationRelationship> oldMap,
			HashMap<TectonicRegionType, ImCorrelationRelationship> newMap) {
		IMCorrRelChangeEvent event = new IMCorrRelChangeEvent(this, oldMap, newMap);
//		System.out.println("Firing IMCorrRel Change Event");
		for (IMCorrRelChangeListener listener : imCorrRelChangeListeners) {
			listener.imCorrRelChange(event);
		}
	}
	
	public boolean isIMCorrRelEnabled(String imCorrRelName) {
		int index = ListUtils.getIndexByName(imCorrRels, imCorrRelName);
		if (index < 0)
			throw new NoSuchElementException("IMCorrRel '" + imCorrRelName + "' not found!");
		
		return imCorrRelEnables.get(index);
	}
	
	/**
	 * the imCorrRel should be enabled if:
	 * * no imt has been selected
	 *  OR
	 * * the imti and imtj are supported
	 * @param imCorrRel
	 * @return
	 */
	private boolean shouldEnableIMCorrRel(ImCorrelationRelationship imCorrRel) {
		if (imti == null)
			return true;
		else {
			ArrayList<Parameter<?>> imjImCorrRelParamList = imCorrRel.getSupportedIntensityMeasuresjList();
			for (int j = 0; j<imjImCorrRelParamList.size(); j++) {
				Parameter<?> imjImCorrRelParam = imjImCorrRelParamList.get(j);
				
				if (imjImCorrRelParam.getName()==imtj.getName()) {
					ArrayList<Parameter<?>> imiImCorrRelParamList = imCorrRel.getSupportedIntensityMeasuresiList();
					Parameter<?> imiImCorrRelParam = imiImCorrRelParamList.get(j);
					if (imiImCorrRelParam.getName()==imti.getName()) {
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * Sets the IMTi that this GUI should use. All IMCorrRels that don't support this IMTi will
	 * be disabled.
	 * 
	 * @param newIMTi - new IMTi, or null to enable all IMCorrRels
	 */
	public void setIMTi(Parameter<Double> newIMTi) {
		this.imti = newIMTi;

		for (int i=0; i<imCorrRels.size(); i++) {
			ImCorrelationRelationship imCorrRel = imCorrRels.get(i);
			Boolean enabled = shouldEnableIMCorrRel(imCorrRel);
			imCorrRelEnables.set(i, enabled);
		}
		for (ChooserComboBox chooser : chooserBoxes) {
			// if the selected imCorrRel is disabled
			if (!imCorrRelEnables.get(chooser.getSelectedIndex())) {
				// then we select the first enabled one in the list and use that
				for (int i=0; i<chooser.getItemCount(); i++) {
					if (imCorrRelEnables.get(i)) {
						chooser.setSelectedIndex(i);
						break;
					}
				}
			}
			chooser.repaint();
		}
	}
	
	/**
	 * Returns a clone of this GUI's IMCorrRel list
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<ImCorrelationRelationship> getIMCorrRels() {
		return (ArrayList<ImCorrelationRelationship>) imCorrRels.clone();
	}
	
	public NtoNMap<TectonicRegionType, ImCorrelationRelationship> getNtoNMap() {
		NtoNMap<TectonicRegionType, ImCorrelationRelationship> map =
			new NtoNMap<TectonicRegionType, ImCorrelationRelationship>();
		for (TectonicRegionType trt : imCorrRelMap.keySet()) {
			map.put(trt, imCorrRelMap.get(trt));
		}
		return map;
	}
	
//	/**
//	 * 
//	 * @return IMCorrRel metadata as HTML for display
//	 */
//	public String getIMCorrRelMetadataHTML() {
//		if (isMultipleIMCorrRels()) {
//			NtoNMap<TectonicRegionType, ImCorrelationRelationship> map = getNtoNMap();
//			String meta = null;
//			Set<ImCorrelationRelationship> myIMCorrRels = map.getRights();
//			for (ImCorrelationRelationship imCorrRel : myIMCorrRels) {
//				if (meta == null)
//					meta = "";
//				else
//					meta += "<br>";
//				meta += "--- IMCorrRel: " + imCorrRel.getName() + " ---<br>";
//				String trtNames = null;
//				Collection<TectonicRegionType> trtsForIMCorrRel = map.getLefts(imCorrRel);
//				for (TectonicRegionType trt : trtsForIMCorrRel) {
//					if (trtNames == null)
//						trtNames = "";
//					else
//						trtNames += ", ";
//					trtNames += trt.toString();
//				}
//				meta += "--- TectonicRegion";
//				if (trtsForIMCorrRel.size() > 1)
//					meta += "s";
//				meta += ": " + trtNames + " ---<br>";
//				meta += "--- Params ---<br>";
//				ParameterList paramList = (ParameterList) imCorrRel.getOtherParamsList().clone();
//				if (paramList.containsParameter(TectonicRegionTypeParam.NAME))
//					paramList.removeParameter(TectonicRegionTypeParam.NAME);
//				meta += paramList.getParameterListMetadataString();
//			}
//			return meta;
//		} else {
//			String meta = "IMCorrRel = " + getSelectedIMCorrRel().getName() + "; ";
//			meta += paramEdit.getVisibleParameters().getParameterListMetadataString();
//			return meta;
//		}
//	}

	@Override
	public void imtChange(IMTChangeEvent e) {
//		System.out.println("IMTChangeEvent: " + e.getNewIMT().getName());
		this.setIMTi(e.getNewIMT());
	}
	
	/**
	 * Sets the number of characters that should be displayed in the chooser lists. This helps
	 * to constrain GUI width.
	 * 
	 * @param maxChooserChars
	 */
	public void setMaxChooserChars(int maxChooserChars) {
		this.maxChooserChars = maxChooserChars;
	}

}
