package org.opensha.commons.gui.plot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;

import com.google.common.base.Preconditions;

/**
 * Animated GIF renderer utility class for creating plot animations.
 * 
 * Call writeFrame(image) to add each image, then finalizeAnimation() at the end to close the file.
 * 
 * @author kevin
 *
 */
public class AnimatedGIFRenderer {
	
	private ImageWriter writer;
	private FileImageOutputStream output;
	
	private ImageWriteParam imageWriteParam;
	private IIOMetadata imageMetaData;
	
	public AnimatedGIFRenderer(File outputFile, double fps, boolean doLoop) throws IOException {
		Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix("gif");
		Preconditions.checkArgument(iter.hasNext(), "No GIF image writers available!");
		writer = iter.next();
		
		imageWriteParam = writer.getDefaultWriteParam();
		ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);

		imageMetaData = writer.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);

		String metaFormatName = imageMetaData.getNativeMetadataFormatName();

		IIOMetadataNode root = (IIOMetadataNode)imageMetaData.getAsTree(metaFormatName);

		IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
		
		// in hundreths of a second
		int delayTime = (int)(100d/fps);

		graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
		graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("delayTime", delayTime+"");
		graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

		IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
		commentsNode.setAttribute("CommentExtension", "Created by OpenSHA");

		IIOMetadataNode appEntensionsNode = getNode(root, "ApplicationExtensions");

		IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");

		child.setAttribute("applicationID", "NETSCAPE");
		child.setAttribute("authenticationCode", "2.0");

		int loop = 0;
		if (!doLoop)
			loop = 1;

		child.setUserObject(new byte[]{ 0x1, (byte) (loop & 0xFF), (byte)((loop >> 8) & 0xFF)});
		appEntensionsNode.appendChild(child);

		imageMetaData.setFromTree(metaFormatName, root);

		if (outputFile.exists())
			Preconditions.checkState(outputFile.delete());
		output = new FileImageOutputStream(outputFile);
		writer.setOutput(output);
		writer.prepareWriteSequence(imageMetaData);
	}
	
	public void writeFrame(BufferedImage img) throws IOException {
		writer.writeToSequence(new IIOImage(img, null, imageMetaData), imageWriteParam);
	}
	
	public void finalizeAnimation() throws IOException {
		writer.endWriteSequence();
		output.close();
	}
	
	/**
	 * Returns an existing child node, or creates and returns a new child node (if 
	 * the requested node does not exist).
	 * 
	 * @param rootNode the <tt>IIOMetadataNode</tt> to search for the child node.
	 * @param nodeName the name of the child node.
	 * 
	 * @return the child node, if found or a new node created with the given name.
	 */
	private static IIOMetadataNode getNode(
			IIOMetadataNode rootNode,
			String nodeName) {
		int nNodes = rootNode.getLength();
		for (int i = 0; i < nNodes; i++) {
			if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName)
					== 0) {
				return((IIOMetadataNode) rootNode.item(i));
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(nodeName);
		rootNode.appendChild(node);
		return(node);
	}

}
