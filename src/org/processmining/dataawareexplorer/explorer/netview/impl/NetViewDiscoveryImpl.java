/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeLiteral;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.QueryResult;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.ResultOption;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.events.NetViewConfigChangedEvent;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryAlgorithm;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryResult;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.ProgressPublisher;
import org.processmining.dataawareexplorer.explorer.work.TransitionStatistics;
import org.processmining.dataawareexplorer.utils.InitialValueMappingPanel;
import org.processmining.dataawareexplorer.utils.PetrinetUtils;
import org.processmining.datadiscovery.AbstractDecisionRuleDiscovery;
import org.processmining.datadiscovery.DecisionTreeConfig;
import org.processmining.datadiscovery.PetrinetDecisionRuleDiscovery.PetrinetDecisionRule;
import org.processmining.datadiscovery.WekaDecisionTreeRuleDiscovery.TreeRule;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.visualizers.PrefuseTreeVisualization;
import org.processmining.datadiscovery.visualizers.PrefuseTreeVisualization.TreePanel;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMComboCheckBox;
import org.processmining.log.utils.XUtils;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;

import com.fluxicon.slickerbox.components.NiceDoubleSlider;
import com.fluxicon.slickerbox.components.NiceSlider.Orientation;
import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;

import weka.core.Drawable;

class NetViewDiscoveryImpl extends NetViewAbstractPerformanceAware {

	public final class UpdateInitialValuesAction extends AbstractAction {

		private static final long serialVersionUID = 1L;

		UpdateInitialValuesAction() {
			super("Expert Config");
		}

		public void actionPerformed(ActionEvent e) {
			Set<String> userSelectedAttributes = getUserSelectedAttributes();

			Map<String, String> defaultValues = guessDefaultValues(userSelectedAttributes);
			InitialValueMappingPanel<String> initialValuePanel = new InitialValueMappingPanel<>(
					"<html><h1>Configure initial values assumed for attributes</h1>" + "<br>"
							+ "When activated each attribute is assigned a value that acts as placeholder for not initialised (NULL)."
							+ "<br>By using those values the discovery algorithms can mine guards that depend on whether a attribute has already been written."
							+ "</html>",
					userSelectedAttributes, defaultValues);
			QueryResult result = explorerContext.getUserQuery().queryOkCancel("Configure initial values for attributes",
					initialValuePanel);

			if (result.getStandard() == ResultOption.OK) {
				if (initialValuePanel.useInitialValues()) {
					for (Entry<String, String> entry : initialValuePanel.getResult().entrySet()) {
						try {
							initialValues.put(entry.getKey(), convertDefaultValue(entry.getKey(), entry.getValue()));
						} catch (NumberFormatException | ParseException e1) {
							explorerContext.getUserQuery().showError(
									"Cannot parse value for " + entry.getKey() + "\n" + e1.getMessage(),
									"Error parsing initial values", e1);
						}
					}
				} else {
					initialValues = new HashMap<String, Object>();
				}
				// Maybe new literals have been added
				explorerModel.setLiteralValues(
						getLiteralValuesMap(explorerModel.getAlignment(), initialValues, attributesForDiscovery));
			}
		}

		private Map<String, String> guessDefaultValues(Set<String> userSelectedAttributes) {
			//TODO initialize before button click
			Map<String, String> defaultValues = new HashMap<String, String>();
			for (String attribute : userSelectedAttributes) {
				defaultValues.put(attribute, guessDefaultValue(attribute).toString());
			}
			return defaultValues;
		}

		private Object convertDefaultValue(String key, String value) throws ParseException, NumberFormatException {
			Class<?> type = attributesForDiscovery.get(key);
			if (type == String.class) {
				return value;
			} else if (type == Boolean.class) {
				return Boolean.valueOf(value);
			} else if (type == Long.class) {
				return Long.parseLong(value);
			} else if (type == Integer.class) {
				return Integer.parseInt(value);
			} else if (type == Double.class) {
				return Double.parseDouble(value);
			} else if (type == Float.class) {
				return Float.parseFloat(value);
			} else if (type == Date.class) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
				return format.parse(value);
			} else {
				throw new IllegalArgumentException("Unkown type " + type);
			}
		}

	}

	public static final int NOMINAL_ATTRIBUTE_WARNING_LIMIT = 100;

	private final class DiscoverGuardsAction extends AbstractAction {

		private static final long serialVersionUID = -5736267012366214173L;

		public DiscoverGuardsAction() {
			super("Discover data-perspective");
		}

		public void actionPerformed(ActionEvent e) {

			if (!queryInfrequentTransitions()) {
				return;
			}

			startComputation();

			Set<String> filteredAttributeSelection = queryLargeNominalAttributeSet(getUserSelectedAttributes());

			// This is the strange way to set the selection in this ComboBox
			attributeCCBox.clearSelection();
			attributeCCBox.addSelectedItems(filteredAttributeSelection);

			final DecisionTreeConfig config = createDiscoveryConfiguration();
			final Set<Place> consideredPlaces = createConsideredPlaces(explorerModel.getModel(),
					explorerModel.getFilterConfiguration().getSelectedNodes());

			discoveryProgressbar
					.setString(String.format("Discovering rules for %s place(s) ...", consideredPlaces.size()));
			if (consideredPlaces.size() == 1) {
				discoveryProgressbar.setIndeterminate(true);
			} else {
				discoveryProgressbar.setIndeterminate(false);
				discoveryProgressbar.setMaximum(consideredPlaces.size());
				discoveryProgressbar.setValue(0);
			}

			explorerContext.getExecutor().execute(new SwingWorker<DiscoveryResult, PetrinetDecisionRule>() {

				protected DiscoveryResult doInBackground() throws Exception {

					Set<String> currentSelectedAttributes = getUserSelectedAttributes();
					if (!currentSelectedAttributes.equals(consideredAttributes)) {
						consideredAttributes = ImmutableSet.copyOf(currentSelectedAttributes);
						explorerModel.setDiscoveredWriteOperations(null);
					}

					if (explorerModel.getDiscoveredWriteOperations() == null) {
						explorerModel.setDiscoveredWriteOperations(DiscoveryProcessor.discoverWriteOperations(
								explorerModel.getFilteredAlignments(), explorerModel.getModel().getTransitions(),
								explorerModel.getTransitionsLocalId(), getWriteThreshold(),
								getUserSelectedAttributes()));
					}

					DiscoveryProcessor processor = new DiscoveryProcessor(explorerModel.getModel(),
							explorerModel.getFilteredAlignments(), initialValues, attributesForDiscovery,
							attributeTypes, explorerModel.getLiteralValues(), explorerModel.getTransitionsLocalId(), explorerModel.getDiscoveryResult());
					return processor.discover(getSelectedDiscoveryAlgorithm(), config,
							explorerModel.getDiscoveredWriteOperations(), consideredPlaces, getUserSelectedAttributes(),
							new ProgressPublisher() {

								public void onProgress(PetrinetDecisionRule decisionRule) {
									publish(decisionRule);
								}
							}, explorerContext.getExecutor());

				}

				protected void done() {
					try {
						DiscoveryResult result = get();
						if (result != null) {
							explorerModel.setDiscoveryResult(result);
							updater.post(new NetViewConfigChangedEvent() {

								public Object getSource() {
									return NetViewDiscoveryImpl.this;
								}

							});
						} else {
							explorerContext.getUserQuery().showWarning(
									"Could not discover decision rules. "
											+ "Maybe the model does not contain decision points (places with more than one outgoing edge)?",
									"No rules discovered");
						}
					} catch (ExecutionException e) {
						explorerContext.getUserQuery().showError("Error discovering rules", e);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						finishComputation();
					}
				}

				protected void process(List<PetrinetDecisionRule> chunks) {
					for (PetrinetDecisionRule c : chunks) {
						discoveryProgressbar.setString(String.format("Discovered %s rule(s) for %s ...",
								c.getRulesForTransition().size(), c.getDecisionPoint()));
						discoveryProgressbar.setValue(discoveryProgressbar.getValue() + 1);
					}
				}

			});
		}

		private boolean queryInfrequentTransitions() {

			Set<Transition> consideredTransitions = new HashSet<Transition>();
			Set<Object> selectedNodes = explorerModel.getFilterConfiguration().getSelectedNodes();
			DataPetriNetsWithMarkings net = explorerModel.getModel();

			for (Object node : selectedNodes) {
				if (node instanceof Place && net.getOutEdges((Place) node).size() > 1) {
					consideredTransitions.addAll(PetrinetUtils.getTransitionPostSet(net, (Place) node));
				}
			}

			List<Transition> infrequentlyExecutedTransitions = new ArrayList<Transition>();

			Map<Transition, TransitionStatistics> transitionStatistics = explorerModel
					.getAlignmentInformation().transitionStatistics;
			if (!selectedNodes.isEmpty()) {
				for (Object node : selectedNodes) {
					if (node instanceof Place && net.getOutEdges((Place) node).size() > 1) {
						Place place = (Place) node;
						Set<Transition> transitions = PetrinetUtils.getTransitionPostSet(net, place);
						for (Transition transition : transitions) {
							if (transitionStatistics.get(transition).getObservedMoves() < getMinNumLeaf(place)) {
								infrequentlyExecutedTransitions.add(transition);
							}
						}
						consideredTransitions.addAll(transitions);
					}
				}
			} else {
				for (Place place : net.getPlaces()) {
					if (net.getOutEdges(place).size() > 1) {
						Set<Transition> transitions = PetrinetUtils.getTransitionPostSet(net, place);
						for (Transition transition : transitions) {
							if (transitionStatistics.get(transition).getObservedMoves() < getMinNumLeaf(place)) {
								infrequentlyExecutedTransitions.add(transition);
							}
						}
						consideredTransitions.addAll(transitions);
					}
				}
			}

			if (Iterables.isEmpty(explorerModel.getFilteredAlignments())) {
				String message = String.format(
						"<HTML>With the current filter setting no traces are available to discover guards.</h1></HTML>");
				explorerContext.getUserQuery().showMessage(message, "No traces available");
				return false;
			}

			if (!infrequentlyExecutedTransitions.isEmpty()) {
				String message = String.format(
						"<HTML>Some activities (highlighted in red) are executed fewer times than the current threshold for the minimum number of instances per leaf (Min instances on leafs).<BR/>"
								+ "This may cause rules for the following activities not to be discovered :<BR/><UL><LI> %s",
						Joiner.on("<LI>").join(infrequentlyExecutedTransitions) + "</UL>"
								+ "Please note that in multi-split situation (more than 2 options available) rules may still be discovered."
								+ "</HTML>");
				QueryResult result = explorerContext.getUserQuery().queryCustom("Infrequent activities detected!",
						new JLabel(message), new String[] { "Continue anyway", "Cancel" });
				if (result.getCustom() == 1) {
					return false;
				}
			}
			return true;
		}

		private Set<String> queryLargeNominalAttributeSet(Set<String> selectedAttributes) {
			Set<String> filteredAttributes = new HashSet<String>();
			for (String attrName : selectedAttributes) {
				if (!largeValueSetAttributes.contains(attrName)) {
					Set<String> possibleValues = explorerModel.getLiteralValues()
							.get(AbstractDecisionRuleDiscovery.escapeAttributeName(attrName));
					if (possibleValues != null && possibleValues.size() > NOMINAL_ATTRIBUTE_WARNING_LIMIT) {
						QueryResult result = explorerContext.getUserQuery().queryCustom(
								"Large set of values for a nominal attribute detected!",
								new JLabel("<HTML>The nominal/literal attribute: <I>" + attrName + "</I> has a large (>"
										+ NOMINAL_ATTRIBUTE_WARNING_LIMIT
										+ ") set of possible values, for example:<BR><UL><LI>"
										+ Joiner.on("<LI>").join(Iterators.limit(possibleValues.iterator(), 5))
										+ "...</UL>When including this attribute, the discovery of the data-flow may take a long time, consume large amounts of memory, and return very complicated rules. Do you still want to include the attribute?</HTML>"),
								new String[] { "Include anyway", "Exclude attribute" });
						if (result.getCustom() == 0) {
							filteredAttributes.add(attrName);
							largeValueSetAttributes.add(attrName);
						}
					} else {
						filteredAttributes.add(attrName);
					}
				} else {
					filteredAttributes.add(attrName);
				}
			}
			return filteredAttributes;
		}

	}

	private final class ApplyModelAction implements ActionListener {

		private final ExplorerUpdater explorerUpdater;
		private final ExplorerModel explorerModel;

		private ApplyModelAction(ExplorerUpdater updater, ExplorerModel explorerModel, PluginContext context) {
			this.explorerUpdater = updater;
			this.explorerModel = explorerModel;
		}

		public void actionPerformed(ActionEvent e) {
			if (explorerModel.getDiscoveryResult() != null) {
				explorerModel.getController().addExplorerWithDiscoveredModel(explorerContext, explorerUpdater,
						explorerModel, explorerModel.getDiscoveryResult());
			}
		}

	}

	// Weka does not work with a setting less than 2	
	private static final int MIN_INSTANCES_AT_LEAF = 2;

	private ProMComboBox<DiscoveryAlgorithm> discoveryComboBox;

	private ProMComboCheckBox attributeCCBox;
	private JButton initialValuesButton;
	private Map<String, Object> initialValues;

	private NiceDoubleSlider minLeafSlider;
	private NiceDoubleSlider fitnessThresholdSlider;
	private JCheckBox crossValidationCheckbox;
	private JCheckBox binaryCheckbox;

	private JProgressBar discoveryProgressbar;

	private final JPanel discoveredModelActionPanel;
	private final JButton discoverButton;
	private final JButton applyButton;
	private final JButton resetButton;

	private Set<String> consideredAttributes;
	private final Set<String> largeValueSetAttributes = new HashSet<String>();
	private final Map<String, Class<?>> attributesForDiscovery;
	private final Map<String, Type> attributeTypes;

	public NetViewDiscoveryImpl(final ExplorerContext explorerContext, final ExplorerUpdater updater,
			final ExplorerModel explorerModel) {
		super(explorerContext, updater, explorerModel);

		discoveryComboBox = new ProMComboBox<>(DiscoveryAlgorithm.values());
		discoveryComboBox.setMinimumSize(new Dimension(150, 30));
		discoveryComboBox.setToolTipText("Decision rule discovery algorithm");
		configPanel.addConfigurationComponent(discoveryComboBox);

		attributeCCBox = new ProMComboCheckBox(new Object[] {}, false);
		attributeCCBox.setMinimumSize(new Dimension(150, 30));
		attributeCCBox.setToolTipText("Attributes for decision rule discovery");

		initialValuesButton = SlickerFactory.instance().createButton("Initial Values");
		initialValuesButton.setToolTipText("Initial values assumed for the attributes");
		initialValuesButton.setAction(new UpdateInitialValuesAction());

		JPanel attributesBox = new JPanel();
		attributesBox.setLayout(new BoxLayout(attributesBox, BoxLayout.X_AXIS));
		attributesBox.add(attributeCCBox);
		attributesBox.add(initialValuesButton);
		configPanel.addConfigurationComponent(attributesBox);

		// Initialize attributes
		attributesForDiscovery = getAvailableAttributesForDiscovery();
		attributeTypes = getAttributeTypeMap(attributesForDiscovery);
		attributeCCBox.resetObjs(attributesForDiscovery.keySet(), true);
		consideredAttributes = ImmutableSet.copyOf(attributesForDiscovery.keySet());
		initialValues = new HashMap<>();
		for (String key : attributesForDiscovery.keySet()) {
			initialValues.put(key, guessDefaultValue(key));
		}
		if (explorerModel.getLiteralValues() == null) {
			explorerModel.setLiteralValues(
					getLiteralValuesMap(explorerModel.getAlignment(), initialValues, attributesForDiscovery));
		}

		minLeafSlider = SlickerFactory.instance().createNiceDoubleSlider("Min instances on leafs", 0.001d, 0.5d, 0.25d,
				Orientation.HORIZONTAL);
		configPanel.addConfigurationComponent(minLeafSlider);
		minLeafSlider.addChangeListener(new ChangeListener() {

			public void stateChanged(ChangeEvent e) {
				JSlider source = (JSlider) e.getSource();
				if (!source.getValueIsAdjusting()) {
					updater.post(new NetViewConfigChangedEvent() {

						public Object getSource() {
							return NetViewDiscoveryImpl.this;
						}

					});
				}
			}
		});
		minLeafSlider.addMouseListener(new MouseAdapter() {

			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 2) {
					String userInput = explorerContext.getUserQuery()
							.queryString("Enter value for 'Min instances' parameter", "");
					if (userInput == null || userInput.isEmpty()) {
						explorerContext.getUserQuery().showWarning("Please use format '#.##' (for example, 0.05)",
								"Please enter 'Min instances' parameter!");
					} else {
						try {
							minLeafSlider.setValue(Double.parseDouble(userInput));
						} catch (NumberFormatException e1) {
							explorerContext.getUserQuery().showError("Could not parse input", e1);
						}
					}
				}
			}

		});
		minLeafSlider.getSlider().setToolTipText(
				"<HTML>Minimum number of instances (i.e., traces) for which the decision discovery algorithm still tries to find a rule. For decision trees this controls the stopping critera for splits."
						+ "<BR/>Can be used to avoid overfitting the data. Use a higher value to find rules that are less precise, but most likely simpler.</HTML>");

		fitnessThresholdSlider = SlickerFactory.instance().createNiceDoubleSlider("Min fitness considered", 0.0, 1.0,
				0.8, Orientation.HORIZONTAL);
		fitnessThresholdSlider.getSlider()
				.setToolTipText("Only use traces with a fitness exceeding the configured value.");
		configPanel.addConfigurationComponent(fitnessThresholdSlider);

		JPanel checkboxes = new JPanel();
		checkboxes.setLayout(new BoxLayout(checkboxes, BoxLayout.X_AXIS));
		crossValidationCheckbox = SlickerFactory.instance().createCheckBox("Cross validate", false);
		binaryCheckbox = SlickerFactory.instance().createCheckBox("Binary rules", false);
		checkboxes.add(crossValidationCheckbox);
		checkboxes.add(binaryCheckbox);
		configPanel.addConfigurationComponent(checkboxes);

		discoveryProgressbar = new JProgressBar();
		discoveryProgressbar.setVisible(false);
		discoveryProgressbar.setString("Discovering ...");
		discoveryProgressbar.setStringPainted(true);
		configPanel.addConfigurationComponent(discoveryProgressbar);

		discoverButton = SlickerFactory.instance().createButton("Discover");
		discoverButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		discoverButton
				.setToolTipText("Discover decision rules for the selected places or all places upon empty selection.");
		discoverButton.setAction(new DiscoverGuardsAction());
		configPanel.addConfigurationComponent(discoverButton);

		applyButton = SlickerFactory.instance().createButton("Save as new model");
		applyButton.setToolTipText("Saves the discovered decision rules in a new tab.");
		discoverButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		applyButton.addActionListener(new ApplyModelAction(updater, explorerModel, getContext()));

		resetButton = SlickerFactory.instance().createButton("Discard rules");
		resetButton.setToolTipText("Discards the discovered decision rules.");
		discoverButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		resetButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				explorerModel.resetDiscoveryResult();
				updater.post(new NetViewConfigChangedEvent() {

					public Object getSource() {
						return NetViewDiscoveryImpl.this;
					}

				});
			}
		});

		discoveredModelActionPanel = new JPanel();
		discoveredModelActionPanel.add(applyButton);
		discoveredModelActionPanel.add(resetButton);
		configPanel.addConfigurationComponent(discoveredModelActionPanel);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.dataawareexplorer.explorer.netview.
	 * NetViewAbstractAlignmentAware#updateData()
	 */
	public void updateData() throws NetVisualizationException {
		super.updateData();

		if (hasDiscoveryResult()) {

			// After discovery, omit frequencies			
			DiscoveryResult discoveryResult = explorerModel.getDiscoveryResult();
			for (Transition transition : discoveryResult.getModel().getTransitions()) {
				if (transition instanceof PNWDTransition) {
					if (((PNWDTransition) transition).getGuardExpression() != null) {
						setGuardLabel(explorerModel.getModel(),
								discoveryResult.getModelTransitionMapping().get(transition),
								((PNWDTransition) transition).getGuardExpression());
					}
				}
			}

			buildLocalInfo(discoveryResult);

		} else {
			addFrequencies(explorerModel.getModel(), decorationData, explorerModel.getFilteredAlignments(),
					LabelMode.SECOND);
			colorPlacesBasedOnNumLeafParameter();
		}

	}

	private void buildLocalInfo(DiscoveryResult discoveryResult) {
		for (Transition node : explorerModel.getModel().getTransitions()) {
			Transition newTransition = discoveryResult.getModelTransitionMapping().inverse().get(node);
			if (((PNWDTransition) newTransition).getGuardExpression() != null) {
				infoData.addLocal(node, "Guard",
						((PNWDTransition) newTransition).getGuardExpression().toPrettyString(1), InfoType.STRING);
				double quality = ((PNWDTransition) newTransition).getQuality();
				if (quality > 0) {
					infoData.addLocal(node, "Guard f1-score", quality, InfoType.PERCENTAGE);
				} else {
					infoData.addLocal(node, "Guard f1-score", "N/A", InfoType.STRING);
				}
			}
		}
		for (final Place place : explorerModel.getModel().getPlaces()) {
			final PetrinetDecisionRule decisionRule = discoveryResult.getRules()
					.get(discoveryResult.getModelPlaceMapping().inverse().get(place));
			if (decisionRule != null) {
				if (decisionRule.getRule() instanceof TreeRule) {
					double weightedFMeasure = ((TreeRule) decisionRule.getRule()).getEvaluation().weightedFMeasure();
					infoData.addLocal(place, "Weighted f1-score", weightedFMeasure, InfoType.NUMBER);
				}
				if (decisionRule.getRule() instanceof TreeRule) {
					TreeRule treeRule = (TreeRule) decisionRule.getRule();
					addDecisionTreeVisualisers(place, treeRule);
					decorationData.putAttribute(place, DecorationKey.EXTRALABEL,
							MessageFormat.format("{0,number,#.##}", treeRule.getEvaluation().weightedFMeasure()));
				}
				JMenuItem classifierDetailsItem = new JMenuItem("Show classifier details");
				classifierDetailsItem.addActionListener(new ActionListener() {

					public void actionPerformed(ActionEvent e) {
						JTextArea evaluationPanel = new JTextArea(decisionRule.getRule().toString());
						evaluationPanel.setFont(new Font("monospaced", Font.PLAIN, 12));
						explorerContext.getUserQuery().showCustom(evaluationPanel,
								"Classifier details for place " + place.getLabel(), ModalityType.MODELESS);
					}
				});
				decorationData.addContextMenuItem(place, classifierDetailsItem);
			}
		}

	}

	private void addDecisionTreeVisualisers(final Place node, final TreeRule treeRule) {
		if (treeRule.getClassifier() instanceof Drawable) {
			final Drawable drawable = (Drawable) treeRule.getClassifier();
			JMenuItem prefuseItem = new JMenuItem("Show Decision Tree");
			prefuseItem.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					try {
						final TreePanel treePanel = createTreePanel(node, drawable);
						treePanel.setToolTipText("Use CTRL+I to save as image");
						explorerContext.getUserQuery().showCustom(treePanel,
								"Decision tree for place " + node.getLabel(), ModalityType.MODELESS);
					} catch (Exception e1) {
						explorerContext.getUserQuery().showError("Could not visualise classification result", e1);
					}
				}

				private TreePanel createTreePanel(final Place node, final Drawable drawable) throws Exception {
					PrefuseTreeVisualization treeVis = new PrefuseTreeVisualization();
					final TreePanel treePanel = treeVis.display(drawable.graph(), node.getLabel());
					treePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
							.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK), "saveImage");
					treePanel.getActionMap().put("saveImage", new AbstractAction() {

						private static final long serialVersionUID = 1L;

						public void actionPerformed(ActionEvent e) {
							JFileChooser fileChooser = new JFileChooser();
							Preferences preferences = Preferences.userRoot().node("org.processmining.graphviz");
							fileChooser.setCurrentDirectory(
									new File(preferences.get("lastUsedFolder", new File(".").getAbsolutePath())));
							FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Image", "png");
							fileChooser.addChoosableFileFilter(pngFilter);
							fileChooser.setFileFilter(pngFilter);

							int option = fileChooser.showSaveDialog(treePanel);
							if (option == JFileChooser.APPROVE_OPTION) {
								String filename = fileChooser.getSelectedFile().getPath();
								if (!filename.endsWith(".png")) {
									filename = filename + ".png";
								}
								try (FileOutputStream output = new FileOutputStream(filename)) {
									treePanel.saveImage(output, "png", 1.0d);
								} catch (IOException e1) {
									explorerContext.getUserQuery().showError("Could not save visualisation", e1);
								}
							}
						}

					});
					return treePanel;
				}
			});
			decorationData.addContextMenuItem(node, prefuseItem);
		}
	}

	private void colorPlacesBasedOnNumLeafParameter() {
		for (Place place : explorerModel.getModel().getPlaces()) {
			if (explorerModel.getModel().getOutEdges(place).size() > 1) {
				Map<Transition, TransitionStatistics> transitionStatistics = explorerModel
						.getAlignmentInformation().transitionStatistics;
				Set<Transition> transitions = PetrinetUtils.getTransitionPostSet(explorerModel.getModel(), place);
				for (Transition t : transitions) {
					long minNumLeaf = getMinNumLeaf(place);
					if (transitionStatistics.get(t).getObservedMoves() < minNumLeaf && isInSelectionFilter(place)) {
						decorationData.putAttribute(t, DecorationKey.LINECOLOR, Color.RED);
					} else {
						decorationData.putAttribute(t, DecorationKey.LINECOLOR, Color.BLACK);
					}
					decorationData.putAttribute(place, DecorationKey.EXTRALABEL,
							MessageFormat.format("[{0}]", minNumLeaf));
				}
			}
		}
	}

	private boolean isInSelectionFilter(Place place) {
		return explorerModel.getFilterConfiguration().getSelectedNodes().isEmpty()
				|| explorerModel.getFilterConfiguration().getSelectedNodes().contains(place);
	}

	private boolean hasDiscoveryResult() {
		return explorerModel.getDiscoveryResult() != null;
	}

	private long getMinNumLeaf(Place place) {
		long modelMovesGoingThroughPlace = 0;
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = place.getGraph()
				.getOutEdges(place);
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : outEdges) {
			if (edge.getTarget() instanceof Transition) {
				TransitionStatistics transitionStatistics = explorerModel.getAlignmentInformation().transitionStatistics
						.get(edge.getTarget());
				modelMovesGoingThroughPlace += transitionStatistics.getObservedMoves();
			}
		}
		return Math.max(MIN_INSTANCES_AT_LEAF,
				(long) Math.floor(modelMovesGoingThroughPlace * minLeafSlider.getValue()));
	}

	@Override
	public void updateUI() {
		fitnessThresholdSlider.setValue(getAverageFitness());

		if (hasDiscoveryResult()) {
			discoveredModelActionPanel.setVisible(true);
		} else {
			discoveredModelActionPanel.setVisible(false);
		}

	}

	private final Map<String, Class<?>> getAvailableAttributesForDiscovery() {
		Builder<String, Class<?>> builder = ImmutableMap.builder();
		for (String key : Ordering.natural().sortedCopy(explorerModel.getLogAttributes())) {
			if (!explorerModel.isStandardAttribute(key)) {
				builder.put(key, explorerModel.getLogAttributeType(key));
			}
		}
		return builder.build();
	}

	private DiscoveryAlgorithm getSelectedDiscoveryAlgorithm() {
		return (DiscoveryAlgorithm) discoveryComboBox.getSelectedItem();
	}

	private double getMinPercentageTracesOnLeaf() {
		return minLeafSlider.getValue();
	}

	private boolean isCrossValidate() {
		return crossValidationCheckbox.getModel().isSelected();
	}

	private boolean isBinary() {
		return binaryCheckbox.getModel().isSelected();
	}

	private DecisionTreeConfig createDiscoveryConfiguration() {
		final DecisionTreeConfig config = new DecisionTreeConfig();
		config.setConfidenceTreshold(0.25f);
		config.setUnpruned(false);
		config.setBinarySplit(isBinary());
		config.setMinPercentageObjectsOnLeaf(getMinPercentageTracesOnLeaf());
		config.setCrossValidate(isCrossValidate());
		return config;
	}

	private Set<Place> createConsideredPlaces(DataPetriNet net, Set<Object> selectedNodes) {
		final Set<Place> consideredPlaces = new HashSet<Place>();

		if (!selectedNodes.isEmpty()) {
			for (Object node : selectedNodes) {
				if (node instanceof Place && net.getOutEdges((Place) node).size() > 1) {
					consideredPlaces.add((Place) node);
				}
			}
			if (consideredPlaces.isEmpty()) {
				for (Place p : net.getPlaces()) {
					if (net.getOutEdges(p).size() > 1) {
						consideredPlaces.add(p);
					}
				}
			}
		} else {
			for (Place p : net.getPlaces()) {
				if (net.getOutEdges(p).size() > 1) {
					consideredPlaces.add(p);
				}
			}
		}
		return consideredPlaces;
	}

	private Set<String> getUserSelectedAttributes() {
		Set<String> consideredAttributeSet = new HashSet<>();
		for (Object attr : attributeCCBox.getSelectedItems()) {
			consideredAttributeSet.add((String) attr);
		}
		return consideredAttributeSet;
	}

	private Object guessDefaultValue(String key) {
		Class<?> type = attributesForDiscovery.get(key);
		if (type == String.class) {
			return "N/A";
		} else if (type == Boolean.class) {
			return false; //TODO this is problematic, as there is no reasonable unassigned value for a boolean
		} else if (type == Long.class) {
			return -1l;
		} else if (type == Integer.class) {
			return -1;
		} else if (type == Double.class) {
			return -1.0d;
		} else if (type == Float.class) {
			return -1.0f;
		} else if (type == Date.class) {
			return new Date();
		} else {
			throw new IllegalArgumentException("Unkown type " + type);
		}
	}

	private double getWriteThreshold() {
		return 0.25d; //TODO make configurable
	}

	private void startComputation() {
		discoveryProgressbar.setVisible(true);
	}

	private void finishComputation() {
		discoveryProgressbar.setVisible(false);
	}

	private static Map<String, Type> getAttributeTypeMap(Map<String, Class<?>> attributesForDiscovery) {
		Map<String, Type> attributeTypeMap = new HashMap<>();
		for (Entry<String, Class<?>> entry : attributesForDiscovery.entrySet()) {
			Class<?> type = entry.getValue();
			String varName = AbstractDecisionRuleDiscovery.escapeAttributeName(entry.getKey());
			if (type == Boolean.class) {
				attributeTypeMap.put(varName, Type.BOOLEAN);
			} else if (type == Double.class || type == Float.class) {
				attributeTypeMap.put(varName, Type.CONTINUOS);
			} else if (type == Long.class || type == Integer.class) {
				attributeTypeMap.put(varName, Type.DISCRETE);
			} else if (type == Date.class) {
				attributeTypeMap.put(varName, Type.TIMESTAMP);
			} else if (type == String.class) {
				attributeTypeMap.put(varName, Type.LITERAL);
			} else {
				throw new IllegalArgumentException("Does not support " + type);
			}
		}
		return attributeTypeMap;
	}

	private static Map<String, Set<String>> getLiteralValuesMap(Iterable<XAlignment> alignments,
			Map<String, Object> initialValues, Map<String, Class<?>> attributesForDiscovery) {
		Map<String, Set<String>> retValue = new HashMap<>();
		for (XAlignment alignment : alignments) {
			for (XAlignmentMove move : alignment) {
				for (XAttribute attributeValue : move.getEvent().getAttributes().values()) {
					if (attributeValue instanceof XAttributeLiteral) {
						String attributeName = attributeValue.getKey();
						if (attributesForDiscovery.containsKey(attributeName)) {
							Object value = XUtils.getAttributeValue(attributeValue);
							String varName = AbstractDecisionRuleDiscovery.escapeAttributeName(attributeName);
							Set<String> literalValues = getLazySet(retValue, varName);
							literalValues.add((String) value);
						}
					}
				}
			}
		}
		for (Entry<String, Object> initialEntry : initialValues.entrySet()) {
			Object value = initialEntry.getValue();
			if (value instanceof String) {
				String varName = AbstractDecisionRuleDiscovery.escapeAttributeName(initialEntry.getKey());
				Set<String> existingValues = getLazySet(retValue, varName);
				existingValues.add((String) value);
			}
		}
		return retValue;
	}

	private static Set<String> getLazySet(Map<String, Set<String>> retValue, String varName) {
		Set<String> existingValues = retValue.get(varName);
		if (existingValues == null) {
			existingValues = new TreeSet<>();
			retValue.put(varName, existingValues);
		}
		return existingValues;
	}

}