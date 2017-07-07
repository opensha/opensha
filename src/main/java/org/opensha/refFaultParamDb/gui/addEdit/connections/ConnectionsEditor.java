package org.opensha.refFaultParamDb.gui.addEdit.connections;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.FaultSectionConnectionsDB_DAO;
import org.opensha.refFaultParamDb.dao.db.FaultSectionVer2_DB_DAO;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;
import org.opensha.refFaultParamDb.vo.FaultSectionConnection;
import org.opensha.refFaultParamDb.vo.FaultSectionConnectionList;
import org.opensha.refFaultParamDb.vo.FaultSectionData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

import com.google.common.collect.Lists;

public class ConnectionsEditor extends JPanel implements ActionListener, ParameterChangeListener {
	
	private FaultSectionVer2_DB_DAO fs2db;
	private FaultSectionConnectionsDB_DAO connsDB;
	private DB_AccessAPI db;
	
	FaultSectionConnectionList conns;
	
	private JTable table;
	private ConnectionsTableModel model;
	
	private JButton updateForEditingButton = new JButton("Enable/Refresh For Editing");
	
	private ArrayList<FaultSectionData> sectionData;
	private HashMap<Integer, FaultSectionData> sectionDataIDMap;
	private HashMap<Integer, ArrayList<PotentialConnectionPoint>> potentialConnectionsMap;
	
	private static final String DEFAULT_PARAM_MESSAGE = "(enable above to edit)";
	
	private static final String FIRST_SELECTION_NAME = "Fault 1";
	private StringParameter firstSectionSelector;
	
	private static final String SECOND_SELECTION_NAME = "Fault 2";
	private StringParameter secondSectionSelector;
	
	private static final String LOCATION_SELECTION_NAME = "End Locations";
	private static final String LOCATION_SELECTION_START_START =	"[Start] to [Start]";
	private static final String LOCATION_SELECTION_START_END =		"[Start] to [End]";
	private static final String LOCATION_SELECTION_END_START =		"[End] to [Start]";
	private static final String LOCATION_SELECTION_END_END =		"[End] to [End]";
	private StringParameter locationSelector;
	
	private FaultSectionData curFirstSection;
	private ArrayList<PotentialConnectionPoint> curSortedPotentialConnections;
	private PotentialConnectionPoint curSelectedConnection;
	
	private JButton addButton = new JButton("Add");
	private JButton removeButton = new JButton("Remove Selected");
	
	public ConnectionsEditor(DB_AccessAPI db) {
		super(new BorderLayout());
		
		this.db = db;
		connsDB = new FaultSectionConnectionsDB_DAO(db);
		fs2db = new FaultSectionVer2_DB_DAO(db);
		
		model = new ConnectionsTableModel(null, null);
		table = new JTable(model);
		
		updateConnections();
		
		JScrollPane tableScroll = new JScrollPane(table);
		
		updateForEditingButton.addActionListener(this);
		
		this.add(updateForEditingButton, BorderLayout.NORTH);
		this.add(tableScroll, BorderLayout.CENTER);
		
		firstSectionSelector = new StringParameter(FIRST_SELECTION_NAME, Lists.newArrayList(DEFAULT_PARAM_MESSAGE));
		firstSectionSelector.setValue(DEFAULT_PARAM_MESSAGE);
		firstSectionSelector.addParameterChangeListener(this);
		secondSectionSelector = new StringParameter(SECOND_SELECTION_NAME, Lists.newArrayList(DEFAULT_PARAM_MESSAGE));
		secondSectionSelector.setValue(DEFAULT_PARAM_MESSAGE);
		secondSectionSelector.addParameterChangeListener(this);
		locationSelector = new StringParameter(LOCATION_SELECTION_NAME, Lists.newArrayList(DEFAULT_PARAM_MESSAGE));
		locationSelector.setValue(DEFAULT_PARAM_MESSAGE);
		
		JPanel editorPanel = new JPanel();
		editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.Y_AXIS));
//		JLabel editLabel = new JLabel("Add New Connection");
//		editLabel.setFont(new Font(Font.SERIF, Font.BOLD, 20));
//		editorPanel.add(editLabel);
		JPanel paramEditPanel = new JPanel();
		paramEditPanel.setLayout(new BoxLayout(paramEditPanel, BoxLayout.X_AXIS));
		paramEditPanel.add(firstSectionSelector.getEditor().getComponent());
		paramEditPanel.add(secondSectionSelector.getEditor().getComponent());
		paramEditPanel.add(locationSelector.getEditor().getComponent());
		paramEditPanel.add(addButton);
		addButton.addActionListener(this);
		paramEditPanel.add(removeButton);
		removeButton.addActionListener(this);
		editorPanel.add(paramEditPanel);
		
		updateEnables();
		
		this.add(editorPanel, BorderLayout.SOUTH);
	}
	
	private void updateConnections() {
		conns = connsDB.getAllConnections();
		model.setConnections(conns);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == updateForEditingButton) {
			System.out.println("Fetching fault sections...");
			CalcProgressBar progress = new CalcProgressBar("Updating", null);
			progress.setProgressMessage("Fetching Fault Sections...");
			progress.showProgress(true);
			sectionData = fs2db.getAllFaultSections();
			Collections.sort(sectionData, new NamedComparator());
			sectionDataIDMap = new HashMap<Integer, FaultSectionData>();
			potentialConnectionsMap = new HashMap<Integer, ArrayList<PotentialConnectionPoint>>();
			
			progress.setProgressMessage("Calculating Distances...");
			// init maps
			for (FaultSectionData data : sectionData) {
				sectionDataIDMap.put(data.getSectionId(), data);
				potentialConnectionsMap.put(data.getSectionId(), new ArrayList<PotentialConnectionPoint>());
			}
			
			System.out.println("Finding possible connections...");
			// find every possible connection
			for (FaultSectionData data1 : sectionData) {
				ArrayList<PotentialConnectionPoint> conns = potentialConnectionsMap.get(data1.getSectionId());
				for (FaultSectionData data2 : sectionData) {
					if (data1 == data2)
						continue;
					PotentialConnectionPoint conn = new PotentialConnectionPoint(data1, data2);
					if (!conns.contains(conn)) {
						conn.compute();
						conns.add(conn);
						potentialConnectionsMap.get(data2.getSectionId()).add(conn.getReversal());
					}
				}
			}
			
			progress.showProgress(false);
			
			System.out.println("done.");
			model.setFaultSectionData(sectionDataIDMap);
			
			updateEnables();
			
			updateFirstSelector();
		} else if (e.getSource() == addButton) {
			// make sure it doesn't already exist
			int id1 = curSelectedConnection.getFSD1().getSectionId();
			int id2 = curSelectedConnection.getFSD2().getSectionId();
			if (conns.containsConnectionBetween(id1, id2)) {
				JOptionPane.showMessageDialog(this, "A connection already exists between these faults",
						"Connection Already Exsists!", JOptionPane.ERROR_MESSAGE);
				return;
			}
			FaultTrace trace1 = curSelectedConnection.getFSD1().getFaultTrace();
			FaultTrace trace2 = curSelectedConnection.getFSD2().getFaultTrace();
			Location loc1, loc2;
			int locInd = ((StringConstraint)locationSelector.getConstraint()).getAllowedStrings().indexOf(locationSelector.getValue());
			switch (locInd) {
			case 0:
				loc1 = trace1.get(0);
				loc2 = trace2.get(0);
				break;
			case 1:
				loc1 = trace1.get(0);
				loc2 = trace2.get(trace2.size()-1);
				break;
			case 2:
				loc1 = trace1.get(trace1.size()-1);
				loc2 = trace2.get(0);
				break;
			case 3:
				loc1 = trace1.get(trace1.size()-1);
				loc2 = trace2.get(trace2.size()-1);
				break;

			default:
				throw new IllegalStateException("Illegal location index: "+locInd);
			}
			FaultSectionConnection conn = new FaultSectionConnection(id1, id2, loc1, loc2);
			connsDB.addConnection(conn);
			updateConnections();
		} else if (e.getSource() == removeButton) {
			int[] rows = table.getSelectedRows();
			if (rows == null || rows.length == 0) {
				JOptionPane.showMessageDialog(this, "No connections selected for removal.",
						"Nothing selected.", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			int ret = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove "+rows.length+" rows?",
					"Removing "+rows.length+" Rows", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (ret == JOptionPane.OK_OPTION) {
				for (int row : rows) {
					int id1 = (Integer) model.getValueAt(row, 0);
					int id2 = (Integer) model.getValueAt(row, 2);
					System.out.println("Removing connection: "+id1+" => "+id2);
					connsDB.removeConnection(id1, id2);
				}
			}
			updateConnections();
		}
	}
	
	private void updateEnables() {
		boolean updated = sectionData != null;
		boolean editable = SessionInfo.getContributor() != null;
		
		firstSectionSelector.getEditor().setEnabled(updated);
		secondSectionSelector.getEditor().setEnabled(updated);
		locationSelector.getEditor().setEnabled(updated);
		addButton.setEnabled(updated && editable);
		removeButton.setEnabled(editable);
	}
	
	private ArrayList<String> getSectionNames(int skipID) {
		ArrayList<String> names = new ArrayList<String>();
		
		for (FaultSectionData data : sectionData)
			if (data.getSectionId() != skipID)
				names.add(data.getSectionId()+". "+data.getSectionName());
		
		return names;
	}
	
	private void updateFirstSelector() {
		System.out.println("Updating first selector");
		ArrayList<String> names = getSectionNames(-1);
		
		StringConstraint constr = (StringConstraint)firstSectionSelector.getConstraint();
		constr.setStrings(names);
		
		firstSectionSelector.setValue(names.get(0));
		// this will trigger a change event for the frist selector, calling the below code
		
		firstSectionSelector.getEditor().refreshParamEditor();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == firstSectionSelector) {
			System.out.println("Updating second selector");
			// update the current section
			int sectionInd = ((StringConstraint)firstSectionSelector.getConstraint()).getAllowedStrings()
					.indexOf(firstSectionSelector.getValue());
			curFirstSection = sectionData.get(sectionInd);
			
			// update the 2nd one
			curSortedPotentialConnections = new ArrayList<PotentialConnectionPoint>();
			curSortedPotentialConnections.addAll(potentialConnectionsMap.get(curFirstSection.getSectionId()));
			Collections.sort(curSortedPotentialConnections);
			
			// create list of potential connections
			ArrayList<String> potentialNames = new ArrayList<String>();
			for (PotentialConnectionPoint pot : curSortedPotentialConnections)
				potentialNames.add(pot.getFSD2().getSectionId()+". "+pot.getFSD2().getName());
			
			// remove change listener so that we don't get duplicate updatse
			secondSectionSelector.removeParameterChangeListener(this);
			((StringConstraint)secondSectionSelector.getConstraint()).setStrings(potentialNames);
			secondSectionSelector.setValue(potentialNames.get(0));
			secondSectionSelector.getEditor().refreshParamEditor();
			secondUpdated();
			// add change listener again
			secondSectionSelector.addParameterChangeListener(this);			
		} else if (event.getParameter() == secondSectionSelector) {
			secondUpdated();
		}
	}
	
	private void secondUpdated() {
		System.out.println("Updating location");
		// set the currently selected connection
		curSelectedConnection = curSortedPotentialConnections.get(((StringConstraint)secondSectionSelector.getConstraint())
				.getAllowedStrings().indexOf(secondSectionSelector.getValue()));
		
		// update the location
		ArrayList<String> strings = new ArrayList<String>();
		
		double d00, d01, d10, d11;
		double min;
		int minInd = -1;
		
		d00 = curSelectedConnection.getDistance(0, 0);
		min = d00;
		minInd = 0;
		strings.add(LOCATION_SELECTION_START_START+": "+	(float)d00+" KM");
		d01 = curSelectedConnection.getDistance(0, 1);
		if (d01 < min) {
			min = d01;
			minInd = 1;
		}
		strings.add(LOCATION_SELECTION_START_END+": "+		(float)d01+" KM");
		d10 = curSelectedConnection.getDistance(1, 0);
		if (d10 < min) {
			min = d10;
			minInd = 2;
		}
		strings.add(LOCATION_SELECTION_END_START+": "+		(float)d10+" KM");
		d11 = curSelectedConnection.getDistance(1, 1);
		if (d11 < min) {
			min = d11;
			minInd = 3;
		}
		strings.add(LOCATION_SELECTION_END_END+": "+		(float)d11+" KM");
		
		((StringConstraint)locationSelector.getConstraint()).setStrings(strings);
		locationSelector.setValue(strings.get(minInd));
		locationSelector.getEditor().refreshParamEditor();
	}

}
