/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.SwingWorker;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.events.NetViewConfigChangedEvent;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawarereplayer.precision.DataAwarePrecisionPlugin;
import org.processmining.dataawarereplayer.precision.PrecisionConfig;
import org.processmining.dataawarereplayer.precision.PrecisionResult;
import org.processmining.dataawarereplayer.precision.state.PrecisionState;
import org.processmining.dataawarereplayer.precision.state.StateTransition;
import org.processmining.dataawarereplayer.precision.visualizer.PrecisionStateListViewUtils;
import org.processmining.dataawarereplayer.precision.visualizer.PrecisionTrace;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.framework.util.ui.widgets.ColorScheme;
import org.processmining.framework.util.ui.widgets.ColorSchemeLegend;
import org.processmining.framework.util.ui.widgets.ProMComboCheckBox;
import org.processmining.framework.util.ui.widgets.traceview.ProMTraceList;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;

class NetViewPrecisionImpl extends NetViewPerformanceImpl {

	private static final class ShowPrecisionDetailsAction extends AbstractAction {

		private static final long serialVersionUID = 4926361056055583827L;

		private final ExplorerContext explorerContext;

		private final Place place;
		private final PrecisionResult precisionResult;

		public ShowPrecisionDetailsAction(String label, Place place, PrecisionResult precisionResult,
				ExplorerContext explorerContext) {
			super(label);
			this.place = place;
			this.precisionResult = precisionResult;
			this.explorerContext = explorerContext;
		}

		public void actionPerformed(ActionEvent e) {

			new SwingWorker<Collection<PrecisionTrace>, Void>() {

				protected Collection<PrecisionTrace> doInBackground() throws Exception {
					SetMultimap<PrecisionState, StateTransition> possibleStateTransitions = precisionResult
							.getPossibleStateTransitions(place);
					SetMultimap<PrecisionState, StateTransition> observedStateTransitions = precisionResult
							.getObservedStateTransitions(place);
					return PrecisionStateListViewUtils.createPrecisionTraces(precisionResult, possibleStateTransitions,
							observedStateTransitions);
				}

				protected void done() {
					try {
						ProMTraceList<PrecisionTrace> traceList = PrecisionStateListViewUtils
								.createPrecisionTraceList(get());
						explorerContext.getUserQuery().showCustom(traceList,
								"Precision diagnostics for place " + place.getLabel(), ModalityType.MODELESS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (ExecutionException e) {
						explorerContext.getUserQuery().showError("Could not build precision diagnostics",
								e.getMessage(), e);
					}
				}

			}.execute();
		}
	}

	//Color brewer 
	private Color[] colorPalette = new Color[] { new Color(128, 0, 38), new Color(227, 26, 28), new Color(253, 141, 60),
			new Color(254, 217, 118), new Color(255, 255, 204) };

	private final ProMComboCheckBox attributeCCBox;

	private PrecisionResult precisionResult;

	public NetViewPrecisionImpl(ExplorerContext explorerContext, final ExplorerUpdater updater,
			ExplorerModel explorerModel) {
		super(explorerContext, updater, explorerModel);

		attributeCCBox = new ProMComboCheckBox(new Object[] {}, false);
		attributeCCBox.setMinimumSize(new Dimension(150, 30));
		attributeCCBox.setToolTipText("Attributes considered for caluclation of data-aware precision measure");
		attributeCCBox.resetObjs(getAvailableAttributesForDiscovery(), false);
		attributeCCBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				updater.post(new NetViewConfigChangedEvent() {

					public Object getSource() {
						return NetViewPrecisionImpl.this;
					}

				});
			}
		});

		// for each mapped attribute mark it as selected
		attributeCCBox.addSelectedItems(explorerModel.getAlignmentConfiguration().getVariableMapping().values());
		configPanel.addConfigurationComponent("Considered attributes", attributeCCBox);

		ColorSchemeLegend colorLegend = new ColorSchemeLegend(colorPalette);
		colorLegend.setMinimumSize(new Dimension(200, 30));
		colorLegend.setPreferredSize(new Dimension(200, 30));
		colorLegend.setMaximumSize(new Dimension(200, 40));
		configPanel.addConfigurationComponent(javax.swing.Box.createVerticalStrut(5));
		configPanel.addConfigurationComponent("Precision legend", colorLegend);
		
		setEdgeMeasure(PerformanceMode.NONE);
		setFirstMeasure(PerformanceMode.NONE);
		setSecondMeasure(PerformanceMode.NONE);
	}

	private Collection<String> getAvailableAttributesForDiscovery() {
		Builder<String> builder = ImmutableSet.builder();
		for (String key : Ordering.natural().sortedCopy(explorerModel.getLogAttributes())) {
			if (!explorerModel.isStandardAttribute(key)) {
				builder.add(key);
			}
		}
		return builder.build();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.dataawareexplorer.explorer.netview.
	 * NetViewPerformanceImpl #updateData()
	 */
	@SuppressWarnings("rawtypes")
	public void updateData() throws NetVisualizationException {
		super.updateData();

		BalancedProcessorConfiguration config = explorerModel.getAlignmentConfiguration();

		final PrecisionConfig precisionConfig = new PrecisionConfig(config.getInitialMarking(),
				DataAwarePrecisionPlugin.convertMapping(config.getActivityMapping()),
				config.getActivityMapping().getEventClassifier(), config.getVariableMapping());

		if (attributeCCBox.getSelectedItems() != null) { // strange CheckComboBox semantics return null when no selection
			Map<String, Class> extraAttributes = new HashMap<>();
			for (Object attribute : attributeCCBox.getSelectedItems()) {
				String attributeKey = (String) attribute;
				extraAttributes.put(attributeKey, explorerModel.getLogAttributeType(attributeKey));
			}
			// Remove all attributes already mapped to a variable
			for (String mappedKey : config.getVariableMapping().values()) {
				extraAttributes.remove(mappedKey);
			}
			precisionConfig.setExtraAttributes(extraAttributes);
		} else {
			precisionConfig.setExtraAttributes(ImmutableMap.<String, Class>of());
		}

		precisionConfig.setConcurrentThreads(Runtime.getRuntime().availableProcessors() - 1);

		Future<PrecisionResult> precisionFuture = explorerContext.getExecutor().submit(new Callable<PrecisionResult>() {

			public PrecisionResult call() throws Exception {
				return new DataAwarePrecisionPlugin().doMeasurePrecisionWithAlignment(explorerModel.getModel(),
						explorerModel.getLog(), explorerModel.getFilteredAlignments(), precisionConfig,
						explorerModel.getEventClasses());
			}
		});

		try {
			precisionResult = precisionFuture.get();

			for (Place place : explorerModel.getModel().getPlaces()) {
				double precision = precisionResult.getLocalPrecision(place);
				decorationData.putAttribute(place, DecorationKey.FILLCOLOR,
						ColorScheme.getColorFromGradient((float) precision, colorPalette, Color.WHITE));

				decorationData.addContextMenuItem(place,
						new JMenuItem(new ShowPrecisionDetailsAction("Show detailled precision diagnostics (trace view)", place,
								precisionResult, explorerContext)));
			}
		} catch (InterruptedException e) {
			return;
		} catch (ExecutionException e) {
			throw new NetVisualizationException(e);
		}

		infoData.addGlobal("# Moves Possible", countMultimap(precisionResult.getPossibleStateTransitions()),
				InfoType.INTEGER);
		infoData.addGlobal("# Moves Observed", countMultimap(precisionResult.getObservedStateTransitions()),
				InfoType.INTEGER);

		infoData.addGlobal("Avg activity precision", precisionResult.getPrecision(), InfoType.PERCENTAGE);

		for (Place place : explorerModel.getModel().getPlaces()) {
			infoData.addLocal(place, "# Possible locally", precisionResult.getPossibleLocalContinuations(place),
					InfoType.INTEGER);
			infoData.addLocal(place, "# Observed locally", precisionResult.getObservedLocalContinuations(place),
					InfoType.INTEGER);
			infoData.addLocal(place, "Local place-precision", precisionResult.getLocalPrecision(place),
					InfoType.PERCENTAGE);

			infoData.addLocal(place, "# Possible globally", precisionResult.getPossibleContinuations(place),
					InfoType.INTEGER);
			infoData.addLocal(place, "# Observed globally", precisionResult.getObservedContinuations(place),
					InfoType.INTEGER);
			infoData.addLocal(place, "Global place-precision", precisionResult.getPrecision(place),
					InfoType.PERCENTAGE);

		}

		/*
		 * for (Place place : explorerModel.getModel().getPlaces()) { if
		 * (areTransitionsGuarded(place)) { String netLabel =
		 * explorerModel.getModel().getLabel(); String placeLabel =
		 * place.getLabel();
		 * System.out.println(String.format("\"%s\",\"%s\",%.3f,%.3f", netLabel,
		 * placeLabel, computeAvgGuardViolationsOnParentAlignment(place),
		 * precisionResult.getLocalPrecision(place))); } }
		 */

	}

	/*
	 * private boolean areTransitionsGuarded(Place place) { for (PetrinetEdge<?
	 * extends PetrinetNode, ? extends PetrinetNode> edge :
	 * place.getGraph().getOutEdges(place)) { if (edge.getTarget() instanceof
	 * PNWDTransition) { if (((PNWDTransition)
	 * edge.getTarget()).hasGuardExpression()) { return true; } } } return
	 * false; }
	 */

	private final long countMultimap(SetMultimap<PrecisionState, StateTransition> states) {
		long count = 0;
		for (Entry<PrecisionState> entry : states.keys().entrySet()) {
			count += entry.getCount() * precisionResult.getFrequency(entry.getElement());
		}
		return count;
	}

}