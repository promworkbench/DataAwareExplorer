package org.processmining.dataawareexplorer.explorer.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.processmining.dataawareexplorer.explorer.DefaultConfig;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryResult;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithData;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;

public final class AlignmentUtil {

	private AlignmentUtil() {
	}

	/**
	 * Create an alignment configuration for the newly discovered model
	 * 
	 * @param discoveryResult
	 * @return
	 */
	public static BalancedProcessorConfiguration createAlignmentConfig(DiscoveryResult discoveryResult,
			ExplorerModel oldModel) {

		// copy old configuration
		BalancedProcessorConfiguration newAlignmentConfig = new BalancedProcessorConfiguration(
				oldModel.getAlignmentConfiguration());

		// Update variables
		Set<String> variableNames = PetriNetWithData.getAllVariableNames(discoveryResult.getModel());
		Map<String, String> variableMapping = BalancedProcessorConfiguration.createDefaultVariableMapping(variableNames,
				oldModel.getLogAttributes());
		
		newAlignmentConfig.setVariableMapping(variableMapping);
		VariableMatchCosts variableCost = BalancedProcessorConfiguration.createDefaultVariableCost(
				discoveryResult.getModel(), variableNames, DefaultConfig.MISSING_COST, DefaultConfig.INCORRECT_COST);
		newAlignmentConfig.setVariableCost(variableCost);

		Marking newInitialMarking = new Marking();
		for (Place p : newAlignmentConfig.getInitialMarking()) {
			newInitialMarking.add(discoveryResult.getModelPlaceMapping().inverse().get(p));
		}
		newAlignmentConfig.setInitialMarking(newInitialMarking);

		Marking[] newFinalMarkings = new Marking[newAlignmentConfig.getFinalMarkings().length];
		int i = 0;
		for (Marking marking : newAlignmentConfig.getFinalMarkings()) {
			newFinalMarkings[i] = new Marking();
			for (Place p : marking) {
				newFinalMarkings[i].add(discoveryResult.getModelPlaceMapping().inverse().get(p));
			}
			i++;
		}
		newAlignmentConfig.setFinalMarkings(newFinalMarkings);

		TransEvClassMapping activityMapping = new TransEvClassMapping(
				newAlignmentConfig.getActivityMapping().getEventClassifier(),
				newAlignmentConfig.getActivityMapping().getDummyEventClass());
		for (Entry<Transition, XEventClass> entry : newAlignmentConfig.getActivityMapping().entrySet()) {
			activityMapping.put(discoveryResult.getModelTransitionMapping().inverse().get(entry.getKey()),
					entry.getValue());
		}
		newAlignmentConfig.setActivityMapping(activityMapping);

		Map<Transition, Integer> newTrans2Cost = new HashMap<Transition, Integer>();
		for (Entry<Transition, Integer> entry : newAlignmentConfig.getMapTrans2Cost().entrySet()) {
			newTrans2Cost.put(discoveryResult.getModelTransitionMapping().inverse().get(entry.getKey()),
					entry.getValue());
		}
		newAlignmentConfig.setMapTrans2Cost(newTrans2Cost);

		return newAlignmentConfig;

	}

}
