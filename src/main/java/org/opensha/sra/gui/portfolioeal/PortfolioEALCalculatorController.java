package org.opensha.sra.gui.portfolioeal;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.CustomFileFilter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sra.gui.portfolioeal.gui.PortfolioEALCalculatorView;
import org.opensha.sra.vulnerability.Vulnerability;
import org.opensha.sra.vulnerability.models.SimpleVulnerability;
import org.opensha.sra.vulnerability.models.VulnFileReader;
import org.opensha.sra.vulnerability.models.curee.caltech.CCLargeHouseImmediateOccupancy;
import org.opensha.sra.vulnerability.models.curee.caltech.CCLargeHouseRigidDiaphram;
import org.opensha.sra.vulnerability.models.curee.caltech.CCLargeHouseTypical;
import org.opensha.sra.vulnerability.models.curee.caltech.CCLargeHouseWaistWall;
import org.opensha.sra.vulnerability.models.curee.caltech.CCSmallHouseRetro;
import org.opensha.sra.vulnerability.models.curee.caltech.CCSmallHouseTypical;
import org.opensha.sra.vulnerability.models.curee.caltech.CCTownhouseLimitedDrift;
import org.opensha.sra.vulnerability.models.curee.caltech.CCTownhouseTypical;
import org.opensha.sra.vulnerability.models.servlet.VulnerabilityServletAccessor;

/**
 * The controller for the Portfolio EAL calculator.  This class is used to 
 * define the underlying functionality of the calculator.
 * 
 * @author Jeremy Leakakos
 * @see    PortfolioEALCalculatorView
 */
public class PortfolioEALCalculatorController implements ActionListener, ItemListener, Runnable, CalculationExceptionHandler {
	
	public static final String APP_NAME = "Portfolio EAL Application";
	public static final String APP_SHORT_NAME = "PortfolioEAL";

	private double EAL;
	private PortfolioEALCalculatorView view;
	private String aString = "";
	private int count;
	private Site site;
	private String fileName = "";
	private Thread calcThread = null;
	private File portfolioFile = null;
	private Portfolio portfolio = null;
	
	/**
	 * The default constructor.  The main class of the program, which start the program, It creates
	 * and displays the view.
	 */
	public PortfolioEALCalculatorController () {
		CalcProgressBar startAppProgressClass = new CalcProgressBar("Starting Application", "Initializing Application .. Please Wait");
		view = PortfolioEALCalculatorView.getView();
		view.registerUI( this );
		view.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		view.setVisible(true);
		startAppProgressClass.dispose();
		EAL = 0.0;
	}
	
	/**
	 * The main method which drives the whole program.
	 * 
	 * @param args The command line arguments - These should be null.
	 */
	public static void main( String[] args ) {
		PortfolioEALCalculatorController controller = new PortfolioEALCalculatorController();
		controller.toString();
	}
	
	/**
	 * This computes the EAL for a portfolio.
	 */
	public double computeEAL() {

		double retVal = 0.0;
		try {
			portfolio = Portfolio.createPortfolio( portfolioFile );
			System.gc();
			retVal = portfolio.calculatePortfolioEAL(
					view.getIMR().getIMRBean().getSelectedIMR_Instance(),
					view.getDistanceControlPanel().getDistance(),
					view.getSite().getSiteBean().getSite(),
					view.getERF().getPanel().getSelectedERF(),
					this
					);
		} catch ( Exception e ) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		return retVal;
	}
	

	
	/**
	 * This method formats the output that will be sent to the I/O pane.
	 * A single string is sent to the I/O pane's JTextArea, so as to keep all of the already
	 * formatted/printed text in it.  There is a counter to keep track of the number of calculations
	 * already done.
	 */
	private void printToIO(double secs) {
		// This variable keeps track of the number of Portfolio EAL's that have been calculated
		count += 1;
		
		// Show the number of Portfolio EAL's that have been calculated
		aString += "Portfolio EAL Calculation # " + count + "\n\n";

		// Show the name of the portfolio file being used
		aString += "Portfolio File Name: " + fileName + "\n\n";
		
		// Get formatted output for the ERF
		aString += formatOutput( view.getERF().getPanel().getERFParameterList(), "Earthquake Rupture Forecast" );
		
		// Get formatted output for the IMR
		aString += formatOutput( view.getIMR().getIMRBean().getParameterList(), "Intesity Measure Relationship");
		
		// Get formatted output for the IMT
		aString += formatOutput( ((Parameter)view.getIMR().getIMRBean().getSelectedIMR_Instance().getIntensityMeasure()).getIndependentParameterList(), "Intesity Measure Type");
		
		// Get formatted output for the Site(s)
		for( Site assetSite : portfolio.getSiteList()) {
			site = assetSite;
			aString += formatOutput( site, "Site" );
		}
			
		// Get formatted output for the Timespan
		aString += formatOutput( view.getERF().getPanel().getSelectedERFTimespanGuiBean().getParameterList(), "Timespan" );
		
		aString += "Max. Source-Site Distance = " + view.getDistanceControlPanel().getDistance() + "\n\n";
		
		DecimalFormat money = new DecimalFormat("$0.00");
		// Get formatted output for the Hazard Function
		for( int i = 0; i < portfolio.getAssetList().size(); i++ ) {
			Asset asset = portfolio.getAssetList().get(i);
			aString += "Asset " + i + " - Hazard Curve\n";
			aString += asset.getHazardFunction().toString();
			aString += "Asset " + (i + 1) + " EAL = " + money.format(asset.getAssetEAL()) + "\n\n";
		}
		
		// format the EAL into a proper money format; $0.00
		String formatEAL = money.format(EAL);
		aString += "Portfolio EAL " + count + " = "  + formatEAL + "\n\n";
		aString += "\nCalculation Time: "+secs+" seconds\n";
		aString += "----------------------------------------\n\n";
		// Send the string to the view to set the I/O pane
		view.setIO(aString);
	}
	
	/**
	 * This method takes a ParameterList, an input string to append to, and the name 
	 * of what is being formatted.
	 * 
	 * @param list     	The parameter list to be used in formatting
	 * @param aString  	The string that everything will be appended too
	 * @param type     	The name of what is being formatted
	 * @return 		   	The formatted string that will be used in the ouput
	 */
	private String formatOutput( ParameterList params, String type ) {
		aString = type + " Parameter List\n-------------------\n";
		if ( type.equals("Site") ) {
			if ( site.getName() != null ) aString += "Name: " + site.getName() + "\n";
			aString += "Latitute: " + site.getLocation().getLatitude() + "\n";
			aString += "Longitude: " + site.getLocation().getLongitude() + "\n";
		}
		for (Parameter<?> next : params)
			aString += next.getName() + ": " + next.getValue() + "\n";
		aString += "\n";
		return aString;
	}
	
	private void computeButtonPressed() {
		if (portfolioFile != null ) {
			view.setButtonsOnCompute();
			calcThread = new Thread(this);
			calcThread.start();
		}
		else {
			JOptionPane.showMessageDialog(view, "You must select a portfolio file first!", "Error", JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/**
	 * This is called when the "Clear" button is pressed.  It asks if you would like to
	 * clear the I/O pane, and if yes is selected, sets the I/O pane input string to the 
	 * empty string, and clears the I/O pane.
	 */
	private void clearButtonPressed() {
		int choice = JOptionPane.showConfirmDialog(view, "Are you sure you would like to clear the screen?",
				  "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if ( choice == JOptionPane.YES_OPTION ) {
			aString = "";
			view.setIO(null);
		}
	}
	
	/**
	 * This is called when the "Cancel" button is pressed.  It resets the buttons to
	 * their original state, and stops the current calculation.
	 */
	private void cancelButtonPressed() {
		view.setButtonsOnCancel();
		calcThread.stop();
	}
	
	/**
	 * This is called when the "Open Portfolio" button is pressed.  It creates a new
	 * file chooser, and sets the portfolio file to the one selected.
	 */
	private void openPortfolioButtonPressed() {
		if (chooser == null) {
			chooser = new JFileChooser(new File(".").getAbsoluteFile());
			CustomFileFilter csvFF = new CustomFileFilter("csv",
				"Portfolio Files");
			chooser.addChoosableFileFilter(csvFF);
			chooser.setFileFilter(csvFF);
		}
		int retval = chooser.showOpenDialog(view);
		if (retval == JFileChooser.APPROVE_OPTION) {
			portfolioFile = chooser.getSelectedFile();
			try {
				fileName = portfolioFile.getName();
				view.setPortfolioField(portfolioFile.getCanonicalPath());
			} catch (IOException e) {}
		}
	}
	
	private JFileChooser chooser;

	/**
	 *  Listen for the JButtons and the JComboBox.
	 *	<b>"Compute"</b> starts up a new thread that calls computeEAL(), which computes the EAL for a portfolio.><br>
	 *	<b>"Clear Results"</b> goes to clearResults(), which clears the I/O text area.<br>
	 *  <b>"Open Portfolio"</b> opens up a new dialog that allows you to open a portfolio file.<br>
	 *  <b>"Cancel"</b> stops the thread running the calculations and resets the buttons.
	 *  <b>JComboBox: "comboBoxSelection"</b> Calls a method in view based on the selection
	 *  
	 *  
	 *  @param event The ActionEvent fired when the buttons/combo box are selected.
	 */
	public void actionPerformed(ActionEvent event) {
		// Listen for the JComboBox, and pass on the selected value to comboBoxSelection
		// Set the JComboBox back to the inital selection
		if (event.getSource().getClass().toString().equals("class javax.swing.JComboBox")) {
			view.comboBoxSelection(((JComboBox)event.getSource()).getSelectedItem().toString());
			((JComboBox)event.getSource()).setSelectedIndex(0);
		}
		// Listen for the JButtons, and call the appropriate method on the controller
		else if (event.getSource().getClass().toString().equals("class javax.swing.JButton")) {
			String buttonName = event.getActionCommand();
			// COMPUTE
			if ( buttonName.equals("Compute") ) {
				computeButtonPressed();
			}
			// CLEAR
			else if ( buttonName.equals("Clear Results") ) {
				clearButtonPressed();
			}
			// CANCEL
			else if ( buttonName.equals("Cancel") ) {
				cancelButtonPressed();
			}
			// OPEN PORTFOLIO
			else if ( buttonName.equals("Open Portfolio" ) ) {
				openPortfolioButtonPressed();
			}
		}	
	}
	
	/**
	 *  ItemListener for the JCheckBox
	 *  
	 *  @param event The ItemEvent The event fired whent the check box is selected.
	 */
	public void itemStateChanged(ItemEvent event) {
		// Listen for the JCheckBox
		// If selected, a progress bar is shown when the "Compute" button is hit
		if (event.getSource().getClass().toString().equals("class javax.swing.JCheckBox")) {
			if ( event.getStateChange() == ItemEvent.SELECTED ){
				
				view.setProgressBarChecked( PortfolioEALCalculatorView.CHECKED_YES );
			}
			else {
				view.setProgressBarChecked( PortfolioEALCalculatorView.CHECKED_NO );
			}
		}
	}

	/**
	 * This method is used when new thread is started on an object of this class.  It
	 * starts the computation for the EAL, and then changes the buttons back to their
	 * default state.  It also prints the calculation information to the I/O pane.
	 */
	public void run() {
		StopWatch watch = new StopWatch();
		watch.start();
		EAL = computeEAL();
		watch.stop();
		double secs = watch.getTime() / 1000d;
		printToIO(secs);
		// The next call is not appropriately named, but it has the proper functionality
		view.setButtonsOnCancel();
	}
	
	/**
	 * Exceptions occur during a calculation; this method gets the program back to its start state
	 * 
	 * @param errorMessage The string representation of the exception's error message.
	 */
	public void calculationException( String errorMessage ) {
		JOptionPane.showMessageDialog(view, errorMessage, "Error", JOptionPane.ERROR_MESSAGE );
		view.setButtonsOnCancel();
		calcThread.stop();
	}
}
