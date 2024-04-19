/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.chartview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.math3.stat.StatUtils;
import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.freehep.graphicsio.ps.PSGraphics2D;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Week;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.chartview.Attribute.AttributeOccurence;
import org.processmining.dataawareexplorer.explorer.chartview.Attribute.AttributeOrigin;
import org.processmining.dataawareexplorer.explorer.events.ModelSelectionChangedEvent;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.framework.util.ui.widgets.ColorScheme;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.helper.ProMUIHelper;
import org.processmining.log.utils.XUtils;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;
import org.processmining.xesalignmentextension.XDataAlignmentExtension;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.XDataAlignmentExtensionException;

import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.Doubles;

public class AttributeChartView implements ChartView {

	private final class TransitionLabelComparator implements Comparator<Transition> {
		public int compare(Transition o1, Transition o2) {
			return o1.getLabel().compareTo(o2.getLabel());
		}
	}

	private static final class ImprovedChartPanel extends ChartPanel {

		private static final long serialVersionUID = 1L;

		private ImprovedChartPanel(JFreeChart chart, boolean useBuffer) {
			super(chart, useBuffer);
		}

		@Override
		public void doSaveAs() throws IOException {
			JFileChooser fileChooser = new JFileChooser();
			Preferences preferences = Preferences.userRoot().node("org.processmining.graphviz");
			fileChooser
					.setCurrentDirectory(new File(preferences.get("lastUsedFolder", new File(".").getAbsolutePath())));
			FileNameExtensionFilter pngFilter = new FileNameExtensionFilter(
					localizationResources.getString("PNG_Image_Files"), "png");
			fileChooser.addChoosableFileFilter(pngFilter);

			FileNameExtensionFilter epsFilter = new FileNameExtensionFilter("EPS (Encapsulated Postscript)", "eps");
			fileChooser.addChoosableFileFilter(epsFilter);

			fileChooser.setFileFilter(pngFilter);

			int option = fileChooser.showSaveDialog(this);
			if (option == JFileChooser.APPROVE_OPTION) {
				FileFilter fileFilter = fileChooser.getFileFilter();
				if (fileFilter == pngFilter) {
					String filename = fileChooser.getSelectedFile().getPath();
					if (isEnforceFileExtensions()) {
						if (!filename.endsWith(".png")) {
							filename = filename + ".png";
						}
					}
					ChartUtilities.saveChartAsPNG(new File(filename), getChart(), getWidth(), getHeight());
				} else { //EPS
					String filename = fileChooser.getSelectedFile().getPath();
					if (!filename.endsWith(".eps")) {
						filename = filename + ".eps";
					}
					Dimension dimension = new Dimension(getWidth(), getHeight());
					PSGraphics2D g = new PSGraphics2D(new File(filename), dimension);
					Properties p = new Properties(PSGraphics2D.getDefaultProperties());
					p.setProperty(PSGraphics2D.PAGE_SIZE, PSGraphics2D.CUSTOM_PAGE_SIZE);
					p.setProperty(PSGraphics2D.PAGE_MARGINS, "0, 0, 0, 0");
					p.put(PSGraphics2D.CUSTOM_PAGE_SIZE, dimension.width + ", " + dimension.height);
					g.setProperties(p);
					g.startExport();
					ChartRenderingInfo chartRenderingInfo = new ChartRenderingInfo();
					getChart().draw(g, new Rectangle(dimension), chartRenderingInfo);
					g.endExport();
				}
			}
		}
	}

	private enum RegularTimeEnum {

		SECOND(new RegularTimePeriodFactory() {

			public RegularTimePeriod createTimePeriod(Date time) {
				return new Second(time);
			}

		}), MINUTE(new RegularTimePeriodFactory() {

			public RegularTimePeriod createTimePeriod(Date time) {
				return new Minute(time);
			}

		}), HOUR(new RegularTimePeriodFactory() {

			public RegularTimePeriod createTimePeriod(Date time) {
				return new Hour(time);
			}

		}), DAY(new RegularTimePeriodFactory() {

			public RegularTimePeriod createTimePeriod(Date time) {
				return new Day(time);
			}

		}), WEEK(new RegularTimePeriodFactory() {

			public RegularTimePeriod createTimePeriod(Date time) {
				return new Week(time);
			}

		});

		private RegularTimePeriodFactory factory;

		private RegularTimeEnum(RegularTimePeriodFactory factory) {
			this.factory = factory;
		}

		public RegularTimePeriod createPeriod(Date time) {
			return factory.createTimePeriod(time);
		}

	}

	private interface RegularTimePeriodFactory {

		RegularTimePeriod createTimePeriod(Date time);

	}

	private static class AttributeContainerImpl implements AttributeContainer {

		private ExplorerModel explorerModel;
		private final Color color;

		private AttributeContainerImpl(ExplorerModel explorerModel, Color color) {
			this.explorerModel = explorerModel;
			this.color = color;
		}

		public Multimap<String, Comparable<?>> getValues(Attribute attribute) {
			Multimap<String, Comparable<?>> values = ArrayListMultimap.create();
			for (XTrace t : explorerModel.getFilteredLog()) {
				String traceName = XUtils.getConceptName(t);
				addValue(attribute, values, traceName, t);
				for (XEvent e : t) {
					addValue(attribute, values, traceName, e);
				}
			}
			return values;
		}

		private void addValue(Attribute attribute, Multimap<String, Comparable<?>> values, String traceName, XAttributable attributable) {
			XAttribute attributeValue = attributable.getAttributes().get(attribute.getKey());
			if (attributeValue != null) {
				values.put(traceName,
						(Comparable<?>) XUtils.getAttributeValue(attributeValue));
			}
		}

		public Set<Attribute> getAttribute() {
			return Sets.filter(explorerModel.getChartAttributes(), new Predicate<Attribute>() {

				public boolean apply(Attribute a) {
					AttributeOrigin origin = a.getOrigin();
					return a.getOccurence() == AttributeOccurence.ANYWHERE && origin == AttributeOrigin.UNMAPPED_EVENT;
				}
			});
		}

		public String getLabel() {
			return "Anywhere";
		}

		public Color getColor() {
			return color;
		}

	}

	private static abstract class AbstractAttributeContainerImpl implements AttributeContainer {

		private final Color color;

		protected ExplorerModel explorerModel;

		private AbstractAttributeContainerImpl(ExplorerModel explorerModel, Color color) {
			this.explorerModel = explorerModel;
			this.color = color;
		}

		abstract protected boolean considerValue(XAlignmentMove move);

		public Multimap<String, Comparable<?>> getValues(Attribute attribute) {
			if (attribute instanceof AttributeWithData) {
				return ((AttributeWithData) attribute).getValues();
			}
			Multimap<String, Comparable<?>> finalValues = ArrayListMultimap.create();
			for (XAlignment a : explorerModel.getFilteredAlignments()) {
				String traceName = a.getName();
				
				XAttributeMap traceAttributes = a.getTrace().getAttributes();
				XAttribute traceAttribute = traceAttributes.get(attribute.getKey());
				// Only post value is relevant for trace
				if (traceAttribute != null) {
					finalValues.put(traceName, (Comparable<?>) XUtils.getAttributeValue(traceAttribute));
				}

				Object currentValue = null;
				for (XAlignmentMove m : a) {

					// Pre values
					AttributeOccurence occurence = attribute.getOccurence();
					if (occurence == AttributeOccurence.PRE && considerValue(m) && currentValue != null) {
						finalValues.put(traceName, (Comparable<?>) currentValue);
					}

					XAttribute attributeValue = extractAttribute(attribute, m);
					if (attributeValue != null) {
						currentValue = XUtils.getAttributeValue(attributeValue);
						if (occurence == AttributeOccurence.ANYWHERE) {
							finalValues.put(traceName, (Comparable<?>) currentValue);
						} else if (occurence == AttributeOccurence.WRITTEN && considerValue(m)
								&& currentValue != null) {
							finalValues.put(traceName, (Comparable<?>) currentValue);
						}

					}

					// Post values
					if (occurence == AttributeOccurence.POST && considerValue(m) && currentValue != null) {
						finalValues.put(traceName, (Comparable<?>) currentValue);
					}
				}
			}
			return finalValues;
		}

		private final XAttribute extractAttribute(Attribute attribute, XAlignmentMove m) {
			XAttribute attributeValue = null;
			XDataAlignmentExtension dataAlignmentExtension = XDataAlignmentExtension.instance();
			switch (attribute.getOrigin()) {
				case UNMAPPED_EVENT :
				case ALIGNMENT_EVENT :
					attributeValue = m.getEvent().getAttributes().get(attribute.getKey());
					break;
				case VARIABLE_LOG :
					XAttribute logAttribute = m.getEvent().getAttributes().get(attribute.getKey());
					if (logAttribute != null) {
						attributeValue = dataAlignmentExtension.extractLogValue(logAttribute);
					}
					break;
				case VARIABLE_LOG_INVALID :
					XAttribute invalidLogAttribute = m.getEvent().getAttributes().get(attribute.getKey());
					if (invalidLogAttribute != null) {
						try {
							if (dataAlignmentExtension.isIncorrectAttribute(invalidLogAttribute)) {
								attributeValue = dataAlignmentExtension.extractLogValue(invalidLogAttribute);
							}
						} catch (XDataAlignmentExtensionException e) {
						}
					}
					break;
				case VARIABLE_PROCESS :
					attributeValue = m.getEvent().getAttributes().get(attribute.getKey());
					break;
				default :
					attributeValue = m.getEvent().getAttributes().get(attribute.getKey());
					break;
			}
			return attributeValue;
		}

		public Color getColor() {
			return color;
		}

	}

	private static final class AttributeContainerLocalImpl extends AbstractAttributeContainerImpl {

		private final Transition transition;

		public AttributeContainerLocalImpl(ExplorerModel explorerModel, Transition transition, Color color) {
			super(explorerModel, color);
			this.transition = transition;
		}

		public String getLabel() {
			return transition.getLabel();
		}

		protected boolean considerValue(XAlignmentMove move) {
			return explorerModel.getTransitionsLocalId().get(move.getActivityId()) == transition;
		}

		public Set<Attribute> getAttribute() {
			return explorerModel.getChartAttributes();
		}

	}

	private static final class AttributeContainerGlobalImpl extends AbstractAttributeContainerImpl {

		public AttributeContainerGlobalImpl(ExplorerModel explorerModel, Color color) {
			super(explorerModel, color);
		}

		public String getLabel() {
			return "Anywhere (i.e., all activities)";
		}

		protected boolean considerValue(XAlignmentMove move) {
			return true;
		}

		public Set<Attribute> getAttribute() {
			return Sets.filter(explorerModel.getChartAttributes(), new Predicate<Attribute>() {

				public boolean apply(Attribute a) {
					return a.getOccurence() == AttributeOccurence.ANYWHERE;
				}
			});
		}
	}

	public interface AttributeContainer {

		Set<Attribute> getAttribute();

		Multimap<String, Comparable<?>> getValues(Attribute attribute);

		String getLabel();

		Color getColor();

	}

	private static final Attribute NO_ATTRIBUTE = new Attribute(null, null, null, Object.class) {

		public int hashCode() {
			return System.identityHashCode(this);
		}

		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else {
				return false;
			}
		}

		public String toString() {
			return "Choose ...";
		}

	};

	private final ExplorerModel explorerModel;
	private final Collection<AttributeContainer> attributeContainers = new ArrayList<AttributeChartView.AttributeContainer>();

	private final JPanel mainPanel;

	private ProMComboBox<AttributeOccurence> occurenceFilter;
	private ProMComboBox<Attribute> attributesComboBox;
	private DefaultComboBoxModel<Attribute> attributesModel;

	private JPanel chartWrapper;
	private HistogramType currentHistogramType = HistogramType.RELATIVE_FREQUENCY;
	private JCheckBox relativeFrequencies;
	private JCheckBox useBoxplot;

	private int categoryLimit = 15;
	private DefaultComboBoxModel<RegularTimeEnum> timeModel;
	private ProMComboBox<RegularTimeEnum> timeBox;

	private JLabel timeResolutionLabel;

	private boolean isUpdating;

	static {
		BarRenderer.setDefaultShadowsVisible(false);
		XYBarRenderer.setDefaultShadowsVisible(false);
	}

	public AttributeChartView(ExplorerModel explorerModel, ExplorerUpdater explorerUpdater) {
		super();
		this.explorerModel = explorerModel;
		explorerUpdater.getEventBus().register(this);

		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		mainPanel.setBackground(Color.WHITE);

		chartWrapper = new JPanel();
		chartWrapper.setLayout(new BoxLayout(chartWrapper, BoxLayout.Y_AXIS));
		chartWrapper.setBackground(Color.WHITE);

		occurenceFilter = new ProMComboBox<AttributeOccurence>(AttributeOccurence.values());
		occurenceFilter.setPreferredSize(new Dimension(300, 30));
		occurenceFilter.setMaximumSize(new Dimension(300, 30));
		occurenceFilter.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				updateAttributeContainers();
			}
		});

		attributesModel = new DefaultComboBoxModel<Attribute>();
		attributesModel.addElement(NO_ATTRIBUTE);
		attributesComboBox = new ProMComboBox<Attribute>(attributesModel);
		attributesComboBox.setMinimumSize(null);
		attributesComboBox.setMaximumSize(null);
		attributesComboBox.setPreferredSize(null);
		attributesComboBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				try {
					showSelectedChart();
				} catch (Exception e1) {
					ProMUIHelper.showErrorMessage(mainPanel, e1.getMessage(), "Error Updating Charts", e1);
				}
			}

		});

		JPanel configWrapper = new JPanel();
		configWrapper.setLayout(new BoxLayout(configWrapper, BoxLayout.X_AXIS));
		configWrapper.add(new JLabel("Select type: "));
		configWrapper.add(occurenceFilter);
		configWrapper.add(new JLabel("Select attribute: "));
		configWrapper.add(attributesComboBox);
		configWrapper.add(Box.createGlue());
		relativeFrequencies = new JCheckBox("Histogram shows relative frequencies", true);
		relativeFrequencies.setVisible(false);
		relativeFrequencies.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (relativeFrequencies.getModel().isSelected()) {
					currentHistogramType = HistogramType.RELATIVE_FREQUENCY;
				} else {
					currentHistogramType = HistogramType.FREQUENCY;
				}
				try {
					showSelectedChart();
				} catch (Exception e1) {
					ProMUIHelper.showErrorMessage(mainPanel, e1.getMessage(), "Error Updating Charts", e1);
				}
			}
		});
		configWrapper.add(relativeFrequencies);

		useBoxplot = new JCheckBox("Use Boxplot instead of Histogram", false);
		useBoxplot.setVisible(false);
		useBoxplot.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				try {
					showSelectedChart();
				} catch (Exception e1) {
					ProMUIHelper.showErrorMessage(mainPanel, e1.getMessage(), "Error Updating Charts", e1);
				}
			}
		});
		configWrapper.add(useBoxplot);

		timeModel = new DefaultComboBoxModel<RegularTimeEnum>(RegularTimeEnum.values());
		timeBox = new ProMComboBox<RegularTimeEnum>(timeModel);
		timeBox.setVisible(false);
		timeBox.setMinimumSize(null);
		timeBox.setMaximumSize(null);
		timeBox.setPreferredSize(new Dimension(300, 30));
		timeBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				try {
					showSelectedChart();
				} catch (Exception e1) {
					ProMUIHelper.showErrorMessage(mainPanel, e1.getMessage(), "Error Updating Charts", e1);
				}
			}

		});

		timeResolutionLabel = new JLabel("Time-series resolution: ");
		timeResolutionLabel.setVisible(false);
		configWrapper.add(timeResolutionLabel);
		configWrapper.add(timeBox);

		mainPanel.add(chartWrapper, BorderLayout.CENTER);
		mainPanel.add(configWrapper, BorderLayout.SOUTH);
	}

	public Attribute getCurrentAttribute() {
		return (Attribute) attributesComboBox.getSelectedItem();
	}

	public void setCurrentAttribute(Attribute attribute) {
		attributesComboBox.setSelectedItem(attribute);
	}

	public Component getComponent() {
		return mainPanel;
	}

	@Subscribe
	public void modelSelectionUpdated(ModelSelectionChangedEvent event) {
		if (explorerModel.hasAlignment()) {
			new SwingWorker<Void, Void>() {

				protected Void doInBackground() throws Exception {
					updateData();
					return null;
				}

				protected void done() {
					try {
						get();
						updateUI();
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
				}

			}.execute();
		}
		// otherwise we cannot change anything
	}

	public void updateData() {
		attributeContainers.clear();
		if (explorerModel.hasAlignment()) {
			List<Transition> relevantTransitions = new ArrayList<>();
			Set<Object> selectedNodes = explorerModel.getFilterConfiguration().getSelectedNodes();
			for (final Object node : selectedNodes) {
				if (node instanceof Transition) {
					relevantTransitions.add((Transition) node);
				} else if (node instanceof Place) {
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : explorerModel.getModel()
							.getOutEdges((Place) node)) {
						assert edge
								.getTarget() instanceof Transition : "Outgoing edge of a place must be a transition!";
						relevantTransitions.add((Transition) edge.getTarget());
					}
				}
			}

			// Sort by name
			relevantTransitions = Ordering.from(new TransitionLabelComparator())
					.immutableSortedCopy(relevantTransitions);

			if (relevantTransitions.isEmpty()) {
				// NULL is permitted by LinkedHashMap
				attributeContainers.add(new AttributeContainerGlobalImpl(explorerModel, Color.BLACK));
			} else {
				int i = 0;
				for (final Transition transition : relevantTransitions) {
					attributeContainers.add(new AttributeContainerLocalImpl(explorerModel, transition,
							ColorScheme.COLOR_BREWER_5CLASS_SET1.getColor(i++)));
				}
			}

		} else {
			attributeContainers.add(new AttributeContainerImpl(explorerModel, Color.BLACK));
		}
	}

	public void updateUI() {
		updateAttributeContainers();
		if (explorerModel.getFilterConfiguration().getSelectedNodes().isEmpty()) {
			occurenceFilter.setSelectedItem(AttributeOccurence.ANYWHERE);
		} else if (occurenceFilter.getSelectedItem() == AttributeOccurence.ANYWHERE) {
			occurenceFilter.setSelectedItem(AttributeOccurence.PRE);
		}
	}

	private void updateAttributeContainers() {
		Attribute oldItem = (Attribute) attributesComboBox.getSelectedItem();
		try {
			NavigableSet<Attribute> attributeKeys = new TreeSet<>();
			attributeKeys.add(NO_ATTRIBUTE);
			for (AttributeContainer c : attributeContainers) {
				for (Attribute attr : c.getAttribute()) {
					if (isInFilter(attr)) {
						attributeKeys.add(attr);
					}
				}
			}
			replaceAttributesModel(attributeKeys);
		} finally {
			attributesComboBox.setSelectedItem(oldItem);
		}
	}

	private void replaceAttributesModel(NavigableSet<Attribute> attributeKeys) {
		attributesModel.removeAllElements();
		for (Attribute key : attributeKeys) {
			attributesModel.addElement(key);
		}
	}

	private boolean isInFilter(Attribute attr) {
		return occurenceFilter.getSelectedItem() == attr.getOccurence();
	}

	private void showSelectedChart() {

		if (!isUpdating) {

			Attribute currentXAttribute = (Attribute) attributesComboBox.getSelectedItem();
			chartWrapper.removeAll();

			if (currentXAttribute != null && currentXAttribute != NO_ATTRIBUTE) {
				Class<?> type = getType(attributeContainers, currentXAttribute);
				if (type != null) {
					if (isNumeric(type)) {
						relativeFrequencies.setVisible(true);
						useBoxplot.setVisible(true);
					} else {
						relativeFrequencies.setVisible(false);
						useBoxplot.setVisible(false);
						if (type == Date.class) {
							timeBox.setVisible(true);
						} else {
							timeBox.setVisible(false);
						}
					}
					//Defensive copy of the attribute containers
					addChart(chartWrapper, ImmutableList.copyOf(attributeContainers), currentXAttribute, type);
				} else {
					chartWrapper.add(SlickerFactory.instance().createLabel(
							"<HTML><H1>Please select an attribute. Select places or transitions to obtain context dependant charts!</H1></HTML>"));
				}
			} else {
				chartWrapper.add(SlickerFactory.instance().createLabel(
						"<HTML><H1>Please select an attribute. Select places or transitions to obtain context dependant charts!</H1></HTML>"));
			}
			chartWrapper.repaint();
			chartWrapper.validate();
		}
	}

	private void addChart(final JPanel chartWrapper, final Collection<AttributeContainer> containers,
			final Attribute currentAttribute, final Class<?> type) {

		startUpdate();

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		chartWrapper.add(progressBar);

		new SwingWorker<ChartPanel, Void>() {

			protected ChartPanel doInBackground() throws Exception {
				if (isNumeric(type) && isHistogram()) {
					return createHistogram(containers, currentAttribute, type);
				} else if (isNumeric(type) && !isHistogram()) {
					return createBoxplot(containers, currentAttribute, type);
				} else if (type == Date.class) {
					return createTimeplot(containers, currentAttribute, type);
				} else {
					return createBarchart(containers, currentAttribute, type);
				}
			}

			protected void done() {
				chartWrapper.removeAll();
				try {
					chartWrapper.add(get());
				} catch (InterruptedException | ExecutionException e) {
					ProMUIHelper.showErrorMessage(mainPanel, e.getMessage(), "Error Updating Charts", e);
				} finally {
					finishUpdate();
				}
				chartWrapper.validate();
			}

		}.execute();
	}

	private void startUpdate() {
		isUpdating = true;
		attributesComboBox.setEnabled(false);
		occurenceFilter.setEnabled(false);
	}

	private void finishUpdate() {
		isUpdating = false;
		attributesComboBox.setEnabled(true);
		occurenceFilter.setEnabled(true);
	}

	private ChartPanel createBarchart(final Collection<AttributeContainer> containers, Attribute currentAttribute,
			Class<?> type) {
		JFreeChart categoryChart = createChartCategoricalData(containers, currentAttribute, type);
		ChartPanel chartPanel = new ImprovedChartPanel(categoryChart, true);
		// Ugly hack to use Graphviz preferences 
		Preferences preferences = Preferences.userRoot().node("org.processmining.graphviz");
		chartPanel.setDefaultDirectoryForSaveAs(
				new File(preferences.get("lastUsedFolder", new File(".").getAbsolutePath())));
		chartPanel.setMouseWheelEnabled(true);
		chartPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		return chartPanel;
	}

	private ChartPanel createHistogram(final Collection<AttributeContainer> containers, Attribute currentAttribute,
			Class<?> type) {

		CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(new NumberAxis(currentAttribute.getKey()));
		combinedPlot.setGap(20.0);

		int binSize;
		double[] allValue = new double[0];
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (AttributeContainer c : containers) {
			//TODO only get the values once! performance!
			Collection<Comparable<?>> observations = c.getValues(currentAttribute).values();
			double[] values = new double[observations.size()];
			if (!observations.isEmpty()) {
				int i = 0;
				for (Object element : observations) {
					double val = ((Number) element).doubleValue();
					values[i++] = val;
					min = Math.min(min, val);
					max = Math.max(max, val);
				}
			}
			allValue = Doubles.concat(allValue, values);
		}

		binSize = calcBinSize(allValue, max, min);

		if (currentAttribute.getOrigin() != AttributeOrigin.TIME) {
			for (AttributeContainer c : containers) {
				XYPlot histogramPlot = createHistogramPlot(c.getValues(currentAttribute).values(), c.getLabel(),
						c.getColor(), currentAttribute, type, binSize, min, max);
				combinedPlot.add(histogramPlot);
			}
		} else {
			AttributeContainer c = FluentIterable.from(containers).first().get();
			XYPlot histogramPlot = createHistogramPlot(c.getValues(currentAttribute).values(),
					currentAttribute.getKey(), Color.BLACK, currentAttribute, type, binSize, min, max);
			combinedPlot.add(histogramPlot);
		}

		combinedPlot.configureDomainAxes();
		combinedPlot.configureRangeAxes();

		double upperRange = -1;
		for (Object obj : combinedPlot.getSubplots()) {
			XYPlot plot = (XYPlot) obj;
			Range range = plot.getRangeAxis().getRange();
			upperRange = Math.max(upperRange, range.getUpperBound());
		}

		for (Object obj : combinedPlot.getSubplots()) {
			XYPlot plot = (XYPlot) obj;
			plot.getRangeAxis().setUpperBound(upperRange);
		}

		final JFreeChart histogramChart = new JFreeChart("Histogram of attribute values", JFreeChart.DEFAULT_TITLE_FONT,
				combinedPlot, true);
		histogramChart.setBackgroundPaint(Color.WHITE);
		//histogramChart.removeLegend();

		ChartPanel chartPanel = new ImprovedChartPanel(histogramChart, true);
		chartPanel.setMouseWheelEnabled(true);
		chartPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.BLACK));
		chartPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

		return chartPanel;
	}

	private ChartPanel createTimeplot(Collection<AttributeContainer> containers, Attribute currentAttribute,
			Class<?> type) {

		DateAxis dateAxis = new DateAxis("Time");

		CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(dateAxis);
		combinedPlot.setGap(20.0);

		for (AttributeContainer c : containers) {

			TimeSeries series = new TimeSeries(c.getLabel());

			Multimap<String, Comparable<?>> values = c.getValues(currentAttribute);

			for (java.util.Map.Entry<String, Collection<Comparable<?>>> entry : values.asMap().entrySet()) {
				for (Comparable<?> value : entry.getValue()) {
					RegularTimeEnum timeFactory = (RegularTimeEnum) timeBox.getSelectedItem();
					Date time = (Date) value;
					try {
						RegularTimePeriod current = timeFactory.createPeriod(time);
						if (series.getDataItem(current) != null) {
							series.addOrUpdate(current, series.getValue(current).intValue() + 1);
						} else {
							series.addOrUpdate(current, 1);
						}
					} catch (IllegalArgumentException e) {
					}
				}
			}

			TimeSeriesCollection seriesCollection = new TimeSeriesCollection(series);

			DateAxis timeAxis = new DateAxis("Time");
			NumberAxis valueAxis = new NumberAxis("Frequency");
			valueAxis.setAutoRangeIncludesZero(false); // override default
			valueAxis.setAutoRange(true);

			XYPlot plot = new XYPlot(seriesCollection, timeAxis, valueAxis, null);

			XYToolTipGenerator toolTipGenerator = null;
			toolTipGenerator = StandardXYToolTipGenerator.getTimeSeriesInstance();

			XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
			renderer.setSeriesPaint(0, c.getColor());
			renderer.setBaseToolTipGenerator(toolTipGenerator);
			plot.setRenderer(renderer);

			plot.setOrientation(PlotOrientation.VERTICAL);
			plot.setNoDataMessage("=== No Datapoints Detected ====");
			plot.setBackgroundPaint(Color.WHITE);
			plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
			plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

			combinedPlot.add(plot);
		}

		combinedPlot.configureDomainAxes();
		combinedPlot.configureRangeAxes();

		JFreeChart timeSeriesChart = new JFreeChart("Timeseries of events", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot,
				true);
		timeSeriesChart.setBackgroundPaint(Color.WHITE);

		ChartPanel chartPanel = new ImprovedChartPanel(timeSeriesChart, true);
		Preferences preferences = Preferences.userRoot().node("org.processmining.graphviz");
		chartPanel.setDefaultDirectoryForSaveAs(
				new File(preferences.get("lastUsedFolder", new File(".").getAbsolutePath())));
		chartPanel.setMouseWheelEnabled(true);
		chartPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.BLACK));
		chartPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

		return chartPanel;
	}

	private ChartPanel createBoxplot(final Collection<AttributeContainer> containers, Attribute currentAttribute,
			Class<?> type) {

		DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset() {

			private static final long serialVersionUID = 1L;

			@SuppressWarnings("rawtypes")
			public List getOutliers(int row, int column) {
				return ImmutableList.of();
			}

			@SuppressWarnings("rawtypes")
			public List getOutliers(Comparable rowKey, Comparable columnKey) {
				return ImmutableList.of();
			}

		};
		BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
		renderer.setUseOutlinePaintForWhiskers(false);
		renderer.setMeanVisible(false);

		if (currentAttribute.getOrigin() != AttributeOrigin.TIME) {
			for (AttributeContainer c : containers) {
				addBoxplotValues(currentAttribute, dataset, c);
			}
			renderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
			int i = 0;
			for (AttributeContainer c : containers) {
				renderer.setSeriesPaint(i, c.getColor());
				i++;
			}
		} else {
			addBoxplotValues(currentAttribute, dataset, FluentIterable.from(containers).first().get());
			renderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
			renderer.setSeriesPaint(0, Color.LIGHT_GRAY);
		}

		CategoryAxis domainAxis = new CategoryAxis(currentAttribute.getKey());
		domainAxis.setAxisLinePaint(Color.BLACK);
		domainAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));

		ValueAxis valueAxis = new NumberAxis("Value");
		valueAxis.setAxisLinePaint(Color.BLACK);
		valueAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));

		CategoryPlot plot = new CategoryPlot(dataset, domainAxis, valueAxis, renderer);
		plot.setOrientation(PlotOrientation.VERTICAL);
		plot.setNoDataMessage("=== No Datapoints Detected ====");
		plot.setBackgroundPaint(Color.WHITE);
		plot.setBackgroundPaint(Color.WHITE);
		plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
		plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

		String title = "Boxplot of attribute values";
		JFreeChart boxplotChart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
		boxplotChart.setBackgroundPaint(Color.WHITE);

		if (containers.size() == 1) {
			boxplotChart.removeLegend();
		}

		ChartPanel chartPanel = new ImprovedChartPanel(boxplotChart, true);
		// Ugly hack to use graphviz preferences, as I 
		Preferences preferences = Preferences.userRoot().node("org.processmining.graphviz");
		chartPanel.setDefaultDirectoryForSaveAs(
				new File(preferences.get("lastUsedFolder", new File(".").getAbsolutePath())));
		chartPanel.setMouseWheelEnabled(true);
		chartPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

		return chartPanel;
	}

	private static void addBoxplotValues(Attribute currentAttribute, DefaultBoxAndWhiskerCategoryDataset dataset,
			AttributeContainer c) {
		Collection<Comparable<?>> observations = c.getValues(currentAttribute).values();
		if (!observations.isEmpty()) {
			double[] values = new double[observations.size()];
			int i = 0;
			for (Object element : observations) {
				double val = ((Number) element).doubleValue();
				values[i++] = val;
			}
			dataset.add(Doubles.asList(values), c.getLabel(), "");
		}
	}

	private boolean isHistogram() {
		return !useBoxplot.getModel().isSelected();
	}

	private Class<?> getType(final Collection<AttributeContainer> containers, Attribute currentAttribute) {
		return currentAttribute.getType();
	}

	private boolean isNumeric(Class<?> type) {
		return type == Double.class || type == Long.class || type == Float.class;
	}

	private JFreeChart createChartCategoricalData(final Collection<AttributeContainer> containers,
			Attribute currentAttribute, Class<?> type) {

		boolean isFullSetShown = true;

		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		// Determine what to show
		Set<Comparable<?>> shownCategories = new HashSet<>();
		for (AttributeContainer c : containers) {
			ImmutableMultiset<Comparable<?>> orderedValues = Multisets
					.copyHighestCountFirst(ImmutableMultiset.copyOf(c.getValues(currentAttribute).values()));
			int categoryCount = 0;
			for (Entry<Comparable<?>> entry : orderedValues.entrySet()) {
				if (categoryCount <= categoryLimit) {
					categoryCount++;
					shownCategories.add(entry.getElement());
				} else {
					// We exceeded the maximum reasonable number of categories to be shown
					isFullSetShown = false;
				}
			}
		}
		
		// Actually add the values
		for (AttributeContainer c : containers) {
			ImmutableMultiset<Comparable<?>> orderedValues = Multisets
					.copyHighestCountFirst(ImmutableMultiset.copyOf(c.getValues(currentAttribute).values()));
			for (Entry<Comparable<?>> entry : orderedValues.entrySet()) {
				if (shownCategories.contains(entry.getElement())) {
					dataset.setValue(entry.getCount(), c.getLabel(), entry.getElement());
				}
			}
		}

		CategoryAxis domainAxis = new CategoryAxis(currentAttribute.getKey());
		domainAxis.setAxisLinePaint(Color.BLACK);
		domainAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));
		if (dataset.getRowCount() > 4) {
			domainAxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_45);
		}

		ValueAxis valueAxis = new NumberAxis("Frequency");
		valueAxis.setAxisLinePaint(Color.BLACK);
		valueAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));

		BarRenderer renderer = new BarRenderer();
		renderer.setShadowVisible(false);
		renderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
		renderer.setBarPainter(new StandardBarPainter());
		int i = 0;
		for (AttributeContainer c : containers) {
			renderer.setSeriesPaint(i++, c.getColor());
		}

		CategoryPlot plot = new CategoryPlot(dataset, domainAxis, valueAxis, renderer);
		plot.setOrientation(PlotOrientation.VERTICAL);
		plot.setNoDataMessage("=== No Datapoints Detected ====");
		plot.setBackgroundPaint(Color.WHITE);
		plot.setBackgroundPaint(Color.WHITE);
		plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
		plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

		String title = "Frequency of attribute values";
		if (!isFullSetShown) {
			title = title + " (Top " + shownCategories.size() + ")";
		}
		JFreeChart categoryChart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
		categoryChart.setBackgroundPaint(Color.WHITE);

		if (containers.size() == 1) {
			categoryChart.removeLegend();
		}

		return categoryChart;
	}

	private XYPlot createHistogramPlot(Collection<Comparable<?>> collection, String label, Color color,
			Attribute currentAttribute, Class<?> type, int binSize, double min, double max) {

		HistogramDataset dataset = new HistogramDataset();

		if (!collection.isEmpty()) {
			double[] values = new double[collection.size()];
			int i = 0;
			for (Object element : collection) {
				double val = ((Number) element).doubleValue();
				values[i++] = val;
			}
			dataset.addSeries(label, values, binSize, min, max);
			dataset.setType(currentHistogramType);
		}

		NumberAxis domainAxis = new NumberAxis(currentAttribute.getKey());
		domainAxis.setAutoRangeIncludesZero(false);
		domainAxis.setAutoRange(true);
		domainAxis.setAxisLinePaint(Color.BLACK);
		domainAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));

		ValueAxis valueAxis = new NumberAxis("Frequency");
		valueAxis.setAxisLinePaint(Color.BLACK);
		valueAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));

		XYBarRenderer renderer = new XYBarRenderer();
		renderer.setShadowVisible(false);
		renderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
		renderer.setSeriesPaint(0, color);
		StandardXYBarPainter barPainter = new StandardXYBarPainter();
		renderer.setBarPainter(barPainter);

		XYPlot plot = new XYPlot(dataset, domainAxis, valueAxis, renderer);
		plot.setOrientation(PlotOrientation.VERTICAL);
		plot.setNoDataMessage("=== No Datapoints Detected ====");
		plot.setBackgroundPaint(Color.WHITE);
		plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
		plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

		return plot;
	}

	private static int calcBinSize(double[] data, double max, double min) {
		double binWidth = (2 * (StatUtils.percentile(data, 75.0) - StatUtils.percentile(data, 25.0))
				* Math.pow(data.length, -(1.0 / 3.0)));
		if (binWidth == 0.0d) {
			// Most of the values are zero, but there might be outliers
			return 100;
		} else {
			return Math.min(Math.max(1, (int) Math.ceil((max - min) / binWidth)), 10000);
		}
	}

}
