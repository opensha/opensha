package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.swing.*;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.ControlPanel;
import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.BugReportDialog;
import org.opensha.commons.util.bugReports.DefaultExceptionHandler;
import org.opensha.sha.calc.params.filters.*;
import org.opensha.sha.calc.IM_EventSet.AbstractIMEventSetCalc;
import org.opensha.sha.calc.IM_EventSet.IM_EventSetOutputWriter;
import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.IM_EventSet.outputImpl.OriginalModWriter;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.controls.CalculationSettingsControlPanel;
import org.opensha.sha.gui.controls.CalculationSettingsControlPanelAPI;
import org.opensha.sha.gui.infoTools.IndeterminateProgressBar;
import org.opensha.sha.imr.ScalarIMR;

/**
 * This GUI application calculates intensity measures on event sets.
 * Pass multiple IMRs, IMTs, sites, and data providers to compute
 * means and standard deviations.
 */
public class IMEventSetCalculatorGUI extends JFrame implements ActionListener, CalculationSettingsControlPanelAPI {
	
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

    private JButton computeButton;
    private JButton calcSettingsButton;
    private ControlPanel calcSettingsControlPanel;
	private JFileChooser outputChooser;
	private JComboBox<?> outputWriterChooser;

    private SourceFilterManager sourceFilters;
    private final ParameterList adjustableParams = new ParameterList();

	private final IndeterminateProgressBar bar = new IndeterminateProgressBar("Calculating...");
	private Timer doneTimer = null; // show when calculation is done

	public IMEventSetCalculatorGUI() {
        try {
            AbstractIMEventSetCalc.initLogger(Level.CONFIG);

            erfGuiBean = createERF_GUI_Bean();
            imtChooser = new IMT_ChooserPanel();

            // Initialize params for control panel.
            sourceFilters = SourceFiltersParam.getDefault();
            SourceFiltersParam sourceFilterParam = new SourceFiltersParam(sourceFilters);
            adjustableParams.addParameter(sourceFilterParam);

            // dataBean needs to be passed to SitesPanel to set sites with selected site data providers
            OrderedSiteDataProviderList providers = OrderedSiteDataProviderList.createSiteDataProviderDefaults();
            dataBean = new OrderedSiteDataGUIBean(providers);

            sitesPanel = new SitesPanel(dataBean);

            imrChooser = new IMR_ChooserPanel(imtChooser, sitesPanel);

            calcSettingsControlPanel = new CalculationSettingsControlPanel(this, this);

            // ======== app tabs ========
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

            // ======== button panel ========
            JPanel buttonPanel = new JPanel(new GridBagLayout()) {
                private static final long serialVersionUID = 1L;

                @Override
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.gray);
                    g.drawLine(0, 0, getWidth(), 0);
                }
            };
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 40));
            buttonPanel.setBackground(HazardCurveApplication.getBottomBarColor()); // we should move this out somewhere else

            JLabel shaLogo = new JLabel(new ImageIcon(
                    FileUtils.loadImage("logos/opensha_64.png")));

            String[] writers = new String[2];
            writers[0] = OriginalModWriter.NAME;
            writers[1] = HAZ01Writer.NAME;
            outputWriterChooser = new JComboBox<>(writers);

            calcSettingsButton = new JButton(CalculationSettingsControlPanel.NAME);
            calcSettingsButton.addActionListener(this);
            JPanel calcSettingsButtonWrapper = new JPanel(new BorderLayout());
            calcSettingsButtonWrapper.setOpaque(false);
            calcSettingsButtonWrapper.setBorder(BorderFactory.createEmptyBorder(-4, 0, 2, 0));
            calcSettingsButtonWrapper.add(calcSettingsButton, BorderLayout.CENTER);

            computeButton = new JButton("Compute");
            computeButton.addActionListener(this);
            JPanel computeButtonWrapper = new JPanel(new BorderLayout());
            computeButtonWrapper.setOpaque(false);
            computeButtonWrapper.setBorder(BorderFactory.createEmptyBorder(-4, 0, 2, 0));
            computeButtonWrapper.add(computeButton, BorderLayout.CENTER);

            // Create a panel to hold the combo box and button together
            JPanel controlsPanel = new JPanel();
            controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.X_AXIS));
            controlsPanel.setOpaque(false);
            controlsPanel.add(outputWriterChooser);
            controlsPanel.add(Box.createHorizontalStrut(18));
            controlsPanel.add(calcSettingsButtonWrapper);
            controlsPanel.add(computeButtonWrapper);

            // Set the progress bar to match the width of the controls above
            Dimension controlsPanelSize = controlsPanel.getPreferredSize();
            bar.setPreferredSize(new Dimension(controlsPanelSize.width, bar.getPreferredSize().height));
            bar.setMaximumSize(new Dimension(controlsPanelSize.width, bar.getPreferredSize().height));

            // Create a panel to stack controls and progress bar vertically
            JPanel rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            rightPanel.setOpaque(false);
            rightPanel.add(controlsPanel);
            rightPanel.add(Box.createVerticalStrut(5));
            rightPanel.add(bar);

            GridBagConstraints gbc = new GridBagConstraints();

            // Logo on the left
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0.0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 0, 0, 0);
            buttonPanel.add(shaLogo, gbc);

            // Spacer in the middle to push right panel to the right
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            buttonPanel.add(Box.createHorizontalGlue(), gbc);

            // Right panel (controls + progress bar) on the right
            gbc.gridx = 2;
            gbc.weightx = 0.0;
            gbc.anchor = GridBagConstraints.LINE_END;
            gbc.fill = GridBagConstraints.NONE;
            buttonPanel.add(rightPanel, gbc);

            // ======== main panel ========
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(tabbedPane, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            setTitle(APP_NAME + " (" + getAppVersion() + ")");
            setContentPane(mainPanel);
            pack();
        } catch (Exception e) {
            e.printStackTrace();
            BugReport bug = new BugReport(e, "Error occurred during app initialization.", APP_SHORT_NAME, getAppVersion(), this);
            BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
            bugDialog.setVisible(true);

        }
    }

	private ERF_GuiBean createERF_GUI_Bean() {
		try {
			return new ERF_GuiBean(ERF_Ref.get(false, ServerPrefUtils.SERVER_PREFS));
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

    private boolean isReadyForCalc(ArrayList<Location> locs, ArrayList<ParameterList> dataLists,
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
		IMEventSetCalculatorGUI instance = this; // Need to get instance inside SwingWorker
		if (e.getSource().equals(computeButton)) {

            // Spawn thread for precalculation logic to determine if ready
            SwingWorker<Boolean, Integer> precalcWorker = new SwingWorker<>() {
                ArrayList<Location> locs = null;
                ArrayList<ParameterList> dataLists = null;
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
                        dataLists = sitesPanel.getSiteDataParams();
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
                            IMEventSetCalcGUIImpl calc = new IMEventSetCalcGUIImpl(
                                    locs, dataLists, outputDir, sourceFilters);
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
                                        computeButton.setEnabled(true);
                                        if (calcException != null)
                                            calcException.printStackTrace();
                                        throw new RuntimeException(calcException);
                                    }
                                    computeButton.setEnabled(true);
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
                            computeButton.setEnabled(false);

                            calcWorker.execute();
                            // Give EDT a moment to update before kicking off background work
//							SwingUtilities.invokeLater(() -> calcWorker.execute());
                        }
                    });

                }
            };
            precalcWorker.execute();
        } else if (e.getSource().equals(calcSettingsButton)) {
            calcSettingsControlPanel.showControlPanel();
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

    public static IMEventSetCalculatorGUI launch(DefaultExceptionHandler handler) {
        IMEventSetCalculatorGUI applet = new IMEventSetCalculatorGUI();
        if (handler != null) {
            handler.setApp(applet);
            handler.setParent(applet);
        }
        applet.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        applet.setSize(900, 700);

        applet.setVisible(true);
        return applet;
    }

    // static initializer for setting look & feel
    static {
        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        } catch (Exception e1) {
            System.err.println("WARNING: could not set property 'apple.laf.useScreenMenuBar'");
        }
//		String osName = System.getProperty("os.name");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
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

    @Override
    public ParameterList getCalcAdjustableParams() {
        return adjustableParams;
    }

    @Override
    public String getCalcParamMetadataString() {
        ParameterList params = getCalcAdjustableParams();
        if (params == null)
            return "";
        return params.getParameterListMetadataString();
    }
}
