package org.processmining.dataawareexplorer.explorer.work;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.processmining.dataawareexplorer.utils.PetrinetUtils;
import org.processmining.datadiscovery.AbstractDecisionRuleDiscovery;
import org.processmining.datadiscovery.BasicDecisionTreeImpl;
import org.processmining.datadiscovery.BasicDecisionTreeImpl.NoLeafAction;
import org.processmining.datadiscovery.DecisionTreeConfig;
import org.processmining.datadiscovery.OverlappingLocalDecisionTreesImpl;
import org.processmining.datadiscovery.OverlappingPairwiseDecisionTreeImpl;
import org.processmining.datadiscovery.PetrinetDecisionRuleDiscovery;
import org.processmining.datadiscovery.PetrinetDecisionRuleDiscovery.PetrinetDecisionRule;
import org.processmining.datadiscovery.ProjectedEvent;
import org.processmining.datadiscovery.ProjectedLog;
import org.processmining.datadiscovery.ProjectedTrace;
import org.processmining.datadiscovery.RuleDiscoveryException;
import org.processmining.datadiscovery.TrueFalseDecisionTreeImpl;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.weka.WekaUtil;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.exception.NonExistingVariableException;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.log.utils.XUtils;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithDataFactory;
import org.processmining.xesalignmentextension.XAlignmentExtension.MoveType;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;
import org.processmining.xesalignmentextension.XDataAlignmentExtension;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.XDataAlignmentExtensionException;

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

// TODO this should be moved outside of the data aware explorer
public final class DiscoveryProcessor {

	public enum DiscoveryAlgorithm {
		BASIC_TRUE("Decision Tree (default true)"), TRUE_FALSE("Decision Trees (T/F)"), OVERLAPPING_LOCAL(
				"Overlapping Rules"), OVERLAPPING_PAIRWISE(
						"Decision Trees (Pairwise)"), BASIC_FALSE("Decision Tree (default false)");

		private final String desc;

		private DiscoveryAlgorithm(String desc) {
			this.desc = desc;
		}

		public String toString() {
			return desc;
		}
	}

	public interface DiscoveryResult {

		DataPetriNetsWithMarkings getModel();

		Map<Place, PetrinetDecisionRule> getRules();

		/**
		 * Mapping from new transition in the discovered model to old transition
		 */
		BiMap<Transition, Transition> getModelTransitionMapping();

		/**
		 * Mapping from new place in the discovered model to old place
		 */
		BiMap<Place, Place> getModelPlaceMapping();

		DecisionTreeConfig getConfig();

		DiscoveryAlgorithm getAlgorithm();

	}

	public interface ProgressPublisher {
		void onProgress(PetrinetDecisionRule decisionRule);
	}

	public final static class NoOpProgressPublisher implements ProgressPublisher {
		public void onProgress(PetrinetDecisionRule decisionRule) {
		}
	}

	final class ProjectedLogForDiscovery implements ProjectedLog {

		private Iterable<ProjectedTrace> projectedTraces;
		private Map<String, Object> initialValues;

		public ProjectedLogForDiscovery(Iterable<ProjectedTrace> projectedTraces, Map<String, Object> initialValues) {
			this.projectedTraces = projectedTraces;
			this.initialValues = initialValues;
		}

		public Iterator<ProjectedTrace> iterator() {
			return projectedTraces.iterator();
		}

		public Set<String> getAttributes() {
			return initialValues.keySet();
		}

		public Object getInitialValue(String attributeName) {
			return initialValues.get(attributeName);
		}

	}

	final class ProjectedTraceForDiscovery implements ProjectedTrace {

		private final List<ProjectedEvent> eventsForDiscovery;

		private ProjectedTraceForDiscovery(XAlignment alignment, SetMultimap<Transition, String> writtenAttributes,
				Map<String, Transition> transitionsLocalId) {
			this.eventsForDiscovery = new ArrayList<ProjectedEvent>(alignment.size());
			for (XAlignmentMove move : alignment) {
				MoveType type = move.getType();
				if (type != MoveType.LOG) {
					Transition transition = transitionsLocalId.get(move.getActivityId());
					ProjectedEventForDiscovery eventForDiscovery = new ProjectedEventForDiscovery(move,
							writtenAttributes.get(transition), transition);
					eventsForDiscovery.add(eventForDiscovery);
				}
			}
		}

		public Iterator<ProjectedEvent> iterator() {
			return eventsForDiscovery.iterator();
		}

		public Object getAttributeValue(String attributeName) {
			return null;
		}

		public Set<String> getAttributes() {
			return ImmutableSet.of();
		}

	}

	private static final Object NULL = new Object();

	final class ProjectedEventForDiscovery implements ProjectedEvent {

		private final Transition transition;
		private final ImmutableMap<String, Object> attributes;

		private ProjectedEventForDiscovery(XAlignmentMove move, Set<String> writtenAttributes, Transition transition) {
			this.transition = transition;
			Builder<String, Object> attributeBuilder = ImmutableMap.builder();
			XDataAlignmentExtension dae = XDataAlignmentExtension.instance();
			XAttributeMap eventAttributes = move.getEvent().getAttributes();
			for (String key : writtenAttributes) {
				XAttribute attribute = eventAttributes.get(key);
				if (attribute != null) {
					if (isTreatMissingValuesAsNA()) {
						try {
							if (!dae.isMissingAttribute(attribute)) { // we treat missing values as N/A for the discovery
								attributeBuilder.put(key, XUtils.getAttributeValue(attribute));
							} // else do not add results in N/A in discovery
						} catch (XDataAlignmentExtensionException e) {
							throw new RuntimeException("Invalid alignment format!", e);
						}
					} else {
						attributeBuilder.put(key, XUtils.getAttributeValue(attribute));
					}
				} else {
					if (isTreatMissingValuesAsNA()) {
						// Add will result in discovery obtaining a NULL, which is used as special value for missing!
						attributeBuilder.put(key, NULL);
					}
				}
			}
			this.attributes = attributeBuilder.build();
		}

		public Transition getActivity() {
			return transition;
		}

		public Set<String> getAttributes() {
			return attributes.keySet();
		}

		public Object getAttributeValue(String varName) {
			Object value = attributes.get(varName);
			if (value == NULL) {
				return null;
			}
			return value;
		}
	}

	private final DataPetriNetsWithMarkings net;
	private final Map<String, Transition> transitionsLocalId;

	private final Iterable<XAlignment> alignedLog;

	private final Map<String, Class<?>> attributesForDiscoveryWithPreparedNames;
	private final Map<String, Type> attributeTypeMap;

	private boolean isTreatMissingValuesAsNA = true;
	private boolean isAddWriteOperationsNotInGuard = false;

	private final Map<String, Object> initialValues;
	private final Map<String, Set<String>> literalValues;
	private final DiscoveryResult existingResult;

	public DiscoveryProcessor(DataPetriNetsWithMarkings net, Iterable<XAlignment> alignedLog,
			Map<String, Object> initialValues, Map<String, Class<?>> attributesForDiscovery,
			Map<String, Type> attributeTypes, Map<String, Set<String>> literalValues,
			Map<String, Transition> transitionsLocalId, DiscoveryResult oldDiscoveryResult) {
		this.net = net;
		this.alignedLog = alignedLog;
		this.existingResult = oldDiscoveryResult;
		this.attributesForDiscoveryWithPreparedNames = transformToWekaNames(attributesForDiscovery);
		this.transitionsLocalId = transitionsLocalId;
		this.initialValues = initialValues;
		this.attributeTypeMap = attributeTypes;
		this.literalValues = literalValues;
	}

	private Map<String, Class<?>> transformToWekaNames(Map<String, Class<?>> attributesForDiscovery) {
		com.google.common.collect.ImmutableMap.Builder<String, Class<?>> builder = ImmutableMap.builder();
		for (Entry<String, Class<?>> attributeEntry : attributesForDiscovery.entrySet()) {
			//TODO this should be somehow done better!
			// Prepare as it was prepared for WEKA
			String wekaAttribute = AbstractDecisionRuleDiscovery.escapeAttributeName(attributeEntry.getKey());
			// Prepare for use in guards
			String guardEscape = GuardExpression.Factory.transformToVariableIdentifier(wekaAttribute);
			builder.put(guardEscape, attributeEntry.getValue());
		}
		return builder.build(); // throws illegal argument when duplicate variable names are detected
	}

	public DiscoveryResult discover(final DiscoveryAlgorithm algorithm, final DecisionTreeConfig config,
			final SetMultimap<Transition, String> attributesWritten, Set<Place> consideredPlaces,
			final Set<String> consideredAttributes, ProgressPublisher progressPublisher, ExecutorService executor)
			throws RuleDiscoveryException {

		Map<String, Object> initialValuesForConsideredAttributes = filterInitialAttributeByConsidered(
				consideredAttributes);

		ProjectedLog projectedLog = new ProjectedLogForDiscovery(
				Iterables.transform(alignedLog, new Function<XAlignment, ProjectedTrace>() {

					public ProjectedTrace apply(XAlignment alignment) {
						return new ProjectedTraceForDiscovery(alignment, attributesWritten, transitionsLocalId);
					}
				}), initialValuesForConsideredAttributes);

		final PetrinetDecisionRuleDiscovery ruleDiscovery = createRuleDiscovery(algorithm, config, projectedLog);

		Collection<Future<PetrinetDecisionRule>> futureRules = new ArrayList<>();

		for (final Place p : consideredPlaces) {
			Callable<PetrinetDecisionRule> discoveryCallable = new Callable<PetrinetDecisionRule>() {

				public PetrinetDecisionRule call() throws Exception {
					return ruleDiscovery.discoverRulesForPlace(net, p);
				}

			};
			futureRules.add(executor.submit(discoveryCallable));
		}

		Collection<PetrinetDecisionRule> discoveredRules = new ArrayList<>();
		for (Future<PetrinetDecisionRule> future : futureRules) {
			try {
				PetrinetDecisionRule decisionRule = future.get();
				discoveredRules.add(decisionRule);
				progressPublisher.onProgress(decisionRule);
			} catch (InterruptedException e) {
				throw new RuleDiscoveryException(e);
			} catch (ExecutionException e) {
				if (e.getCause() instanceof RuleDiscoveryException
						&& e.getCause().getCause() instanceof StackOverflowError) {
					throw (RuleDiscoveryException) e.getCause();
				} else if (e.getCause() instanceof RuleDiscoveryException) {
					throw (RuleDiscoveryException) e.getCause();
				} else {
					throw new RuleDiscoveryException("An error occured while trying to discover rules!", e);
				}
			}

		}

		if (discoveredRules.size() > 0) {

			PetriNetWithDataFactory discoveredNetFactory = cloneExistingNet();
			final DataPetriNetsWithMarkings newNet = discoveredNetFactory.getRetValue();

			final BiMap<Transition, Transition> old2NewTransitions = createTransitionMapping(discoveredNetFactory);
			final BiMap<Place, Place> old2NewPlaces = createPlaceMapping(discoveredNetFactory);
			final Map<Place, PetrinetDecisionRule> discoveryInfo = new HashMap<>();

			if (existingResult != null) {
				addExistingResult(newNet, old2NewTransitions, old2NewPlaces, discoveryInfo);
			}

			// Copy old variables
			for (DataElement element : net.getVariables()) {
				newNet.addVariable(element.getVarName(), element.getType(), element.getMinValue(),
						element.getMaxValue());
			}

			// Copy old guards & write ops
			for (Transition t : net.getTransitions()) {
				PNWDTransition oldTransition = (PNWDTransition) t;
				PNWDTransition newTransition = (PNWDTransition) old2NewTransitions.get(oldTransition);
				for (DataElement op : oldTransition.getWriteOperations()) {
					newNet.assignWriteOperation(newTransition, newNet.getVariable(op.getVarName()));
				}
				if (oldTransition.getGuardExpression() != null) {
					try {
						newNet.setGuard(newTransition, oldTransition.getGuardExpression());
					} catch (NonExistingVariableException e) {
						throw new RuntimeException("Could not set guard expression!", e);
					}
				}
			}

			// Add new read operations
			for (PetrinetDecisionRule rule : discoveredRules) {
				for (Entry<Transition, FunctionEstimation> ruleEntry : rule.getRulesForTransition().entrySet()) {
					Transition oldTransition = ruleEntry.getKey();
					PNWDTransition discoveredTransition = (PNWDTransition) old2NewTransitions.get(oldTransition);

					// Read operations for normal variables
					GuardExpression guardExpression = ruleEntry.getValue().getExpression();
					assignReadOperations(newNet, discoveredTransition, guardExpression);
				}
			}

			// Mine new write operations uses information about added variables while handling read operations
			for (Transition oldTransition : net.getTransitions()) {
				PNWDTransition discoveredTransition = (PNWDTransition) old2NewTransitions.get(oldTransition);
				//Set the write operations
				Collection<String> variableWrites = attributesWritten.get(oldTransition);
				for (String varName : variableWrites) {
					DataElement dataElem = newNet.getVariable(varName);
					if (dataElem != null) {
						newNet.assignWriteOperation(discoveredTransition, dataElem);
					} else if (isRelevantAttribute(varName) && isAddWriteOperationsNotInGuard()) {
						// Add new variable not in any guard								
						Class<?> type = attributesForDiscoveryWithPreparedNames.get(varName);
						if (type != null) {
							DataElement newVariable = newNet.addVariable(varName, type, null, null);
							newNet.assignWriteOperation(discoveredTransition, newVariable);
						} else {
							throw new IllegalArgumentException(
									"Could not find type information for variable " + varName);
						}
					}
				}
			}

			// Set guards
			for (PetrinetDecisionRule rule : discoveredRules) {
				for (Transition oldTransition : PetrinetUtils.getTransitionPostSet(net, rule.getDecisionPoint())) {
					PNWDTransition discoveredTransition = (PNWDTransition) old2NewTransitions.get(oldTransition);
					FunctionEstimation estimation = rule.getRulesForTransition().get(oldTransition);
					if (estimation != null && !estimation.getExpression().isTrue()) {
						GuardExpression expression = estimation.getExpression();
						try {
							discoveredTransition.setGuard(newNet, expression, estimation.getQualityMeasure());
						} catch (NonExistingVariableException e) {
							throw new RuntimeException("Could not set guard expression!", e);
						}
					} else {
						discoveredTransition.removeGuard();
					}
				}
				discoveryInfo.put(old2NewPlaces.get(rule.getDecisionPoint()), rule);
			}

			return new DiscoveryResult() {

				public Map<Place, PetrinetDecisionRule> getRules() {
					return discoveryInfo;
				}

				public DataPetriNetsWithMarkings getModel() {
					return newNet;
				}

				public BiMap<Transition, Transition> getModelTransitionMapping() {
					return old2NewTransitions.inverse();
				}

				public BiMap<Place, Place> getModelPlaceMapping() {
					return old2NewPlaces.inverse();
				}

				public DecisionTreeConfig getConfig() {
					return config;
				}

				public DiscoveryAlgorithm getAlgorithm() {
					return algorithm;
				}
			};

		} else {
			return existingResult;
		}

	}

	private void addExistingResult(final DataPetriNetsWithMarkings newNet,
			final BiMap<Transition, Transition> old2NewTransitions, final BiMap<Place, Place> old2NewPlaces,
			final Map<Place, PetrinetDecisionRule> discoveryInfo) {
		BiMap<Place, Place> existingPlaceMapping = existingResult.getModelPlaceMapping();
		BiMap<Transition, Transition> existingTransitionMapping = existingResult.getModelTransitionMapping();
		for (Entry<Place, PetrinetDecisionRule> entry : existingResult.getRules().entrySet()) {
			Place originalPlace = existingPlaceMapping.get(entry.getKey());
			discoveryInfo.put(old2NewPlaces.get(originalPlace), entry.getValue());
		}
		for (DataElement variable : existingResult.getModel().getVariables()) {
			newNet.addVariable(variable.getVarName(), variable.getType(), variable.getMinValue(),
					variable.getMaxValue());
		}
		for (Transition t : existingResult.getModel().getTransitions()) {
			if (t instanceof PNWDTransition) {
				PNWDTransition existingTransition = (PNWDTransition) t;
				Transition originalTransition = existingTransitionMapping.get(t);
				Transition newTransition = old2NewTransitions.get(originalTransition);
				for (DataElement writeOp : existingTransition.getWriteOperations()) {
					newNet.assignWriteOperation(newTransition, newNet.getVariable(writeOp.getVarName()));
				}
				GuardExpression guardExpression = existingTransition.getGuardExpression();
				if (guardExpression != null) {
					try {
						newNet.setGuard(newTransition, existingTransition.getGuardExpression());
						assignReadOperations(newNet, newTransition, guardExpression);
					} catch (NonExistingVariableException e) {
						throw new RuntimeException(e); // should not happen
					}
				}
			}
		}
	}

	private void assignReadOperations(final DataPetriNetsWithMarkings newNet, Transition newTransition,
			GuardExpression guardExpression) {
		for (String varName : guardExpression.getNormalVariables()) {
			DataElement variable = newNet.getVariable(varName);
			if (variable == null) {
				Class<?> type = attributesForDiscoveryWithPreparedNames.get(varName);
				if (type != null) {
					variable = newNet.addVariable(varName, type, null, null);
				} else {
					throw new IllegalArgumentException("Could not find type information for variable " + varName);
				}
			}
			newNet.assignReadOperation(newTransition, variable);
		}
	}

	private BiMap<Place, Place> createPlaceMapping(PetriNetWithDataFactory discoveredNetFactory) {
		return HashBiMap.create(discoveredNetFactory.getPlaceMapping());
	}

	private BiMap<Transition, Transition> createTransitionMapping(PetriNetWithDataFactory discoveredNetFactory) {
		return HashBiMap.create(discoveredNetFactory.getTransMapping());
	}

	private PetriNetWithDataFactory cloneExistingNet() {
		return new PetriNetWithDataFactory(net, net.getLabel() + " @" + DateFormat.getTimeInstance().format(new Date()),
				false);
	}

	private Map<String, Object> filterInitialAttributeByConsidered(final Set<String> consideredAttributes) {
		Map<String, Object> initialValuesForConsideredAttributes = Maps
				.newHashMapWithExpectedSize(consideredAttributes.size());
		for (String considered : consideredAttributes) {
			initialValuesForConsideredAttributes.put(considered, initialValues.get(considered));
		}
		return initialValuesForConsideredAttributes;
	}

	private boolean isRelevantAttribute(String key) {
		return !(XConceptExtension.KEY_NAME.equals(key) || XTimeExtension.KEY_TIMESTAMP.equals(key)
				|| XLifecycleExtension.KEY_TRANSITION.equals(key));
	}

	private PetrinetDecisionRuleDiscovery createRuleDiscovery(DiscoveryAlgorithm algorithm,
			final DecisionTreeConfig config, final ProjectedLog alignedLog) {

		switch (algorithm) {

			case BASIC_FALSE :
				BasicDecisionTreeImpl ruleDiscovery = new BasicDecisionTreeImpl(config, alignedLog, attributeTypeMap,
						literalValues, 256);
				ruleDiscovery.setNoLeafAction(NoLeafAction.TREAT_AS_FALSE);
				return ruleDiscovery;

			case BASIC_TRUE :
				BasicDecisionTreeImpl ruleDiscovery1 = new BasicDecisionTreeImpl(config, alignedLog, attributeTypeMap,
						literalValues, 256);
				ruleDiscovery1.setNoLeafAction(NoLeafAction.TREAT_AS_TRUE);
				return ruleDiscovery1;

			case TRUE_FALSE :
				return new TrueFalseDecisionTreeImpl(config, alignedLog, attributeTypeMap, literalValues, 256);

			case OVERLAPPING_PAIRWISE :
				return new OverlappingPairwiseDecisionTreeImpl(config, alignedLog, attributeTypeMap, literalValues,
						256);

			case OVERLAPPING_LOCAL :
				return new OverlappingLocalDecisionTreesImpl(config, alignedLog, attributeTypeMap, literalValues, 256);

			default :
				throw new IllegalArgumentException("Unkown algorithm " + algorithm);

		}
	}

	public boolean isTreatMissingValuesAsNA() {
		return isTreatMissingValuesAsNA;
	}

	public void setTreatMissingValuesAsNA(boolean isTreatMissingValuesAsNA) {
		this.isTreatMissingValuesAsNA = isTreatMissingValuesAsNA;
	}

	public boolean isAddWriteOperationsNotInGuard() {
		return isAddWriteOperationsNotInGuard;
	}

	public void setAddWriteOperationsNotInGuard(boolean isAddWriteOperationsNotInGuard) {
		this.isAddWriteOperationsNotInGuard = isAddWriteOperationsNotInGuard;
	}

	public static SetMultimap<Transition, String> discoverWriteOperations(Iterable<XAlignment> alignedLog,
			Collection<Transition> transitions, Map<String, Transition> transitionsLocalId, double writeThreshold,
			Set<String> consideredAttributes) {

		final Map<Transition, ConcurrentHashMultiset<String>> numOccurenceAttributePerTransition = new HashMap<>();
		for (Transition trans : transitions) {
			numOccurenceAttributePerTransition.put(trans, ConcurrentHashMultiset.<String>create());
		}
		final ConcurrentHashMultiset<Transition> numOccurenceTransition = ConcurrentHashMultiset.create();
		determineFrequencies(alignedLog, numOccurenceAttributePerTransition, numOccurenceTransition,
				consideredAttributes, transitionsLocalId);

		final SetMultimap<Transition, String> attributesWritten = HashMultimap.create();

		// Mine write operations
		for (Transition transition : transitions) {
			//Set the write operations
			long numberOfExecution = numOccurenceTransition.count(transition);
			for (com.google.common.collect.Multiset.Entry<String> numWritesVariable : numOccurenceAttributePerTransition
					.get(transition).entrySet()) {
				if (numWritesVariable.getCount() > (numberOfExecution * writeThreshold)) {
					String varName = numWritesVariable.getElement();
					attributesWritten.put(transition, WekaUtil.replaceNonUriEncodedChars(varName));
				}
			}
		}

		return attributesWritten;
	}

	private static void determineFrequencies(Iterable<XAlignment> alignedLog,
			final Map<Transition, ConcurrentHashMultiset<String>> numOccurenceAttributePerTransition,
			final ConcurrentHashMultiset<Transition> numOccurenceTransition, final Set<String> consideredAttributes,
			final Map<String, Transition> transitionsLocalId) {

		Iterable<List<XAlignment>> partitionedAlignments = Iterables.partition(alignedLog, 10000);
		List<Callable<Void>> callables = new ArrayList<>();
		for (final List<XAlignment> subAlignments : partitionedAlignments) {
			callables.add(new Callable<Void>() {

				public Void call() throws Exception {
					for (XAlignment alignment : subAlignments) {
						for (XAlignmentMove move : alignment) {
							if (move.getType() != MoveType.LOG) {
								Transition transition = transitionsLocalId.get(move.getActivityId());
								numOccurenceTransition.add(transition);
								ConcurrentHashMultiset<String> numOccurenceAttribute = numOccurenceAttributePerTransition
										.get(transition);
								XAttributeMap attributes = move.getEvent().getAttributes();
								for (String key : attributes.keySet()) {
									if (consideredAttributes.contains(key)) {
										numOccurenceAttribute.add(key);
									}
								}
							}
						}
					}
					return null;
				}

			});
		}

		ExecutorService pool = Executors
				.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

		try {
			pool.invokeAll(callables);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		pool.shutdown();

	}

}