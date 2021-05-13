package org.opensha.commons.mapping.gmt.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opensha.commons.util.cpt.CPT;

public class CPTPanel extends JPanel
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private BufferedImage bi;
	private BufferedImageOp bufImgOp;
	private float minVal, maxVal;
	private Map<Float,JLabel> labelTable;
	private int tickWidth;

	private int barX;
	private int barY;
	private int barWidth;
	private int barHeight;

	private DecimalFormat df = null;
	
	private Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);

	public CPTPanel (	BufferedImage bufImg,
			Map<Float,JLabel> labelTable,
			int tickWidth,
			float minVal,
			float maxVal) {
		update(bufImg, labelTable, tickWidth, minVal, maxVal);
		init();
	}

	public CPTPanel(CPT cpt, int width, int height, int numTicks, int tickWidth) {
		update(cpt, width, height, numTicks, tickWidth);
		init();
	}

	private void init() {
		//this will use an identity affine transform and linear, pixelated scaling (luckily scaling won't be necessary)
		this.bufImgOp = new AffineTransformOp(new AffineTransform(), AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		setLayout(null);
	}

	public void setDecimalFormat(DecimalFormat df) {
		this.df = df;
	}
	
	public void update(CPT cpt, int width, int height, int numTicks, int tickWidth) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Map<Float, JLabel> labelTable;
		float minVal;
		float maxVal;
		if (cpt == null) {
			labelTable = null;
			minVal = 0f;
			maxVal = 1f;
		} else {
			labelTable = new HashMap<Float, JLabel>();

			minVal = cpt.getMinValue();
			maxVal = cpt.getMaxValue();

			cpt.paintGrid(bi);

			if (numTicks >= 0) {
				float delta = (maxVal - minVal) / (float)(numTicks + 1);

				for (float tickVal=minVal; tickVal<=maxVal; tickVal+= delta) {
					JLabel label = new JLabel(getTickLabel(tickVal));
					labelTable.put(tickVal, label);
				}
			} else {
				int num = cpt.size();
				int mod = 1;
				while (num / mod > 10)
					mod++;
				for (int i=0; i<num; i++) {
					if (i % mod != 0)
						continue;
					float tickVal = cpt.get(i).start;
					JLabel label = new JLabel(getTickLabel(tickVal));
					labelTable.put(tickVal, label);
				}
				if (num > 0) {
					float tickVal = cpt.get(num-1).end;
					JLabel label = new JLabel(getTickLabel(tickVal));
					labelTable.put(tickVal, label);
				}
			}
		}

		update(bi, labelTable, tickWidth, minVal, maxVal);
	}

	private String getTickLabel(float tickVal) {
		if (df == null)
			return tickVal + "";
		else
			return df.format(tickVal);
	}

	public void updateCPT(CPT cpt) {
		bi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
		if (cpt != null) {
			cpt.paintGrid(bi);
		}
	}

	public void update(BufferedImage bufImg,
			Map<Float,JLabel> labelTable,
			int tickWidth,
			float minVal,
			float maxVal) {
		this.minVal = minVal;
		this.maxVal = maxVal;
		this.labelTable = labelTable;
		this.tickWidth = tickWidth;
		this.bi = bufImg;
		removeAll();
//		this.ready = false;
	}

	public void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		super.paintComponents(graphics);//might not be necessary
		
		barX = (getWidth() - bi.getWidth()) / 2;
		barY = 27;
		barY = (getHeight() - bi.getHeight()) / 2;
		barWidth = bi.getWidth();
		barHeight = bi.getHeight();

		if (labelTable != null) {
			removeAll();
			float pixSpace = (maxVal - minVal) / (float)barWidth;
			int pixOver;
			for (float dist : labelTable.keySet()) {
				pixOver = (int)((dist - minVal) / pixSpace);
				JLabel label = labelTable.get(dist);
				label.updateUI();
				Dimension d = label.getPreferredSize();
				label.setBounds(barX + pixOver - d.width / 2,
						barY + barHeight + tickWidth + 4,
						d.width,
						d.height);
				add(label);
			}
			invalidate();
		}

//		if (!ready) {
//			makeReady();
//			invalidate();
//			paintComponent(graphics);
//			return;
//		}

		Graphics2D g2d = (Graphics2D)graphics;
		g2d.drawImage(	bi,
				bufImgOp,
				barX,
				barY);
		//make 1 px black border around gradient
		g2d.setColor(Color.BLACK);
		g2d.drawRect(	barX - 1,
				barY - 1,
				barWidth + 1,
				barHeight + 1);
		//make tick marks
		if (labelTable != null) {
			float pixSpace = (maxVal - minVal) / (float)(barWidth);
			int pixOver;
			for (float dist : labelTable.keySet()) {
				pixOver = (int)((dist - minVal) / pixSpace);
				if (pixOver == 0)
					pixOver = -1; //to account for the very first mark (since a 1 px rectangle was drawn)
				g2d.drawLine(	barX + pixOver,
						barY + barHeight,
						barX + pixOver,
						barY + barHeight + tickWidth);
			}
		}
	}
}

