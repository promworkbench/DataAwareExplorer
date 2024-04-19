package org.processmining.dataawareexplorer.explorer.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.processmining.dataawareexplorer.explorer.DefaultConfig;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.utils.MarkingPanel;
import org.processmining.dataawareexplorer.utils.PetrinetUtils;
import org.processmining.dataawareexplorer.utils.VariableMappingPanel;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.ui.ImprovedEvClassLogMappingUI;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.DataConformance.DataAlignment.PetriNet.ReplayableTransition;
import org.processmining.plugins.DataConformance.GUI.VariableMatchCostPanel;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.balancedconformance.ui.BalancedAlignmentConfigPanel;
import org.processmining.plugins.balancedconformance.ui.ControlFlowCostUI;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.utils.ConnectionManagerHelper;

import com.google.common.collect.Lists;

abstract public class AbstractAlignmentAction<T> extends AbstractExplorerAction<T> {

	public AbstractAlignmentAction(ExplorerContext explorerContext) {
		super(explorerContext);
	}

	protected void queryCost(PluginContext context, DataPetriNet model, XLog log,
			BalancedProcessorConfiguration config) {

		// populate event classes
		XEventClassifier classifier = config.getActivityMapping().getEventClassifier();
		XLogInfo info = log.getInfo(classifier);
		if (info == null) {
			info = XLogInfoFactory.createLogInfo(log, classifier);
		}
		XEventClasses eventClassesName = info.getEventClasses();
		List<XEventClass> evClasses = new ArrayList<>(eventClassesName.getClasses());
		evClasses.add(config.getActivityMapping().getDummyEventClass());
		ArrayList<Transition> transitionList = Lists.newArrayList(model.getTransitions());
		Collections.sort(evClasses, new Comparator<XEventClass>() {

			public int compare(XEventClass o1, XEventClass o2) {
				return o1.getId().compareTo(o2.getId());
			}
		});
		Collections.sort(transitionList, new Comparator<Transition>() {

			public int compare(Transition o1, Transition o2) {
				return o1.getLabel().compareTo(o2.getLabel());
			}
		});

		ControlFlowCostUI controlFlowCostUI = new ControlFlowCostUI(transitionList, evClasses,
				config.getActivityMapping());

		getExplorerContext().getUserQuery().queryCustom("Specify Cost of Deviations in Control-Flow Perspective",
				controlFlowCostUI, new String[] { "Confirm Control-Flow Cost" });
		config.setMapEvClass2Cost(controlFlowCostUI.getMapEvClassToCost());
		config.setMapTrans2Cost(controlFlowCostUI.getTransitionWeight());

		HashMap<ReplayableTransition, XEventClass> activityMapping2 = new HashMap<>();
		for (Entry<Transition, XEventClass> entry : config.getActivityMapping().entrySet()) {
			activityMapping2.put(new ReplayableTransition(entry.getKey()), entry.getValue());
		}

		if (!model.getVariables().isEmpty()) {
			VariableMatchCostPanel<XEventClass> variablePanel = new VariableMatchCostPanel<>(activityMapping2,
					config.getVariableMapping());
			getExplorerContext().getUserQuery().queryCustom("Specify Costs of Deviations in Data Perspective",
					variablePanel, new String[] { "Confirm Data Cost" });
			config.setVariableCost(variablePanel.getCosts());
		} else {
			VariableMatchCosts variableCost = BalancedProcessorConfiguration.createDefaultVariableCost(model,
					Collections.<String>emptySet(), 0, 0);
			config.setVariableCost(variableCost);
		}
	}

	protected boolean hasUnmappedActivity(DataPetriNet currentNet, BalancedProcessorConfiguration config) {
		for (Transition t : currentNet.getTransitions()) {
			if (!t.isInvisible()
					&& config.getActivityMapping().get(t) == config.getActivityMapping().getDummyEventClass()) {
				return true;
			}
		}
		return false;
	}

	protected boolean hasUnmappedVariable(DataPetriNet currentNet, BalancedProcessorConfiguration config) {
		for (DataElement e : currentNet.getVariables()) {
			if (config.getVariableMapping().get(e.getVarName()) == null) {
				return true;
			}
		}
		return false;
	}

	protected Map<String, String> queryVariableMapping(PluginContext context, DataPetriNet net, Set<String> attributeKeys) {
		TreeSet<String> processAttributes = new TreeSet<>();
		for (DataElement variable : net.getVariables()) {
			processAttributes.add(variable.getVarName());
		}

		VariableMappingPanel mapVariablePanel = new VariableMappingPanel(processAttributes, attributeKeys);
		mapVariablePanel.setMinimumSize(null);
		mapVariablePanel.setMaximumSize(null);
		mapVariablePanel.setPreferredSize(null);
		getExplorerContext().getUserQuery().queryCustom("Review Mapping between Variables and Attributes",
				mapVariablePanel, new String[] { "Confirm Variable-Attribute Mapping" });
		return mapVariablePanel.getMapping(true);
	}

	protected void queryBalancedConformanceConfig(BalancedProcessorConfiguration config) {
		BalancedAlignmentConfigPanel balancedAlignmentConfigPanel = new BalancedAlignmentConfigPanel();
		getExplorerContext().getUserQuery().queryCustom("Performance & Alignment-related Parameters",
				balancedAlignmentConfigPanel, new String[] { "Confirm Parameters" });
		config.setActivateDataViewCache(balancedAlignmentConfigPanel.isDataViewCacheActivated());
		config.setUseOptimizations(balancedAlignmentConfigPanel.getIsUseOptimizations());
		config.setConcurrentThreads(balancedAlignmentConfigPanel.getConcurrentThreads());
		config.setSorting(balancedAlignmentConfigPanel.getSorting());
		config.setIlpSolver(balancedAlignmentConfigPanel.getILPSolver());
		config.setUsePartialDataAlignments(balancedAlignmentConfigPanel.getIsUsePartialDataAlignments());
		config.setVariablesUnassignedMode(balancedAlignmentConfigPanel.getUnassignedMode());
		//config.setQueueingModel(DataConformanceConfigUIUtils.createQueueFactory(
		//		balancedAlignmentConfigPanel.getQueueingModel(), balancedAlignmentConfigPanel.getSearchMethod()));
		config.setMaxCostFactor(balancedAlignmentConfigPanel.getMaxCost());
		config.setKeepControlFlowSearchSpace(balancedAlignmentConfigPanel.getIsKeepControlFlowSearchSpace());
		config.setKeepDataFlowSearchSpace(balancedAlignmentConfigPanel.getIsKeepDataFlowSearchSpace());
		config.setSearchMethod(balancedAlignmentConfigPanel.getSearchMethod());
		config.setMaxQueuedStates(balancedAlignmentConfigPanel.getMaxQueuedStates());
	}

	protected TransEvClassMapping queryMapping(final DataPetriNet currentNet, final XLog log) {
		Object[] availableEventClass = getStandardAndLogEventClassifiers(log);
		ImprovedEvClassLogMappingUI mappingPanel = new ImprovedEvClassLogMappingUI(log, currentNet,
				availableEventClass);
		mappingPanel.setPreferredSize(null);
		getExplorerContext().getUserQuery().queryCustom("Review Mapping between Events and Activities", mappingPanel,
				new String[] { "Confirm Event-Activity Mapping" });
		return mappingPanel.getMap();
	}

	protected Marking[] queryFinalMarkings(final DataPetriNet currentNet) {
		if (currentNet instanceof DataPetriNetsWithMarkings
				&& hasFinalMarkings(((DataPetriNetsWithMarkings) currentNet))) {
			return ((DataPetriNetsWithMarkings) currentNet).getFinalMarkings();
		}
		Marking[] finalMarkings;
		try {
			finalMarkings = new Marking[] { ConnectionManagerHelper
					.safeGetFirstConnection(getContext().getConnectionManager(), FinalMarkingConnection.class,
							currentNet)
					.getObjectWithRole(FinalMarkingConnection.MARKING) };
			if (finalMarkings.length != 1 || finalMarkings[0].isEmpty()) {
				Marking finalMarking = guessOrAskFinal(currentNet);
				getContext().getProvidedObjectManager()
						.createProvidedObject("Final Marking for " + currentNet.getLabel(), finalMarking, getContext());
				getContext().getConnectionManager().addConnection(new FinalMarkingConnection(currentNet, finalMarking));
				finalMarkings = new Marking[] { finalMarking };
			}
		} catch (ConnectionCannotBeObtained e1) {
			Marking finalMarking = guessOrAskFinal(currentNet);
			getContext().getProvidedObjectManager().createProvidedObject("Final Marking for " + currentNet.getLabel(),
					finalMarking, getContext());
			getContext().getConnectionManager().addConnection(new FinalMarkingConnection(currentNet, finalMarking));
			finalMarkings = new Marking[] { finalMarking };
		}
		return finalMarkings;
	}

	private boolean hasFinalMarkings(final DataPetriNetsWithMarkings currentNet) {
		//TODO use MarkingsHelper from DPN
		return currentNet.getFinalMarkings() != null && currentNet.getFinalMarkings().length > 0
				&& !(currentNet.getFinalMarkings()[0].isEmpty());
	}

	private boolean hasInitialMarking(final DataPetriNetsWithMarkings currentNet) {
		//TODO use MarkingsHelper from DPN
		return currentNet.getInitialMarking() != null && !currentNet.getInitialMarking().isEmpty();
	}

	private Marking guessOrAskFinal(final PetrinetGraph currentNet) {
		Marking finalMarking = PetrinetUtils.guessFinalMarking(currentNet);
		if (finalMarking == null) {
			finalMarking = queryUserFinalMarking(currentNet, finalMarking);
		}
		return finalMarking;
	}

	private Marking queryUserFinalMarking(final PetrinetGraph currentNet, Marking finalMarking) {
		MarkingPanel markingPanel = new MarkingPanel("Final Marking", currentNet, DefaultConfig.SINK_PLACES);
		getExplorerContext().getUserQuery().queryCustom("Final Marking", markingPanel,
				new String[] { "Confirm Final Marking" });
		return markingPanel.getMarking();
	}

	protected Marking queryInitialMarking(final DataPetriNet currentNet) {
		if (currentNet instanceof DataPetriNetsWithMarkings
				&& hasInitialMarking(((DataPetriNetsWithMarkings) currentNet))) {
			return ((DataPetriNetsWithMarkings) currentNet).getInitialMarking();
		}
		Marking initialMarking;
		try {
			initialMarking = ConnectionManagerHelper.safeGetFirstConnection(getContext().getConnectionManager(),
					InitialMarkingConnection.class, currentNet).getObjectWithRole(InitialMarkingConnection.MARKING);
			if (initialMarking.isEmpty()) {
				initialMarking = guessOrQueryInitalMarking(currentNet);
				getContext().getProvidedObjectManager().createProvidedObject(
						"Initial Marking for " + currentNet.getLabel(), initialMarking, getContext());
				getContext().getConnectionManager()
						.addConnection(new InitialMarkingConnection(currentNet, initialMarking));
			}
		} catch (ConnectionCannotBeObtained e1) {
			initialMarking = guessOrQueryInitalMarking(currentNet);
			getContext().getProvidedObjectManager().createProvidedObject("Initial Marking for " + currentNet.getLabel(),
					initialMarking, getContext());
			getContext().getConnectionManager().addConnection(new InitialMarkingConnection(currentNet, initialMarking));
		}
		return initialMarking;
	}

	private Marking guessOrQueryInitalMarking(final PetrinetGraph currentNet) {
		Marking initialMarking;
		initialMarking = PetrinetUtils.guessInitialMarking(currentNet);
		if (initialMarking == null) {
			initialMarking = queryUserInitialMarking(currentNet);
		}
		return initialMarking;
	}

	private Marking queryUserInitialMarking(final PetrinetGraph currentNet) {
		MarkingPanel markingPanel = new MarkingPanel("Initial Marking", currentNet, DefaultConfig.SOURCE_PLACES);
		getExplorerContext().getUserQuery().queryCustom("Specify Initial Marking", markingPanel,
				new String[] { "Confirm Initial Marking" });
		return markingPanel.getMarking();
	}

	private XEventClassifier[] getStandardAndLogEventClassifiers(final XLog log) {
		List<XEventClassifier> classList = new ArrayList<>(log.getClassifiers());
		if (!classList.contains(XLogInfoImpl.RESOURCE_CLASSIFIER)) {
			classList.add(XLogInfoImpl.RESOURCE_CLASSIFIER);
		}
		if (!classList.contains(XLogInfoImpl.STANDARD_CLASSIFIER)) {
			classList.add(XLogInfoImpl.STANDARD_CLASSIFIER);
		}
		if (!classList.contains(XLogInfoImpl.NAME_CLASSIFIER)) {
			classList.add(0, XLogInfoImpl.NAME_CLASSIFIER);
		}
		return classList.toArray(new XEventClassifier[classList.size()]);
	}

}