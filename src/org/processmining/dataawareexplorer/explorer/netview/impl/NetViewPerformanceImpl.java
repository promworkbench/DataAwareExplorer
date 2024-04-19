/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.events.NetViewConfigChangedEvent;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;

class NetViewPerformanceImpl extends NetViewAbstractPerformanceAware {

	public final class UpdateViewListener implements ActionListener {

		private final ExplorerUpdater updater;

		public UpdateViewListener(ExplorerUpdater updater) {
			this.updater = updater;
		}

		public void actionPerformed(ActionEvent e) {
			updater.post(new NetViewConfigChangedEvent() {

				public Object getSource() {
					return NetViewPerformanceImpl.this;
				}

			});
		}

	}

	private final ProMComboBox<PerformanceMode> edgeMeasureMode;
	private final ProMComboBox<PerformanceMode> firstMeasureMode;
	private final ProMComboBox<PerformanceMode> secondMeasureMode;
	private final ProMComboBox<TransformationMode> transformationMode;

	private PerformanceStatistics statistics;

	public NetViewPerformanceImpl(ExplorerContext explorerContext, ExplorerUpdater updater,
			ExplorerModel explorerModel) {
		super(explorerContext, updater, explorerModel);

		edgeMeasureMode = new ProMComboBox<>(PerformanceMode.values());
		edgeMeasureMode.setMinimumSize(new Dimension(100, 30));
		edgeMeasureMode.setPreferredSize(new Dimension(100, 30));
		edgeMeasureMode.setSelectedItem(PerformanceMode.FREQUENCY);
		edgeMeasureMode.addActionListener(new UpdateViewListener(updater));

		firstMeasureMode = new ProMComboBox<>(PerformanceMode.values());
		firstMeasureMode.setMinimumSize(new Dimension(180, 30));
		firstMeasureMode.setPreferredSize(new Dimension(180, 30));
		firstMeasureMode.setSelectedItem(PerformanceMode.TRACE_PERCENTAGE);
		firstMeasureMode.addActionListener(new UpdateViewListener(updater));

		secondMeasureMode = new ProMComboBox<>(PerformanceMode.values());
		secondMeasureMode.setMinimumSize(new Dimension(180, 30));
		secondMeasureMode.setPreferredSize(new Dimension(180, 30));
		secondMeasureMode.setSelectedItem(PerformanceMode.FREQUENCY);
		secondMeasureMode.addActionListener(new UpdateViewListener(updater));

		transformationMode = new ProMComboBox<>(TransformationMode.values());
		transformationMode.setMinimumSize(new Dimension(100, 30));
		transformationMode.setPreferredSize(new Dimension(100, 30));
		transformationMode.setSelectedItem(TransformationMode.LINEAR);
		transformationMode.addActionListener(new UpdateViewListener(updater));

		configPanel.addConfigurationComponent("1st measure", firstMeasureMode);
		configPanel.addConfigurationComponent("2nd measure", secondMeasureMode);
		configPanel.addConfigurationComponent("Edge measure", edgeMeasureMode);
		configPanel.addConfigurationComponent("Edge scaling", transformationMode);
	}

	@Override
	public void updateData() throws NetVisualizationException {
		super.updateData();

		DataPetriNet model = explorerModel.getModel();
		Iterable<XAlignment> alignments = explorerModel.getFilteredAlignments();

		statistics = calculatePerformanceStats(model, alignments);

		hideUnobseredPaths(model, decorationData, statistics);

		PerformanceMode edgeMeasure = getEdgeMeasure();
		PerformanceMode firstMeasure = getFirstMeasure();
		PerformanceMode secondMeasure = getSecondMeasure();
		addMeasures(model, decorationData, statistics, LabelMode.BOTH, edgeMeasure, firstMeasure, secondMeasure);
	}

	protected PerformanceMode getSecondMeasure() {
		return (PerformanceMode) secondMeasureMode.getSelectedItem();
	}

	protected void setSecondMeasure(PerformanceMode mode) {
		secondMeasureMode.setSelectedItem(mode);
	}

	protected PerformanceMode getFirstMeasure() {
		return (PerformanceMode) firstMeasureMode.getSelectedItem();
	}

	protected void setFirstMeasure(PerformanceMode mode) {
		firstMeasureMode.setSelectedItem(mode);
	}

	protected PerformanceMode getEdgeMeasure() {
		return (PerformanceMode) edgeMeasureMode.getSelectedItem();
	}

	protected void setEdgeMeasure(PerformanceMode mode) {
		edgeMeasureMode.setSelectedItem(mode);
	}

	@Override
	protected TransformationMode getTransformationMode() {
		return (TransformationMode) transformationMode.getSelectedItem();
	}

	protected void setTransformationMode(TransformationMode mode) {
		transformationMode.setSelectedItem(mode);
	}

	public PerformanceStatistics getStatistics() {
		return statistics;
	}

}