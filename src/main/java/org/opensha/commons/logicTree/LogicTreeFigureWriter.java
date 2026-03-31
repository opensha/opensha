package org.opensha.commons.logicTree;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.CorrTruncatedNormalDistribution;
import org.apache.commons.statistics.distribution.LogNormalDistribution;
import org.apache.commons.statistics.distribution.NormalDistribution;
import org.apache.commons.statistics.distribution.TriangularDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.jfree.chart.ui.RectangleAnchor;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_LogicTreeBranchAverageBuilder.NodeLevelPair;

import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

public class LogicTreeFigureWriter extends JPanel {
	
	private static final int levelGap = 10;
	private static final int levelHGap = 10;
	private static final int choiceHGap = 5;
	private static final int lineGap = 5;
	private static final int minWidthPerNode = 100;
	private static final Font levelFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
	private static final Font choiceFont = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
	private static final Font distFont = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
	private static final Font weightFont = new Font(Font.SANS_SERIF, Font.ITALIC, 18);
	private static final DecimalFormat weightDF = new DecimalFormat("0.###");
	private int levelFontHeight;
	private int choiceFontHeight;
	private int choiceWidth;
	private int lineHeight;
	private int levelHeight;
	private int width;
	private int height;
	private List<LogicTreeLevel<? extends LogicTreeNode>> includedLevels;
	private List<List<NodeLevelPair>> includedLevelChoices;

	private LogicTree<?> tree;
	private Map<String, String> nameRemappings;
	private double totalWeight;
	private Map<NodeLevelPair, Double> choiceWeights;
	private int maxNodes;
	
	public LogicTreeFigureWriter(LogicTree<?> tree, boolean includeSingleChoice, boolean useLevelWeights) {
		this(tree, includeSingleChoice, useLevelWeights, null);
	}
	
	public LogicTreeFigureWriter(LogicTree<?> tree, boolean includeSingleChoice, boolean useLevelWeights,
			Map<String, String> nameRemappings) {
		this.tree = tree;
		if (nameRemappings == null)
			nameRemappings = Map.of();
		this.nameRemappings = nameRemappings;

		maxNodes = 1;
		int maxChoiceFontWidth = 0;
		int maxLevelFontWidth = 0;
		includedLevels = new ArrayList<>();
		includedLevelChoices = new ArrayList<>();
		totalWeight = 0d;
		choiceWeights = new HashMap<>();
		for (int i=0; i<tree.size(); i++)
			totalWeight += tree.getBranchWeight(i);
		int numLevels = tree.getLevels().size();
		for (int l=0; l<numLevels; l++) {
			LogicTreeLevel<?> level = tree.getLevels().get(l);
			// figure out how many choices I have
			List<NodeLevelPair> uniqueChoices = new ArrayList<>();
			if (useLevelWeights) {
				List<Double> levelWeights = new ArrayList<>();
				double levelWeightSum = 0d;
				for (LogicTreeNode node : level.getNodes()) {
					double weight;
					if (node instanceof LogicTreeNode.FixedWeightNode) {
						weight = ((LogicTreeNode.FixedWeightNode)node).getNodeWeight();
					} else {
						double sumWeightUsing = 0d;
						int countUsing = 0;
						for (LogicTreeBranch<?> branch : tree) {
							if (branch.getValue(l).equals(node)) {
								sumWeightUsing += node.getNodeWeight(branch);
								countUsing++;
							}
						}
						if (countUsing == 0)
							weight = 0d;
						else
							weight = sumWeightUsing / (double)countUsing;
					}
					if (weight > 0d) {
						NodeLevelPair nodePair = new NodeLevelPair(node, level, l);
						uniqueChoices.add(nodePair);
						levelWeights.add(weight);
						levelWeightSum += weight;
					}
				}
				double scalar = totalWeight/levelWeightSum;
				for (int i=0; i<uniqueChoices.size(); i++)
					choiceWeights.put(uniqueChoices.get(i), levelWeights.get(i) * scalar);
			} else {
				HashSet<NodeLevelPair> uniqueChoicesSet = new HashSet<>();
				for (LogicTreeBranch<?> branch : tree) {
					LogicTreeNode node = branch.getValue(l);
					NodeLevelPair nodePair = new NodeLevelPair(node, level, l);
					double weight = tree.getBranchWeight(branch);
					if (!uniqueChoicesSet.contains(nodePair)) {
						uniqueChoices.add(nodePair);
						uniqueChoicesSet.add(nodePair);
						choiceWeights.put(nodePair, weight);
					} else {
						choiceWeights.put(nodePair, choiceWeights.get(nodePair)+weight);
					}
				}
			}
			System.out.println(level.getName()+" has "+uniqueChoices.size()+" unique nodes");
			if (includeSingleChoice || uniqueChoices.size() > 1) {
				includedLevels.add(level);
				includedLevelChoices.add(uniqueChoices);
				maxLevelFontWidth = Integer.max(maxLevelFontWidth, new JPanel().getFontMetrics(levelFont).stringWidth(remapped(level.getName())));
				if (!(level instanceof RandomLevel<?,?>)) {
					maxNodes = Integer.max(maxNodes, uniqueChoices.size());
					for (NodeLevelPair node : uniqueChoices) {
						String name = remapped(node.node.getShortName());
						FontMetrics metrics = new JPanel().getFontMetrics(choiceFont);
						maxChoiceFontWidth = Integer.max(maxChoiceFontWidth, metrics.stringWidth(name));
					}
				}
			}
		}
		
		System.out.println("We have "+includedLevelChoices.size()+" levels and at most "+maxNodes+" nodes");
		
		FontMetrics metrics = new JPanel().getFontMetrics(levelFont);
		levelFontHeight = metrics.getHeight();
		System.out.println("Level font height in pixels: " + levelFontHeight);
		metrics = new JPanel().getFontMetrics(choiceFont);
		choiceFontHeight = metrics.getHeight();
		System.out.println("Choice font height in pixels: " + choiceFontHeight);
		lineHeight = (int)(1.5d*levelFontHeight + 0.5);
		levelHeight = levelFontHeight + lineGap*2 + lineHeight + choiceFontHeight + choiceFontHeight;

		choiceWidth = Integer.max(minWidthPerNode, maxChoiceFontWidth+choiceHGap);
		width = Integer.max((int)(choiceWidth*maxNodes + 0.5*choiceWidth + 0.5),
				maxLevelFontWidth+2*levelHGap);
		height = levelHeight * includedLevelChoices.size() + levelGap * includedLevels.size();
		
		System.out.println("Calculated dimensions: "+width+"x"+height);
		
		setSize(width, height);
		setBackground(Color.WHITE);
	}
	
	private String remapped(String name) {
		if (nameRemappings.containsKey(name))
			return nameRemappings.get(name);
		return name;
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		int y = (int)(0.5*levelGap + 0.5);
		int middleX = (int)(0.5*width + 0.5);
		
		Graphics2D g2d = (Graphics2D) g;
		float lineWidth = 3f;
		BasicStroke lineStroke = new BasicStroke(lineWidth);
		BasicStroke randLineStroke = new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
				BasicStroke.JOIN_BEVEL,0,new float[] {Float.min(6, Float.max(lineWidth*0.7f, 3))},0);
		g2d.setStroke(new BasicStroke(3));
		g2d.setPaint(Color.BLACK);
		
		DecimalFormat groupedDF = new DecimalFormat("0");
		groupedDF.setGroupingSize(3);
		groupedDF.setGroupingUsed(true);
		
		for (int l=0; l<includedLevels.size(); l++) {
			LogicTreeLevel<? extends LogicTreeNode> level = includedLevels.get(l);
			String name = remapped(level.getName());
			g2d.setFont(levelFont);
			drawText(g2d, name, middleX, y, RectangleAnchor.TOP);
			
			y += levelFontHeight;
			y += lineGap;
			List<NodeLevelPair> nodes = includedLevelChoices.get(l);
			int botLineY = y+lineHeight;
			int topChoiceY = botLineY + lineGap;
			int topWeightY = topChoiceY + choiceFontHeight;
			if (level instanceof RandomLevel<?,?> && nodes.size() > maxNodes && includedLevels.size() > 1) {
				// figure out how many branches we have without this
				HashSet<String> uniquesWithout = new HashSet<>();
				for (LogicTreeBranch<?> branch : tree) {
					String str = "";
					for (int l1=0; l1<branch.size(); l1++) {
						if (branch.getLevel(l1) == level)
							continue;
						str += "_"+branch.getValue(l1).getFilePrefix()+"_";
					}
					uniquesWithout.add(str);
//					System.out.println(str);
				}
				int prefNumLines;
				if (tree.size() % uniquesWithout.size() == 0) {
					int numPer = tree.size() / uniquesWithout.size();
					g2d.setFont(choiceFont);
					String samplesStr;
					if (numPer == 1) {
						prefNumLines = 3;
						samplesStr = numPer+" sample per branch";
					} else {
						prefNumLines = numPer;
						samplesStr = groupedDF.format(numPer)+" samples per branch";
					}
					drawText(g2d, samplesStr+", "+groupedDF.format(nodes.size())+" in total", middleX, topChoiceY, RectangleAnchor.TOP);
				} else {
					prefNumLines = nodes.size();
					drawText(g2d, groupedDF.format(nodes.size())+" samples", middleX, topChoiceY, RectangleAnchor.TOP);
				}
				if (prefNumLines > maxNodes)
					prefNumLines = maxNodes;
				int myNodesWidth = choiceWidth*prefNumLines;
				int sideBuffer = (int)(0.5*(width - myNodesWidth));
				int leftX = sideBuffer;
				g2d.setStroke(randLineStroke);
				for (int i=0; i<prefNumLines; i++) {
					int rightX = leftX + choiceWidth;
					int choiceCenterX = (int)(0.5*(leftX + rightX));
					int topX = middleX + (int)(0.2*(choiceCenterX - middleX));
					g2d.drawLine(topX, y, choiceCenterX, botLineY);
					leftX = rightX;
				}
				g2d.setStroke(lineStroke);
				double minWeight = Double.POSITIVE_INFINITY;
				double maxWeight = Double.NEGATIVE_INFINITY;
				for (NodeLevelPair node : nodes) {
					double weight = node.node.getNodeWeight(null);
					minWeight = Math.min(minWeight, weight);
					maxWeight = Math.max(maxWeight, weight);
				}
				if (level instanceof AbstractContinuousDistributionSampledLevel<?>) {
					AbstractContinuousDistributionSampledLevel<?> distLevel = (AbstractContinuousDistributionSampledLevel<?>)level;
					ContinuousDistribution dist = distLevel.getDistribution();
					String distStr;
					if (dist == null) {
						distStr = "Unknown distribution";
					} else {
						double lower = distLevel.getLowerBound();
						double upper = distLevel.getUpperBound();
						String rangeStr = "["+distParamStr(lower)+", "+distParamStr(upper)+"]";
						if (dist instanceof UniformContinuousDistribution) {
							distStr = "𝑈"+rangeStr;
						} else if (dist instanceof NormalDistribution) {
							NormalDistribution norm = (NormalDistribution)dist;
							distStr = "𝑁(μ="+distParamStr(norm.getMean())
									+", σ="+distParamStr(norm.getStandardDeviation())+")";
						} else if (dist instanceof CorrTruncatedNormalDistribution) {
							CorrTruncatedNormalDistribution tNorm = (CorrTruncatedNormalDistribution)dist;
							distStr = "Trunc𝑁(μ="+distParamStr(tNorm.getParentMean())
									+", σ="+distParamStr(tNorm.getParentStandardDeviation())
									+", "+rangeStr+")";
						} else if (dist instanceof TriangularDistribution) {
							TriangularDistribution triDist = (TriangularDistribution)dist;
							distStr = "Tri("+distParamStr(lower)
									+", "+distParamStr(triDist.getMode())+", "+distParamStr(upper)+")";
						} else if (dist instanceof LogNormalDistribution) {
							LogNormalDistribution logNorm = (LogNormalDistribution)dist;
							distStr = "Log𝑁(μ="+distParamStr(logNorm.getMu())
									+", σ="+distParamStr(logNorm.getSigma())+")";
						} else {
							distStr = "Unknown"+rangeStr;
						}
						String units = distLevel.getUnits();
						if (units != null && !units.isBlank())
							distStr += " "+units;
					}
					g2d.setFont(distFont);
					drawText(g2d, distStr, middleX, topWeightY, RectangleAnchor.TOP);
				} else if ((float)minWeight == (float)maxWeight) {
					g2d.setFont(weightFont);
					drawText(g2d, "(equally weighted)", middleX, topWeightY, RectangleAnchor.TOP);
				} else {
					g2d.setFont(weightFont);
					drawText(g2d, "("+weightDF.format(minWeight)+"-"+weightDF.format(maxWeight)+")", middleX, topWeightY, RectangleAnchor.TOP);
				}
			} else {
				int myNodesWidth = choiceWidth*nodes.size();
				int sideBuffer = (int)(0.5*(width - myNodesWidth));
				int leftX = sideBuffer;
				for (NodeLevelPair node : nodes) {
					int rightX = leftX + choiceWidth;
					int choiceCenterX = (int)(0.5*(leftX + rightX));
					int topX = middleX + (int)(0.2*(choiceCenterX - middleX));
					g2d.drawLine(topX, y, choiceCenterX, botLineY);
					leftX = rightX;
					g2d.setFont(choiceFont);
					drawText(g2d, remapped(node.node.getShortName()), choiceCenterX, topChoiceY, RectangleAnchor.TOP);
					double weight = choiceWeights.get(node)/totalWeight;
					g2d.setFont(weightFont);
					drawText(g2d, "("+weightDF.format(weight)+")", choiceCenterX, topWeightY, RectangleAnchor.TOP);
				}
			}
//			y += lineHeight;
//			y += lineGap;
//			y += choiceFontHeight;
//			y += choiceFontHeight;
			y = topWeightY + choiceFontHeight + levelGap;
		}
//		// Custom drawing code goes here
//		g2d.setStroke(new BasicStroke(2));
//		g2d.setColor(Color.BLACK);
//		g2d.drawLine(50, 50, 150, 150);
//		g2d.setFont(levelFont);
//		
//		drawText(g2d, "Test Text", 200, 0, RectangleAnchor.TOP);
		
	}
	
	private static DecimalFormat oDF = new DecimalFormat("0.###");
	private static String distParamStr(double distParam) {
		if (!Double.isFinite(distParam))
			return distParam+"";
		double abs = Math.abs(distParam);
		if ((float)abs == (float)Math.round(abs))
			return (int)distParam+"";
		if (abs < 0.001 || abs > 1e7)
			return (float)distParam+"";
		return oDF.format(distParam);
	}
	
	private static void drawText(Graphics2D g2d, String text, int x, int y, RectangleAnchor anchor) {
		FontMetrics metrics = g2d.getFontMetrics();
		int textWidth = metrics.stringWidth(text);
		int textHeight = metrics.getHeight();
		int drawX = x;
		int drawY = y;

		switch (anchor) {
			case CENTER:
				drawX = x - textWidth / 2;
				drawY = y + metrics.getAscent() / 2 - textHeight / 4;
				break;
			case TOP_LEFT:
				drawX = x;
				drawY = y + metrics.getAscent();
				break;
			case TOP_RIGHT:
				drawX = x - textWidth;
				drawY = y + metrics.getAscent();
				break;
			case BOTTOM_LEFT:
				drawX = x;
				drawY = y - metrics.getDescent();
				break;
			case BOTTOM_RIGHT:
				drawX = x - textWidth;
				drawY = y - metrics.getDescent();
				break;
			case TOP:
				drawX = x - textWidth / 2;
				drawY = y + metrics.getAscent();
				break;
			case BOTTOM:
				drawX = x - textWidth / 2;
				drawY = y - metrics.getDescent();
				break;
			case LEFT:
				drawX = x;
				drawY = y + metrics.getAscent() / 2 - textHeight / 4;
				break;
			case RIGHT:
				drawX = x - textWidth;
				drawY = y + metrics.getAscent() / 2 - textHeight / 4;
				break;
		}

		g2d.drawString(text, drawX, drawY);
	}
	
	public void write(File outputDir, String prefix, boolean writePNG, boolean writePDF) throws IOException {
		if (writePNG) {
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = image.createGraphics();

			// Enable anti-aliasing for better quality
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// Render the panel onto the image
			this.setSize(width, height);
			this.paint(g2);
			g2.dispose();
			
			// Write the BufferedImage to a PNG file
			File outputFile = new File(outputDir, prefix+".png");
			ImageIO.write(image, "png", outputFile);
		}

		if (writePDF) {
			// step 1
			com.itextpdf.text.Rectangle pageSize = new com.itextpdf.text.Rectangle(width, height);
			pageSize.setBackgroundColor(BaseColor.WHITE);
			Document metadataDocument = new Document(pageSize);
			metadataDocument.addAuthor("OpenSHA");
			metadataDocument.addCreationDate();
			
			try {
				// step 2
				PdfWriter writer;

				writer = PdfWriter.getInstance(metadataDocument,
						new BufferedOutputStream(new FileOutputStream(new File(outputDir, prefix+".pdf"))));
				// step 3
				metadataDocument.open();
				// step 4
				PdfContentByte cb = writer.getDirectContent();
				
				PdfTemplate tp = cb.createTemplate(width, height);
				
				FontMapper fontMapper = new PDF_UTF8_FontMapper();
				PdfGraphics2D g2d = new PdfGraphics2D(tp, width, height, fontMapper);
				g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				this.paint(g2d);
				g2d.dispose();
				cb.addTemplate(tp, 0, 0);
			}
			catch (DocumentException de) {
				throw ExceptionUtils.asRuntimeException(de);
			}
			// step 5
			metadataDocument.close();
		}
	}

}
