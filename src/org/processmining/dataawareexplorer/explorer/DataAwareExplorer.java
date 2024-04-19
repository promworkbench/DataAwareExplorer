/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;

import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.dataawareexplorer.explorer.action.ComputeAlignmentAction;
import org.processmining.dataawareexplorer.explorer.action.ConfigureAlignmentAction;
import org.processmining.dataawareexplorer.explorer.chartview.AttributeChartView;
import org.processmining.dataawareexplorer.explorer.chartview.ChartView;
import org.processmining.dataawareexplorer.explorer.events.DataChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.ExplorerEvent;
import org.processmining.dataawareexplorer.explorer.events.FilterChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.NetViewChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.NetViewConfigChangedEvent;
import org.processmining.dataawareexplorer.explorer.infoview.InfoView;
import org.processmining.dataawareexplorer.explorer.infoview.InfoViewImpl;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration.SelectionFilterMode;
import org.processmining.dataawareexplorer.explorer.netview.NetView;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.explorer.traceview.TraceView;
import org.processmining.dataawareexplorer.explorer.traceview.TraceViewImpl;
import org.processmining.dataawareexplorer.utils.ProMAutoCompletingTextField;
import org.processmining.dataawareexplorer.utils.UIUtils;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.helper.ProMUIHelper;
import org.processmining.logenhancement.view.LogViewContext;
import org.processmining.logenhancement.view.LogViewContextProM;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignedLog;

import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public final class DataAwareExplorer implements ExplorerUpdater, ExplorerContext, ExplorerInterface {

	private static final String QUERY_HELP_TEXT = "<HTML><h1>Query Syntax</h1>"
			+ "SQL-like filtering by trace/event names (event occurrence) or by attributes (attribute with specified value present in trace)"
			+ "<h2>Examples</h2>" + "<ul>" + "<li>'A' - searches for traces that contain event with exact name 'A'</li>"
			+ "<li>'\"event A\"' - searches for traces that contain event with exact name 'event A'</li>"
			+ "<li>'%A' - searches for traces that contain event whose name contains 'A'</li>"
			+ "<li>'~.*A.*' - searches for traces that contain event whose name matches the regex '.*A.*'</li>"
			+ "<li>'amount>50' - searches for traces that contain events with numeric attribute 'amount' which is greater than 50</li>"
			+ "<li>'name%joe' - searches for traces that contain events with literal attribute 'name' which contains the value 'joe'</li>"
			+ "</ul>" + "<h2>Details</h2>" + "<ul>"
			+ "<li>Either searches for 'concept:name' attributes of traces and events (start with '~' for use a regular expression, start with '%' to use a 'contains' query)</li>"
			+ "<li>Supports filtering by trace/event attributes in form of 'eventName'.'attributeName OP attributeValue'.</li>"
			+ "<li>Supported operators (OP) are (=, >, <, !=, >=, <=, % (contains), ~ (regex), some operators only work with numeric/date attributes.</li>"
			+ "<li>Terms can be connected with (AND, OR) and nested with parens.</li>" + "</ul></HTML>";

	private static final int CONFIGURATION_SPACING = 15;

	private abstract class ExplorerWorker extends SwingWorker<Void, Void> {

		protected void done() {
			try {
				// Ensure result is ready!
				get();

				updateUI();

				mainPanel.validate();

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				String message = buildErrorMessage(e);
				ProMUIHelper.showErrorMessage(mainPanel, String.format("Error while updating the view: %s", message),
						"Error updating view", e);
			} catch (RuntimeException e) {
				ProMUIHelper.showErrorMessage(mainPanel,
						String.format("Error while updating the view: %s", e.getMessage()), "Error updating view", e);
			} finally {
				getModel().resetDirtyFlags();
				afterUpdate();
			}
		}

		protected abstract void updateUI();

	}

	public class ShowDetailsAction implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			showFrame(traceFrame);
		}

	}

	public class ShowChartAction implements ActionListener {

		public void actionPerformed(ActionEvent arg0) {
			showFrame(chartFrame);
		}

	}

	private static final int FREETEXT_SEARCH_LIMIT = 500;

	private final PluginContext context;
	private ExecutorService executor = Executors
			.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

	private final ExplorerModel explorerModel;

	private final JSplitPane mainPanel;
	private final JPanel bottomPanel;
	private final JPanel graphPanel;

	private NetVisualization netVisualization;

	private final ProMComboBox<ViewMode> viewModeChooser;
	private final JButton showDetails;
	private final JButton showChart;

	private NetView currentNetView;

	private final JPanel configurationPanel;
	private final InfoView infoView;

	private final JFrame traceFrame;
	private final TraceView traceView;

	private final JFrame chartFrame;
	private final ChartView chartView;

	private final ProMAutoCompletingTextField filterQuery;
	private final ProMComboBox<SelectionFilterMode> filterSelectionMode;

	private boolean isUpdating = false;

	private JLabel labelConfig;

	private final Progress alignmentProgressListener;
	private final JProgressBar alignmentProgressBar;
	private final JButton alignmentComputeButton;
	private final JButton alignmentCancelButton;
	private final JPanel alignmentProgressPanel;

	private JLabel labelFilterSelectionMode;

	private final EventBus eventBus = new EventBus("explorer");

	public DataAwareExplorer(final PluginContext context, XLog log, DataPetriNetsWithMarkings net) {
		super();
		this.context = context;
		eventBus.register(this);

		explorerModel = new ExplorerModel(log, net);
		netVisualization = new NetVisualizationImpl(getExplorerUpdater(), getExplorerContext(), getModel());

		traceFrame = new JFrame("MPE: Traces for " + getModel().getModel().getLabel());
		traceFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		traceView = new TraceViewImpl(getExplorerContext(), this, getModel());
		traceFrame.getContentPane().add(traceView.getComponent(), BorderLayout.CENTER);

		chartFrame = new JFrame("MPE: Charts for " + getModel().getModel().getLabel());
		chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		chartView = new AttributeChartView(getModel(), getExplorerUpdater());
		chartFrame.getContentPane().add(chartView.getComponent(), BorderLayout.CENTER);

		mainPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT) {

			private static final long serialVersionUID = 6346301825270129760L;

			private boolean wasDetailsVisible;
			private boolean wasChartVisible;

			@Override
			public void removeNotify() {
				super.removeNotify();
				if (DataAwareExplorer.this.traceFrame.isVisible()) {
					DataAwareExplorer.this.traceFrame.setVisible(false);
					wasDetailsVisible = true;
				}
				if (DataAwareExplorer.this.chartFrame.isVisible()) {
					DataAwareExplorer.this.chartFrame.setVisible(false);
					wasChartVisible = true;
				}
			}

			@Override
			public void addNotify() {
				super.addNotify();
				if (wasDetailsVisible) {
					DataAwareExplorer.this.traceFrame.setVisible(true);
				}
				if (wasChartVisible) {
					DataAwareExplorer.this.chartFrame.setVisible(true);
				}
			}

		};

		graphPanel = new JPanel();
		graphPanel.setOpaque(false);
		graphPanel.setLayout(new BoxLayout(graphPanel, BoxLayout.Y_AXIS));
		graphPanel.add(netVisualization.getComponent());

		bottomPanel = new JPanel();
		bottomPanel.setLayout(new GridBagLayout());
		bottomPanel.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.BLACK));

		GridBagConstraints cInputCon = new GridBagConstraints();
		cInputCon.insets = new Insets(0, 5, 5, 5);
		cInputCon.gridx = 0;
		cInputCon.gridy = 0;
		cInputCon.anchor = GridBagConstraints.CENTER;
		cInputCon.gridwidth = 2;
		bottomPanel.add(UIUtils.createHeading("ALIGNMENT"), cInputCon);

		cInputCon.anchor = GridBagConstraints.CENTER;
		cInputCon.gridwidth = 2;
		cInputCon.gridx = 0;
		cInputCon.gridy = 1;

		JPanel alignmentPanel = new JPanel();
		alignmentPanel.setLayout(new BoxLayout(alignmentPanel, BoxLayout.Y_AXIS));

		JPanel alignmentButtonPanel = new JPanel();
		alignmentButtonPanel.setLayout(new BoxLayout(alignmentButtonPanel, BoxLayout.X_AXIS));

		alignmentComputeButton = SlickerFactory.instance().createButton("(Re)compute Alignment");
		alignmentComputeButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				ViewMode nextMode = getCurrentMode() == ViewMode.MODEL ? ViewMode.FITNESS : getCurrentMode();
				computeAlignment(nextMode);
			}

		});
		alignmentComputeButton.setToolTipText("<HTML>(Re)computes the alignment between the event log and the model."
				+ "<BR/>An alignment matches events from the log with activities in the model returning the most likely path through the model for each trace."
				+ "<BR/>Most display modes of the MPE require an alignment to be computed. "
				+ "Depending on the complexity of the model and size of the log the computation may take a long time and require lots of memory."
				+ "<BR>(> 4GB available memory for the MPE is advised).</HTML>");
		alignmentButtonPanel.add(alignmentComputeButton);

		alignmentPanel.add(alignmentButtonPanel);

		alignmentProgressPanel = new JPanel();
		alignmentProgressPanel.setVisible(false);
		alignmentProgressPanel.setLayout(new BoxLayout(alignmentProgressPanel, BoxLayout.X_AXIS));

		alignmentProgressBar = new JProgressBar();
		alignmentProgressBar.setString("Computing ...");
		alignmentProgressBar.setStringPainted(true);
		alignmentProgressListener = new ExplorerProgressListener(alignmentProgressBar);

		alignmentCancelButton = SlickerFactory.instance().createButton("Cancel");
		alignmentCancelButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				alignmentProgressListener.cancel();
			}
		});
		alignmentCancelButton.setToolTipText(
				"Cancels the alignment computation. The subset of traces for which the alignment computation finished will be used.");
		alignmentProgressPanel.add(alignmentProgressBar);
		alignmentProgressPanel.add(alignmentCancelButton);

		alignmentPanel.add(alignmentProgressPanel);

		bottomPanel.add(alignmentPanel, cInputCon);

		cInputCon.gridx = 0;
		cInputCon.gridy = 2;
		cInputCon.anchor = GridBagConstraints.CENTER;
		cInputCon.fill = GridBagConstraints.NONE;
		cInputCon.gridwidth = 2;
		bottomPanel.add(UIUtils.createHeading("FILTER"), cInputCon);

		cInputCon.gridx = 0;
		cInputCon.gridy = 3;
		cInputCon.anchor = GridBagConstraints.NORTHEAST;
		cInputCon.gridwidth = 1;
		bottomPanel.add(UIUtils.createSubHeading("Query"), cInputCon);
		filterQuery = new ProMAutoCompletingTextField("", "SQL-like filtering");
		filterQuery.setDictionary(getModel().getDictionary());

		filterQuery.setMinimumSize(new Dimension(180, 30));
		cInputCon.gridx = 1;
		cInputCon.gridy = 3;

		JButton helpButton = SlickerFactory.instance().createButton("?");
		helpButton.setMinimumSize(new Dimension(30, 30));
		helpButton.setPreferredSize(new Dimension(30, 30));
		helpButton.setMaximumSize(new Dimension(30, 30));
		helpButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				JEditorPane pane = new JEditorPane("text/html", QUERY_HELP_TEXT);
				pane.setEditable(false);
				ExplorerDialogs.showInfo(mainPanel, "Query Syntax Help", pane);
			}

		});
		helpButton.setToolTipText(QUERY_HELP_TEXT);

		JPanel queryPanel = new JPanel();
		queryPanel.setLayout(new BoxLayout(queryPanel, BoxLayout.X_AXIS));
		queryPanel.add(filterQuery);
		queryPanel.add(helpButton);
		bottomPanel.add(queryPanel, cInputCon);

		cInputCon.gridx = 0;
		cInputCon.gridy = 4;
		labelFilterSelectionMode = UIUtils.createSubHeading("Selection");
		bottomPanel.add(labelFilterSelectionMode, cInputCon);
		filterSelectionMode = new ProMComboBox<FilterConfiguration.SelectionFilterMode>(
				FilterConfiguration.SelectionFilterMode.values());
		filterSelectionMode.setMinimumSize(new Dimension(210, 30));
		filterSelectionMode
				.setToolTipText("Depending on this setting, the selection of activities in the model filters the log.");
		filterSelectionMode.setSelectedItem(FilterConfiguration.SelectionFilterMode.NONE);
		cInputCon.gridx = 1;
		cInputCon.gridy = 4;
		bottomPanel.add(filterSelectionMode, cInputCon);

		GridBagConstraints cDisplayCon = new GridBagConstraints();
		cDisplayCon.insets = new Insets(0, 5, 5, 5);
		cDisplayCon.gridx = 2;
		cDisplayCon.gridy = 0;
		cDisplayCon.anchor = GridBagConstraints.CENTER;
		cDisplayCon.gridwidth = 2;
		bottomPanel.add(UIUtils.createHeading("MPE MODE"), cDisplayCon);

		cDisplayCon.anchor = GridBagConstraints.NORTHWEST;
		cDisplayCon.gridwidth = 1;
		cDisplayCon.gridx = 2;
		cDisplayCon.gridy = 1;

		viewModeChooser = new ProMComboBox<>(ViewMode.values());
		viewModeChooser.setMinimumSize(new Dimension(200, 30));
		viewModeChooser.setSelectedItem(ViewMode.MODEL);
		viewModeChooser
				.setToolTipText("Changes the current mode of the MPE. Each mode supports a use case of the MPE.");
		bottomPanel.add(viewModeChooser, cDisplayCon);

		cDisplayCon.gridwidth = 1;
		cDisplayCon.gridx = 2;
		cDisplayCon.gridy = 2;
		cDisplayCon.anchor = GridBagConstraints.CENTER;
		bottomPanel.add(UIUtils.createHeading("DETAIL VIEWS"), cDisplayCon);

		cDisplayCon.anchor = GridBagConstraints.NORTH;
		cDisplayCon.gridx = 2;
		cDisplayCon.gridy = 3;
		showDetails = SlickerFactory.instance().createButton("Toggle Trace View");
		showDetails.addActionListener(new ShowDetailsAction());
		showDetails.setToolTipText("Opens a new window that allows to investigate individual traces.");
		showChart = SlickerFactory.instance().createButton("Toggle Chart View");
		showChart.addActionListener(new ShowChartAction());
		showChart.setToolTipText(
				"Opens a new window that allows to investigate the distribution of attribute values in the log for certain parts of the model.");
		bottomPanel.add(showDetails, cDisplayCon);
		cDisplayCon.gridx = 2;
		cDisplayCon.gridy = 4;
		bottomPanel.add(showChart, cDisplayCon);

		GridBagConstraints spacerCon = new GridBagConstraints();
		spacerCon.gridx = 4;
		spacerCon.gridy = 0;
		bottomPanel.add(Box.createHorizontalStrut(CONFIGURATION_SPACING), spacerCon);

		GridBagConstraints cConfigCon = new GridBagConstraints();
		cConfigCon.insets = new Insets(0, 0, 5, 5);
		cConfigCon.gridx = 5;
		cConfigCon.gridy = 0;
		labelConfig = UIUtils.createHeading("CONFIGURATION PANEL");
		bottomPanel.add(labelConfig, cConfigCon);
		configurationPanel = new JPanel();
		configurationPanel.setLayout(new BoxLayout(configurationPanel, BoxLayout.Y_AXIS));
		cConfigCon.gridx = 5;
		cConfigCon.gridy = 1;
		cConfigCon.anchor = GridBagConstraints.NORTH;
		cConfigCon.gridheight = GridBagConstraints.REMAINDER;
		cConfigCon.fill = GridBagConstraints.NONE;
		bottomPanel.add(configurationPanel, cConfigCon);

		spacerCon.gridx = 6;
		spacerCon.gridy = 0;
		bottomPanel.add(Box.createHorizontalStrut(CONFIGURATION_SPACING), spacerCon);

		GridBagConstraints cInfoCon = new GridBagConstraints();
		cInfoCon.insets = new Insets(0, 0, 5, 5);
		cInfoCon.gridx = 7;
		cInfoCon.gridy = 0;
		cInfoCon.weightx = 1.0;
		bottomPanel.add(UIUtils.createHeading("INFORMATION PANEL"), cInfoCon);
		infoView = new InfoViewImpl(getExplorerUpdater());
		cInfoCon.gridy = 1;
		cInfoCon.anchor = GridBagConstraints.EAST;
		cInfoCon.weighty = 1.0;
		cInfoCon.fill = GridBagConstraints.BOTH;
		cInfoCon.gridheight = GridBagConstraints.REMAINDER;
		bottomPanel.add(infoView.getComponent(), cInfoCon);

		mainPanel.setOpaque(true);
		mainPanel.setBackground(Color.WHITE);
		mainPanel.setOneTouchExpandable(false);
		mainPanel.setResizeWeight(1.0);
		mainPanel.setDividerSize(2);
		mainPanel.setTopComponent(graphPanel);
		mainPanel.setBottomComponent(bottomPanel);

		viewModeChooser.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				ViewMode newViewMode = (ViewMode) viewModeChooser.getSelectedItem();
				if (newViewMode != getModel().getViewMode()) {
					if (getModel().getDiscoveryResult() != null) {
						QueryResult result = ExplorerDialogs.queryCustom(mainPanel, "Unsaved discovered model",
								new JLabel("<HTML>The model contains unsaved discovered data rules.<BR/> "
										+ "Do you want to save the discovered rules as a new model? Or, do you want to change the MPE mode using the previous model and discard the rules?</HTML>"),
								new String[] { "Save model", "Discard rules" });
						if (result.getCustom() == 0) {
							DataAwareExplorer newExplorer = explorerModel.getController()
									.addExplorerWithDiscoveredModel(getExplorerContext(), getExplorerUpdater(),
											explorerModel, explorerModel.getDiscoveryResult());
							if (newExplorer != null) {
								explorerModel.getController().switchExplorer(newExplorer, newViewMode);
								return;
							}
						} else {
							getModel().resetDiscoveryResult();
						}
					}
					getModel().setViewMode(newViewMode);
					if (getModel().getViewMode() == ViewMode.MODEL) {
						filterSelectionMode.setEnabled(false);
					} else {
						filterSelectionMode.setEnabled(true);
					}
					post(new NetViewChangedEvent() {

						public Object getSource() {
							return DataAwareExplorer.this;
						}
					});
				} // else nothing to do	
			}

		});

		filterQuery.addActionListener(new ActionListener() {

			private boolean hasShownNoAlignmentWarning = false;

			public void actionPerformed(ActionEvent e) {
				if (getModel().getViewMode() == ViewMode.MODEL) {
					if (!hasShownNoAlignmentWarning) {
						ExplorerDialogs.showInfo(mainPanel, "No alignment available", new JLabel(
								"No alignment information available. Filtering will be only only effective in the 'trace view'!"));
						hasShownNoAlignmentWarning = true;
					}
				}
				if (filterQuery.getText().length() > FREETEXT_SEARCH_LIMIT) {
					filterQuery.setText("");
					ProMUIHelper.showWarningMessage(mainPanel,
							String.format("Filter query exceeds limit of %s characters!", FREETEXT_SEARCH_LIMIT),
							"Query exceeds limit");
				} else {
					filterQuery.getTextField().setForeground(Color.WHITE);
					getModel().getFilterConfiguration().setFilterQuery(filterQuery.getText());
					post(new FilterChangedEvent() {

						public Object getSource() {
							return DataAwareExplorer.this;
						}
					});
				}
			}

		});

		filterSelectionMode.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (getCurrentMode() != ViewMode.MODEL) {
					getModel().getFilterConfiguration()
							.setSelectionFilterMode((SelectionFilterMode) filterSelectionMode.getSelectedItem());
					if (!getModel().getFilterConfiguration().getSelectedNodes().isEmpty()) {
						post(new FilterChangedEvent() {

							public Object getSource() {
								return DataAwareExplorer.this;
							}
						});
					}
				}
			}
		});

		post(new DataChangedEvent() {

			public Object getSource() {
				return DataAwareExplorer.this;
			}
		});
	}

	public void beforeUpdate() {
		netVisualization.beforeUpdate();
		isUpdating = true;
		mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		disableControls();
	}

	private void disableControls() {
		viewModeChooser.setEnabled(false);
		filterQuery.getTextField().setEnabled(false);
		if (getCurrentMode() != ViewMode.MODEL) {
			filterSelectionMode.setEnabled(false);
		}
		alignmentComputeButton.setEnabled(false);
	}

	public void afterUpdate() {
		netVisualization.afterUpdate();
		mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		enableControls();
		isUpdating = false;
	}

	private void enableControls() {
		viewModeChooser.setEnabled(true);
		filterQuery.getTextField().setEnabled(true);
		if (getCurrentMode() != ViewMode.MODEL) {
			filterSelectionMode.setEnabled(true);
		}
		alignmentComputeButton.setEnabled(true);
	}

	@Subscribe
	public void netViewChanged(NetViewChangedEvent event) {

		if (!getModel().hasAlignment()) {
			if (getCurrentMode() != ViewMode.MODEL) {
				final ViewMode requestedMode = getCurrentMode();
				setCurrentMode(ViewMode.MODEL);
				computeAlignment(requestedMode);
				return; // update only after having computed alignment
			}
		}

		executeWorker(new ExplorerWorker() {

			protected Void doInBackground() throws Exception {
				currentNetView = getCurrentMode().getViewFactory().newInstance(getExplorerContext(),
						getExplorerUpdater(), getModel());
				currentNetView.updateData();
				netVisualization.updateData(currentNetView.getModelDecorationData());
				return null;
			}

			protected void updateUI() {

				// does not trigger another update as we are currently updating
				viewModeChooser.setSelectedItem(getCurrentMode());

				currentNetView.updateUI();
				netVisualization.updateUI();
				updateConfigurationUI();
				infoView.updateUI(currentNetView.getInfoData(), getModel().getFilterConfiguration().getSelectedNodes());
			}

		});
	}

	@Subscribe
	public void netViewConfigChanged(NetViewConfigChangedEvent event) {
		executeWorker(new ExplorerWorker() {

			protected Void doInBackground() throws Exception {
				currentNetView.updateData();
				netVisualization.updateData(currentNetView.getModelDecorationData());
				return null;
			}

			protected void updateUI() {
				currentNetView.updateUI();
				netVisualization.updateUI();
				updateConfigurationUI();
				infoView.updateUI(currentNetView.getInfoData(), getModel().getFilterConfiguration().getSelectedNodes()); // might have change the info
			}

		});
	}

	@Subscribe
	public void dataChanged(DataChangedEvent event) {
		executeWorker(new ExplorerWorker() {

			protected Void doInBackground() throws Exception {
				getModel().filterData();
				currentNetView = getCurrentMode().getViewFactory().newInstance(getExplorerContext(),
						getExplorerUpdater(), getModel());
				currentNetView.updateData();
				netVisualization.updateData(currentNetView.getModelDecorationData());
				chartView.updateData();
				traceView.updateData();
				return null;
			}

			protected void updateUI() {

				currentNetView.updateUI();
				netVisualization.updateUI();
				updateConfigurationUI();
				infoView.updateUI(currentNetView.getInfoData(), getModel().getFilterConfiguration().getSelectedNodes());

				chartView.updateUI();
				chartFrame.getContentPane().validate();

				traceView.updateUI();
				traceFrame.getContentPane().validate();

			}

		});
	}

	@Subscribe
	public void filterChanged(FilterChangedEvent event) {
		filterQuery.setText(explorerModel.getFilterConfiguration().getFilterQuery());
		executeWorker(new ExplorerWorker() {

			protected Void doInBackground() throws Exception {
				getModel().filterData();
				currentNetView.updateData();
				netVisualization.updateData(currentNetView.getModelDecorationData());
				chartView.updateData();
				traceView.updateData();
				return null;
			}

			protected void updateUI() {
				currentNetView.updateUI();
				netVisualization.updateUI();
				updateConfigurationUI();
				infoView.updateUI(currentNetView.getInfoData(), getModel().getFilterConfiguration().getSelectedNodes());

				chartView.updateUI();
				chartFrame.getContentPane().validate();

				traceView.updateUI();
				traceFrame.getContentPane().validate();
			}

		});
	}

	private final void executeWorker(ExplorerWorker worker) {
		if (isUpdating) {
			return;
		}
		beforeUpdate();
		worker.execute();
	}

	private void computeAlignment(final ViewMode requestedMode) {
		// Possibility to change parameters
		getModel().setAlignmentConfiguration(null);
		getModel().setEventClassifier(new XEventNameClassifier());
		QueryResult result = ExplorerDialogs.queryCustom(mainPanel, "Computing alignment between model and log",
				new JLabel("<HTML>An alignment between the shown model an the event log will be computed. "
						+ "<BR/>An alignment matches events from the log with activities in the model returning the most likely path through the model for each trace. "
						+ "<BR/>Most display modes of the MPE require an alignment to be computed. "
						+ "<BR/>Depending on the complexity of the model and size of the log the computation may take a long time and require lots of memory."
						+ "<BR/><BR/>"
						+ "There are several parameters that may need to be set for the alignment computation. "
						+ "<BR/>Do you want to use the <B>simple</B> configuration mode (use default parameters) or the <B>expert</B> configuration mode (manually specify all parameters)?</HTML>"),
				new String[] { "Simple Configuration", "Expert Configuration" });
		if (result.getCustom() == 1) {
			new ConfigureAlignmentAction(getExplorerContext(), getModel()) {

				public void onBeforeAction() {
				}

				public void onAfterAction(BalancedProcessorConfiguration config) {
					getModel().setAlignmentConfiguration(config);
				}
			}.execute();
		}
		new ComputeAlignmentAction(getExplorerContext(), getModel(), alignmentProgressListener) {

			public void onBeforeAction() {
				beforeUpdate();
				alignmentProgressPanel.setVisible(true);
			}

			public void onAfterAction(XAlignedLog result) {
				alignmentProgressPanel.setVisible(false);
				afterUpdate();
				setCurrentMode(requestedMode);
				post(new DataChangedEvent() {

					public Object getSource() {
						return DataAwareExplorer.this;
					}
				});
			}

			public void onError(String errorTitle, Exception e) {
				super.onError(errorTitle, e);
				alignmentProgressPanel.setVisible(false);
				afterUpdate();
			}

		}.execute();
	}

	private void updateConfigurationUI() {
		configurationPanel.removeAll();
		Component configurationComponent = currentNetView.getConfigurationPanel().getComponent();
		if ((configurationComponent instanceof Container)
				&& ((Container) configurationComponent).getComponentCount() > 0) {
			labelConfig.setVisible(true);
			configurationPanel.add(configurationComponent);
		} else {
			labelConfig.setVisible(false);
		}
		mainPanel.resetToPreferredSizes();
	}

	private String buildErrorMessage(ExecutionException e) {
		String message = "Reason unknown!";
		if (e.getCause() != null && e.getCause().getMessage() != null) {
			message = e.getCause().getMessage();
		} else if (e.getMessage() != null) {
			message = e.getMessage();
		}
		return message;
	}

	private ViewMode getCurrentMode() {
		return getModel().getViewMode();
	}

	void setCurrentMode(ViewMode viewMode) {
		getModel().setViewMode(viewMode);
	}

	public JComponent getComponent() {
		return mainPanel;
	}

	private void showFrame(JFrame frame) {
		if (!frame.isVisible()) {
			if (frame.getWidth() == 0 && frame.getHeight() == 0) {
				// Frame was not resized before -> first time shown
				GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
				GraphicsDevice[] gs = ge.getScreenDevices();
				if (gs.length > 1) {
					for (int i = 0; i < gs.length; i++) {
						if (mainPanel.getGraphicsConfiguration() != null
								&& gs[i] != mainPanel.getGraphicsConfiguration().getDevice()) {
							JFrame dummy = new JFrame(gs[i].getDefaultConfiguration());
							frame.setLocationRelativeTo(dummy);
							frame.setSize(new Dimension(800, 800));
							frame.setExtendedState(Frame.MAXIMIZED_BOTH);
							dummy.dispose();
							break;
						}
					}
				} else {
					Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
					frame.setLocation(screenSize.width / 6, screenSize.height / 6);
					frame.setSize(screenSize.width * 2 / 3, screenSize.height * 2 / 3);
					frame.setExtendedState(Frame.NORMAL);
				}
			}
			frame.setVisible(true);
		} else {
			frame.setVisible(false);
		}
	}

	public void setParent(ExplorerController parent) {
		getModel().setController(parent);
	}

	public ExplorerModel getModel() {
		return explorerModel;
	}

	public Component getParentView() {
		return mainPanel;
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public PluginContext getContext() {
		return context;
	}

	public ExplorerContext getExplorerContext() {
		return this;
	}

	private ExplorerUpdater getExplorerUpdater() {
		return this;
	}

	@Override
	public void post(ExplorerEvent event) {
		getEventBus().post(event);
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	public ExplorerInterface getUserQuery() {
		return this;
	}

	public void showMessage(String message, String title) {
		JOptionPane.showMessageDialog(mainPanel, message, title, JOptionPane.INFORMATION_MESSAGE);
	}

	public void showError(String errorTitle, Exception e) {
		String message = "Reason unknown!";
		if (Throwables.getRootCause(e).getMessage() != null) {
			message = Throwables.getRootCause(e).getMessage();
		} else if (e.getMessage() != null) {
			message = e.getMessage();
		}
		ProMUIHelper.showErrorMessage(mainPanel, message, errorTitle, e);
	}

	public void showError(String errorMessage, String errorTitle, Exception e) {
		ProMUIHelper.showErrorMessage(mainPanel, errorMessage, errorTitle, e);
	}

	public void showWarning(String warningMessage, String warningTitle) {
		ProMUIHelper.showWarningMessage(mainPanel, warningMessage, warningTitle);
	}

	public void showCustom(JComponent component, String dialogTitle, ModalityType modalityType) {
		ExplorerDialogs.showCustom(component, dialogTitle, modalityType);
	}

	public QueryResult queryOkCancel(String queryTitle, JComponent queryComponent) {
		return ExplorerDialogs.queryOkCancel(mainPanel, queryTitle, queryComponent);
	}

	public QueryResult queryYesNo(String queryTitle, JComponent queryComponent) {
		return ExplorerDialogs.queryYesNo(mainPanel, queryTitle, queryComponent);
	}

	public QueryResult queryCustom(String queryTitle, JComponent queryComponent, String[] options) {
		return ExplorerDialogs.queryCustom(mainPanel, queryTitle, queryComponent, options);
	}

	public String queryString(String query, String initialLabel) {
		return JOptionPane.showInputDialog(mainPanel, query, initialLabel);
	}

	public LogViewContext getLogViewContext() {
		return new LogViewContextProM(context);
	}

}