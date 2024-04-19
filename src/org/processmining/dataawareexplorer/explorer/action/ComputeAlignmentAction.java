/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.dataawareexplorer.explorer.DefaultConfig;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.framework.plugin.Progress;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.balancedconformance.BalancedDataXAlignmentPlugin;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration.UnassignedMode;
import org.processmining.plugins.balancedconformance.observer.DataConformancePlusObserver.ImpossibleTrace;
import org.processmining.plugins.balancedconformance.observer.DataConformancePlusObserverNoOpImpl;
import org.processmining.plugins.balancedconformance.result.DataAlignedTrace;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.xesalignmentextension.XAlignmentExtension;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignedLog;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

abstract public class ComputeAlignmentAction extends AbstractAlignmentAction<XAlignedLog> {

	private final ExplorerModel explorerModel;
	private final Progress progressListener;

	public ComputeAlignmentAction(ExplorerContext context, ExplorerModel explorerModel, Progress progressListener) {
		super(context);
		this.progressListener = progressListener;
		this.explorerModel = explorerModel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.dataawareexplorer.explorer.action.ExplorerAction#
	 * execute ()
	 */
	public void execute() {

		if (explorerModel.hasLog()) {

			final DataPetriNet currentNet = explorerModel.getModel();
			final XLog log = explorerModel.getLog();

			if (explorerModel.getAlignmentConfiguration() == null) {

				final Marking initialMarking = queryInitialMarking(currentNet);
				final Marking[] finalMarkings = queryFinalMarkings(currentNet);

				BalancedProcessorConfiguration config = BalancedProcessorConfiguration.newDefaultInstance(currentNet,
						initialMarking, finalMarkings, log, explorerModel.getEventClassifier(),
						DefaultConfig.MODEL_MOVE_COST, DefaultConfig.LOG_MOVE_COST, DefaultConfig.MISSING_COST,
						DefaultConfig.INCORRECT_COST);

				// Default use half of the cores, being defensive wrt. memory consumption
				config.setConcurrentThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

				if (hasUnmappedActivity(currentNet, config)) {
					TransEvClassMapping activityMapping = queryMapping(currentNet, log);

					explorerModel.setEventClassifier(activityMapping.getEventClassifier());
					// Re-compute default costs for log moves with new classifier
					config.setMapEvClass2Cost(BalancedProcessorConfiguration
							.createDefaultLogMoveCost(explorerModel.getEventClasses(), DefaultConfig.LOG_MOVE_COST));
					config.setActivityMapping(activityMapping);
				}

				config.setVariablesUnassignedMode(UnassignedMode.NULL);

				if (hasUnmappedVariable(currentNet, config)) {
					Map<String, String> variableMapping = queryVariableMapping(getContext(), currentNet,
							explorerModel.getLogAttributes());
					config.setVariableMapping(variableMapping);
				}

				// Randomize by default
				/*
				 * config.setQueueFactory(new PriorityQueueFactory() {
				 * 
				 * public FastLookupPriorityQueue newInstance() { return new
				 * RandomDataPriorityQueue(2048); } });
				 */

				explorerModel.setAlignmentConfiguration(config);
			}

			final int reportWindow = Math.max(log.size() / 25, 10);
			progressListener.setCaption("Computing ...");
			progressListener.setIndeterminate(false);
			progressListener.setMinimum(0);
			progressListener.setValue(0);
			progressListener.setMaximum((log.size() / reportWindow) + 2);

			final Collection<ImpossibleTrace> impossibleAlignments = new ArrayList<>();
			explorerModel.getAlignmentConfiguration().setObserver(new DataConformancePlusObserverNoOpImpl() {

				public void foundOptimalAlignment(int traceIndex, XTrace trace, DataAlignedTrace dataAlignment,
						int partialDataAlignmentsNeeded, int cacheHit, int cacheSize, long queuedStates,
						long dataStateCount, long usedTime) {
					if (traceIndex % reportWindow == 0) {
						progressListener.inc();
					}
				}

				public void foundImpossibleAlignments(Collection<ImpossibleTrace> impossibleTraces) {
					impossibleAlignments.addAll(impossibleTraces);
				}

			});

			onBeforeAction();

			final BalancedDataXAlignmentPlugin alignmentPlugin = new BalancedDataXAlignmentPlugin();		

			getExecutor().execute(new SwingWorker<XAlignedLog, Void>() {

				protected XAlignedLog doInBackground() throws Exception {

					// Auto guess bounds, TODO only do that when something changed 
					BalancedProcessorConfiguration.autoGuessBounds(explorerModel.getAlignmentConfiguration(),
							currentNet, log);

					XLog alignmentResult = alignmentPlugin.alignLog(progressListener, currentNet, log,
							explorerModel.getAlignmentConfiguration());
					XAlignedLog alignedLog = XAlignmentExtension.instance().extendLog(alignmentResult);

					explorerModel.setAlignment(alignedLog);

					return alignedLog;
				}

				protected void done() {
					try {
						onAfterAction(get());
						if (!impossibleAlignments.isEmpty()) {
							String errorMessage = "No alignment could be computed for the following traces. Please check whether the model is sound. Showing first 10 errors:\n\n"
									+ Joiner.on("\n\n").join(Iterables.limit(impossibleAlignments, 5));
							String errorTitle = String.format("Alignment failed for %s traces",
									impossibleAlignments.size());
							getExplorerContext().getUserQuery().showWarning(errorMessage, errorTitle);
						}
					} catch (ExecutionException e) {
						onError("Error computing alignment", e);
					} catch (InterruptedException e) {
						onError("Error computing alignment", e);
					}
				}

			});
		} else {
			onError("Please select an event log!", null);
		}

	}

}