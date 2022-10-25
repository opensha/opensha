package org.opensha.commons.param.editor.impl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.impl.ArbitrarilyDiscretizedFuncTableModel.ArbitrarilyDiscretizedFuncTableCellRenderer;
import org.opensha.commons.param.impl.ArbitrarilyDiscretizedFuncParameter;

/**
 * <b>Title:</b> ArbitrarilyDiscretizedFuncParameterEditor<p>
 *
 * <b>Description:</b> Subclass of ParameterEditor for editing ArbitrarilyDiscretizedFunc.
 * The widget is a JTextArea which allows X and Y values to be filled in.  <p>
 *
 * @author Vipin Gupta, Nitin Gupta
 * @version 1.0
 */
public class ArbitrarilyDiscretizedFuncParameterEditor
extends AbstractParameterEditor<ArbitrarilyDiscretizedFunc>
implements ActionListener, DocumentListener, TableModelListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "DiscretizedFuncParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	private ArbitrarilyDiscretizedFuncTableModel tableModel;
	private JTable table;

	private JButton addButton;
	private JButton removeButton;

	private JTextField xField;
	private JTextField yField;

	boolean xDataGood;
	boolean yDataGood;

	private boolean isFocusListenerForX = false;

	private ArbitrarilyDiscretizedFuncTableCellRenderer renderer;

	private JPanel widgetPanel;

	/**
	 * Constructor that sets the parameter that it edits. An
	 * Exception is thrown if the model is not an DiscretizedFuncParameter <p>
	 */
	public ArbitrarilyDiscretizedFuncParameterEditor(Parameter<ArbitrarilyDiscretizedFunc> model)
	throws Exception {

		super(model);
	}

	/**
	 * Whether you want the user to be able to type in the X values
	 * @param isEnabled
	 */
	public void setXEnabled(boolean isEnabled) {
		// TODO: fix
		//      this.xValsTextArea.setEnabled(isEnabled);
	}

	/**
	 * It enables/disables the editor according to whether user is allowed to
	 * fill in the values.
	 */
	public void setEnabled(boolean isEnabled) {
		// TODO: fix this
		//		this.xValsTextArea.setEnabled(isEnabled);
		//		this.yValsTextArea.setEnabled(isEnabled);

		this.table.setEnabled(isEnabled);
		this.tableModel.setEnabled(isEnabled);
		this.addButton.setEnabled(isEnabled);
		this.removeButton.setEnabled(isEnabled);

		//		super.setEnabled(isEnabled);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == addButton) {
			// add
			if (D) System.out.print("Adding...");
			double x = Double.parseDouble(xField.getText());
			double y = Double.parseDouble(yField.getText());
			if (D) System.out.println(" " + x + ", " + y);
			tableModel.addPoint(x, y);
		} else if (e.getSource() == removeButton) {
			if (D) System.out.println("Removing...");
			int selected[] = table.getSelectedRows();
			if (selected.length > 0)
				tableModel.removePoints(selected);
		}
	}

	public void changedUpdate(DocumentEvent e) {
		validateAddInput();
	}

	public void insertUpdate(DocumentEvent e) {
		validateAddInput();
	}

	public void removeUpdate(DocumentEvent e) {
		validateAddInput();
	}

	private void validateAddInput() {
		if (D) System.out.print("Validating..._");
		try {
			if (D) System.out.print(xField.getText() + "_");
			Double x = Double.parseDouble(xField.getText());
			if (D) System.out.print(yField.getText());
			Double y = Double.parseDouble(yField.getText());
			boolean bad = x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite();
			if (D) {
				if (bad)
					System.out.println("_NaN/Inf!");
				else
					System.out.println("_GOOD!");
			}
			addButton.setEnabled(!bad);
		} catch (NumberFormatException e1) {
			if (D) System.out.println("_BAD!");
			addButton.setEnabled(false);
		}
	}

	public void tableChanged(TableModelEvent e) {
		if (D) System.out.println("Table event...");
		setValue(tableModel.getFunction().deepClone());
		if (D) System.out.println("Table event DONE");
	}

	public static void main(String args[]) throws Exception {
		JFrame frame = new JFrame();

		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.set(0.1, 5.5);
		func.set(0.2, 90.5);
		func.set(0.4, 2.5);
		func.set(0.8, 1.5);

		ArbitrarilyDiscretizedFuncParameter param = new ArbitrarilyDiscretizedFuncParameter("Param", func);

		ArbitrarilyDiscretizedFuncParameterEditor editor = new ArbitrarilyDiscretizedFuncParameterEditor(param);

		frame.setSize(500, 500);
		frame.setContentPane(editor);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		editor.validate();
		editor.refreshParamEditor();
	}

	@Override
	public boolean isParameterSupported(
			Parameter<ArbitrarilyDiscretizedFunc> param) {
		if (param == null)
			return false;
		
		if (param.getValue() != null && !(param.getValue() instanceof ArbitrarilyDiscretizedFunc))
			return false;
		
		return true;
	}

	@Override
	protected JComponent buildWidget() {
		// set the value in ArbitrarilyDiscretizedFunc
		ArbitrarilyDiscretizedFunc function = getValue();
		if (function == null)
			function = new ArbitrarilyDiscretizedFunc();

		JPanel buttonPanel = new JPanel(new BorderLayout());
		JPanel leftButtonPanel = new JPanel();
		leftButtonPanel.setLayout(new BoxLayout(leftButtonPanel, BoxLayout.X_AXIS));

		if (addButton == null) {
			addButton = new JButton("+");
			addButton.addActionListener(this);
			addButton.setEnabled(false);
		}
		if (removeButton == null) {
			removeButton = new JButton("-");
			removeButton.addActionListener(this);
		}
		if (xField == null) {
			xField = new JTextField();
			//			xField.addFocusListener(focusListener);
			xField.getDocument().addDocumentListener(this);
			xField.setColumns(4);
			xDataGood = false;
		}
		if (yField == null) {
			yField = new JTextField();
			//			yField.addFocusListener(focusListener);
			yField.getDocument().addDocumentListener(this);
			yField.setColumns(4);
			yDataGood = false;
		}
		leftButtonPanel.add(new JLabel("x: "));
		leftButtonPanel.add(xField);
		leftButtonPanel.add(new JLabel("y: "));
		leftButtonPanel.add(yField);
		leftButtonPanel.add(addButton);

		buttonPanel.add(leftButtonPanel, BorderLayout.WEST);
		buttonPanel.add(removeButton, BorderLayout.EAST);

		tableModel = new ArbitrarilyDiscretizedFuncTableModel(function);
		table = new JTable(tableModel);
		tableModel.addTableModelListener(this);
		//		table.setDefaultEditor(Double.class, new ArbitrarilyDiscretizedFuncTableCellEditor());
		renderer = tableModel.getRenderer();
		table.setDefaultRenderer(Double.class, renderer);

		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(100, 200));

		widgetPanel = new JPanel(new BorderLayout());
		widgetPanel.add(scroll, BorderLayout.CENTER);
		widgetPanel.add(buttonPanel, BorderLayout.SOUTH);

		widgetPanel.setBackground(null);
		widgetPanel.validate();
		widgetPanel.repaint();

		return widgetPanel;
	}

	@Override
	protected JComponent updateWidget() {
		ArbitrarilyDiscretizedFunc function = getValue();
		if (D) System.out.println("Update called!");
		if (function == null)
			function = new ArbitrarilyDiscretizedFunc();
		tableModel.updateData(function);

		widgetPanel.validate();
		widgetPanel.repaint();
		return widgetPanel;
	}
}
