package org.opensha.sha.calc.sourceFilters.params;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;

public class SourceFiltersParamEditor extends AbstractParameterEditor<SourceFilterManager> implements ActionListener {
	
	private static final SourceFilters[] filters = SourceFilters.values();
	
	private JPanel panel;
	
	private JCheckBox[] checkBoxes;
	
	private ParameterList params;
	private ParameterListEditor paramsEdit;
	
	private boolean globalEnabled = true;
	
	public SourceFiltersParamEditor(SourceFiltersParam param) {
		super(param);
	}

	@Override
	public boolean isParameterSupported(Parameter<SourceFilterManager> param) {
		return param instanceof SourceFiltersParam;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (panel == null)
			return;
		
		globalEnabled = enabled;
		updateWidget();
		
		for (JCheckBox box : checkBoxes)
			box.setEnabled(enabled);
		
		SourceFilterManager manager = getParameter().getValue();
		for (int i=0; i<filters.length; i++) {
			boolean selected = manager.isEnabled(filters[i]);
			
			ParameterList filterParams = manager.getFilterInstance(filters[i]).getAdjustableParams();
			if (filterParams != null)
				for (Parameter<?> param : filterParams)
					param.getEditor().setEnabled(enabled && selected);
		}
		paramsEdit.refreshParamEditor();
	}
	
	@Override
	public boolean isEnabled() {
		return globalEnabled;
	}

	@Override
	protected JComponent buildWidget() {
		globalEnabled = true;
		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		SourceFilterManager manager = getParameter().getValue();
		
		params = new ParameterList();
		checkBoxes = new JCheckBox[filters.length];
		
		for (int i=0; i<filters.length; i++) {
			SourceFilters filter = filters[i];
			checkBoxes[i] = new JCheckBox(filter.toString(), null, manager.isEnabled(filter));
			checkBoxes[i].addActionListener(this);
			checkBoxes[i].setHorizontalAlignment(SwingConstants.LEFT);
//			panel.add(checkBoxes[i]);
			JPanel subPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			subPanel.add(checkBoxes[i]);
			panel.add(subPanel);
			
			ParameterList filterParams = manager.getFilterInstance(filter).getAdjustableParams();
			if (filterParams != null) {
				for (Parameter<?> param : filterParams) {
					params.addParameter(param);
					ParameterEditor<?> editor = param.getEditor();
					if (editor instanceof AbstractParameterEditor<?>)
						// to make it clear what is enabled vs disabled
						((AbstractParameterEditor<?>)editor).setShowDisabledStatusInTitle(true);
				}
				params.addParameterList(filterParams);
			}
		}
		
		paramsEdit = new ParameterListEditor(params);
		paramsEdit.setTitle("Adjustable Parameters");
		panel.add(paramsEdit);
		
		updateWidget();
		return panel;
	}

	@Override
	protected JComponent updateWidget() {
		SourceFilterManager manager = getParameter().getValue();
		for (int i=0; i<filters.length; i++) {
			boolean checked = checkBoxes[i].isSelected();
			boolean filterEnabled = manager.isEnabled(filters[i]);
			if (filterEnabled != checked)
				checkBoxes[i].setSelected(filterEnabled);
			checkBoxes[i].setEnabled(globalEnabled);
			
			ParameterList filterParams = manager.getFilterInstance(filters[i]).getAdjustableParams();
			if (filterParams != null) {
				for (Parameter<?> param : filterParams) {
					param.getEditor().setEnabled(globalEnabled && filterEnabled);
					param.getEditor().refreshParamEditor();
				}
			}
		}
		paramsEdit.refreshParamEditor();
		return panel;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		for (int i=0; i<checkBoxes.length; i++) {
			if (e.getSource() == checkBoxes[i]) {
				boolean selected = checkBoxes[i].isSelected();
				SourceFilterManager manager = getParameter().getValue();
				manager.setEnabled(filters[i], selected);
				updateWidget();
				break;
			}
		}
	}
	
	public static void main(String[] args) {
		SourceFiltersParam param = new SourceFiltersParam();
		
		SourceFiltersParamEditor editor = new SourceFiltersParamEditor(param);
		
		JFrame frame = new JFrame();
		frame.setContentPane(editor.buildWidget());
		
		frame.setVisible(true);
//		frame.setPreferredSize(new Dimension(400, 400));
		frame.setSize(400, 400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.validate();
	}

}
