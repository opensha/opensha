package org.opensha.sha.gui.infoTools;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
//import javax.swing.UIManager;

/**
 * The Swing JProgressBar has an indeterminate loading bar option, where
 * an animated brick can swing back and forth inside the progress bar
 * to represent progress of an unknown period of time.
 * Unfortunately, this feature does not work with the modern macOS default
 * UI Manager, "Aqua Look and Feel (L&F)". While changing the UI manager
 * does resolve this issue it changes the layout of the entire application
 * and can make it look very outdated.
 * 
 * The IndeterminateProgressBar reimplements the logic such that it can be
 * rendered correctly across platforms with the default UI Manager.
 * 
 * While this can be used as a determinate progress bar as well, there's no
 * reason to do so and it can be confusing so it's discouraged. Use JProgressBar
 * directly for such cases.
 */
public class IndeterminateProgressBar extends JProgressBar {
	private static final long serialVersionUID = -8490124049802986652L;
	private Timer timer;
	private int offset = 0;
	private int direction = 1; // 1 = right, -1 = left
	private final int step = 10;
	
	public IndeterminateProgressBar(String loadingMessage) {
		setString(loadingMessage);
        setPreferredSize(new Dimension(300, 24));
	}
	
	public IndeterminateProgressBar() {
		setString("Loading...");
	}

	@Override
	public void setIndeterminate(boolean newValue) {
		super.setIndeterminate(newValue);
		if (newValue) {
			if (timer == null) {
				timer = new Timer(100, e -> {
					offset += direction * step;

					if (offset + getBarWidth() >= getWidth()) {
						offset = getWidth() - getBarWidth();
						direction = -1;
					} else if (offset <= 0) {
						offset = 0;
						direction = 1;
					}

					repaint();
				});
			}
			timer.start();
		} else {
			if (timer != null) {
				timer.stop();
				offset = 0;
				repaint();
			}
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (isIndeterminate()) {
			// Draw animated bar
			Graphics2D g2 = (Graphics2D) g.create();
			int barHeight = getHeight() / 2;
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // 50% opacity
			g2.setColor(Color.BLUE);
			g2.fillRoundRect(offset, 0, getBarWidth(), barHeight, 10, 10); // 10 is arc width/height
			
			 // Draw string if enabled
			if (isStringPainted() && getString() != null) {
				g2.setColor(getForeground()); // Or any contrasting color
				g2.setComposite(AlphaComposite.SrcOver); // Full opacity
				FontMetrics fm = g2.getFontMetrics();
				String text = getString();
				int textWidth = fm.stringWidth(text);
				int textHeight = fm.getAscent();
				int x = (getWidth() - textWidth) / 2;
				int y = (getHeight() + textHeight) / 2 - fm.getDescent();
				g2.drawString(text, x, y);
			}

			g2.dispose();
		} else {
			super.paintComponent(g);
		}
	}
	
	private int getBarWidth() {
		return getWidth() / 5;
	}
	
	// Use the toggle method to set and unset the indeterminate progress bar
	public void toggle() {
		setStringPainted(!isIndeterminate());
		setIndeterminate(!isIndeterminate());
	}

	// Demonstration
	public static void main(String[] args) {
//		try {
//			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		IndeterminateProgressBar indBar = new IndeterminateProgressBar();
		indBar.setPreferredSize(new Dimension(200, 24));
		indBar.toggle();

		JPanel mainPanel = new JPanel();
		mainPanel.add(indBar);

		JFrame demoApp = new JFrame("Progress Demo");
		demoApp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		demoApp.add(mainPanel);
		demoApp.pack(); // <-- Adjusts size based on contents
		demoApp.setLocationRelativeTo(null); // <-- Optional: center on screen
		demoApp.setVisible(true); // <-- Makes it visible!


	}
}
