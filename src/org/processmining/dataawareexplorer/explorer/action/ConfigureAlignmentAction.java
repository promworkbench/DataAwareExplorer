package org.processmining.dataawareexplorer.explorer.action;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;

import javax.swing.JLabel;

import org.deckfour.xes.model.XLog;
import org.processmining.dataawareexplorer.explorer.DefaultConfig;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.QueryResult;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration.UnassignedMode;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;

abstract public class ConfigureAlignmentAction extends AbstractAlignmentAction<BalancedProcessorConfiguration> {

	private final ExplorerModel explorerModel;

	protected ConfigureAlignmentAction(ExplorerContext context, ExplorerModel explorerModel) {
		super(context);
		this.explorerModel = explorerModel;
	}

	public void execute() {

		final DataPetriNet currentNet = explorerModel.getModel();
		final XLog log = explorerModel.getLog();

		onBeforeAction();

		final Marking initialMarking = queryInitialMarking(explorerModel.getModel());
		final Marking[] finalMarkings = queryFinalMarkings(explorerModel.getModel());

		BalancedProcessorConfiguration config = BalancedProcessorConfiguration.newDefaultInstance(currentNet,
				initialMarking, finalMarkings, log, explorerModel.getEventClassifier(), DefaultConfig.MODEL_MOVE_COST,
				DefaultConfig.LOG_MOVE_COST, DefaultConfig.MISSING_COST, DefaultConfig.INCORRECT_COST);
		config.setVariablesUnassignedMode(UnassignedMode.NULL);

		queryBalancedConformanceConfig(config);

		TransEvClassMapping activityMapping = queryMapping(currentNet, log);
		config.setActivityMapping(activityMapping);

		if (!activityMapping.getEventClassifier().equals(explorerModel.getEventClassifier())) {
			explorerModel.setEventClassifier(activityMapping.getEventClassifier());
			// Re-compute default costs for log moves with new classifier
			config.setMapEvClass2Cost(BalancedProcessorConfiguration
					.createDefaultLogMoveCost(explorerModel.getEventClasses(), DefaultConfig.LOG_MOVE_COST));
		}

		if (!currentNet.getVariables().isEmpty()) {
			Map<String, String> variableMapping = queryVariableMapping(getContext(), currentNet,
					explorerModel.getLogAttributes());
			config.setVariableMapping(variableMapping);
		} else {
			config.setVariableMapping(Collections.<String, String>emptyMap());
		}

		String text = "<html><h2>Do you want to use standard cost or specify custom costs for deviations?</h2></br>"
				+ "Standard costs are:" + "<ul>" + "<li>Missing event (log move): {0}</li>"
				+ "<li>Missing activity (model move): {1}</li>" + "<li>Incorrect data: {2}</li>"
				+ "<li>Missing data: {3}</li>" + "</ul>" + "</html>";
		JLabel queryLabel = new JLabel(MessageFormat.format(text, DefaultConfig.LOG_MOVE_COST,
				DefaultConfig.MODEL_MOVE_COST, DefaultConfig.INCORRECT_COST, DefaultConfig.MISSING_COST));
		QueryResult result = getExplorerContext().getUserQuery().queryCustom("Custom costs for deviations?", queryLabel,
				new String[] { "Standard", "Custom" });
		if (result.getCustom() == 1) {
			queryCost(getContext(), currentNet, log, config);
		}

		onAfterAction(config);

	}

}
