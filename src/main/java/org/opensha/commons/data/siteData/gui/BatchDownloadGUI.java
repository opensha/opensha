package org.opensha.commons.data.siteData.gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValueList;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.CSVTable;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.IntegerDiscreteConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.util.CustomFileFilter;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.SimpleBugMessagePanel;

import com.google.common.base.Preconditions;

public class BatchDownloadGUI extends JFrame implements ActionListener, ParameterChangeListener,
ChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private OrderedSiteDataProviderList list;

	private JButton loadButton = new JButton("Load Sites");
	private String loadButtonInfo =
		"<html>Loads a list of locations where site data is to be retreived.<br><br>" +
		"Data can be in CSV or TXT (space/tab delimeted) formats. You will be<br>" +
		"prompted to specify which columns contain the laitude/longitude values.</html>";

	private JButton saveButton = new JButton("Save Data");
	private String saveButtonInfo =
		"<html>Saves the current data to a CSV or TXT file.</html>";

	private JButton fetchPreferredButton = new JButton("Fetch Preferred Data");
	private String fetchPreferredButtonInfo =
		"<html>This retrieves and stores the first valid value of each data type<br>" +
		"from the currently enabled data sources, in order of priority.</html>";

	private JButton fetchAllButton = new JButton("Fetch All Data");
	private String fetchAllButtonInfo =
		"<html>This retrieves and stores all data values from the<br>" +
		"currently enabled data sources, in order of priority.</html>";

	private JFileChooser loadChooser;
	private JFileChooser saveChooser;

	private CustomFileFilter csvFilter = new CustomFileFilter(".csv", "CSV file");
	private CustomFileFilter txtFilter = new CustomFileFilter(".txt", "TXT file (tab/space delimeted)");

	private LocationList locations;
	private CSVFile<String> inputCSV;
	private CSVFile<String> dataCSV;

	private ArrayList<SiteDataValueList<?>> valLists;

	private CSVTable table = new CSVTable();

	// this is input file format stuff
	private IntegerParameter numColumnsParam;
	private IntegerParameter latColumnParam;
	private IntegerParameter lonColumnParam;
	private ParameterList inputFormatParamList;
	private ParameterListEditor inputFormatEditor;
	private IntegerParameter txtHeaderLinesParam;
	private BooleanParameter csvHasHeaderParam;

	public BatchDownloadGUI(OrderedSiteDataProviderList list) {
		setTitle("Batch Data Download");
		this.list = list;
		table.setEditingEnabled(false);
		
		list.addChangeListener(this);

		this.getContentPane().setLayout(new BorderLayout());

		saveButton.setEnabled(false);
		fetchPreferredButton.setEnabled(false);
		fetchAllButton.setEnabled(false);

		loadButton.setToolTipText(loadButtonInfo);
		saveButton.setToolTipText(saveButtonInfo);
		fetchPreferredButton.setToolTipText(fetchPreferredButtonInfo);
		fetchAllButton.setToolTipText(fetchAllButtonInfo);

		loadButton.addActionListener(this);
		saveButton.addActionListener(this);
		fetchPreferredButton.addActionListener(this);
		fetchAllButton.addActionListener(this);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

		buttonPanel.add(loadButton);
		buttonPanel.add(saveButton);
		buttonPanel.add(fetchPreferredButton);
		buttonPanel.add(fetchAllButton);

		this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		JScrollPane tableScroll = new JScrollPane(table);

		this.getContentPane().add(tableScroll, BorderLayout.CENTER);

		this.setSize(800, 600);
	}

	private static ArrayList<Integer> getDiscrete(int max) {
		ArrayList<Integer> vals = new ArrayList<Integer>();
		for (int i=1; i<=max; i++)
			vals.add(i);
		return vals;
	}

	private ParameterListEditor getInputFormatParamEditor(File file) {
		if (inputFormatParamList == null) {
			numColumnsParam = new IntegerParameter("Num. Columns", 2, 20);
			numColumnsParam.setValue(2);
			numColumnsParam.addParameterChangeListener(this);

			IntegerDiscreteConstraint colConst = new IntegerDiscreteConstraint(
					getDiscrete(numColumnsParam.getValue()));

			latColumnParam = new IntegerParameter("Latitude Column");
			latColumnParam.setConstraint(colConst);
			latColumnParam.setValue(1);

			lonColumnParam = new IntegerParameter("Longitude Column");
			lonColumnParam.setConstraint(colConst);
			lonColumnParam.setValue(2);

			txtHeaderLinesParam = new IntegerParameter("Num. Header Lines", 0, 100);
			txtHeaderLinesParam.setValue(0);

			csvHasHeaderParam = new BooleanParameter("1st Line Is Header?", true);

			inputFormatParamList = new ParameterList();
			inputFormatParamList.addParameter(numColumnsParam);
			inputFormatParamList.addParameter(latColumnParam);
			inputFormatParamList.addParameter(lonColumnParam);
			inputFormatParamList.addParameter(txtHeaderLinesParam);
			inputFormatParamList.addParameter(csvHasHeaderParam);

			inputFormatEditor = new ParameterListEditor(inputFormatParamList);
		}

		boolean csv = file.getName().toLowerCase().endsWith(".csv");
		inputFormatEditor.setParameterVisible(txtHeaderLinesParam.getName(), !csv);
		inputFormatEditor.setParameterVisible(csvHasHeaderParam.getName(), csv);

		return inputFormatEditor;
	}

	private boolean isInputConfigurationValid() {
		int numColumns = numColumnsParam.getValue();
		int latColumn = latColumnParam.getValue();
		int lonColumn = lonColumnParam.getValue();

		if (latColumn < 1 || lonColumn < 1)
			return false;

		if (latColumn > numColumns || lonColumn > numColumns)
			return false;

		if (latColumn == lonColumn)
			return false;

		return true;
	}

	private static void validateLatLon(List<String> line, int lineNumber, int latColumn, int lonColumn) {
		String latStr = line.get(latColumn-1);
		String lonStr = line.get(lonColumn-1);

		try {
			double lat = Double.parseDouble(latStr);
			GeoTools.validateLat(lat);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Incorrectly formatted latitutde value at line "+lineNumber+": "+latStr);
		}

		try {
			double lon = Double.parseDouble(lonStr);
			GeoTools.validateLon(lon);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Incorrectly formatted longitude value at line "+lineNumber+": "+lonStr);
		}
	}

	private static ArrayList<String> buildDefaultHeader(int numColumns, int latColumn, int lonColumn) {
		ArrayList<String> header = new ArrayList<String>();
		for (int i=0; i<numColumns; i++)
			header.add("");
		header.set(latColumn-1, "Latitude");
		header.set(lonColumn-1, "Longitude");
		return header;
	}

	private static CSVFile<String> loadTxtAsCsv(File file, int numColumns, int latColumn, int lonColumn, int headerLines)
	throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);

		ArrayList<String> header = buildDefaultHeader(numColumns, latColumn, lonColumn);

		csv.addLine(header);

		ArrayList<String> lines = FileUtils.loadFile(file.getAbsolutePath(), false);
		for (int i=0; i<lines.size(); i++) {
			if (i < headerLines)
				continue;

			String line = lines.get(i);

			if (line == null || line.length() == 0)
				continue;

			StringTokenizer tok = new StringTokenizer(line);

			Preconditions.checkState(tok.countTokens() == numColumns, "Incorrect number of columns found at line "
					+(i+1)+". Expected: "+numColumns+", Actual: "+tok.countTokens());

			ArrayList<String> strings = new ArrayList<String>();
			while (tok.hasMoreTokens())
				strings.add(tok.nextToken());

			validateLatLon(strings, i+1, latColumn, lonColumn);

			csv.addLine(strings);
		}

		return csv;
	}

	private void loadFile(File file) throws IOException {
		int numColumns = numColumnsParam.getValue();
		int latColumn = latColumnParam.getValue();
		int lonColumn = lonColumnParam.getValue();

		CSVFile<String> csv;
		if (file.getName().toLowerCase().endsWith(".csv")) {
			csv = CSVFile.readFile(file, true, numColumns);
			if (!csvHasHeaderParam.getValue()) {
				// if no header, add one
				ArrayList<String> header = buildDefaultHeader(numColumns, latColumn, lonColumn);

				CSVFile<String> newCSV = new CSVFile<String>(true);
				newCSV.addLine(header);
				for (List<String> line : csv)
					newCSV.addLine(line);
				csv = newCSV;
			}
			for (int i=1; i<csv.getNumRows(); i++) {
				validateLatLon(csv.getLine(i), i+1, latColumn, lonColumn);
			}
		} else
			csv = loadTxtAsCsv(file, numColumns, latColumn, lonColumn, txtHeaderLinesParam.getValue());

		if (csv.getNumRows() <= 1)
			throw new RuntimeException("No valid locations loaded!");

		fetchPreferredButton.setEnabled(true);
		fetchAllButton.setEnabled(true);
		saveButton.setEnabled(false);

		valLists = null;
		dataCSV = null;
		table.setEditingEnabled(false);
		this.inputCSV = csv;
		table.setCSV(csv, true);

		locations = new LocationList();
		for (int i=1; i<csv.getNumRows(); i++) {
			List<String> line = csv.getLine(i);
			double lat = Double.parseDouble(line.get(latColumn-1));
			double lon = Double.parseDouble(line.get(lonColumn-1));

			locations.add(new Location(lat, lon));
		}
	}

	private void saveFile(File file) throws IOException {
		CSVFile<String> csv = table.buildCSVFromTable(true);
		if (file.getName().toLowerCase().endsWith(".csv")) {
			csv.writeToFile(file);
			return;
		}

		csv.writeToTabSeparatedFile(file, 1);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == loadButton) {
			if (loadChooser == null) {
				loadChooser = new JFileChooser();
				loadChooser.addChoosableFileFilter(csvFilter);
				loadChooser.addChoosableFileFilter(txtFilter);
			}
			int ret = loadChooser.showOpenDialog(this);
			if (ret == JFileChooser.APPROVE_OPTION) {
				File file = loadChooser.getSelectedFile();

				ParameterListEditor editor = getInputFormatParamEditor(file);

				while (true) {
					int selection = JOptionPane.showConfirmDialog(
							this, editor, "Input File Format", JOptionPane.OK_CANCEL_OPTION);

					if (selection == JOptionPane.OK_OPTION) {
						if (!isInputConfigurationValid()) {
							JOptionPane.showMessageDialog(this, "Latitude & Longitude columns can't be equal!",
									"Error with Input Format", JOptionPane.ERROR_MESSAGE);
							continue;
						}

						try {
							loadFile(file);
							break;
						} catch (Exception e1) {
							e1.printStackTrace();
							JOptionPane.showMessageDialog(this, "Error Loading File:\n"+e1.getMessage(),
									"Error Loading File", JOptionPane.ERROR_MESSAGE);
							continue;
						}
					} else {
						break;
					}
				}
			}
		} else if (e.getSource() == saveButton) {
			if (saveChooser == null) {
				saveChooser = new JFileChooser(loadChooser.getCurrentDirectory());
				saveChooser.addChoosableFileFilter(csvFilter);
				saveChooser.addChoosableFileFilter(txtFilter);
			}
			int ret = saveChooser.showSaveDialog(this);
			if (ret == JFileChooser.APPROVE_OPTION) {
				File file = saveChooser.getSelectedFile();

				try {
					saveFile(file);
				} catch (IOException e1) {
					e1.printStackTrace();
					showBugReportDialog(e1, "Error fetching saving data:\n"+e1.getMessage(), "Error Saving Site Data");
				}
			}
		} else if (e.getSource() == fetchAllButton || e.getSource() == fetchPreferredButton) {
			try {
				fetch(e.getSource() == fetchPreferredButton);
			} catch (Exception e1) {
				e1.printStackTrace();
				showBugReportDialog(e1, "Error fetching site data:\n"+e1.getMessage(), "Error Fetching Site Data");
			}
		}
	}

	private void showBugReportDialog(Throwable t, String message, String title) {
		BugReport bug = new BugReport(t, "Loading site data", SiteDataCombinedApp.APP_SHORT_NAME,
				SiteDataCombinedApp.getAppVersion(), this);
		SimpleBugMessagePanel panel = new SimpleBugMessagePanel(bug, message);

		JOptionPane.showMessageDialog(this, panel, title, JOptionPane.ERROR_MESSAGE);
	}

	private void fetch(boolean preferred) throws IOException {
		if (valLists == null)
			valLists = list.getAllAvailableData(locations);
		CSVFile<String> csv;
		if (preferred)
			csv = fetchPreferred();
		else
			csv = fetchAll();
		dataCSV = csv;
		table.setEditingEnabled(true);
		saveButton.setEnabled(true);
		table.setCSV(dataCSV, true);
	}

	private static ArrayList<String> copyOf(List<String> line) {
		ArrayList<String> copy = new ArrayList<String>();
		for (String val : line)
			copy.add(val);
		return copy;
	}

	private CSVFile<String> fetchAll() throws IOException {
		ArrayList<SiteData<?>> providers = list.getEnabledProviders();

		Preconditions.checkState(valLists.size() == providers.size());

		CSVFile<String> dataCSV = new CSVFile<String>(true);
		ArrayList<String> header = copyOf(inputCSV.getLine(0));

		for (SiteData<?> provider : providers) {
			header.add(provider.getDataType()+" ("+provider.getShortName()+")");
		}
		dataCSV.addLine(header);

		for (int i=0; i<locations.size(); i++) {
			int csvI = i+1;

			ArrayList<String> line = copyOf(inputCSV.getLine(csvI));

			for (SiteDataValueList<?> valList : valLists) {
				Object val = valList.getValue(i).getValue();
				if (val == null)
					line.add(null);
				else
					line.add(val.toString());
			}
			dataCSV.addLine(line);
		}
		return dataCSV;
	}

	private CSVFile<String> fetchPreferred() throws IOException {
		ArrayList<SiteData<?>> providers = list.getEnabledProviders();

		Preconditions.checkState(valLists.size() == providers.size());

		CSVFile<String> dataCSV = new CSVFile<String>(true);
		ArrayList<String> header = copyOf(inputCSV.getLine(0));

		ArrayList<String> dataTypes = new ArrayList<String>();
		for (SiteDataValueList<?> valList : valLists) {
			String type = valList.getType();
			if (!dataTypes.contains(type))
				dataTypes.add(type);
		}
		header.addAll(dataTypes);
		dataCSV.addLine(header);

		for (int i=0; i<locations.size(); i++) {
			int csvI = i+1;

			ArrayList<String> line = copyOf(inputCSV.getLine(csvI));

			for (String type : dataTypes) {
				Object val = null;
				Object rejectVal = null;
				for (int j=0; j<valLists.size(); j++) {
					SiteDataValueList valList = valLists.get(j);
					if (!valList.getType().equals(type))
						continue;
					SiteData provider = providers.get(j);
					Object testVal = valList.getValue(i).getValue();
					if (testVal != null) {
						if (provider.isValueValid(testVal)) {
							val = testVal;
							break;
						} else {
							rejectVal = testVal;
						}
					}
				}
				if (val == null)
					val = rejectVal;

				if (val == null)
					line.add(null);
				else
					line.add(val.toString());
			}
			dataCSV.addLine(line);
		}
		return dataCSV;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == numColumnsParam) {
			int cols = numColumnsParam.getValue();

			IntegerDiscreteConstraint colConst = new IntegerDiscreteConstraint(
					getDiscrete(cols));

			if (latColumnParam.getValue() > cols)
				latColumnParam.setValue(1);
			latColumnParam.setConstraint(colConst);
			latColumnParam.getEditor().refreshParamEditor();

			if (lonColumnParam.getValue() > cols)
				lonColumnParam.setValue(1);
			lonColumnParam.setConstraint(colConst);
			lonColumnParam.getEditor().refreshParamEditor();
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == list)
			valLists = null;
	}

}
