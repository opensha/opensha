package scratch.UCERF3.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;

import com.google.common.base.Preconditions;

public class UCERF3_DataUtils {
	
	private static final String s = File.separator;
	
	/**
	 * The local scratch data directory that is ignored by repository commits.
	 */
	public static File DEFAULT_SCRATCH_DATA_DIR =
		new File("src"+s+"scratch"+s+"UCERF3"+s+"data"+s+"scratch");
	
	/**
	 * The URL prefix for loading file from the persistent data directory. This MUST have forward slashes
	 * as it is for jar file loading. It cannot use File.separator.
	 */
	public static String DATA_URL_PREFIX = "/scratch/UCERF3/data";
	
	/**
	 * This gives the URL of a file in the specified sub directory of our UCERF3 data directory.
	 * 
	 * @param pathElements array of path elements (sub directories) with the file name last
	 * @return
	 */
	public static URL locateResource(String... pathElements) {
		String relativePath = getRelativePath(pathElements);
		URL url = UCERF3_DataUtils.class.getResource(relativePath);
		Preconditions.checkNotNull(url, "Resource '"+pathElements[pathElements.length-1]+"' could not be located: "+relativePath);
		return url;
	}
	
	private static String getRelativePath(String... pathElements) {
		String relativePath = DATA_URL_PREFIX;
		for (String pathElement : pathElements)
			if (pathElement != null)
				relativePath += "/"+pathElement;
		return relativePath;
	}
	
	/**
	 * This loads the given file as a stream from our data directory.
	 * 
	 * @param fileName
	 * @return
	 */
	public static InputStream locateResourceAsStream(String fileName) {
		return locateResourceAsStream(null, fileName);
	}
	
	/**
	 * This loads the given file as a stream from our data directory.
	 * 
	 * @param pathElements
	 * @return
	 */
	public static InputStream locateResourceAsStream(String... pathElements) {
		String relativePath = getRelativePath(pathElements);
		InputStream stream = UCERF3_DataUtils.class.getResourceAsStream(relativePath);
		Preconditions.checkNotNull(stream, "Resource '"+pathElements[pathElements.length-1]+"' could not be located: "+relativePath);
		return stream;
	}
	
	/**
	 * This creates an input stream reader for the given input stream. Note that this
	 * is NOT buffered, so you should buffer it yourself if needed by wrapping in a 
	 * buffered stream reader.
	 * 
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static Reader getReader(InputStream is) throws IOException {
		return new InputStreamReader(is);
	}
	
	/**
	 * This creates an input stream reader for the given URL. Note that this
	 * is NOT buffered, so you should buffer it yourself if needed by wrapping in a 
	 * buffered stream reader.
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static Reader getReader(URL url) throws IOException {
		URLConnection uc = url.openConnection();
		return new InputStreamReader((InputStream) uc.getContent());
	}
	
	/**
	 * This loads the given resource as a reader
	 * 
	 * @param pathElements
	 * @return
	 * @throws IOException 
	 */
	public static Reader getReader(String... pathElements) throws IOException {
		InputStream stream = locateResourceAsStream(pathElements);
		return getReader(stream);
	}

}
