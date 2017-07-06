package org.opensha.commons.param.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

import org.opensha.commons.gui.LabeledBorderPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ClassUtils;

public abstract class AbstractParameterEditor<E> extends LabeledBorderPanel implements ParameterEditor<E> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Parameter<E> param;

	private JComponent widget;

	protected final static Dimension LABEL_DIM = new Dimension( 100, 20 );
	protected final static Dimension LABEL_PANEL_DIM = new Dimension( 120, 20 );
	protected final static Dimension WIGET_PANEL_DIM = new Dimension( 120, 28 );

	public static Font DEFAULT_FONT = new Font( "SansSerif", Font.PLAIN, 11 );

	protected final static Border CONST_BORDER = BorderFactory.createLineBorder( Color.blue, 1 );
	protected final static Border FOCUS_BORDER = BorderFactory.createLineBorder( Color.orange, 1 );
	protected final static Border ETCHED = BorderFactory.createEtchedBorder();

//	public static Font DEFAULT_LABEL_FONT = new Font( "SansSerif", Font.BOLD, 12 );
//	public static Color FORE_COLOR = new Color( 80, 80, 140 );

	/**
	 * Default Constructor. Sets parameter to null.
	 */
	public AbstractParameterEditor() {
		this(null);
	}

	/**
	 * Create the parameter editor and set the parameter to the given parameter.
	 * 
	 * @param param
	 */
	public AbstractParameterEditor(Parameter<E> param) {
		super(new BorderLayout(), true, false);
		
//		this.setBackground(null);
//		editorPanel.setBackground(null);
//		mainPanel.setBackground(null);
//		setBorderColor(FORE_COLOR);
//		setTitleFont(DEFAULT_LABEL_FONT);
		super.initParameterLookAndFeel();

		setParameter(param);
	}

	/**
	 * Set the value in the parameter. This also calls <code>refreshParamEditor</code>, and should be
	 * used to externally both set the parameter value and refresh the parameter.
	 * 
	 * @param value the value to set in the current parameter
	 * @throws NullPointerException if the current parameter is null
	 */
	@Override
	public final void setValue(E value) throws NullPointerException {
		this.param.setValue(value);
		refreshParamEditor();
	}

	@Override
	public final E getValue() throws NullPointerException {
		return param.getValue();
	}

	@Override
	public void unableToSetValue(Object value) {
		param.unableToSetValue(value);
	}

	@Override
	public final Parameter<E> getParameter() {
		return param;
	}

	@Override
	public final void setParameter(Parameter<E> model) {
		if (!isParameterSupported(model)) {
			if (model == null)
				throw new IllegalArgumentException("null parameters not supported by this editor");
			else
				throw new IllegalArgumentException(
						"Parameter '"+model.getName()+"' of type '"
						+ClassUtils.getClassNameWithoutPackage(model.getClass())
						+"' not supported by this editor");
		}
		this.param = model;
		updateTitle();
		refreshParamEditor();
	}

	/**
	 * Abstract method that allows subclasses to define if a specific parameter is valid. An editor
	 * could, for example, disallow null parameters or only allow parameters with a specific type
	 * of constraint. This is called when <code>setParameter</code> is called.
	 * 
	 * @param param
	 * @return
	 */
	public abstract boolean isParameterSupported(Parameter<E> param);

	protected void updateTitle() {
		String label;
		if (param == null) {
			label = "";
		} else {
			label = param.getName();
			String units = param.getUnits();
			if (label == null) {
				label = "";
			} else {
				if ( ( units != null ) && !( units.equals( "" ) ) )
					label += " (" + units + ")";
				label += ':';
			}
		}
		super.setTitle(label);
	}

	@Override
	public final void setFocusEnabled(boolean newFocusEnabled) {
		// TODO Auto-generated method stub
	}

	@Override
	public final boolean isFocusEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public final JComponent getComponent() {
		return this;
	}

	@Override
	public final void refreshParamEditor() {
		if (widget == null) {
			widget = buildWidget();
			this.add(widget);
		} else {
			JComponent updated = updateWidget();
			if (updated == null)
				updated = buildWidget();
			if (updated != widget) {
				// this means it was completely rebuilt
				this.removeWidget();
				this.widget = updated;
				this.add(widget);
			}
		}
		widget.validate();
		this.validate();
		super.setToolTipText(getLabelToolTipText());
		widget.setToolTipText(getWidgetToolTipText());
		//		mainPanel.setMinimumSize( WIGET_PANEL_DIM );
		//		mainPanel.setPreferredSize( WIGET_PANEL_DIM );
		editorPanel.invalidate();

	}

	@Override
	public abstract void setEnabled(boolean enabled);

	/**
	 * This will be called by <code>refreshParamEditor</code> to initially build the widget component.
	 * 
	 * @return widget editing component to be displayed in the editor
	 */
	protected abstract JComponent buildWidget();

	/**
	 * This is called by <code>refreshParamEditor</code> when the parameters value is changed externally
	 * and the widget needs to be updated to reflect the new value. If the widget can be simply updated
	 * to display/edit the new value, it should do so.
	 * 
	 * <br><br>If it needs to be rebuilt, then this can either
	 * return null (which will result in a subsequent call to <code>buildWidget</code>), or return the new
	 * component.
	 * 
	 * @return updated component, or new component/null if it needs to be rebuilt
	 */
	protected abstract JComponent updateWidget();

	/**
	 * 
	 * @return tool tip text for the parameter label. can be overridden if constraint specific text
	 * needs to be added.
	 */
	protected String getLabelToolTipText() {
		if (param == null)
			return null;
		String info = param.getInfo();
		if (info == null || info.length() == 0 || info.equals(" "))
			return null;
		
		return "<html>"+info.replaceAll("\n", "<br>")+"</html>";
	}

	/**
	 * 
	 * @return tool tip text for the parameter. can be overridden if constraint specific text
	 * needs to be added.
	 */
	protected String getWidgetToolTipText() {
		return getLabelToolTipText();
	}

	protected void removeWidget() {
		if (widget != null)
			super.remove(widget);
	}

	protected final JComponent getWidget() {
		return widget;
	}

	@Override
	public void setEditorBorder(Border border) {
		mainPanel.setBorder(border);
	}

	// TODO this should take an object and call toString()
	public static JLabel makeSingleConstraintValueLabel( String label ) {

		JLabel l = new JLabel();
		l.setPreferredSize( LABEL_DIM );
		l.setMinimumSize( LABEL_DIM );
//		l.setFont( JCOMBO_FONT );
		l.setForeground( Color.blue );
		l.setBorder( CONST_BORDER );
		l.setText( label );
		return l;
	}

}
