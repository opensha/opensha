package org.opensha.commons.util;


import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.PrintGraphics;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.SystemUtils;


/**
 * <b>Title:</b>FileUtils<p>
 *
 * <b>Description:</b>Generic functions used in handling text files, such as
 * loading in the text data from a file.<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */
// TODO clean and test
public class FileUtils {

	/** Class name used for debug strings */
	protected final static String C = "FileUtils";
	/** boolean that indicates if print out debug statements */
	protected final static boolean D = false;

	/**
	 * Loads in each line to a text file into an ArrayList ( i.e. a vector ). Each
	 * element in the ArrayList represents one line from the file.
	 *
	 * @param fileName                  File to load in
	 * @return                          ArrayList each element one line from the file
	 * @throws FileNotFoundException    If the filename doesn't exist
	 * @throws IOException              Unable to read from the file
	 */
	public static ArrayList<String> loadFile(String fileName)
	throws
	FileNotFoundException,
	IOException
	{
		return loadFile(fileName, true);
	}

	/**
	 * Loads in each line to a text file into an ArrayList ( i.e. a vector ). Each
	 * element in the ArrayList represents one line from the file.
	 *
	 * @param fileName                  File to load in
	 * @return                          ArrayList each element one line from the file
	 * @throws FileNotFoundException    If the filename doesn't exist
	 * @throws IOException              Unable to read from the file
	 */
	public static ArrayList<String> loadFile(String fileName, boolean skipBlankLines)
	throws
	FileNotFoundException,
	IOException
	{

		// Debugging
		String S = C + ": loadFile(): ";
		if( D ) System.out.println(S + "Starting");
		if (D) System.out.println(S + fileName);
		// Allocate variables
		ArrayList<String> list = new ArrayList<String>();
		File f = new File(fileName);

		// Read in data if it exists
		if( f.exists() ){

			if( D ) System.out.println(S + "Found " + fileName + " and loading.");

			boolean ok = true;
			int counter = 0;
			String str;
			FileReader in = new FileReader(fileName);
			LineNumberReader lin = new LineNumberReader(in);

			while(ok){
				try{
					str = lin.readLine();

					if(str != null) {
						//omit the blank line
						if(skipBlankLines && str.trim().equals(""))
							continue;

						list.add(str);
						if(D){
							System.out.println(S + counter + ": " + str);
							counter++;
						}
					}
					else ok = false;
				}
				catch(IOException e){ok = false;}
			}
			lin.close();
			in.close();

			if( D ) System.out.println(S + "Read " + counter + " lines from " + fileName + '.');

		}
		else if(D) System.out.println(S + fileName + " does not exist.");

		// Done
		if( D ) System.out.println(S + "Ending");
		return list;

	}

	/**
	 *
	 * @param url : URL of file to be read
	 * @return : arrayList containing the lines in file
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static ArrayList<String> loadFile(URL url) throws IOException {
		if(D) System.out.println("url="+url);
		URLConnection uc = url.openConnection();
		return loadStream((InputStream)uc.getContent());
	}

	/**
	 *
	 * @param is : input stream of file to be read
	 * @return : arrayList containing the lines in file
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static ArrayList<String> loadStream(InputStream is) throws IOException {
		ArrayList<String> list = new ArrayList<String>();
		BufferedReader tis =
			new BufferedReader(new InputStreamReader(is));
		String str = tis.readLine();
		while(str != null) {
			list.add(str);
			str = tis.readLine();
		}
		tis.close();
		return list;
	}


	/**
	 * load from Jar file
	 * @param fileName : File name to be read from Jar file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static ArrayList<String> loadJarFile(String fileName)
	throws  FileNotFoundException, IOException {
		try {
			if(D) System.out.println("FileUtils:filename="+fileName);
			if (!fileName.startsWith("/"))
				fileName = "/"+fileName;
			return loadFile(FileUtils.class.getResource(fileName));
		}catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * save the serialized object into the specified file
	 * @param fileName
	 * @param obj
	 * @throws IOException 
	 */
	public static void saveObjectInFile(String fileName, Object obj) throws IOException {
		// write  object to the file
		FileOutputStream fileOut = new FileOutputStream(fileName);
		ObjectOutputStream objectStream = new ObjectOutputStream(fileOut);
		objectStream.writeObject(obj);
		objectStream.close();
		fileOut.close();
	}

	/**
	 * return a object read from the URL
	 * @param url
	 * @return
	 */
	public static Object loadObjectFromURL(URL url) {
		try {
			URLConnection uc = url.openConnection();
			ObjectInputStream tis = new ObjectInputStream( (InputStream) uc.
					getContent());
			Object obj = tis.readObject();
			tis.close();
			return obj;
		}catch(Exception e) { e.printStackTrace(); }
		return null;
	}
	
	public static void createZipFile(File zipFile, File dir, boolean topLevelChildren) throws IOException {
		ArrayList<String> fileNames = new ArrayList<>();
		if (topLevelChildren) {
			for (File file : dir.listFiles())
				populateZipFilePaths(fileNames, file, "");
			createZipFile(zipFile.getAbsolutePath(), dir.getAbsolutePath(), fileNames);
		} else {
			populateZipFilePaths(fileNames, dir, "");
			createZipFile(zipFile.getAbsolutePath(), dir.getParentFile().getAbsolutePath(), fileNames);
		}
	}
	
	private static void populateZipFilePaths(ArrayList<String> fileNames, File file, String path) {
		if (file.isDirectory()) {
			path += file.getName()+"/";
			for (File sub : file.listFiles())
				populateZipFilePaths(fileNames, sub, path);
		} else {
			if (path.isEmpty())
				fileNames.add(file.getName());
			else
				fileNames.add(path+file.getName());
		}
	}

	/**
	 * This creates a zip file with the given name from a list of file names.
	 * 
	 * @param zipFile
	 * @param files
	 * @throws IOException
	 */
	public static void createZipFile(String zipFile, String dir, Collection<String> fileNames) throws IOException {

		byte[] buffer = new byte[18024];

		ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir += File.separator;

		// Set the compression ratio
		out.setLevel(Deflater.DEFAULT_COMPRESSION);

		// iterate through the array of files, adding each to the zip file
		for (String file : fileNames) {
			// Add ZIP entry to output stream.
			out.putNextEntry(new ZipEntry(file));
			
			File f = new File(dir + file);
			if (f.isDirectory())
				continue;
		
			// Associate a file input stream for the current file
			FileInputStream in = new FileInputStream(f);

			// Transfer bytes from the current file to the ZIP file
			//out.write(buffer, 0, in.read(buffer));

			int len;
			while ((len = in.read(buffer)) > 0)
			{
				out.write(buffer, 0, len);
			}



			// Close the current entry
			out.closeEntry();

			// Close the current file input stream
			in.close();

		}
		// Close the ZipOutPutStream
		out.close();
	}

	/**
	 * This function creates a Zip file called "allFiles.zip" for all the
	 * files that exist in filesPath.
	 * @param filesPath String Folder with absolute path in zip file will be created.
	 * This function searches for all the files in the folder "filesPath" and adds
	 * those to a single zip file "allFiles.zip".
	 */
	public static void createZipFile(String filesPath){
		int BUFFER = 8192;
		String zipFileName = "allFiles.zip";
		if(!filesPath.endsWith(SystemUtils.FILE_SEPARATOR))
			filesPath = filesPath+SystemUtils.FILE_SEPARATOR;
		try {
			BufferedInputStream origin = null;
			FileOutputStream dest = new
			FileOutputStream(filesPath+zipFileName);
			ZipOutputStream out = new ZipOutputStream(new
					BufferedOutputStream(dest));
			out.setMethod(ZipOutputStream.DEFLATED);
			byte data[] = new byte[BUFFER];
			// get a list of files from current directory
			File f = new File(filesPath);
			String files[] = f.list();
			for (int i = 0; i < files.length; i++) {
				if(files[i].equals(zipFileName))
					continue;
				System.out.println("Adding: " + files[i]);
				FileInputStream fi = new
				FileInputStream(filesPath+files[i]);
				origin = new
				BufferedInputStream(fi, BUFFER);
				ZipEntry entry = new ZipEntry(files[i]);
				out.putNextEntry(entry);
				int count;
				while ( (count = origin.read(data, 0,
						BUFFER)) != -1) {
					out.write(data, 0, count);
				}
				origin.close();
			}
			out.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void unzipFile(File zipFile, File directory) throws FileNotFoundException, IOException {
		ZipFile zip = new ZipFile(zipFile);

		Enumeration<? extends ZipEntry> entries = zip.entries();
		while(entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if(entry.isDirectory()) {
				// Assume directories are stored parents first then children.
//				System.err.println("Extracting directory: " + entry.getName());
				// This is not robust, just for demonstration purposes.
				(new File(directory, entry.getName())).mkdir();
				continue;
			}
//			System.err.println("Extracting file: " + entry.getName());
			copyInputStream(zip.getInputStream(entry),
					new BufferedOutputStream(new FileOutputStream(new File(directory, entry.getName()))));
		}
		zip.close();
	}
	
	public static final void copyInputStream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int len;
		while((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);
		in.close();
		out.close();
	}

	/**
	 * @param fileName File from where object needs to be read
	 * @return Object object read from the file
	 */
	public static Object loadObject(String fileName)
	{
		if(D) System.out.println("fileName="+fileName);
		try {
			FileInputStream fin = new FileInputStream(fileName);
			ObjectInputStream tis = new ObjectInputStream( fin);
			Object obj =  tis.readObject();
			tis.close();
			fin.close();
			return obj;
		}catch(Exception e) { e.printStackTrace(); }
		return null;
	}

	public static File createTempDir() throws IOException {
		File tempDir;
		// first see if the system property was set
		String tempDirProp = System.getProperty("TempDir");
		if (tempDirProp != null) {
			Random r = new Random();
			tempDir = new File(tempDirProp, "openSHA"+r.nextInt(10000)+"temp");
			while (tempDir.exists()) {
				tempDir = new File(tempDirProp, "openSHA"+r.nextInt(10000)+"temp");
			}
		} else {
			tempDir = File.createTempFile("openSHA", "temp");
		}
		tempDir.delete();
		tempDir.mkdir();
		return tempDir;
	}
	
	public static boolean deleteRecursive(File f) {
		if (f.isFile())
			return f.delete();
		else {
			for (File child : f.listFiles()) {
				deleteRecursive(child);
			}
			return f.delete();
		}
	}

	/**
	 * this method accepts the filename and loads the image from the jar file
	 * @param fileName
	 * @return
	 */
	public static Image loadImage(String fileName) {
		String imageFileName = FileUtils.imagePath+fileName;
		java.net.URL url = FileUtils.class.getResource(imageFileName);
		Image img=Toolkit.getDefaultToolkit().getImage(url);
		return img;
	}

	/**
	 * this is the path where images will be put into
	 */
	private static final String imagePath = "/images/";
	
	public static void downloadURL(String addr, File outFile) throws IOException {
		downloadURL(new URL(addr), outFile);
	}

	public static void downloadURL(URL url, File outFile) throws IOException {
		InputStream in = url.openStream();         // throws an IOException
		System.out.println("Downloading " + url + " to " + outFile.getAbsolutePath());

		FileOutputStream out = new FileOutputStream(outFile);

		byte[] buf = new byte[4 * 1024]; // 4K buffer
		int bytesRead;
		while ((bytesRead = in.read(buf)) > 0) {
			out.write(buf, 0, bytesRead);
		}
		
		in.close();
		out.close();
		System.out.println("DONE");
	}

	/**
	 * Prints a Text file
	 * @param pjob PrintJob  created using getToolkit().getPrintJob(JFrame,String,Properties);
	 * @param pg Graphics
	 * @param textToPrint String
	 */
	public static void print(PrintJob pjob, Graphics pg, String textToPrint) {
	
		int margin = 60;
		
		int pageNum = 1;
		int linesForThisPage = 0;
		int linesForThisJob = 0;
		// Note: String is immutable so won't change while printing.
		if (!(pg instanceof PrintGraphics)) {
			throw new IllegalArgumentException ("Graphics context not PrintGraphics");
		}
		StringReader sr = new StringReader (textToPrint);
		LineNumberReader lnr = new LineNumberReader (sr);
		String nextLine;
		int pageHeight = pjob.getPageDimension().height - margin;
		Font helv = new Font("Monaco", Font.PLAIN, 12);
		//have to set the font to get any output
		pg.setFont (helv);
		FontMetrics fm = pg.getFontMetrics(helv);
		int fontHeight = fm.getHeight();
		int fontDescent = fm.getDescent();
		int curHeight = margin;
		try {
			do {
				nextLine = lnr.readLine();
				if (nextLine != null) {
					if ((curHeight + fontHeight) > pageHeight) {
						// New Page
						if (linesForThisPage == 0)
							break;
	
						pageNum++;
						linesForThisPage = 0;
						pg.dispose();
						pg = pjob.getGraphics();
						if (pg != null) {
							pg.setFont (helv);
						}
						curHeight = 0;
					}
					curHeight += fontHeight;
					if (pg != null) {
						pg.drawString (nextLine, margin, curHeight - fontDescent);
						linesForThisPage++;
	
						linesForThisJob++;
					}
				}
			} while (nextLine != null);
		} catch (EOFException eof) {
			// Fine, ignore
		} catch (Throwable t) { // Anything else
			t.printStackTrace();
		}
	}

	/**
	 * Saves the text to a file on the users machine.
	 * File is saved with extension ".txt".
	 * @param panel Component
	 * @param dataToSave String
	 */
	public static void save(String fileName, String dataToSave) {
	
		try {
			FileWriter fw = new FileWriter(fileName);
			fw.write(dataToSave);
			fw.close();
		}
		catch (IOException e) {
			//JOptionPane.showMessageDialog(panel, "Error creating file", "Error",
			//                            JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

}







