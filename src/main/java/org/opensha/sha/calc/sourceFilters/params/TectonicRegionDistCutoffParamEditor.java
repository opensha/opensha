package org.opensha.sha.calc.sourceFilters.params;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.text.ParseException;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.impl.NumericTextField;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter.TectonicRegionDistanceCutoffs;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

class TectonicRegionDistCutoffParamEditor extends AbstractParameterEditor<TectonicRegionDistanceCutoffs>
implements FocusListener, KeyListener {
	
	private static final boolean D = false;
	
	private static TectonicRegionType[] trts = TectonicRegionType.values();
	
	static final DecimalFormat oDF = new DecimalFormat("0.##");
	static final int DIST_COLS = 7;
	
	private JPanel panel;
	private NumericTextField[] fields;

	public TectonicRegionDistCutoffParamEditor(Parameter<TectonicRegionDistanceCutoffs> param) {
		super(param);
	}

	@Override
	public boolean isParameterSupported(Parameter<TectonicRegionDistanceCutoffs> param) {
		return param != null && param.getValue() instanceof TectonicRegionDistanceCutoffs;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (fields != null) {
			panel.setEnabled(enabled);
			for (NumericTextField field : fields)
				field.setEnabled(enabled);
		}
	}
	
	@Override
	public boolean isEnabled() {
		return panel != null && panel.isEnabled();
	}

	@Override
	protected JComponent buildWidget() {
		TectonicRegionDistanceCutoffs cutoffs = getParameter().getValue();
		Preconditions.checkNotNull(cutoffs, "Cutoffs are null in the parameter");
		int rows = trts.length;
		fields = new NumericTextField[rows];
		
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		for (int i=0; i<rows; i++) {
			String text = trts[i].toString();
			double val = cutoffs.getCutoffDist(trts[i]);
			JLabel label = new JLabel(text);
//			label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
//			label.setPreferredSize(new Dimension(40, 20));
			fields[i] = new NumericTextField(text, 7, oDF);
			fields[i].setText(oDF.format(val));
			fields[i].addFocusListener(this);
			fields[i].addKeyListener(this);
//			fields[i].setPreferredSize(new Dimension(40, 20));
			
			JPanel subPanel = new JPanel();
			subPanel.setLayout(new BorderLayout());
			label.setPreferredSize(new Dimension(100, 18));
			fields[i].setPreferredSize(new Dimension(30, 18));
			subPanel.add(label, BorderLayout.CENTER);
			subPanel.add(fields[i], BorderLayout.EAST);
			panel.add(subPanel);
		}
		panel.setPreferredSize(new Dimension(130, 18*rows));
		panel.setMinimumSize(new Dimension(130, 18*rows));
		this.panel = panel;
		return panel;
	}

	@Override
	protected JComponent updateWidget() {
		TectonicRegionDistanceCutoffs cutoffs = getParameter().getValue();
		Preconditions.checkNotNull(cutoffs, "Cutoffs are null in the parameter");
		Preconditions.checkNotNull(panel, "Can't update, not yet built");
		for (int i=0; i<fields.length; i++) {
			double val;
			if (i<trts.length)
				val = cutoffs.getCutoffDist(trts[i]);
			else
				val = cutoffs.getCutoffDist(null);
			fields[i].setText(oDF.format(val));
			fields[i].invalidate();
		}
		return panel;
	}

	@Override
	public void focusGained(FocusEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void focusLost(FocusEvent e) {
		for (int i=0; i<fields.length; i++) {
			if (e.getComponent() == fields[i]) {
				if (D) System.out.println("Focust lost for "+i);
				updateForIndex(i);
				break;
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		if (e.getKeyChar() == '\n') {
			// enter was pressed
			for (int i=0; i<fields.length; i++) {
				if (e.getComponent() == fields[i]) {
					if (D) System.out.println("Enter was pressed for "+i);
					updateForIndex(i);
					break;
				}
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {}
	
	private void updateForIndex(int index) {
		TectonicRegionDistanceCutoffs cutoffs = getParameter().getValue();
		Preconditions.checkNotNull(cutoffs, "Cutoffs are null in the parameter");
		NumericTextField field = fields[index];
		boolean reset = false;
		try {
			double val = field.getDoubleValue();
			if (val <= 0d) {
				JOptionPane.showMessageDialog(panel, "Bad distance: "+val,
						"Error Parsing Distance", JOptionPane.ERROR_MESSAGE);
				reset = true;
			} else {
				if (D) System.out.println("Updating "+index+" to "+(float)val);
				cutoffs.setCutoffDist(trts[index], val);
			}
		} catch (ParseException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(panel, "Error parsing text: "+e.getMessage(),
					"Error Parsing Distance", JOptionPane.ERROR_MESSAGE);
			reset = true;
		}
		
		if (reset) {
			double val = cutoffs.getCutoffDist(trts[index]);
			field.setValue(val);
			field.invalidate();
		}
	}
	
	public static void main(String[] args) {
		TectonicRegionDistCutoffParam param = new TectonicRegionDistCutoffParam();
		
		TectonicRegionDistCutoffParamEditor editor = new TectonicRegionDistCutoffParamEditor(param);
		
		JFrame frame = new JFrame();
		frame.setContentPane(editor.buildWidget());
		
		frame.setVisible(true);
//		frame.setPreferredSize(new Dimension(400, 400));
//		frame.setSize(400, 400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.validate();
	}

}
