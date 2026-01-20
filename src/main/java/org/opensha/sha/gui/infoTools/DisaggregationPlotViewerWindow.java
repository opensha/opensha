package org.opensha.sha.gui.infoTools;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.jfree.chart3d.Chart3DPanel;
import org.jpedal.PdfDecoder;
import org.opensha.commons.util.BrowserUtils;
import org.opensha.commons.util.CustomFileFilter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculatorAPI;
import org.opensha.sha.calc.disaggregation.chart3d.PureJavaDisaggPlotter;

import com.google.common.base.Preconditions;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PRAcroForm;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.SimpleBookmark;


/**
 * <p>Title: DisaggregationPlotViewerWindow</p>
 * <p>Description: this Class thye displays the image of the GMT Map in the
 * Frame window</p>
 * @author: Nitin Gupta & Vipin Gupta
 * @version 1.0
 */

public class DisaggregationPlotViewerWindow extends JFrame implements HyperlinkListener{
	
	private final static int W=830;
	private final static int H=1000;

	private final static String MAP_WINDOW = "Maps using GMT";
	private JSplitPane mapSplitPane = new JSplitPane();
	private JScrollPane mapScrollPane = new JScrollPane();


	private JMenuBar menuBar = new JMenuBar();
	private JMenu fileMenu = new JMenu();

	private JMenuItem fileSaveMenu = new JMenuItem();
	private JToolBar jToolBar = new JToolBar();

	private JButton saveButton = new JButton();
	private ImageIcon saveFileImage = new ImageIcon(FileUtils.loadImage("icons/saveFile.jpg"));


	private BorderLayout borderLayout1 = new BorderLayout();
	private JPanel mapPanel = new JPanel();
	private GridBagLayout layout = new GridBagLayout();
	//private JTextPane mapText = new JTextPane();
	//private final static String HTML_START = "<html><body>";
	//private final static String HTML_END = "</body></html>";

	//the image as URL (if remote)
	private String imagePDF_URL;
	// image pane if native
	private Chart3DPanel imagePanel;

	//creates the tab panes for the user to view different information for the
	//disaggregation plot
	private JTabbedPane infoTabPane = new JTabbedPane();
	//If disaggregation info needs to be scrolled
	private JScrollPane meanModeScrollPane = new JScrollPane();
	private JScrollPane metadataScrollPane = new JScrollPane();
	private JScrollPane sourceListDataScrollPane;
	private JScrollPane consolidatedSourceListDataScrollPane;
	private JScrollPane binnedDataScrollPane;

	//TextPane to show different disaggregation information
	private JTextPane meanModePane = new JTextPane();
	private JTextPane metadataPane = new JTextPane();
	private JTextPane sourceListDataPane;
	private JTextPane consolidatedSourceListDataPane;
	private JTextPane binnedDataPane;

	//Strings for getting the different disaggregation info.
	private String meanModeText,metadataText,binDataText,sourceDataText,consolidatedSourceDataText;

	private JFileChooser fileChooser;
	
	/**
	 * Constructor when using a remote disagg PDF (URL)
	 * @param imagePDF_URL URL of the image
	 * @param calc disaggregation calculator
	 * @param meanModeText mean/mode text
	 * @param metadataText metadata
	 * @param showBinData if ture, bin data will be shown in a tab
	 */
	public DisaggregationPlotViewerWindow(String imagePDF_URL,
			DisaggregationCalculatorAPI calc, String meanModeText, String metadataText, boolean showBinData) throws RuntimeException{
		this(null, imagePDF_URL, calc, meanModeText, metadataText, showBinData);
	}
	
	/**
	 * Constructor when using a java-generated plot
	 * @param imagePanel
	 * @param calc
	 * @param meanModeText mean/mode text
	 * @param metadataText metadata
	 * @param showBinData if ture, bin data will be shown in a tab
	 */
	public DisaggregationPlotViewerWindow(Chart3DPanel imagePanel,
			DisaggregationCalculatorAPI calc, String meanModeText, String metadataText, boolean showBinData) {
		this(imagePanel, null, calc, meanModeText, metadataText, showBinData);
	}
	
	private DisaggregationPlotViewerWindow(
			Chart3DPanel imagePanel, String imagePDF_URL,
			DisaggregationCalculatorAPI calc, String meanModeText, String metadataText, boolean showBinData) {
		this(imagePanel, imagePDF_URL, meanModeText, metadataText,
				showBinData ? calc.getBinData() : null,
				calc.getNumSourcesToShow() > 0 ? calc.getDisaggregationSourceInfo() : null,
				calc.getNumSourcesToShow() > 0 ? calc.getConsolidatedDisaggregationSourceInfo() : null);
	}
	
	public DisaggregationPlotViewerWindow(
			Chart3DPanel imagePanel, String imagePDF_URL,
			String meanModeText, String metadataText, String binDataText,
			String sourceDataText, String consolidatedSourceDataText) {
		Preconditions.checkState(imagePanel != null || imagePDF_URL != null,
				"Both imagePanel and imagePDF_URL are null");
		Preconditions.checkState(imagePanel == null || imagePDF_URL == null,
				"Both imagePanel and imagePDF_URL are non-null");
		this.imagePanel = imagePanel;
		this.imagePDF_URL = imagePDF_URL;
		this.meanModeText = meanModeText;
		this.metadataText = metadataText;
		this.binDataText = binDataText;
		this.sourceDataText = sourceDataText;
		this.consolidatedSourceDataText = consolidatedSourceDataText;
		try {
			jbInit();

			//show the bin data only if it is not  null
			if (binDataText !=null && !binDataText.isBlank()){
				binnedDataScrollPane = new JScrollPane();
				binnedDataPane = new JTextPane();
				//adding the text pane for the bin data
				infoTabPane.addTab("Bin Data", binnedDataScrollPane);
				binnedDataScrollPane.getViewport().add(binnedDataPane, null);
//				binnedDataPane.setForeground(Color.blue);
				binnedDataPane.setText(binDataText);
				binnedDataPane.setEditable(false);
			}

			//show the source list metadata only if it not null
			if(sourceDataText !=null && !sourceDataText.isBlank()){
				sourceListDataScrollPane = new JScrollPane();
				sourceListDataPane = new JTextPane();
				//adding the text pane for the source list data
				infoTabPane.addTab("Source List Data", sourceListDataScrollPane);
				sourceListDataScrollPane.getViewport().add(sourceListDataPane, null);
//				sourceListDataPane.setForeground(Color.blue);
				sourceListDataPane.setText(sourceDataText);
				sourceListDataPane.setEditable(false);
			}
			
			if(consolidatedSourceDataText !=null && !consolidatedSourceDataText.isBlank()){
				consolidatedSourceListDataScrollPane = new JScrollPane();
				consolidatedSourceListDataPane = new JTextPane();
				//adding the text pane for the source list data
				infoTabPane.addTab("Consolidated Source List Data", consolidatedSourceListDataScrollPane);
				consolidatedSourceListDataScrollPane.getViewport().add(consolidatedSourceListDataPane, null);
//				consolidatedSourceListDataPane.setForeground(Color.blue);

               String contributeExceedMsg = "NOTE: Consolidated entries represent summed contributions across sources that share a common " +
                           "underlying feature (as defined by the ERF). A single source from the Source List Data tab may " +
                           "contribute to multiple entries in this list, and the sum of contribution percentages across all consolidated " +
                           "entries may exceed 100%.\n" +
                           "\n" +
                           "A common example is an ERF with multi-fault ruptures, where individual sources represent different combinations of " +
                           "faults rupturing together. In that case, the consolidated list reports the summed contribution of each individual " +
                           "fault across all ruptures in which it participates.\n\n";
                consolidatedSourceDataText = contributeExceedMsg.concat(consolidatedSourceDataText);

				consolidatedSourceListDataPane.setText(consolidatedSourceDataText);
				consolidatedSourceListDataPane.setEditable(false);
			}

		}catch(RuntimeException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
		//addImageToWindow(imageFileName);
		if (imagePDF_URL != null) {
			int currentPage = 1;
			PdfDecoder pdfDecoder = new PdfDecoder();

			try {
				//this opens the PDF and reads its internal details
				pdfDecoder.openPdfFileFromURL(imagePDF_URL);

				//these 2 lines opens page 1 at 100% scaling
				pdfDecoder.decodePage(currentPage);
				pdfDecoder.setPageParameters(1, 1); //values scaling (1=100%). page number
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			//setup our GUI display
			mapScrollPane.setViewportView(pdfDecoder);
		} else {
			mapScrollPane.setViewportView(imagePanel);
//			mapPanel.add(imagePanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
//					,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 3, 5, 5), 0, 0));
		}
			
		this.setVisible(true);
	}




	protected void jbInit() throws RuntimeException {
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.setSize(W,H);
		this.setTitle(MAP_WINDOW);
		this.getContentPane().setLayout(borderLayout1);

		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				saveButton_actionPerformed(actionEvent);
			}
		});
		fileSaveMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileSaveMenu_actionPerformed(e);
			}
		});
		fileSaveMenu.setText("Save");
		fileMenu.setText("File");
		menuBar.add(fileMenu);
		fileMenu.add(fileSaveMenu);

		setJMenuBar(menuBar);

		Dimension d = saveButton.getSize();
		jToolBar.add(saveButton);
		saveButton.setIcon(saveFileImage);
		saveButton.setToolTipText("Save Graph as image");
		saveButton.setSize(d);
		jToolBar.add(saveButton);
		jToolBar.setFloatable(false);

		this.getContentPane().add(jToolBar, BorderLayout.NORTH);

		mapSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		//adding the mean/mode and metadata info tabs to the window
		infoTabPane.addTab("Mean/Mode", meanModeScrollPane);
		infoTabPane.addTab("Metadata", metadataScrollPane);
		meanModeScrollPane.getViewport().add(meanModePane,null);
		metadataScrollPane.getViewport().add(metadataPane,null);

		//adding the metadata text to the metatada info window
		metadataPane.setContentType("text/html");
//		metadataPane.setForeground(Color.blue);
		metadataPane.setText(metadataText);
		metadataPane.setEditable(false);
		metadataPane.addHyperlinkListener(this);

		//adding the meanMode text to the meanMode info window
//		meanModePane.setForeground(Color.blue);
		meanModePane.setText(meanModeText);
		meanModePane.setEditable(false);

		this.getContentPane().add(mapSplitPane, BorderLayout.CENTER);
		mapSplitPane.add(mapScrollPane, JSplitPane.TOP);
		mapSplitPane.add(infoTabPane, JSplitPane.BOTTOM);
		infoTabPane.setTabPlacement(JTabbedPane.BOTTOM);
		mapPanel.setLayout(layout);
		mapScrollPane.getViewport().add(mapPanel, null);
		mapSplitPane.setDividerLocation((int)(H*5d/7d));
	}
	
	/**
	 * Opens a file chooser and gives the user an opportunity to save the Image and Metadata
	 * in PDF format.
	 *
	 * @throws IOException if there is an I/O error.
	 */
	protected void save() throws IOException {
		if (fileChooser == null) {
			fileChooser = new JFileChooser();
			CustomFileFilter pdfChooser = new CustomFileFilter(".pdf", "PDF File");
//			CustomFileFilter pngChooser = new CustomFileFilter(".png", "PNG File");
			CustomFileFilter txtChooser = new CustomFileFilter(".txt", "TXT File");

			fileChooser.addChoosableFileFilter(pdfChooser);
//			fileChooser.addChoosableFileFilter(pngChooser);
			fileChooser.addChoosableFileFilter(txtChooser);
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setFileFilter(pdfChooser);
		}
		int option = fileChooser.showSaveDialog(this);
		String fileName = null;
		if (option == JFileChooser.APPROVE_OPTION) {
			fileName = fileChooser.getSelectedFile().getAbsolutePath();
			CustomFileFilter filter = (CustomFileFilter) fileChooser.getFileFilter();
			String ext = filter.getExtension();
			if (!fileName.toLowerCase().endsWith(ext)) {
				fileName = fileName + ext;
			}
			if (ext.equals(".pdf")) {
				saveAsPDF(fileName);
//			} else if (ext.equals(".png")) {
//				saveAsPNG(fileName);
			} else if (ext.equals(".txt")) {
				saveAsTXT(fileName);
			} else {
				throw new RuntimeException("Unkown save type: "+ext);
			}
		}

	}

	private String getDissaggText() {
		return getDisaggText(meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText);
	}
	
	public static String getDisaggText(String meanModeText,
			String metadataText, String binDataText, String sourceDataText, String consolidatedSourceDataText) {
		StringBuilder str = new StringBuilder("Mean/Mode Metadata :\n");
		str.append(meanModeText);
		str.append("\n\nDisaggregation Plot Parameters Info :\n");
		str.append(metadataText);
		if (binDataText != null && !binDataText.isBlank()) {
			str.append("\n\n"+"Disaggregation Bin Data :\n");
			str.append(binDataText);
		}
		if (sourceDataText != null && !sourceDataText.isBlank()) {
			str.append("\n\nDisaggregation Source List Info:\n");
			str.append(sourceDataText);
		}
		if (consolidatedSourceDataText != null && !consolidatedSourceDataText.isBlank()) {
			str.append("\n\nDisaggregation Source List Info:\n");
			str.append(sourceDataText);
		}
		return str.toString();
	}
	
	protected void saveAsTXT(String outputFileName) {
		saveAsTXT(outputFileName,  meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText);
	}
	
	public static void saveAsTXT(String outputFileName, String meanModeText,
			String metadataText, String binDataText, String sourceDataText,
			String consolidatedSourceDataText) {
		FileUtils.save(outputFileName, getDisaggText(meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText));
	}

	public static void saveAsPDF(Chart3DPanel disaggPanel, String outputFileName, String meanModeText,
			String metadataText, String binDataText, String sourceDataText, String consolidatedSourceDataText) throws IOException {
		saveAsPDF(disaggPanel, null, outputFileName, meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText);
	}

	public static void saveAsPDF(String disaggPDF_URL, String outputFileName, String meanModeText,
			String metadataText, String binDataText, String sourceDataText, String consolidatedSourceDataText) throws IOException {
		saveAsPDF(null, disaggPDF_URL, outputFileName, meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText);
	}

	private static void saveAsPDF(Chart3DPanel disaggPanel, String disaggPDF_URL, String outputFileName, String meanModeText,
			String metadataText, String binDataText, String sourceDataText, String consolidatedSourceDataText) throws IOException {
		String disaggregationInfoString = getDisaggText(meanModeText, metadataText, binDataText, sourceDataText, consolidatedSourceDataText);
		
		if (disaggPanel != null) {
			PureJavaDisaggPlotter.writeChartPDF(new File(outputFileName), disaggPanel, disaggregationInfoString);
		} else {
			// step 1: creation of a document-object
			Document document = new Document();
			//document for temporary storing the metadata as pdf-file
			Document document_temp = new Document();
			//String array to store the 2 pdfs
			String[] pdfFiles = new String[2];
			pdfFiles[0] = disaggPDF_URL;
			pdfFiles[1] = outputFileName+".tmp";
			//creating the temp data pdf for the Metadata
			try {
				PdfWriter.getInstance(document_temp,
						new FileOutputStream(pdfFiles[1]));
				document_temp.open();
				document_temp.add(new Paragraph(disaggregationInfoString));
				document_temp.close();

				//concating the PDF files, one is the temporary pdf file that was created
				//for storing the metadata, other is the Disaggregation plot image pdf file
				//which is read as URL.
				int pageOffset = 0;
				ArrayList master = new ArrayList();

				PdfCopy writer = null;
				for (int f=0; f<pdfFiles.length; f++) {
					// we create a reader for a certain document
					PdfReader reader = null;
					if(f ==0)
						reader = new PdfReader(new URL(pdfFiles[f]));
					else
						reader = new PdfReader(pdfFiles[f]);

					reader.consolidateNamedDestinations();
					// we retrieve the total number of pages
					int n = reader.getNumberOfPages();
					java.util.List bookmarks = SimpleBookmark.getBookmark(reader);
					if (bookmarks != null) {
						if (pageOffset != 0)
							SimpleBookmark.shiftPageNumbers(bookmarks, pageOffset, null);
						master.addAll(bookmarks);
					}
					pageOffset += n;

					if (f == 0) {
						// step 1: creation of a document-object
						document = new Document(reader.getPageSizeWithRotation(1));
						// step 2: we create a writer that listens to the document
						writer = new PdfCopy(document, new FileOutputStream(outputFileName));
						// step 3: we open the document
						document.open();
					}
					// step 4: we add content
					PdfImportedPage page;
					for (int i = 0; i < n; ) {
						++i;
						page = writer.getImportedPage(reader, i);
						writer.addPage(page);
					}
					PRAcroForm form = reader.getAcroForm();
					if (form != null)
						System.err.println("TODO: replace old copyAcroForm method");
//					writer.copyAcroForm(reader);
				}
				if (master.size() > 0)
					writer.setOutlines(master);
				// step 5: we close the document
				document.close();
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			//deleting the temporary PDF file that was created for storing the metadata
			File f = new File(pdfFiles[1]);
			f.delete();
		}
	}

	/**
	 * Allows the user to save the image and metadata as PDF.
	 * This also allows to preserve the color coding of the metadata.
	 * @throws IOException
	 */
	protected void saveAsPDF(String fileName) throws IOException {
		saveAsPDF(imagePanel, imagePDF_URL, fileName, meanModeText, metadataText,
				binDataText, sourceDataText, consolidatedSourceDataText);
	}

	/**
	 * File | Save action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	private void saveButton_actionPerformed(ActionEvent actionEvent) {
		try {
			save();
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}

	/**
	 * File | Save action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	private void fileSaveMenu_actionPerformed(ActionEvent actionEvent) {
		try {
			save();
		}
		catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}


	/** This method implements HyperlinkListener.  It is invoked when the user
	 * clicks on a hyperlink, or move the mouse onto or off of a link
	 **/
	public void hyperlinkUpdate(HyperlinkEvent e) {
		HyperlinkEvent.EventType type = e.getEventType();  // what happened?
		if (type == HyperlinkEvent.EventType.ACTIVATED) {     // Click!
			try{
				//       org.opensha.util.BrowserLauncher.openURL(e.getURL().toString());
				BrowserUtils.launch(e.getURL().toURI());
			}catch(Exception ex) { ex.printStackTrace(); }

			//displayPage(e.getURL());   // Follow the link; display new page
		}
	}

}


