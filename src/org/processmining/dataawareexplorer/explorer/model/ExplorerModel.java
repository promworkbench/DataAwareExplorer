/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.util.XAttributeUtils;
import org.processmining.dataawareexplorer.explorer.DataAwareExplorer;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerController;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.chartview.Attribute;
import org.processmining.dataawareexplorer.explorer.chartview.Attribute.AttributeOccurence;
import org.processmining.dataawareexplorer.explorer.chartview.Attribute.AttributeOrigin;
import org.processmining.dataawareexplorer.explorer.exception.FilterException;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration.SelectionFilterMode;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.explorer.work.AlignmentInformation;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryResult;
import org.processmining.dataawareexplorer.parallel.Input;
import org.processmining.dataawareexplorer.parallel.MapReduce;
import org.processmining.dataawareexplorer.parallel.MapReduceTask;
import org.processmining.dataawareexplorer.parallel.Output;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.GuardDisplayMode;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.PlaceDisplayMode;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.VariableDisplayMode;
import org.processmining.log.utils.XUtils;
import org.processmining.logenhancement.view.LogViewVisualizer;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.DataConformance.visualization.alignment.ColorTheme;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.xesalignmentextension.XAlignmentExtension;
import org.processmining.xesalignmentextension.XAlignmentExtension.MoveType;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignedLog;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.DataMoveType;
import org.processmining.xeslite.query.AttributeTypeResolver;
import org.processmining.xeslite.query.XIndex;
import org.processmining.xeslite.query.syntax.ParseException;
import org.xeslite.XLogMetadata;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

public class ExplorerModel {

	private ExplorerController controller = new ExplorerController() {

		public DataAwareExplorer addExplorer(String title, DataAwareExplorer explorer) {
			throw new UnsupportedOperationException("Cannot add a new explorer in stand-alone mode!");
		}

		public DataAwareExplorer addExplorerWithDiscoveredModel(ExplorerContext explorerContext,
				ExplorerUpdater explorerUpdater, ExplorerModel explorerModel, DiscoveryResult discoveryResult) {
			throw new UnsupportedOperationException("Cannot add a new explorer in stand-alone mode!");
		}

		public void switchExplorer(DataAwareExplorer explorer, ViewMode viewMode) {
			throw new UnsupportedOperationException("Cannot add a new explorer in stand-alone mode!");
		}

	};

	private final XLog log;
	private final DataPetriNetsWithMarkings model;

	private XEventClassifier eventClassifier = new XEventNameClassifier();
	private XEventClasses eventClasses;
	private Map<String, Class<?>> attributesTypes;

	private Map<XEventClass, Color> colorMap = ImmutableMap.of();
	private Iterable<XTrace> filteredLog;

	private final Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> outEdgeMap = new HashMap<>();
	private final Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> inEdgeMap = new HashMap<>();
	private final Map<String, Transition> transitionsLocalId = new HashMap<>();

	private XAlignedLog alignment;
	private Map<String, Color> alignmentColorMap;
	private BalancedProcessorConfiguration alignmentConfiguration;

	private List<XAlignment> queryFilteredAlignments;
	private List<XAlignment> filteredAlignments;

	private AlignmentInformation alignmentInformation;
	private Collection<XAlignment> selectedAlignments = ImmutableList.of();

	private FilterConfiguration filterConfiguration = new FilterConfiguration();
	private List<String> autoCompleteDictionary = new ArrayList<String>();

	private ViewMode viewMode = ViewMode.MODEL;

	private VariableDisplayMode variableDisplayMode = VariableDisplayMode.AUTO_LAYOUT;
	private GuardDisplayMode guardDisplayMode = GuardDisplayMode.EDGES;
	private PlaceDisplayMode placeDisplayMode = PlaceDisplayMode.BASIC;

	private boolean viewModeDirty = true;
	private boolean alignmentDirty = true;
	private boolean discoveryDirty = true;

	private Set<Attribute> chartAttributes = new HashSet<>();

	private DiscoveryResult discoveryResult;

	private ForkJoinPool pool = new ForkJoinPool();

	private Map<String, Set<String>> literalValues;
	private SetMultimap<Transition, String> discoveredWriteOperations;

	public ExplorerModel(XLog log, DataPetriNetsWithMarkings model) {
		super();
		this.log = log;
		this.model = model;
		updateLogDataStructures(log);
		prepareNetDataStructures(model);
	}

	public XLog getLog() {
		return log;
	}

	private void updateLogDataStructures(XLog log) {
		eventClasses = XUtils.createEventClasses(getEventClassifier(), log);

		if (log instanceof XLogMetadata) {
			attributesTypes = ((XLogMetadata) log).getAttributeTypes();
		} else {
			attributesTypes = XUtils.getEventAttributeTypes(log);
			attributesTypes.putAll(XUtils.getTraceAttributeTypes(log));
		}

		// Requires XLogInfo build in prepareLogDataStructure!!
		chartAttributes = updateChartAttributes(model, log, getLogAttributes(), null, alignmentConfiguration);
		colorMap = LogViewVisualizer.createColorMap(eventClasses);
		updateDictionary(getLogAttributes());
	}

	private void updateDictionary(Collection<String> keys) {
		autoCompleteDictionary.clear();
		autoCompleteDictionary.add("true");
		autoCompleteDictionary.add("false");
		autoCompleteDictionary.add(MoveType.LOG.name());
		autoCompleteDictionary.add(MoveType.MODEL.name());
		autoCompleteDictionary.add(MoveType.SYNCHRONOUS.name());
		autoCompleteDictionary.add(DataMoveType.CORRECT.name());
		autoCompleteDictionary.add(DataMoveType.INCORRECT.name());
		autoCompleteDictionary.add(DataMoveType.MISSING.name());
		autoCompleteDictionary.add("alignment:movetype");
		autoCompleteDictionary.add("alignment:fitness");
		for (String key : keys) {
			autoCompleteDictionary.add(key);
		}
		Collections.sort(autoCompleteDictionary);
	}

	public boolean hasLog() {
		return log != null;
	}

	public XEventClasses getEventClasses() {
		return eventClasses;
	}

	public Set<String> getLogAttributes() {
		return attributesTypes.keySet();
	}

	public Class<?> getLogAttributeType(String key) {
		return attributesTypes.get(key);
	}

	public Map<XEventClass, Color> getColorMap() {
		return colorMap;
	}

	public Iterable<XTrace> getFilteredLog() {
		return filteredLog;
	}

	public DataPetriNetsWithMarkings getModel() {
		return model;
	}

	public Marking getInitialMarking() {
		return getModel().getInitialMarking();
	}

	public Marking[] getFinalMarkings() {
		return getModel().getFinalMarkings();
	}

	public XAlignedLog getAlignment() {
		return alignment;
	}

	public void setAlignment(XAlignedLog alignment) {
		if (alignment == null || alignment != this.alignment) {
			alignmentColorMap = ColorTheme.createColorMap(alignment);
			chartAttributes = updateChartAttributes(model, getLog(), getLogAttributes(), alignment,
					getAlignmentConfiguration());
			selectedAlignments = ImmutableList.of();
			alignmentDirty = true;
		}
		this.alignment = alignment;
	}

	public boolean hasAlignment() {
		return alignment != null;
	}

	public Map<String, Color> getAlignmentColorMap() {
		return alignmentColorMap;
	}

	public Iterable<XAlignment> getFilteredAlignments() {
		return filteredAlignments;
	}

	public AlignmentInformation getAlignmentInformation() {
		return alignmentInformation;
	}

	public boolean isAlignmentDirty() {
		return alignmentDirty;
	}

	public FilterConfiguration getFilterConfiguration() {
		return filterConfiguration;
	}

	public void setFilterConfiguration(FilterConfiguration filterConfiguration) {
		this.filterConfiguration = filterConfiguration;
	}

	public ViewMode getViewMode() {
		return viewMode;
	}

	public void setViewMode(ViewMode viewMode) {
		if (viewMode != this.viewMode) {
			viewModeDirty = true;
		}
		this.viewMode = viewMode;
	}

	public boolean isViewModeDirty() {
		return viewModeDirty;
	}

	public void resetDirtyFlags() {
		filterConfiguration.resetDirtyFlags();
		alignmentDirty = false;
		discoveryDirty = false;
		viewModeDirty = false;
	}

	public boolean isFilterDirty() {
		return filterConfiguration.isQueryDirty()
				// Filter mode was changed
				|| filterConfiguration.isSelectionModeDirty()
				// Dirty selection and filter mode is on
				|| (filterConfiguration.isSelectionDirty()
						&& filterConfiguration.getSelectionFilterMode() != SelectionFilterMode.NONE);
	}

	public BalancedProcessorConfiguration getAlignmentConfiguration() {
		return alignmentConfiguration;
	}

	public void setAlignmentConfiguration(BalancedProcessorConfiguration alignmentConfiguration) {
		this.alignmentConfiguration = alignmentConfiguration;
		if (alignmentConfiguration != null) {
			this.model.setInitialMarking(alignmentConfiguration.getInitialMarking());
			this.model.setFinalMarkings(alignmentConfiguration.getFinalMarkings());
		}
	}

	public void setController(ExplorerController parent) {
		this.controller = parent;
	}

	public ExplorerController getController() {
		return controller;
	}

	public Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> getOutEdgeMap() {
		return outEdgeMap;
	}

	public Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> getInEdgeMap() {
		return inEdgeMap;
	}

	public Map<String, Transition> getTransitionsLocalId() {
		return transitionsLocalId;
	}

	public Set<Attribute> getChartAttributes() {
		return chartAttributes;
	}

	// These methods actually belong to some controller class

	private final void prepareNetDataStructures(DataPetriNet net) {
		outEdgeMap.clear();
		inEdgeMap.clear();
		transitionsLocalId.clear();

		for (Transition t : net.getTransitions()) {
			Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = ImmutableList
					.copyOf(Collections2.filter(net.getInEdges(t),
							new Predicate<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>() {

								public boolean apply(
										PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge) {
									return edge.getSource() instanceof Place; // Ignore non-control flow edges
								}
							}));
			Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = ImmutableList
					.copyOf(Collections2.filter(net.getOutEdges(t),
							new Predicate<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>() {

								public boolean apply(
										PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge) {
									return edge.getTarget() instanceof Place; // Ignore non-control flow edges
								}
							}));
			outEdgeMap.put(t.getLocalID().toString(), outEdges);
			inEdgeMap.put(t.getLocalID().toString(), inEdges);
		}

		for (Transition t : net.getTransitions()) {
			transitionsLocalId.put(t.getLocalID().toString(), t);
		}
	}

	public void filter() throws NetVisualizationException {
		if (hasAlignment()) {
			filteredAlignments = doFilterAlignments(getAlignment());
			alignmentInformation = new AlignmentInformation(model, transitionsLocalId,
					alignmentConfiguration.getVariableMapping(), filteredAlignments);
		}
		if (hasLog()) {
			filteredLog = doFilterLog();
		}
	}

	private Iterable<XTrace> doFilterLog() throws NetVisualizationException {
		String filterQuery = getFilterConfiguration().getFilterQuery();
		if (!filterQuery.trim().isEmpty()) {
			try {
				return XIndex.filterTracesOnAttributesOrNames(getLog(), filterQuery, new AttributeTypeResolver() {

					public Class<? extends XAttribute> getAttributeType(final String attributeName) {
						// simply return the first class found
						for (XTrace t : getLog()) {
							XAttribute attribute = t.getAttributes().get(attributeName);
							if (attribute != null) {
								return XAttributeUtils.getType(attribute);
							}
							for (XEvent e : t) {
								attribute = e.getAttributes().get(attributeName);
								if (attribute != null) {
									return XAttributeUtils.getType(attribute);
								}
							}
						}
						return XAttributeLiteral.class;
					}
				});
			} catch (ParseException e) {
				throw new NetVisualizationException("Invalid filter query - Check your query syntax", e);
			}
		} else {
			return getLog();
		}
	}

	private List<XAlignment> doFilterAlignments(final XAlignedLog alignedLog) throws NetVisualizationException {
		final FilterConfiguration filterConfiguration = getFilterConfiguration();
		final String filterQuery = filterConfiguration.getFilterQuery();

		if (filterQuery != null && !filterQuery.isEmpty()) {
			if (filterConfiguration.isQueryDirty()) { //TODO somehow solve differently
				queryFilteredAlignments = doFilterByQuery(alignedLog, filterQuery);
			} // else keep old result
			assert queryFilteredAlignments != null;
		} else {
			queryFilteredAlignments = alignedLog;
		}

		if (filterConfiguration.getSelectedNodes().isEmpty()
				|| filterConfiguration.getSelectionFilterMode() == SelectionFilterMode.NONE) {
			return queryFilteredAlignments;
		} else {
			Predicate<XAlignment> filterPredicate = createSelectionFilter(filterConfiguration);
			return new MapReduce<List<XAlignment>>(pool)
					.execute(new FilterInput(queryFilteredAlignments, filterPredicate));
		}

	}

	private Predicate<XAlignment> createSelectionFilter(final FilterConfiguration filterConfiguration) {
		Predicate<XAlignment> filterPredicate;
		switch (filterConfiguration.getSelectionFilterMode()) {
			case AND :
				filterPredicate = new Predicate<XAlignment>() {

					public boolean apply(XAlignment alignment) {
						if (isTraceInANDSelection(filterConfiguration, alignment, transitionsLocalId, inEdgeMap)) {
							return true;
						}
						return false;
					}
				};
				break;
			case NEVER :
				filterPredicate = new Predicate<XAlignment>() {

					public boolean apply(XAlignment alignment) {
						if (!isInORSelection(filterConfiguration, alignment, transitionsLocalId, inEdgeMap)) {
							return true;
						}
						return false;
					}
				};
				break;
			case OR :
				filterPredicate = new Predicate<XAlignment>() {

					public boolean apply(XAlignment alignment) {
						if (isInORSelection(filterConfiguration, alignment, transitionsLocalId, inEdgeMap)) {
							return true;
						}
						return false;
					}
				};
				break;
			case NONE :
			default :
				filterPredicate = Predicates.alwaysTrue();
		}
		return filterPredicate;
	}

	private List<XAlignment> doFilterByQuery(final XAlignedLog alignedLog, final String filterQuery)
			throws FilterException {
		try {

			//TODO use index if available and place outside of this class
			Iterable<XTrace> queryFilteredTraces = XIndex.filterTracesOnAttributesOrNames(alignedLog.getLog(),
					filterQuery, new AttributeTypeResolver() {

						public Class<? extends XAttribute> getAttributeType(final String attributeName) {
							// simply return the first class found
							for (XTrace t : alignedLog.getLog()) {
								XAttribute attribute = t.getAttributes().get(attributeName);
								if (attribute != null) {
									return XAttributeUtils.getType(attribute);
								}
								for (XEvent e : t) {
									attribute = e.getAttributes().get(attributeName);
									if (attribute != null) {
										return XAttributeUtils.getType(attribute);
									}
								}
							}
							return XAttributeLiteral.class;
						}
					});
			return ImmutableList.copyOf(Iterables.transform(queryFilteredTraces, new Function<XTrace, XAlignment>() {

				public XAlignment apply(XTrace t) {
					return XAlignmentExtension.instance().extendTrace(t);
				}
			}));

		} catch (ParseException e) {
			throw new FilterException("Syntax error\n\n" + e.getMessage(), e);
		}
	}

	private static final class FilterInput implements Input<List<XAlignment>> {

		private final List<XAlignment> alignments;
		private final Predicate<XAlignment> predicate;

		private FilterInput(List<XAlignment> alignments, Predicate<XAlignment> predicate) {
			this.alignments = alignments;
			this.predicate = predicate;
		}

		public boolean shouldBeComputedDirectly() {
			return alignments.size() < 10000;
		}

		public Output<List<XAlignment>> computeDirectly() {
			Builder<XAlignment> resultBuilder = ImmutableList.<XAlignment>builder();
			resultBuilder.addAll(Iterables.filter(alignments, predicate));
			return new FilterOutput(resultBuilder);
		}

		public List<MapReduceTask<List<XAlignment>>> split() {
			List<XAlignment> sub1 = alignments.subList(0, alignments.size() / 2);
			List<XAlignment> sub2 = alignments.subList(alignments.size() / 2, alignments.size());
			return ImmutableList.of(new MapReduceTask<List<XAlignment>>(new FilterInput(sub1, predicate)),
					new MapReduceTask<List<XAlignment>>(new FilterInput(sub2, predicate)));

		}
	}

	private static final class FilterOutput implements Output<List<XAlignment>> {

		private Builder<XAlignment> resultBuilder;

		public FilterOutput(Builder<XAlignment> resultBuilder) {
			this.resultBuilder = resultBuilder;
		}

		public Output<List<XAlignment>> reduce(Output<List<XAlignment>> other) {
			resultBuilder.addAll(other.getResult());
			return this;
		}

		public List<XAlignment> getResult() {
			return resultBuilder.build();
		}
	}

	private static boolean isInORSelection(FilterConfiguration filterConfiguration, XAlignment alignment,
			Map<String, Transition> transitionsLocalId,
			Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> inEdgeMap) {

		Set<Object> selectedNodes = filterConfiguration.getSelectedNodes();

		for (XAlignmentMove move : alignment) {
			MoveType moveType = move.getType();

			if (moveType == MoveType.SYNCHRONOUS || moveType == MoveType.MODEL) {
				String activityId = move.getActivityId();
				Transition activityObject = transitionsLocalId.get(activityId);

				if (selectedNodes.contains(activityObject)) {
					return true;
				}

				// Check if the transition is in the post-set of a selected place
				Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = inEdgeMap
						.get(activityId);
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : inEdges) {
					PetrinetNode source = edge.getSource();
					if (source instanceof Place) {
						if (selectedNodes.contains(source)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	private static boolean isTraceInANDSelection(FilterConfiguration filterConfiguration, XAlignment alignment,
			Map<String, Transition> transitionsLocalId,
			Map<String, Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>>> inEdgeMap) {

		Set<Object> nodesToBeFound = new HashSet<>(filterConfiguration.getSelectedNodes());
		for (XAlignmentMove move : alignment) {
			MoveType moveType = move.getType();

			if (moveType == MoveType.SYNCHRONOUS || moveType == MoveType.MODEL) {
				String activityId = move.getActivityId();
				Transition activityObject = transitionsLocalId.get(activityId);

				// Check if the transition is in the postset of a selected place
				Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = inEdgeMap
						.get(activityId);
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : inEdges) {
					PetrinetNode source = edge.getSource();
					if (source instanceof Place) {
						nodesToBeFound.remove(source);
					}
				}
				nodesToBeFound.remove(activityObject);
			}
		}
		return nodesToBeFound.isEmpty();
	}

	public void filterData() throws NetVisualizationException {
		filter();
	}

	private Set<Attribute> updateChartAttributes(DataPetriNet model, XLog log, Set<String> eventAttributes,
			XAlignedLog alignment, BalancedProcessorConfiguration alignmentConfiguration) {
		Set<Attribute> attributes = new HashSet<>();
		if (alignment != null && alignmentConfiguration != null) {
			for (String attributeKey : alignmentConfiguration.getVariableMapping().keySet()) {
				DataElement variable = model.getVariable(attributeKey);
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_PROCESS, AttributeOccurence.PRE,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_PROCESS, AttributeOccurence.WRITTEN,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_PROCESS, AttributeOccurence.POST,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_LOG, AttributeOccurence.PRE,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_LOG, AttributeOccurence.WRITTEN,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_LOG, AttributeOccurence.POST,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_LOG_INVALID, AttributeOccurence.PRE,
						variable.getType()));
				attributes.add(new Attribute(attributeKey, AttributeOrigin.VARIABLE_LOG_INVALID,
						AttributeOccurence.POST, variable.getType()));
			}
			attributes.add(new Attribute("alignment:fitness", AttributeOrigin.ALIGNMENT_TRACE,
					AttributeOccurence.ANYWHERE, Double.class));
			attributes.add(new Attribute("alignment:movetype", AttributeOrigin.ALIGNMENT_EVENT, AttributeOccurence.POST,
					String.class));
			attributes.add(new Attribute("dataalignment:movetype", AttributeOrigin.ALIGNMENT_EVENT,
					AttributeOccurence.POST, String.class));
		}
		for (String attributeKey : eventAttributes) {
			if (alignment == null || !alignmentConfiguration.getVariableMapping().containsValue(attributeKey)) {
				Class<?> type = attributesTypes.get(attributeKey);
				if (type != null) {
					attributes.add(new Attribute(attributeKey, AttributeOrigin.UNMAPPED_EVENT,
							AttributeOccurence.ANYWHERE, type));
					attributes.add(
							new Attribute(attributeKey, AttributeOrigin.UNMAPPED_EVENT, AttributeOccurence.PRE, type));
					attributes.add(new Attribute(attributeKey, AttributeOrigin.UNMAPPED_EVENT,
							AttributeOccurence.WRITTEN, type));
					attributes.add(
							new Attribute(attributeKey, AttributeOrigin.UNMAPPED_EVENT, AttributeOccurence.POST, type));
				}
			}
		}
		return attributes;
	}

	public List<String> getDictionary() {
		return autoCompleteDictionary;
	}

	public VariableDisplayMode getVariableDisplayMode() {
		return variableDisplayMode;
	}

	public void setVariableDisplayMode(VariableDisplayMode variableDisplayMode) {
		this.variableDisplayMode = variableDisplayMode;
	}

	public GuardDisplayMode getGuardDisplayMode() {
		return guardDisplayMode;
	}

	public void setGuardDisplayMode(GuardDisplayMode guardDisplayMode) {
		this.guardDisplayMode = guardDisplayMode;
	}

	public void setPlaceDisplayMode(PlaceDisplayMode placeDisplayMode) {
		this.placeDisplayMode = placeDisplayMode;
	}

	public PlaceDisplayMode getPlaceDisplayMode() {
		return placeDisplayMode;
	}

	public Collection<XAlignment> getSelectedAlignments() {
		return selectedAlignments;
	}

	public void setSelectedAlignments(Collection<XAlignment> selectedAlignments) {
		this.selectedAlignments = selectedAlignments;
	}

	public void setDiscoveryResult(DiscoveryResult result) {
		this.discoveryResult = result;
		discoveryDirty = true;
	}

	public DiscoveryResult getDiscoveryResult() {
		return discoveryResult;
	}

	public void resetDiscoveryResult() {
		discoveryResult = null;
		discoveryDirty = true;
	}

	public boolean isDiscoveryResultDirty() {
		return discoveryDirty;
	}

	public XEventClassifier getEventClassifier() {
		return eventClassifier;
	}

	public void setEventClassifier(XEventClassifier eventClassifier) {
		if (!this.eventClassifier.equals(eventClassifier)) {
			this.eventClassifier = eventClassifier;
			updateLogDataStructures(this.log);
		} else {
			this.eventClassifier = eventClassifier;
		}
	}

	public boolean isStandardAttribute(String key) {
		return (XConceptExtension.KEY_NAME.equals(key) || XTimeExtension.KEY_TIMESTAMP.equals(key)
				|| XLifecycleExtension.KEY_TRANSITION.equals(key));
	}

	public void setLiteralValues(Map<String, Set<String>> literalValues) {
		this.literalValues = literalValues;
	}

	public Map<String, Set<String>> getLiteralValues() {
		return literalValues;
	}

	protected void finalize() throws Throwable {
		pool.shutdown();
		super.finalize();
	}

	public void setDiscoveredWriteOperations(SetMultimap<Transition, String> discoverWriteOperations) {
		this.discoveredWriteOperations = discoverWriteOperations;
	}

	public SetMultimap<Transition, String> getDiscoveredWriteOperations() {
		return discoveredWriteOperations;
	}

	protected float maxLineWidth = 5.0f;
	protected float minLineWidth = 1.0f;
	
	public float getMinLineWidth() {
		return minLineWidth;
	}

	public float getMaxLineWidth() {
		return maxLineWidth;
	}

}