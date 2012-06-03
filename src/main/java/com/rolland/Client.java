package com.rolland;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

public class Client {
	private static final String SERIES_YML = "series.yml";
	private static final String FEED_XML = "feed.xml";
	private static final String SCENETIME = "http://www.scenetime.com/get_rss.php?feed=direct&user=oysteinr&cat=47,3,1,9,2,43&passkey=9d1b06853a2cc5114fc44f139cd0b972";
	private static final String TV_TORRENTS = "http://www.tvtorrents.com/mytaggedRSS?digest=225d99e01095fd44f1c5d9c9aa8867e178950210&hash=357845debd9d7db7920a5c50673cdb1ca55b8453";
	
	public static void main(String[] args) {
		System.out.println("STARTING NAMEGRABBER");

		String adress = TV_TORRENTS;
		if (args.length == 0) {
			System.out.println("No address found in paramter, using default TV_TORRENTS feed");
		} else {
			adress = args[0];
		}

		System.out.println(" 1. Downloading feed ...");
		downloadFeed(adress, FEED_XML);

		System.out.println(" 2. Loading feed ...");
		Document doc = loadDocument(new File(FEED_XML));

		System.out.println(" 3. Extracting series names ...");
		List<String> names = null;
		boolean unableToRead = false;
		try {
			names = getNamesFromFeed(doc);
		} catch (JDOMException e) {
			unableToRead = true;
		}
		if (names == null || unableToRead) {
			System.err.println("Unable to extract names from feed, please make sure the feed is correct");
			return;
		}

		System.out.println(" 4. Finding new series ...");
		List<String> newSeries = null;
		try {
			newSeries = identifyNewSeries(SERIES_YML, names);
		} catch (FileNotFoundException e) {
			System.err.println("Unable to find YML-file");
		} catch (IOException e) {
			System.err.println("Unable to parse YML-file");
		}

		if (newSeries != null) {
			if (newSeries.size() > 0) {
				System.out.println("New series found: " + newSeries);
				System.out.println(" 5. Appending new series ...");
				String newYML = appendNewSeries(newSeries, SERIES_YML);
				writeNewYML(newYML, SERIES_YML);
			} else {
				System.out.println("No new series!");
			}
		}

		System.out.println("DONE");
	}

	private static void writeNewYML(String newYML, String file) {
		writeToFile(newYML, file);
	}

	private static void writeToFile(String content, String file) {
		try {
			FileWriter fstream = new FileWriter(file);
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(content);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String appendNewSeries(List<String> newSeries, String yml) {
		File file = new File(yml);
		String basicContent = "series:\n  settings:\n    groupa:\n      min_quality: hdtv\n      max_quality: 720p\n      quality: 720p\n      timeframe: 12 hours\n  groupa:";

		if (!file.exists()) {
			System.out.println("YML does not exist, trying to create a new one");
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			writeToFile(basicContent, file.getPath());
		}

		String content = loadFileAsString(file);
		StringBuilder builder = new StringBuilder();
		builder.append(content);
		for (String string : newSeries) {
			builder.append("    - \"" + string + "\"\n");
		}
		return builder.toString();
	}

	private static List<String> identifyNewSeries(String seriesYml, List<String> names) throws FileNotFoundException,
			IOException {
		List<String> yml = loadFile(seriesYml);
		List<String> existingSeries = getExistingSeries(yml);
		List<String> newSeries = new ArrayList<String>();
		for (String string : names) {
			if ((!existingSeries.contains("\"" + string + "\"")) && (!newSeries.contains(string)))
				newSeries.add(string);
		}
		return newSeries;
	}

	private static List<String> getExistingSeries(List<String> yml) {
		List<String> series = new ArrayList<String>();
		for (int i = 0; i < yml.size(); i++) {
			String trimmed = trimSeries(yml.get(i));
			if (trimmed != null) {
				series.add(trimmed);
			}
		}
		return series;
	}

	private static String trimSeries(String string) {
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == '-')
				return string.substring(i + 2);
		}
		return null;
	}

	private static List<String> loadFile(String file) throws FileNotFoundException, IOException {
		List<String> content = new ArrayList<String>();

		FileInputStream fstream = new FileInputStream(file);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		while ((strLine = br.readLine()) != null) {
			content.add(strLine);
		}
		in.close();

		return content;
	}

	private static String loadFileAsString(File file) {
		StringBuilder string = new StringBuilder();
		try {
			FileInputStream fstream = new FileInputStream(file);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				string.append(strLine + "\n");
			}
			in.close();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
		return string.toString();
	}

	private static List<String> filterNames(List<String> names) {
		List<String> filtered = new ArrayList<String>();
		for (String string : names)
			filtered.add(getName(string));
		return filtered;
	}

	private static String getName(String string) {
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == '-') {
				return string.substring(0, i - 1);
			}
		}
		return null;
	}

	private static List<String> getNamesFromFeed(Document document) throws JDOMException {
		List<String> names = new ArrayList<String>();
		List<Element> elements = findAllElementsWithXPathQuery(document, "//*[starts-with(name(), 'item')]");
		for (Element element : elements) {
			names.add(element.getChild("title").getValue());
		}
		return filterNames(names);
	}

	private static Document loadDocument(File file) {
		SAXBuilder builder = new SAXBuilder();
		Document doc = null;
		if (file != null) {
			try {
				doc = builder.build(file);
			} catch (JDOMException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return doc;
	}

	@SuppressWarnings("unchecked")
	public static List<Element> findAllElementsWithXPathQuery(Document doc, String expression) throws JDOMException {
		List<Element> nodeList = null;
		try {
			nodeList = XPath.selectNodes(doc, expression);
		} catch (JDOMException e) {
			e.printStackTrace();
		}
		return nodeList;
	}

	private static void downloadFeed(String url, String filename) {
		try {
			URL scenetime_url = new URL(url);
			ReadableByteChannel rbc = Channels.newChannel(scenetime_url.openStream());
			FileOutputStream fos = new FileOutputStream(filename);
			fos.getChannel().transferFrom(rbc, 0L, 16777216L);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
