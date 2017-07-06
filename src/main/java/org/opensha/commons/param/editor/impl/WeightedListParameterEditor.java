package org.opensha.commons.param.editor.impl;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.gui.WeightedListGUI;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.WeightedListParameter;

public class WeightedListParameterEditor extends AbstractParameterEditor<WeightedList<?>> implements ComponentListener, ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private boolean isGuiMode;
	
	private JButton editButton;
	private WeightedListGUI gui;
	private JDialog guiDialog;
	private JButton dialogOKButton;
	
	public WeightedListParameterEditor() {
		super();
	}
	
	public WeightedListParameterEditor(Parameter model) {
		super(model);
	}
	
	@Override
	public void setEnabled(boolean isEnabled) {
		gui.setEnabled(isEnabled);
	}
	
	public static void main(String[] args) {
		ArrayList<String> objects = new ArrayList<String>();
		ArrayList<Double> weights = new ArrayList<Double>();
		objects.add("item 1");
		weights.add(0.25);
		objects.add("item 2");
		weights.add(0.25);
		objects.add("item 3");
		weights.add(0.25);
		objects.add("item 4");
		weights.add(0.25);
		
		WeightedList<String> list =
			new WeightedList<String>(objects, weights);
		
		WeightedListParameter<String> param =
			new WeightedListParameter<String>("my param", list);
		
		JFrame frame = new JFrame();
		frame.setSize(400, 600);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(new WeightedListParameterEditor(param));
//		frame.pack();
		frame.setVisible(true);
	}

	@Override
	public boolean isParameterSupported(Parameter<WeightedList<?>> param) {
		if (param == null)
			return false;
		if (!(param.getValue() instanceof WeightedList))
			return false;
		return true;
	}

	@Override
	protected JComponent buildWidget() {
		if (gui == null) {
			gui = new WeightedListGUI(getList());
			
			this.addComponentListener(this);
			
			editButton = new JButton("Edit Weights");
			editButton.addActionListener(this);
		}
		return updateWidget();
	}
	
	private WeightedList<?> getList() {
		Parameter<WeightedList<?>> param = getParameter();
		if (param == null)
			return null;
		else
			return param.getValue();
	}
	
	private boolean shouldGuiBeVisible() {
		return this.getSize().getWidth() > 250;
	}

	@Override
	protected JComponent updateWidget() {
		System.out.println("updating!");
		gui.setList(getList());
		if (shouldGuiBeVisible()) {
//			System.out.println("gui mode now!");
			isGuiMode = true;
			return gui;
		} else {
//			System.out.println("button mode now!");
			isGuiMode = false;
			return editButton;
		}
	}

	@Override
	public void componentResized(ComponentEvent e) {
		if (isGuiMode != shouldGuiBeVisible()) {
			refreshParamEditor();
		}
	}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}
	
	private void buildDialog() {
		dialogOKButton = new JButton("Done");
		dialogOKButton.addActionListener(this);
		JPanel dialogPanel = new JPanel(new BorderLayout());
		dialogPanel.add(gui, BorderLayout.CENTER);
		dialogPanel.add(dialogOKButton, BorderLayout.SOUTH);
		guiDialog = new JDialog();
		guiDialog.setModal(true);
		guiDialog.setContentPane(dialogPanel);
		guiDialog.setSize(300, 400);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == editButton) {
			if (guiDialog == null)
				buildDialog();
			guiDialog.setTitle(getTitle());
			guiDialog.setVisible(true);
		} else if (e.getSource() == dialogOKButton) {
			guiDialog.setVisible(false);
		}
	}

}
