package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetCalculation;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.OriginalModWriter;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.infoTools.IndeterminateProgressBar;
import org.opensha.sha.imr.ScalarIMR;

public class IM_EventSetGUI extends JFrame implements ActionListener {
	
	private static final long serialVersionUID = 1L;

	private static File cwd = new File(System.getProperty("user.dir"));
	
	private SitesPanel sitesPanel = null;
	private ERF_GuiBean erfGuiBean = null;
	private IMR_ChooserPanel imrChooser = null;
	private IMT_ChooserPanel imtChooser = null;
	private OrderedSiteDataGUIBean dataBean = null;
	
	private JTabbedPane tabbedPane;
	
	private JPanel imPanel = new JPanel();
	private JPanel siteERFPanel = new JPanel();
	
	private JButton calcButton = new JButton("Start Calculation");
	private JButton saveButton = new JButton("Save Calculation Settings");
	private JButton loadButton = new JButton("Load Calculation Settings");
	
	private JFileChooser openChooser;
	private JFileChooser saveChooser;
	private JFileChooser outputChooser;
	
	private JComboBox<?> outputWriterChooser;
	
	private IndeterminateProgressBar bar = new IndeterminateProgressBar("Calculating...");
	private Timer doneTimer = null; // show when calculation is done
	
	public IM_EventSetGUI() {
		erfGuiBean = createERF_GUI_Bean();
		imtChooser = new IMT_ChooserPanel();
        sitesPanel = new SitesPanel();
		imrChooser = new IMR_ChooserPanel(imtChooser, sitesPanel);

		OrderedSiteDataProviderList providers = OrderedSiteDataProviderList.createSiteDataProviderDefaults();

		dataBean = new OrderedSiteDataGUIBean(providers);
		
		imPanel.setLayout(new BoxLayout(imPanel, BoxLayout.X_AXIS));
		imPanel.add(imrChooser);
		imPanel.add(imtChooser);
		
		siteERFPanel.setLayout(new BoxLayout(siteERFPanel, BoxLayout.X_AXIS));
		siteERFPanel.add(sitesPanel);
		siteERFPanel.add(erfGuiBean);
		
		tabbedPane = new JTabbedPane();

        tabbedPane.addTab("IMRs/IMTs", imPanel);
		tabbedPane.addTab("Sites/ERF", siteERFPanel);
		tabbedPane.addTab("Site Data Providers", dataBean);
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		
		String writers[] = new String[2];
		writers[0] = OriginalModWriter.NAME;
		writers[1] = HAZ01Writer.NAME;
		outputWriterChooser = new JComboBox(writers);
		JPanel outputWriterChooserPanel = new JPanel();
		outputWriterChooserPanel.add(outputWriterChooser);
		
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.add(outputWriterChooserPanel);
		bottomPanel.add(calcButton);
		bottomPanel.add(saveButton);
		bottomPanel.add(loadButton);
		bottomPanel.add(bar);

		calcButton.addActionListener(this);
		saveButton.addActionListener(this);
		loadButton.addActionListener(this);
		
		mainPanel.add(tabbedPane, BorderLayout.CENTER);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
		
		this.setTitle("IM Event Set Calculator");
		
		this.setContentPane(mainPanel); 
		pack();
	}
	
	private ERF_GuiBean createERF_GUI_Bean() {
		try {
			return new ERF_GuiBean(ERF_Ref.get(false, ServerPrefUtils.SERVER_PREFS));
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
	public IM_EventSetCalculation getEventSetCalc() {
		ERF erf = null;
		try {
			erf = (ERF) this.erfGuiBean.getSelectedERF_Instance();
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
		ArrayList<ERF> erfs = new ArrayList<ERF>();
		erfs.add(erf);
		
		ArrayList<ScalarIMR> imrs = imrChooser.getSelectedIMRs();
		
		ArrayList<String> imts = imtChooser.getIMTStrings();
		ArrayList<Location> locs = sitesPanel.getLocs();
		ArrayList<ArrayList<SiteDataValue<?>>> vals = sitesPanel.getDataLists();
		
		OrderedSiteDataProviderList providers = this.dataBean.getProviderList().clone();
		providers.removeDisabledProviders();
		
		return new IM_EventSetCalculation(locs, vals, erfs, imrs, imts, providers);
	}
	
	private boolean isReadyForCalc(ArrayList<Location> locs, ArrayList<ArrayList<SiteDataValue<?>>> dataLists,
			ERF erf, ArrayList<ScalarIMR> imrs, ArrayList<String> imts) {
		
		if (locs.size() < 1) {
			JOptionPane.showMessageDialog(this, "You must add at least 1 site!", "No Sites Selected!",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (locs.size() != dataLists.size()) {
			JOptionPane.showMessageDialog(this, "Internal error: Site data lists not same size as site list!",
					"Internal error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (erf == null) {
			JOptionPane.showMessageDialog(this, "Error instantiating ERF!", "Error with ERF!",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (imrs.size() < 1) {
			JOptionPane.showMessageDialog(this, "You must add at least 1 IMR!", "No IMRs Selected!",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (imts.size() < 1) {
			JOptionPane.showMessageDialog(this, "You must add at least 1 IMT!", "No IMTs Selected!",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}
	
	public void actionPerformed(ActionEvent e) {
		IM_EventSetGUI instance = this; // Need to get instance inside SwingWorker
		if (e.getSource().equals(calcButton)) {

			// Spawn thread for precalculation logic to determine if ready
			SwingWorker<Boolean, Integer> precalcWorker = new SwingWorker<>() {
				ArrayList<Location> locs = null;
				ArrayList<ArrayList<SiteDataValue<?>>> dataLists = null;
				ERF erf = null;
				ArrayList<ScalarIMR> imrs = null;
				ArrayList<String> imts = null;
				Exception precalcException;

				@Override
				protected Boolean doInBackground() {
					try {
						// Ensure no active timers from prior calculations
						if (doneTimer != null && doneTimer.isRunning()) {
							doneTimer.stop();
							doneTimer = null;
						}
						// Get data for calculation
						locs = sitesPanel.getLocs();
						dataLists = sitesPanel.getDataLists();
						erf = (ERF)erfGuiBean.getSelectedERF();
						imrs = imrChooser.getSelectedIMRs();
						imts = imtChooser.getIMTStrings();
						
						return isReadyForCalc(locs, dataLists, erf, imrs, imts);
					} catch (Exception e) {
						precalcException = e;
						return false;
					}
				}
				@Override
				protected void done() {
					// If precalcWorker was successful, begin main calculation
					boolean readyForCalc = false;
					try {
						readyForCalc = get();
					} catch (InterruptedException | ExecutionException e) {
						precalcException = e;
					}
					if (!readyForCalc) {
						if (precalcException != null)
							precalcException.printStackTrace();
						JOptionPane.showMessageDialog(
								instance, precalcException.getMessage(), "Exception Preparing Calculation",
								JOptionPane.ERROR_MESSAGE);
						return;
					}
					// Ask user for output directory on Event Dispatch Thread
					SwingUtilities.invokeLater(() -> {
						if (outputChooser == null) {
							outputChooser = new JFileChooser(cwd);
							outputChooser.setDialogTitle("Select Output Directory");
							outputChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
						}
						int returnVal = outputChooser.showOpenDialog(instance);
						
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							File outputDir = outputChooser.getSelectedFile();
							GUICalcAPI_Impl calc = new GUICalcAPI_Impl(locs, dataLists,
									outputDir, dataBean.getProviderList());
							IM_EventSetOutputWriter writer;
							String writerName = (String) outputWriterChooser.getSelectedItem();
							if (writerName.equals(OriginalModWriter.NAME))
								writer = new OriginalModWriter(calc);
							else if (writerName.equals(HAZ01Writer.NAME))
								writer = new HAZ01Writer(calc);
							else
								throw new RuntimeException("Unknown writer: " + writerName);

							// Spawn thread for calculation. Returns true if ran successfully.
							SwingWorker<Boolean, Integer> calcWorker = new SwingWorker<>() {
								Exception calcException;
								@Override
								protected Boolean doInBackground() {
									instance.validate();
									try {
										writer.writeFiles(erf, imrs, imts);
										return true;
									} catch (IOException e) {
										calcException = e;
									}
									return false;
								}
								
								@Override
								protected void done() {
									boolean calcSuccess = false;
									try {
										calcSuccess = get();
									} catch (InterruptedException | ExecutionException e) {
										calcException = e;
									}
									if (!calcSuccess) {
										bar.toggle();
										calcButton.setEnabled(true);
										if (calcException != null)
											calcException.printStackTrace();
										throw new RuntimeException(calcException);
									}
									calcButton.setEnabled(true);
									// Stop progress bar after calculation over
									bar.toggle();
									
									bar.setString("Done!");
									bar.setStringPainted(true);
									doneTimer = new Timer(1200, e -> {
										bar.setStringPainted(false);
										bar.setString("Calculating...");
									});
									doneTimer.start();
								}
							};
							
							// Show progress bar on bottom panel
							bar.toggle();
							calcButton.setEnabled(false);

							calcWorker.execute();
							// Give EDT a moment to update before kicking off background work
//							SwingUtilities.invokeLater(() -> calcWorker.execute());
						}
					});
					
				}
			};
			precalcWorker.execute();
			
			// TODO: Delete save button and its logic
		} else if (e.getSource().equals(saveButton)) {
			if (saveChooser == null)
				saveChooser = new JFileChooser(cwd);
			int returnVal = saveChooser.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				IM_EventSetCalculation calc = getEventSetCalc();
				File file = saveChooser.getSelectedFile();
				Document doc = XMLUtils.createDocumentWithRoot();
				Element root = doc.getRootElement();
				calc.toXMLMetadata(root);
				try {
					XMLUtils.writeDocumentToFile(file, doc);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			// TODO: Delete load button and its logic
		} else if (e.getSource().equals(loadButton)) {
			if (openChooser == null)
				openChooser = new JFileChooser(cwd);
			int returnVal = openChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = openChooser.getSelectedFile();
				Document doc = null;
				try {
					doc = XMLUtils.loadDocument(file.getAbsolutePath());
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					JOptionPane.showMessageDialog(this, "Error loading XML file:\n" + e1.getMessage(),
							"Error loading XML!", JOptionPane.ERROR_MESSAGE);
					return;
				}
				try {
					Element eventSetEl = doc.getRootElement().element(IM_EventSetCalculation.XML_METADATA_NAME);
					IM_EventSetCalculation calc = IM_EventSetCalculation.fromXMLMetadata(eventSetEl);
					
					// sites
					this.sitesPanel.clear();
					ArrayList<Location> sites = calc.getSites();
					ArrayList<ArrayList<SiteDataValue<?>>> sitesData = calc.getSitesData();
					for (int i=0; i<calc.getSites().size(); i++) {
						this.sitesPanel.addSite(sites.get(i), sitesData.get(i));
					}
					
					// erf
					ArrayList<ERF> erfs = calc.getErfs();
					if (erfs.size() > 0) {
						ERF erf = erfs.get(0);
						this.erfGuiBean.getParameter(ERF_GuiBean.ERF_PARAM_NAME).setValue(erf.getName());
						BaseERF myERF = erfGuiBean.getSelectedERF_Instance();
						for (Parameter myParam : myERF.getAdjustableParameterList()) {
							for (Parameter xmlParam : erf.getAdjustableParameterList()) {
								if (myParam.getName().equals(xmlParam.getName())) {
									myParam.setValue(xmlParam.getValue());
								}
							}
						}
						TimeSpan timeSpan = erf.getTimeSpan();
						myERF.setTimeSpan(timeSpan);
						erfGuiBean.getERFParameterListEditor().refreshParamEditor();
						erfGuiBean.getSelectedERFTimespanGuiBean().setTimeSpan(timeSpan);
					}
					
					// imrs
//					ArrayList<ScalarIMR> imrs = calc.getIMRs();
//					imrChooser.setForIMRS(imrs);

					// imts
					imtChooser.setIMRs(imrChooser.getSelectedIMRs());
					imtChooser.setIMTs(calc.getIMTs());
					
					// site data providers
					OrderedSiteDataProviderList provs = calc.getProviders();
					OrderedSiteDataProviderList defaultProvs = dataBean.getProviderList();
					defaultProvs.mergeWith(provs);
					if (provs != null)
						dataBean.setProviderList(defaultProvs);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		IM_EventSetGUI gui = new IM_EventSetGUI();
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		gui.setSize(900, 700);
		
		gui.setVisible(true);
	}

}
