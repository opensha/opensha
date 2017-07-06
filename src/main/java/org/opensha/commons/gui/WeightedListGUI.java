package org.opensha.commons.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opensha.commons.data.WeightedList;

public class WeightedListGUI extends JPanel implements ChangeListener, ItemListener, KeyListener, FocusListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final DecimalFormat df = new DecimalFormat("0.0000");
	
	private ArrayList<JSlider> sliders;
	private ArrayList<JTextField> textFields;
	
	public static final int MINIMUM_WIDTH = 200;
	
	private WeightedList<?> list;
	
	private JScrollPane editorsScroll;
	private JPanel editorsPanel;
	
	private static final String SET_NAME = "Set Weights...";
	private static final String SET_NORMALIZED = "Normalized";
	private static final String SET_EQUAL = "Equal";
	private static final String SET_ZERO = "Zero";
	private static final String SET_ONE = "One";
	
	private JComboBox setCombo;
	
	public WeightedListGUI(WeightedList<?> list) {
		super(new BorderLayout());
		
		init();
		
		setList(list);
	}
	
	private void init() {
		editorsPanel = new JPanel();
		
		editorsScroll = new JScrollPane(editorsPanel);
		
		this.add(editorsScroll, BorderLayout.CENTER);
		
		String[] items = new String[5];
		items[0] = SET_NAME;
		items[1] = SET_NORMALIZED;
		items[2] = SET_EQUAL;
		items[3] = SET_ZERO;
		items[4] = SET_ONE;
		
		setCombo = new JComboBox(items);
		setCombo.setSelectedIndex(0);
		setCombo.addItemListener(this);
		
		this.add(setCombo, BorderLayout.SOUTH);
	}
	
	public void setList(WeightedList<?> list) {
		this.list = list;
		editorsPanel.removeAll();
		
		if (list == null || list.size() == 0)
			return;
		
		sliders = new ArrayList<JSlider>();
		textFields = new ArrayList<JTextField>();
		
		editorsPanel.setLayout(new BoxLayout(editorsPanel, BoxLayout.Y_AXIS));
		
		for (int i=0; i<list.size(); i++) {
			Object obj = list.get(i);
			
			JSlider slide = new JSlider(weightToPos(list.getWeightValueMin()), weightToPos(list.getWeightValueMax()));
			JTextField field = new JTextField(5);
			
			sliders.add(slide);
			textFields.add(field);
			
			JPanel editorPanel = new JPanel();
			editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));
			
			String name = WeightedList.getName(obj);
			
			editorsPanel.add(new JLabel(name));
			editorPanel.add(slide);
			JPanel fieldPanel = new JPanel();
			fieldPanel.add(field);
			editorPanel.add(fieldPanel);
			
			JPanel editorWrap = new JPanel();
			editorWrap.add(editorPanel);
			editorsPanel.add(editorWrap);
		}
		
		updateSliders();
		
		for (JSlider slide : sliders)
			slide.addChangeListener(this);
		
		for (JTextField field : textFields) {
			field.addKeyListener(this);
			field.addFocusListener(this);
		}
		
		editorsScroll.setPreferredSize(new Dimension(200, 200));
//		editorsScroll.setMinimumSize(new Dimension(200, 200));
		
		editorsScroll.invalidate();
	}
	
	private void updateSliders() {
		for (int i=0; i<list.size(); i++) {
			double weight = list.getWeight(i);
			int pos = weightToPos(weight);
			
			sliders.get(i).setValue(pos);
			textFields.get(i).setText(df.format(weight));
		}
	}
	
	private int weightToPos(double weight) {
		return (int)(weight * 100d + 0.5);
	}
	
	private double posToWeight(int pos) {
		return (double)pos / 100d;
	}
	
	private double getFormattedDouble(double weight) {
		String str = df.format(weight);
		return Double.valueOf(str);
	}
	
	private void weightUpdated(int i, double newWeight) {
		list.setWeight(i, newWeight);
		
		int sliderPos = weightToPos(newWeight);
		JSlider slider = sliders.get(i);
		JTextField field = textFields.get(i);
		if (sliderPos != slider.getValue())
			slider.setValue(sliderPos);
		
		double fieldValue = Double.valueOf(field.getText());
		if (fieldValue != getFormattedDouble(newWeight))
			field.setText(df.format(newWeight));
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() instanceof JSlider) {
			for (int i=0; i<sliders.size(); i++) {
				JSlider slider = sliders.get(i);
				if (slider == e.getSource()) {
					double listWeight = list.getWeight(i);
					int listSlidePos = weightToPos(listWeight);
					if (listSlidePos != slider.getValue()) {
						// this means that the slider was updated manually and
						// the value needs to be updated in the list
						weightUpdated(i, posToWeight(slider.getValue()));
					}
					
					break;
				}
			}
		}
	}
	
	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() == setCombo) {
			if (setCombo.getSelectedIndex() > 0) {
				Object selected = setCombo.getSelectedItem();
				if (SET_NORMALIZED.equals(selected))
					list.normalize();
				else if (SET_EQUAL.equals(selected))
					list.setWeightsEqual();
				else if (SET_ZERO.equals(selected))
					list.setWeightsToConstant(0d);
				else if (SET_ONE.equals(selected))
					list.setWeightsToConstant(1d);
				updateSliders();
				setCombo.setSelectedIndex(0);
			}
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		for (JSlider slider : sliders)
			slider.setEnabled(false);
		for (JTextField field : textFields)
			field.setEnabled(false);
		super.setEnabled(enabled);
	}

	@Override
	public void focusGained(FocusEvent e) {}

	@Override
	public void focusLost(FocusEvent e) {
//		System.out.println("FocusLost");
		textUpdated(e.getComponent());
	}

	@Override
	public void keyTyped(KeyEvent e) {
//		System.out.println("KeyTyped");
		if (e.getKeyChar() == '\n')
			textUpdated(e.getComponent());
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {}
	
	private void resetText(int i) {
		textFields.get(i).setText(df.format(list.getWeight(i)));
	}
	
	private void textUpdated(Component comp) {
		for (int i=0; i<textFields.size(); i++) {
			JTextField field = textFields.get(i);
			if (field == comp) {
				try {
					double weight = Double.parseDouble(field.getText());
					if (!list.isWeightWithinRange(weight))
						resetText(i);
					else
						weightUpdated(i, weight);
				} catch (NumberFormatException e) {
					resetText(i);
				}
			}
		}
	}

}
