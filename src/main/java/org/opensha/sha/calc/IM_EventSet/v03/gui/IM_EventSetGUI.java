package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.*;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.bugReports.DefaultExceptionHandler;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.OriginalModWriter;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.infoTools.IndeterminateProgressBar;
import org.opensha.sha.imr.ScalarIMR;

public class IM_EventSetGUI extends JFrame implements ActionListener {
	
	private static final long serialVersionUID = 1L;

    private static ApplicationVersion version;

    public static final String APP_NAME = "IM Event Set Calculator";
    public static final String APP_SHORT_NAME = "IM_EventSetCalc";

	private static final File cwd = new File(System.getProperty("user.dir"));
	
	private SitesPanel sitesPanel = null;
	private ERF_GuiBean erfGuiBean = null;
	private IMR_ChooserPanel imrChooser = null;
	private IMT_ChooserPanel imtChooser = null;
	private OrderedSiteDataGUIBean dataBean = null;

    private final JButton calcButton = new JButton("Start Calculation");

	private JFileChooser outputChooser;
	
	private final JComboBox<?> outputWriterChooser;
	
	private final IndeterminateProgressBar bar = new IndeterminateProgressBar("Calculating...");
	private Timer doneTimer = null; // show when calculation is done

	public IM_EventSetGUI() {
        erfGuiBean = createERF_GUI_Bean();
        imtChooser = new IMT_ChooserPanel();
        sitesPanel = new SitesPanel();
        imrChooser = new IMR_ChooserPanel(imtChooser, sitesPanel);

        OrderedSiteDataProviderList providers = OrderedSiteDataProviderList.createSiteDataProviderDefaults();

        dataBean = new OrderedSiteDataGUIBean(providers);

        JPanel imPanel = new JPanel();
        imPanel.setLayout(new BoxLayout(imPanel, BoxLayout.X_AXIS));
        imPanel.add(imrChooser);
        imPanel.add(imtChooser);

        JPanel siteERFPanel = new JPanel();
        siteERFPanel.setLayout(new BoxLayout(siteERFPanel, BoxLayout.X_AXIS));
        siteERFPanel.add(sitesPanel);
        siteERFPanel.add(erfGuiBean);

        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("IMRs/IMTs", imPanel);
        tabbedPane.addTab("Sites/ERF", siteERFPanel);
        tabbedPane.addTab("Site Data Providers", dataBean);

        JPanel mainPanel = new JPanel(new BorderLayout());

        String[] writers = new String[2];
        writers[0] = OriginalModWriter.NAME;
        writers[1] = HAZ01Writer.NAME;
        outputWriterChooser = new JComboBox(writers);

        JPanel botLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        botLeftPanel.add(outputWriterChooser);

        JPanel botRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botRightPanel.add(calcButton);
        botRightPanel.add(bar);
        botRightPanel.setBorder(BorderFactory.createEmptyBorder(-4, 0, 0, 0));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        bottomPanel.add(Box.createHorizontalStrut(5));
        bottomPanel.add(botLeftPanel);
        bottomPanel.add(Box.createHorizontalStrut(20));
        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(botRightPanel);
        bottomPanel.add(Box.createHorizontalStrut(5));

        calcButton.addActionListener(this);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setTitle(APP_NAME + " (" + getAppVersion() + ")");
        setContentPane(mainPanel);
        pack();
    }

	private ERF_GuiBean createERF_GUI_Bean() {
		try {
			return new ERF_GuiBean(ERF_Ref.get(false, ServerPrefUtils.SERVER_PREFS));
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

    private boolean isReadyForCalc(ArrayList<Location> locs, ArrayList<ArrayList<SiteDataValue<?>>> dataLists,
			ERF erf, ArrayList<ScalarIMR> imrs, ArrayList<String> imts) {
		
		if (locs.isEmpty()) {
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
		if (imrs.isEmpty()) {
			JOptionPane.showMessageDialog(this, "You must add at least 1 IMR!", "No IMRs Selected!",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		if (imts.isEmpty()) {
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
                        erf = (ERF) erfGuiBean.getSelectedERF();
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
                        if (precalcException != null) {
                            precalcException.printStackTrace();
                            JOptionPane.showMessageDialog(
                                    instance, precalcException.getMessage(), "Exception Preparing Calculation",
                                    JOptionPane.ERROR_MESSAGE);
                        }
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
                            if (writerName == null) {
                                throw new RuntimeException("No output writer selected");
                            }
                            if (writerName.equals(OriginalModWriter.NAME)) {
                                writer = new OriginalModWriter(calc);
                            } else if (writerName.equals(HAZ01Writer.NAME)) {
                                writer = new HAZ01Writer(calc);
                            } else {
                                throw new RuntimeException("Unknown writer: " + writerName);
                            }

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
                                    sitesPanel.rebuildSiteDataList();
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
        }
	}

    // Main method
	public static void main(String[] args) {
        new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
        DefaultExceptionHandler exp = new DefaultExceptionHandler(
                APP_SHORT_NAME, getAppVersion(), null, null);
        Thread.setDefaultUncaughtExceptionHandler(exp);
        launch(exp);
	}

    public static IM_EventSetGUI launch(DefaultExceptionHandler handler) {
        IM_EventSetGUI applet = new IM_EventSetGUI();
        if (handler != null) {
            handler.setApp(applet);
            handler.setParent(applet);
        }
        applet.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        applet.setSize(900, 700);

        applet.setVisible(true);
        return applet;
    }

    /**
     * Returns the Application version
     * @return ApplicationVersion
     */
    public static ApplicationVersion getAppVersion(){
        if (version == null) {
            try {
                version = ApplicationVersion.loadBuildVersion();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return version;
    }

}
