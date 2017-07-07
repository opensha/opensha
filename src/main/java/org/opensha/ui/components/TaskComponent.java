package org.opensha.ui.components;

import static javax.swing.BoxLayout.LINE_AXIS;
import static javax.swing.BoxLayout.PAGE_AXIS;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.apache.commons.lang3.StringUtils;

/**
 * A component that can be updated as a task proceeds. Instances of this
 * class are returned by {@link ProgressPane#addTask(ActionListener)}.
 *
* @author Peter Powers
* @version $Id:$
* @see ProgressPane
 */
public class TaskComponent extends JPanel implements ActionListener {

	static final int PROG_INSET = 10;
	static final int PROG_HEIGHT = 60;

	JProgressBar progress;
	JButton cancel;
	JLabel info;
	
	TaskComponent(ActionListener listener) {
		super(new BorderLayout());
		setLayout(new BoxLayout(this, PAGE_AXIS));
		Dimension d = new Dimension(100, PROG_HEIGHT);
		setPreferredSize(d);
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(PROG_INSET-4, PROG_INSET,
			PROG_INSET, PROG_INSET));
		
		JPanel row1 = new JPanel();
		row1.setLayout(new BoxLayout(row1, LINE_AXIS));
		row1.setOpaque(false);
		JLabel info = new JLabel("Idle");
		info.putClientProperty("JComponent.sizeVariant", "small");
		row1.add(info);
		row1.add(Box.createHorizontalGlue());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(listener);
		cancel.addActionListener(this);
		cancel.putClientProperty("JComponent.sizeVariant", "small");
		cancel.putClientProperty("JButton.buttonType", "textured");
		cancel.setFocusPainted(false);
		row1.add(cancel);
		add(row1);

		add(Box.createVerticalStrut(2));
		
		JProgressBar progress = new JProgressBar(0, 100);
		progress.setValue(0);
		progress.putClientProperty("JComponent.sizeVariant", "mini");
		add(progress);
	}
	
	/**
	 * Updates the components progress bar to the supplied value.
	 * @param value for update
	 */
	public void setProgress(int value) {
		progress.setValue(value);
	}
	
	/**
	 * Updates the components info message to the supplied value
	 * @param info for update
	 */
	public void setInfo(String info) {
		this.info.setText(StringUtils.isEmpty(info) ? " " : info);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		setInfo("Cancelled");
	}
	
}
