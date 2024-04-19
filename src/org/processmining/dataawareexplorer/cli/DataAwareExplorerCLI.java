package org.processmining.dataawareexplorer.cli;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.deckfour.xes.classification.XEventAttributeClassifier;
import org.deckfour.xes.model.XLog;
import org.freehep.graphics2d.VectorGraphics;
import org.freehep.graphicsio.pdf.PDFGraphics2D;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration.SelectionFilterMode;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.plugin.DataAwareExplorerViewsPlugin;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.io.DPNIOException;
import org.processmining.datapetrinets.io.DataPetriNetImporter;
import org.processmining.framework.plugin.PluginContextID;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.events.Logger;
import org.processmining.log.csvimport.CSVConversion;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.graphviz.dot.Dot;
import org.processmining.plugins.graphviz.visualisation.DotPanel;
import org.xeslite.external.XFactoryExternalStore;
import org.xeslite.parser.XesLiteXmlParser;

public class DataAwareExplorerCLI {

	private static final Options OPTIONS = new Options();

	private static final Option HELP = OptionBuilder.withDescription("help").create('h');
	private static final Option MODE = OptionBuilder.hasArg().withArgName("mode").create("mode");
	private static final Option CLASSIFIER = OptionBuilder.hasArg().hasOptionalArgs().withArgName("attributes")
			.create("classifier");
	private static final Option DPN = OptionBuilder.isRequired().hasArg().withArgName("filename").create("dpn");
	private static final Option LOG = OptionBuilder.isRequired().hasArg().withArgName("filename").create("log");
	private static final Option FILTER = OptionBuilder.hasArg().withArgName("query").create("filter");
	private static final Option SELECT = OptionBuilder.hasArg().hasOptionalArgs().withArgName("nodes").create("select");

	static {
		OPTIONS.addOption(HELP);
		OPTIONS.addOption(DPN);
		OPTIONS.addOption(CLASSIFIER);
		OPTIONS.addOption(LOG);
		OPTIONS.addOption(FILTER);
		OPTIONS.addOption(SELECT);
	}

	public static void main(String[] args) {

		try {
			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse(OPTIONS, args);

			if (cmd.hasOption(HELP.getOpt())) {
				printUsage();
				return;
			}
			String logFileName = cmd.getOptionValue(LOG.getOpt());

			String resultFileName;
			if (cmd.getArgs().length == 0) {
				resultFileName = logFileName + ".pdf";
			} else {
				resultFileName = cmd.getArgs()[0];
			}

			File logFile = new File(logFileName);

			System.out.println("Loading " + logFile.getName());

			XLog log = loadLog(logFile);

			System.out.println("Loaded successfully!");

			CLIContext cliContext = loadProMContext();
			System.loadLibrary("lpsolve55");
			System.loadLibrary("lpsolve55j");

			String dpnFilename = cmd.getOptionValue(DPN.getOpt());
			File dpnFile = new File(dpnFilename);
			DataPetriNetsWithMarkings dpn = loadDPN(dpnFile);

			String[] classifier = getClassifierConfig(cmd);

			FilterConfiguration filterConfiguration = new FilterConfiguration();

			if (cmd.hasOption(FILTER.getOpt())) {
				filterConfiguration.setFilterQuery(cmd.getOptionValue(FILTER.getOpt()));
			}

			if (cmd.hasOption(SELECT.getOpt())) {
				String[] selecteLabels = cmd.getOptionValues(SELECT.getOpt());
				filterConfiguration.setSelectionFilterMode(SelectionFilterMode.AND); //TODO configurable
				filterConfiguration.setSelectedNodes(convert(dpn, selecteLabels));
			}

			Dot explorerView = new DataAwareExplorerViewsPlugin().getExplorerView(createPluginContext(cliContext), dpn,
					log, ViewMode.FITNESS, filterConfiguration, new XEventAttributeClassifier("", classifier));

			exportAsPDF(explorerView, new File(resultFileName));

		} catch (ParseException e) {
			printUsage();
			System.err.println(e.getMessage());
		} catch (Throwable e) {
			if (e.getMessage() != null) {
				System.err.println(e.getMessage());
			}
			e.printStackTrace(System.err);
		}

		System.exit(0);

	}

	private static Set<Object> convert(DataPetriNetsWithMarkings dpn, String[] selectedLabels) {
		Set<Object> selectedNodes = new HashSet<>();
		label: for (String label : selectedLabels) {
			for (Transition t : dpn.getTransitions()) {
				if (t.getLabel().equals(label)) {
					selectedNodes.add(t);
					continue label;
				}
			}
			for (Place p : dpn.getPlaces()) {
				if (p.getLabel().equals(label)) {
					selectedNodes.add(p);
					continue label;
				}
			}
			throw new IllegalArgumentException("Unknown node "+ label);
		}
		return selectedNodes;
	}

	private static void exportAsPDF(Dot dot, File file) throws FileNotFoundException {
		DotPanel panel = new DotPanel(dot);
		panel.setSize(new Dimension((int) panel.getSVG().getWidth(), (int) panel.getSVG().getHeight()));
		double width = panel.getImage().getViewRect().getWidth();
		double height = panel.getImage().getViewRect().getHeight();

		Dimension dimension = new Dimension((int) Math.ceil(width), (int) Math.ceil(height));
		VectorGraphics g = new PDFGraphics2D(file, dimension);
		Properties p = new Properties(PDFGraphics2D.getDefaultProperties());
		p.setProperty(PDFGraphics2D.PAGE_SIZE, PDFGraphics2D.CUSTOM_PAGE_SIZE);
		p.setProperty(PDFGraphics2D.PAGE_MARGINS, "0, 0, 0, 0");
		p.put(PDFGraphics2D.CUSTOM_PAGE_SIZE, dimension.width + ", " + dimension.height);
		g.setProperties(p);
		g.startExport();
		panel.print(g);
		g.endExport();
	}

	private static String[] getClassifierConfig(CommandLine commandLine) {
		String[] classifier = commandLine.getOptionValues(CLASSIFIER.getOpt());
		if (classifier == null) {
			classifier = new String[] { "concept:name" };
		}
		return classifier;
	}

	private static CLIContext loadProMContext() throws Throwable {
		return new CLIContext();
	}

	private static XLog loadLog(File logFile) throws IOException {
		XLog log;
		try {
			if (logFile.getName().endsWith(".gz")) {
				log = loadLog(new GZIPInputStream(new FileInputStream(logFile)));
			} else {
				log = loadLog(logFile);
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		return log;
	}

	private static XLog loadLog(InputStream inputStream) throws Exception {
		XesLiteXmlParser parser = new XesLiteXmlParser(new XFactoryExternalStore.InMemoryStoreImpl(), true);
		return parser.parse(inputStream).get(0);
	}

	private static DataPetriNetsWithMarkings loadDPN(File dpnFile) throws DPNIOException, FileNotFoundException {
		return new DataPetriNetImporter().importFromStream(new FileInputStream(dpnFile)).getDPN();
	}

	//TODO get rid of
	private static CLIPluginContext createPluginContext(CLIContext cliContext) {
		CLIPluginContext pluginContext = new CLIPluginContext(cliContext, "Child") {

			public Progress getProgress() {
				return new CSVConversion.NoOpProgressImpl();
			}

		};
		pluginContext.getLoggingListeners().add(new Logger() {

			public void log(String message, PluginContextID contextID, MessageLevel messageLevel) {
				System.out.println(message);
			}

			public void log(Throwable t, PluginContextID contextID) {
				System.err.println(t);
			}
		});
		return pluginContext;
	}

	private static void printUsage() {
		HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.printHelp("mpe [file]", OPTIONS, true);
		return;
	}

}
