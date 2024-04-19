package org.processmining.dataawareexplorer.explorer.traceview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.events.TracesSelectionChangedEvent;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.framework.util.ui.widgets.traceview.ProMTraceList;
import org.processmining.framework.util.ui.widgets.traceview.ProMTraceList.TraceBuilder;
import org.processmining.framework.util.ui.widgets.traceview.ProMTraceView.Event;
import org.processmining.framework.util.ui.widgets.traceview.ProMTraceView.Trace;
import org.processmining.log.utils.XUtils;
import org.processmining.logenhancement.view.LogViewVisualizer;
import org.processmining.plugins.DataConformance.visualization.alignment.AlignmentTrace.DeviationsSetting;
import org.processmining.plugins.DataConformance.visualization.alignment.AlignmentTrace.InvisibleSetting;
import org.processmining.plugins.DataConformance.visualization.alignment.XTraceResolver;
import org.processmining.plugins.DataConformance.visualization.grouping.GroupedAlignmentMasterDetail;
import org.processmining.plugins.DataConformance.visualization.grouping.GroupedAlignmentMasterView;
import org.processmining.plugins.DataConformance.visualization.grouping.GroupedAlignmentMasterView.GroupedAlignmentInput;
import org.processmining.plugins.DataConformance.visualization.grouping.GroupedAlignments.AlignmentGroup;
import org.processmining.plugins.DataConformance.visualization.grouping.GroupedAlignmentsSimpleImpl;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class TraceViewImpl implements TraceView {

	protected static final class AlignmentGroupTraceBuilder implements TraceBuilder<AlignmentGroup> {

		public Trace<? extends Event> build(AlignmentGroup element) {
			return element.getRepresentative(InvisibleSetting.VISIBLE, DeviationsSetting.NORMAL, true);
		}

	}

	private final class TraceViewSelectionListener implements ListSelectionListener {
		private final GroupedAlignmentMasterDetail alignmentTraceView;

		private TraceViewSelectionListener(GroupedAlignmentMasterDetail alignmentTraceView) {
			this.alignmentTraceView = alignmentTraceView;
		}

		public void valueChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting()) {
				GroupedAlignmentMasterView<XAlignment> masterView = alignmentTraceView.getMasterView();
				ProMTraceList<AlignmentGroup> alignmentList = masterView.getMasterList();
				ImmutableList.Builder<XAlignment> selectedAlignmentBuilder = ImmutableList.builder();
				for (int selectedIndex : alignmentList.getList().getSelectedIndices()) {
					AlignmentGroup selectedGroup = alignmentList.getListModel().getElementAt(selectedIndex);
					XAlignment representativeAlignment = masterView.getDetailElements(selectedGroup).iterator().next();
					selectedAlignmentBuilder.add(representativeAlignment);
				}
				final ImmutableList<XAlignment> previouslySelected = ImmutableList
						.copyOf(model.getSelectedAlignments());
				final ImmutableList<XAlignment> selectedAlignments = selectedAlignmentBuilder.build();
				model.setSelectedAlignments(selectedAlignments);
				updater.getEventBus().post(new TracesSelectionChangedEvent() {

					public Collection<XAlignment> getSelectedAlignments() {
						return selectedAlignments;
					}

					public Collection<XAlignment> getPreviouslySelectedAlignments() {
						return previouslySelected;
					}

					public Object getSource() {
						return TraceViewImpl.this;
					}
				});
			}
		}

	}

	private static final class XTraceMapResolver implements XTraceResolver {

		private final Object2ObjectMap<String, XTrace> indexedLog;

		public XTraceMapResolver(XLog log) {
			this.indexedLog = new Object2ObjectOpenHashMap<>(log.size()); 
			for (XTrace trace: log) {
				String name = XUtils.getConceptName(trace);
				if (name != null) {
					indexedLog.put(name, trace);
				}
			}
		}

		public boolean hasOriginalTraces() {
			return true;
		}

		public XTrace getOriginalTrace(String name) {
			return indexedLog.get(name);
		}
	}

	private final ExplorerModel model;
	private final ExplorerUpdater updater;

	private final JPanel panel = new JPanel(new BorderLayout());
	private LogViewVisualizer traceVisualizer;
	private GroupedAlignmentMasterDetail alignmentVisualizer;
	private GroupedAlignmentInput<XAlignment> groupedAlignments;
	private JButton updateButton;

	public TraceViewImpl(final ExplorerContext explorerContext, ExplorerUpdater updater, final ExplorerModel model) {
		this.updater = updater;
		updater.getEventBus().register(this);
		this.model = model;
		traceVisualizer = new LogViewVisualizer(explorerContext.getLogViewContext(), model.getLog(),
				model.getEventClasses(), model.getColorMap());
		traceVisualizer.getDetailFilterPanel().setVisible(false);
		updateButton = new JButton("Click to refresh");
		updateButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(new JProgressBar(), BorderLayout.CENTER);
				panel.validate();
				if (model.hasAlignment()) {
					new SwingWorker<Void, Void>() {

						protected Void doInBackground() throws Exception {
							groupedAlignments = new GroupedAlignmentInput<>(
									new GroupedAlignmentsSimpleImpl(model.getFilteredAlignments(),
											model.getAlignmentColorMap()),
									new XTraceMapResolver(model.getLog()), model.getAlignmentColorMap());
							return null;
						}

						protected void done() {
							try {
								get();
							} catch (InterruptedException e) {
								return;
							} catch (ExecutionException e) {
								throw new RuntimeException(e);
							}
							alignmentVisualizer = new GroupedAlignmentMasterDetail(explorerContext.getContext(),
									groupedAlignments);
							alignmentVisualizer.getMasterView().getMasterList()
									.addTraceSelectionListener(new TraceViewSelectionListener(alignmentVisualizer));
							panel.removeAll();
							panel.add(alignmentVisualizer, BorderLayout.CENTER);
							panel.validate();
						}

					}.execute();
				} else {
					traceVisualizer.getMasterView().reloadTraces(model.getFilteredLog());
					panel.removeAll();
					panel.add(traceVisualizer, BorderLayout.CENTER);
					panel.validate();
				}

			}
		});
		panel.add(updateButton);
	}

	public Component getComponent() {
		return panel;
	}

	@Subscribe
	public void traceSelectionUpdated(TracesSelectionChangedEvent event) {
		if (model.hasAlignment() && alignmentVisualizer != null) {
			if (event.getSource() != this && event.getSelectedAlignments().isEmpty()) {
				// User clicked on net to clear selection
				ListSelectionModel selectionModel = alignmentVisualizer.getMasterView().getMasterList().getList()
						.getSelectionModel();
				selectionModel.clearSelection();
			}
		}
	}

	public void updateData() {
	}

	public void updateUI() {
		panel.removeAll();
		panel.add(updateButton, BorderLayout.CENTER);
		panel.validate();
	}

}