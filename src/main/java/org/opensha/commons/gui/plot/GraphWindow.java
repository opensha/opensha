package org.opensha.commons.gui.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.jfree.data.Range;
import org.opensha.commons.util.CustomFileFilter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Class for quickly displaying JFreeChart plots. As this is often used in quick test code where labels
 * and such are not set from GUI threads, most methods are surrounded in SwingUtilities.invokeAndWait(...)
 * calls to prevent race conditions.
 * @author kevin
 *
 */
public class GraphWindow extends JFrame {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private List<GraphWidget> widgets;
	
	private JPanel mainPanel;
	private JTabbedPane widgetTabPane;
	
	protected JMenuBar menuBar = new JMenuBar();
	protected JMenu fileMenu = new JMenu();

	protected JMenuItem fileExitMenu = new JMenuItem();
	protected JMenuItem fileSaveMenu = new JMenuItem();
	protected JMenuItem fileSaveAllMenu = new JMenuItem();
	protected JMenuItem filePrintMenu = new JCheckBoxMenuItem();
	protected JToolBar jToolBar = new JToolBar();

	protected JButton closeButton = new JButton();
	protected ImageIcon closeFileImage = new ImageIcon(FileUtils.loadImage("icons/closeFile.png"));

	protected JButton printButton = new JButton();
	protected ImageIcon printFileImage = new ImageIcon(FileUtils.loadImage("icons/printFile.jpg"));

	protected JButton saveButton = new JButton();
	protected ImageIcon saveFileImage = new ImageIcon(FileUtils.loadImage("icons/saveFile.jpg"));
	
	protected static int windowNumber = 1;
	protected final static String TITLE = "Plot Window - ";
	
	public GraphWindow(PlotElement elem, String plotTitle) {
		this(Lists.newArrayList(elem), plotTitle);
	}
	
	public GraphWindow(PlotElement elem, String plotTitle, PlotCurveCharacterstics plotChar) {
		this(Lists.newArrayList(elem), plotTitle, Lists.newArrayList(plotChar));
	}
	
	public GraphWindow(List<? extends PlotElement> elems, String plotTitle) {
		this(elems, plotTitle, generateDefaultChars(elems));
	}
	
	public GraphWindow(List<? extends PlotElement> elems, String plotTitle,
			List<PlotCurveCharacterstics> chars) {
		this(new PlotSpec(elems, chars, plotTitle, null, null));
	}
	
	public GraphWindow(List<? extends PlotElement> elems, String plotTitle,
			List<PlotCurveCharacterstics> chars, boolean display) {
		this(new GraphWidget(new PlotSpec(elems, chars, plotTitle, null, null)), display);
	}
	
	public GraphWindow(PlotSpec spec) {
		this(spec, true);
	}
	
	public GraphWindow(PlotSpec spec, boolean display) {
		this(new GraphWidget(spec), display);
	}
	
	public GraphWindow(PlotSpec plotSpec, PlotPreferences plotPrefs, boolean xLog, boolean yLog, Range xRange, Range yRange) {
		this(new GraphWidget(plotSpec, plotPrefs, xLog, yLog, xRange, yRange));
	}
	
	public GraphWindow(GraphWidget widget) {
		this(widget, true);
	}
	
	public GraphWindow(GraphWidget widget, final boolean display) {
		mainPanel = new JPanel(new BorderLayout());
		
		widgets = Lists.newArrayList();
		addTab(widget);
		
		fileMenu.setText("File");
		fileExitMenu.setText("Exit");
		fileSaveMenu.setText("Save");
		fileSaveAllMenu.setText("Save All");
		filePrintMenu.setText("Print");

		fileExitMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileExitMenu_actionPerformed(e);
			}
		});

		fileSaveMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileSaveMenu_actionPerformed(e);
			}
		});
		
		fileSaveAllMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileSaveAllMenu_actionPerformed(e);
			}
		});

		filePrintMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filePrintMenu_actionPerformed(e);
			}
		});

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				closeButton_actionPerformed(actionEvent);
			}
		});
		printButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				printButton_actionPerformed(actionEvent);
			}
		});
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				saveButton_actionPerformed(actionEvent);
			}
		});

		menuBar.add(fileMenu);
		fileMenu.add(fileSaveMenu);
		fileMenu.add(fileSaveAllMenu);
		fileMenu.add(filePrintMenu);
		fileMenu.add(fileExitMenu);

		setJMenuBar(menuBar);
		closeButton.setIcon(closeFileImage);
		closeButton.setToolTipText("Close Window");
		Dimension d = closeButton.getSize();
		jToolBar.add(closeButton);
		printButton.setIcon(printFileImage);
		printButton.setToolTipText("Print Graph");
		printButton.setSize(d);
		jToolBar.add(printButton);
		saveButton.setIcon(saveFileImage);
		saveButton.setToolTipText("Save Graph as image");
		saveButton.setSize(d);
		jToolBar.add(saveButton);
		jToolBar.setFloatable(false);

		mainPanel.add(jToolBar, BorderLayout.NORTH);
		mainPanel.add(widget, BorderLayout.CENTER);
		
		this.setContentPane(mainPanel);
		
		// increasing the window number corresponding to the new window.
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				setTitle(TITLE + windowNumber++);
				
				setSize(700, 800);
				
				if (display)
					setVisible(true);
			}
		});
	}
	
	public void setX_AxisLabel(final String xAxisLabel) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setXAxisLabel(xAxisLabel);
			}
		});
	}
	
	public void setY_AxisLabel(final String yAxisLabel) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setYAxisLabel(yAxisLabel);
			}
		});
	}
	
	public void addTab(PlotSpec spec) {
		addTab(new GraphWidget(spec));
	}
	
	public void addTab(GraphWidget widget) {
		Preconditions.checkState(!widgets.contains(widget));
		if (widgets.size() == 1 && widgetTabPane == null) {
			// switch to tabs
			widgetTabPane = new JTabbedPane();
			mainPanel.remove(widgets.get(0));
			String title = widgets.get(0).getPlotSpec().getTitle();
			if (title == null || title.isEmpty())
				title = "(no title)";
			widgetTabPane.addTab(title, widgets.get(0));
			mainPanel.add(widgetTabPane, BorderLayout.CENTER);
		}
		widgets.add(widget);
		if (widgets.size() > 1) {
			String title = widget.getPlotSpec().getTitle();
			if (title == null || title.isEmpty())
				title = "(no title)";
			widgetTabPane.addTab(title, widget);
			Preconditions.checkState(widgetTabPane.getTabCount() == widgets.size());
			widgetTabPane.setSelectedIndex(widgets.size()-1);
		}
	}
	
	public void setSelectedTab(final int index) {
		doGUIThreadSafe(new Runnable() {

			@Override
			public void run() {
				widgetTabPane.setSelectedIndex(index);
			}
			
		});
	}
	
	public GraphWidget getGraphWidget() {
		if (widgetTabPane == null)
			return widgets.get(0);
		return widgets.get(widgetTabPane.getSelectedIndex());
	}
	
	private static final PlotCurveCharacterstics PLOT_CHAR1 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.BLUE);
	private static final PlotCurveCharacterstics PLOT_CHAR2 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.BLACK);
	private static final PlotCurveCharacterstics PLOT_CHAR3 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.GREEN);
	private static final PlotCurveCharacterstics PLOT_CHAR4 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.MAGENTA);
	private static final PlotCurveCharacterstics PLOT_CHAR5 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.PINK);
	private static final PlotCurveCharacterstics PLOT_CHAR6 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.LIGHT_GRAY);
	private static final PlotCurveCharacterstics PLOT_CHAR7 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.RED);
	private static final PlotCurveCharacterstics PLOT_CHAR8 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.ORANGE);
	private static final PlotCurveCharacterstics PLOT_CHAR9 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.CYAN);
	private static final PlotCurveCharacterstics PLOT_CHAR10 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.DARK_GRAY);
	private static final PlotCurveCharacterstics PLOT_CHAR11 = new PlotCurveCharacterstics(PlotLineType.SOLID,
			2f, Color.GRAY);
	
	protected static ArrayList<PlotCurveCharacterstics> generateDefaultChars(List<? extends PlotElement> elems) {
		ArrayList<PlotCurveCharacterstics> list = new ArrayList<PlotCurveCharacterstics>();
		list.add(PLOT_CHAR1);
		list.add(PLOT_CHAR2);
		list.add(PLOT_CHAR3);
		list.add(PLOT_CHAR4);
		list.add(PLOT_CHAR5);
		list.add(PLOT_CHAR6);
		list.add(PLOT_CHAR7);
		list.add(PLOT_CHAR8);
		list.add(PLOT_CHAR9);
		list.add(PLOT_CHAR10);
		list.add(PLOT_CHAR11);
		
		if (elems == null)
			return list;
		
		int numChars = list.size();
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		for(int i=0; i<elems.size(); ++i)
			plotChars.add(list.get(i%numChars));
		return plotChars;
	}
	
	public static List<Color> generateDefaultColors() {
		ArrayList<Color> colors = new ArrayList<Color>();
		for (PlotCurveCharacterstics pchar : generateDefaultChars(null))
			colors.add(pchar.getColor());
		return colors;
	}
	
	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	protected  void fileExitMenu_actionPerformed(ActionEvent actionEvent) {
		this.dispose();
	}

	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	protected  void fileSaveMenu_actionPerformed(ActionEvent actionEvent) {
		try {
			getGraphWidget().save();
		}
		catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}
	
	protected void fileSaveAllMenu_actionPerformed(ActionEvent actionEvent) {
		try {
			JFileChooser chooser = new JFileChooser();
			
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int option = chooser.showSaveDialog(this);
			if (option == JFileChooser.APPROVE_OPTION) {
				File dir = chooser.getSelectedFile();
				for (GraphWidget widget : widgets) {
					String title = widget.getPlotLabel();
					String fname = title.replaceAll("\\W+", "_");
					String prefix = new File(dir, fname).getAbsolutePath();
					widget.saveAsPNG(prefix+".png");
					widget.saveAsPDF(prefix+".pdf");
					widget.saveAsTXT(prefix+".txt");
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}

	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	protected  void filePrintMenu_actionPerformed(ActionEvent actionEvent) {
		getGraphWidget().print();
	}

	protected  void closeButton_actionPerformed(ActionEvent actionEvent) {
		this.dispose();
	}

	protected  void printButton_actionPerformed(ActionEvent actionEvent) {
		getGraphWidget().print();
	}

	protected  void saveButton_actionPerformed(ActionEvent actionEvent) {
		try {
			getGraphWidget().save();
		}
		catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}

	public void saveAsPDF(final String fileName) throws IOException {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				try {
					getGraphWidget().saveAsPDF(fileName);
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		});
	}

	public void saveAsPNG(final String fileName) throws IOException {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				try {
					getGraphWidget().saveAsPNG(fileName);
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		});
	}
	
	/**
	 * Save a txt file
	 * @param fileName
	 * @throws IOException 
	 */
	public void saveAsTXT(String fileName) throws IOException {
		getGraphWidget().saveAsTXT(fileName);
	}

	public void setXLog(final boolean xLog) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setX_Log(xLog);
			}
		});
	}

	public void setYLog(final boolean yLog) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setY_Log(yLog);
			}
		});
	}

	public void setAxisRange(final double xMin, final double xMax, final double yMin, final double yMax) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setAxisRange(xMin, xMax, yMin, yMax);
			}
		});
	}

	public void setAxisRange(final Range xRange, final Range yRange) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setAxisRange(xRange, yRange);
			}
		});
	}

	public void setPlotSpec(final PlotSpec plotSpec) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setPlotSpec(plotSpec);
			}
		});
	}

	public void setPlotChars(
			final List<PlotCurveCharacterstics> curveCharacteristics) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setPlotChars(curveCharacteristics);
			}
		});
	}

	public void togglePlot() {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().togglePlot();
			}
		});
	}

	public void setPlotLabel(final String plotTitle) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setPlotLabel(plotTitle);
			}
		});
	}

	public void setPlotLabelFontSize(final int fontSize) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setPlotLabelFontSize(fontSize);
			}
		});
	}

	public void setTickLabelFontSize(final int fontSize) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setTickLabelFontSize(fontSize);
			}
		});
	}

	public void setAxisLabelFontSize(final int fontSize) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setAxisLabelFontSize(fontSize);
			}
		});
	}

	public void setX_AxisRange(final double minX, final double maxX) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setX_AxisRange(minX, maxX);
			}
		});
	}

	public void setX_AxisRange(final Range xRange) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setX_AxisRange(xRange);
			}
		});
	}
	
	public Range getX_AxisRange() {
		return getGraphWidget().getX_AxisRange();
	}

	public void setY_AxisRange(final double minY, final double maxY) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setY_AxisRange(minY, maxY);
			}
		});
	}

	public void setY_AxisRange(final Range yRange) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setY_AxisRange(yRange);
			}
		});
	}
	
	public Range getY_AxisRange() {
		return getGraphWidget().getY_AxisRange();
	}

	public void setAllLineTypes(final PlotLineType line, final PlotSymbol symbol) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				for (PlotCurveCharacterstics pchar : getGraphWidget().getPlottingFeatures()) {
					pchar.setLineType(line);
					pchar.setSymbol(symbol);
				}
			}
		});
	}

	public void setAutoRange() {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setAutoRange();
			}
		});
	}
	
	public void setGriddedFuncAxesTicks(final boolean histogramAxesTicks) {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().setGriddedFuncAxesTicks(histogramAxesTicks);
			}
		});
	}
	
	public void redrawGraph() {
		doGUIThreadSafe(new Runnable() {
			
			@Override
			public void run() {
				getGraphWidget().drawGraph();
			}
		});
	}
	
	/**
	 * Will execute the following in the event dispatch thread if not already on it. This prevents
	 * various crashes when called from a main class.
	 * 
	 * Will block until finished.
	 * @param run
	 */
	private static void doGUIThreadSafe(Runnable run) {
		if (SwingUtilities.isEventDispatchThread()) {
			// aready on the EDT, run directly
			run.run();
		} else {
			try {
				SwingUtilities.invokeAndWait(run);
			} catch (Exception e1) {
				ExceptionUtils.throwAsRuntimeException(e1);
			}
		}
	}

}
