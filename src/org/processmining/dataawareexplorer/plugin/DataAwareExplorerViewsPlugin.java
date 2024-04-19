/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.plugin;

import java.awt.Dialog.ModalityType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JComponent;

import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.dataawareexplorer.explorer.DefaultConfig;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.NetVisualizationPanel;
import org.processmining.dataawareexplorer.explorer.events.ExplorerEvent;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration;
import org.processmining.dataawareexplorer.explorer.netview.NetView;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.utils.PetrinetUtils;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginCategory;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.util.ui.widgets.helper.UserCancelledException;
import org.processmining.logenhancement.utils.LogEnhancementHelper;
import org.processmining.logenhancement.view.LogViewContext;
import org.processmining.logenhancement.view.LogViewContextAbstract;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithDataFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.balancedconformance.BalancedDataXAlignmentPlugin;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration.UnassignedMode;
import org.processmining.plugins.balancedconformance.controlflow.ControlFlowAlignmentException;
import org.processmining.plugins.balancedconformance.dataflow.exception.DataAlignmentException;
import org.processmining.plugins.graphviz.dot.Dot;
import org.processmining.xesalignmentextension.XAlignmentExtension;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignedLog;
import org.xeslite.external.XFactoryExternalStore;

import com.google.common.eventbus.EventBus;

public class DataAwareExplorerViewsPlugin {

	private static final class ExplorerContextHeadlessImpl implements ExplorerContext {

		private final ExplorerInterface userQuery;
		private final PluginContext context;
		private XFactory factory = new XFactoryExternalStore.InMemoryStoreAlignmentAwareImpl();

		private ExplorerContextHeadlessImpl(PluginContext context, ExplorerInterface userQuery) {
			this.context = context;
			this.userQuery = userQuery;
		}

		public XFactory getFactory() {
			return factory;
		}

		public ExecutorService getExecutor() {
			return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		}

		public ExplorerInterface getUserQuery() {
			return userQuery;
		}

		public LogViewContext getLogViewContext() {
			return new LogViewContextAbstract() {

				public void exportLog(XLog log) {
				}
			};
		}

		public PluginContext getContext() {
			return context;
		}

	}

	private static final class ExplorerInterfaceHeadlessImpl implements ExplorerInterface {
		public void showError(String errorMessage, Exception e) {
			System.out.println(errorMessage);
			e.printStackTrace();
		}

		public QueryResult queryYesNo(String queryTitle, JComponent queryComponent) {
			return new StandardQueryResult(ResultOption.NO); // we don't want to specifiy anything extra
		}

		public QueryResult queryOkCancel(String queryTitle, JComponent queryComponent) {
			return new StandardQueryResult(ResultOption.OK);
		}

		public QueryResult queryCustom(String queryTitle, JComponent queryComponent, String[] options) {
			return new CustomQueryResult(0); // first option is confirm
		}

		public void showWarning(String warningMessage, String warningTitle) {
			System.out.println(warningMessage);
		}

		public void showMessage(String message, String title) {
			System.out.println(message);
		}

		public void showError(String errorMessage, String errorTitle, Exception e) {
			System.out.println(errorMessage);
			e.printStackTrace();
		}

		public String queryString(String query, String initialLabel) {
			return "";
		}

		public void showCustom(JComponent component, String dialogTitle, ModalityType modalityType) {
		}
	}

	private static final class ExplorerUpdaterNoOpImpl implements ExplorerUpdater {

		private EventBus eventBus = new EventBus("explorer");

		public EventBus getEventBus() {
			return eventBus;
		}

		public void post(ExplorerEvent event) {
			eventBus.post(event);
		}

	}

	@Plugin(name = "Multi-perspective Process Explorer - Performance View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "performance",
									"DPN", "data",
									"alignment" }, help = "Performance view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot explorePerformance(final UIPluginContext context, Petrinet net, XLog log)
			throws NetVisualizationException, ControlFlowAlignmentException, DataAlignmentException,
			UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.PERFORMANCE, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	@Plugin(name = "Multi-perspective Process Explorer - Performance View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "performance",
									"DPN", "data",
									"alignment" }, help = "Performance view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot explorePerformance(final UIPluginContext context, DataPetriNet net, XLog log)
			throws NetVisualizationException, ControlFlowAlignmentException, DataAlignmentException,
			UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.PERFORMANCE, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	@Plugin(name = "Multi-perspective Process Explorer - Fitness View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "conformance",
									"DPN", "data",
									"alignment" }, help = "Fitness view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot exploreFitness(final UIPluginContext context, Petrinet net, XLog log) throws NetVisualizationException,
			ControlFlowAlignmentException, DataAlignmentException, UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.FITNESS, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	@Plugin(name = "Multi-perspective Process Explorer - Fitness View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "conformance",
									"DPN", "data",
									"alignment" }, help = "Fitness view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot exploreFitness(final UIPluginContext context, DataPetriNet net, XLog log)
			throws NetVisualizationException, ControlFlowAlignmentException, DataAlignmentException,
			UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.FITNESS, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	@Plugin(name = "Multi-perspective Process Explorer - Precision View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "precision",
									"DPN", "data",
									"alignment" }, help = "Precision view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot explorePrecision(final UIPluginContext context, Petrinet net, XLog log) throws NetVisualizationException,
			ControlFlowAlignmentException, DataAlignmentException, UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.PRECISION, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	@Plugin(name = "Multi-perspective Process Explorer - Precision View", level = PluginLevel.Regular, parameterLabels = {
			"Petri Net With Data", "Log" }, returnLabels = { "Performance View" }, returnTypes = {
					Dot.class }, userAccessible = true, handlesCancel = true, categories = { PluginCategory.Analytics,
							PluginCategory.Discovery, PluginCategory.ConformanceChecking }, keywords = { "precision",
									"DPN", "data",
									"alignment" }, help = "Precision view of the Multi-perspective Process Explorer without the interactive user interface. "
											+ "The view is returned in the DOT format, which can be visualized in ProM or with GraphViz.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public Dot explorePrecision(final UIPluginContext context, DataPetriNet net, XLog log)
			throws NetVisualizationException, ControlFlowAlignmentException, DataAlignmentException,
			UserCancelledException {
		return getExplorerView(context, net, log, ViewMode.PRECISION, new FilterConfiguration(),
				LogEnhancementHelper.queryEventClassifier(context, log));
	}

	public Dot getExplorerView(final PluginContext context, PetrinetGraph net, XLog log, ViewMode viewMode,
			FilterConfiguration filterConfiguration, XEventClassifier classifier)
			throws NetVisualizationException, ControlFlowAlignmentException, DataAlignmentException {
		DataPetriNetsWithMarkings dpn = wrapPetrinet(net);
		final ExplorerModel explorerModel = new ExplorerModel(log, dpn);
		explorerModel.setEventClassifier(classifier);
		explorerModel.setFilterConfiguration(filterConfiguration);
		ExplorerUpdater updatableExplorer = new ExplorerUpdaterNoOpImpl();
		ExplorerInterface userQuery = new ExplorerInterfaceHeadlessImpl();
		ExplorerContext explorerContext = new ExplorerContextHeadlessImpl(context, userQuery);
		computeAlignment(context, explorerModel, explorerContext);
		context.log("Computing visualization ...");
		explorerModel.filter();
		NetView performanceView = createNetView(viewMode, explorerModel, updatableExplorer, explorerContext);
		return createDot(explorerModel, explorerContext, updatableExplorer, performanceView);
	}

	private Dot createDot(final ExplorerModel explorerModel, ExplorerContext explorerContext, ExplorerUpdater updatableExplorer,
			NetView performanceView) {
		NetVisualizationPanel visualization = new NetVisualizationPanel(updatableExplorer, explorerContext, explorerModel);
		visualization.updateData(performanceView.getModelDecorationData());
		return visualization.getDpnAsDot().getDot();
	}

	private NetView createNetView(ViewMode viewMode, final ExplorerModel explorerModel,
			ExplorerUpdater updatableExplorer, ExplorerContext explorerContext) throws NetVisualizationException {
		NetView performanceView = viewMode.getViewFactory().newInstance(explorerContext, updatableExplorer,
				explorerModel);
		performanceView.updateData();
		return performanceView;
	}

	private void computeAlignment(final PluginContext context, final ExplorerModel explorerModel,
			ExplorerContext explorerContext) throws ControlFlowAlignmentException, DataAlignmentException {
		context.log("Computing alignment ...");

		final Marking initialMarking = PetrinetUtils.guessInitialMarking(explorerModel.getModel());
		final Marking finalMarking = PetrinetUtils.guessFinalMarking(explorerModel.getModel());

		if (initialMarking == null || finalMarking == null) {
			throw new IllegalArgumentException("Could not determine markings!");
		}

		BalancedProcessorConfiguration config = BalancedProcessorConfiguration.newDefaultInstance(
				explorerModel.getModel(), initialMarking, new Marking[] { finalMarking }, explorerModel.getLog(),
				explorerModel.getEventClassifier(), DefaultConfig.MODEL_MOVE_COST, DefaultConfig.LOG_MOVE_COST,
				DefaultConfig.MISSING_COST, DefaultConfig.INCORRECT_COST);

		config.setVariablesUnassignedMode(UnassignedMode.NULL);

		// Auto guess bounds, TODO only do that when something changed 
		BalancedProcessorConfiguration.autoGuessBounds(config, explorerModel.getModel(), explorerModel.getLog());

		explorerModel.setAlignmentConfiguration(config);

		final BalancedDataXAlignmentPlugin alignmentPlugin = new BalancedDataXAlignmentPlugin();

		XLog alignmentResult = alignmentPlugin.alignLog(explorerModel.getModel(), explorerModel.getLog(),
				explorerModel.getAlignmentConfiguration());
		XAlignedLog alignedLog = XAlignmentExtension.instance().extendLog(alignmentResult);
		explorerModel.setAlignment(alignedLog);

	}

	private static DataPetriNetsWithMarkings wrapPetrinet(PetrinetGraph net) {
		if (net instanceof DataPetriNetsWithMarkings) {
			return (DataPetriNetsWithMarkings) net;
		} else {
			PetriNetWithDataFactory factory = new PetriNetWithDataFactory(net, net.getLabel(), false);
			return factory.getRetValue();
		}
	}

}
