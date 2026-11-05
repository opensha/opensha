package org.opensha.sha.calc.sourceFilters.params;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/** A single parameter for selecting an arbitrary set of tectonic region types. */
public class TectonicRegionTypesParam extends AbstractParameter<Set<TectonicRegionType>> {

	public static final String NAME = "Included Tectonic Region Types";

	private Editor editor;

	public TectonicRegionTypesParam() {
		this(NAME, EnumSet.allOf(TectonicRegionType.class));
	}

	public TectonicRegionTypesParam(String name, Set<TectonicRegionType> trts) {
		super(name, null, null, copy(trts));
	}

	@Override
	public synchronized ParameterEditor<Set<TectonicRegionType>> getEditor() {
		if (editor == null)
			editor = new Editor(this);
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		return new TectonicRegionTypesParam(getName(), getValue());
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		return false;
	}

	private static EnumSet<TectonicRegionType> copy(Set<TectonicRegionType> trts) {
		Preconditions.checkNotNull(trts, "tectonic region types cannot be null");
		return trts.isEmpty() ? EnumSet.noneOf(TectonicRegionType.class) : EnumSet.copyOf(trts);
	}

	static class Editor extends AbstractParameterEditor<Set<TectonicRegionType>> implements ActionListener {

		private static final TectonicRegionType[] TRTS = TectonicRegionType.values();

		private JPanel panel;
		private JCheckBox[] checkBoxes;

		Editor(Parameter<Set<TectonicRegionType>> param) {
			super(param);
		}

		@Override
		public boolean isParameterSupported(Parameter<Set<TectonicRegionType>> param) {
			return param != null && param.getValue() instanceof Set<?>;
		}

		@Override
		public void setEnabled(boolean enabled) {
			if (panel != null) {
				panel.setEnabled(enabled);
				for (JCheckBox checkBox : checkBoxes)
					checkBox.setEnabled(enabled);
			}
		}

		@Override
		public boolean isEnabled() {
			return panel != null && panel.isEnabled();
		}

		@Override
		protected JComponent buildWidget() {
			panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			checkBoxes = new JCheckBox[TRTS.length];
			Set<TectonicRegionType> included = getParameter().getValue();
			for (int i=0; i<TRTS.length; i++) {
				checkBoxes[i] = new JCheckBox(TRTS[i].toString(), included.contains(TRTS[i]));
				checkBoxes[i].addActionListener(this);
				panel.add(checkBoxes[i]);
			}
			panel.setPreferredSize(new Dimension(180, 22*TRTS.length));
			panel.setMinimumSize(new Dimension(180, 22*TRTS.length));
			return panel;
		}

		@Override
		protected JComponent updateWidget() {
			Set<TectonicRegionType> included = getParameter().getValue();
			for (int i=0; i<TRTS.length; i++)
				checkBoxes[i].setSelected(included.contains(TRTS[i]));
			return panel;
		}

		@Override
		public void actionPerformed(ActionEvent event) {
			EnumSet<TectonicRegionType> included = EnumSet.noneOf(TectonicRegionType.class);
			for (int i=0; i<TRTS.length; i++)
				if (checkBoxes[i].isSelected())
					included.add(TRTS[i]);
			getParameter().setValue(included);
		}
	}
}
