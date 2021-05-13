package org.opensha.ui.components;

import static java.awt.BorderLayout.*;
import static javax.swing.BoxLayout.*;
import static javax.swing.JScrollPane.*;
import static org.opensha.ui.components.TaskComponent.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Utility window component that provides a graphic interface to monitor and
 * cancel multiple long running tasks. This component runs on the EDT and users
 * are reponsible for executing actual tasks on separate threads. 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ProgressPane extends JFrame {
	
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 480;
	private static final int PANEL_INSET = 140;
	private static final Color STRIPE = new Color(240, 240, 240);

	private JPanel content;
	
	/**
	 * Constructs a new progress pane.
	 * @param title for the pane
	 */
	public ProgressPane(String title) {
		super(title);
		setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
		content = new Content();
		JScrollPane scroller = new JScrollPane(VERTICAL_SCROLLBAR_AS_NEEDED,
			HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(null);
		scroller.setViewportView(content);
		getContentPane().add(scroller, CENTER);
		pack();
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int xPos = d.width - PANEL_WIDTH - PANEL_INSET;
		setLocation(xPos, PANEL_INSET);
		setVisible(true);
	}
	
	/**
	 * Adds and returns a new <code>TaskComponent</code> to the window.
	 * @param listener for cancellations
	 * @return  the added component
	 * @see TaskComponent
	 */
	public TaskComponent addTask(ActionListener listener) {
		TaskComponent pc = new TaskComponent(listener);
		content.add(pc);
		return pc;
	}
	
	/**
	 * Removes the supplied <code>TaskComponent</code> from the window ignoring
	 * <code>null</code> or unlisted ones.
	 * @param task to remove
	 */
	public void removeTask(TaskComponent task) {
		if (task != null) {
			for (ActionListener l : task.cancel.getActionListeners()) {
				task.cancel.removeActionListener(l);
			}
			remove(task);
		}
	}

	private class Content extends JPanel {
		
		Content() {
			setLayout(new BoxLayout(this, PAGE_AXIS));
			setBackground(Color.WHITE);
			setForeground(STRIPE);
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();
			g.setColor(getBackground());
			g.fillRect(0, 0, w, h);
			for (int i=PROG_HEIGHT; i<getHeight(); i+=2*PROG_HEIGHT) {
				g.setColor(getForeground());
				g.fillRect(0, i, w, PROG_HEIGHT);
			}
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable(){
		    @Override
		    public void run(){
		    	ProgressPane pp = new ProgressPane("Progress Monitor");
		    	pp.addTask(null);
		    	pp.addTask(null);
		    } 
		});
	}
}
