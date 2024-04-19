/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationData;
import org.processmining.datapetrinets.utils.TikZUtil;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DPNAsDot;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.GuardDisplayMode;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.PlaceDisplayMode;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverterPlugin.VariableDisplayMode;
import org.processmining.datapetrinets.visualization.graphviz.RatioAwareDotPanel;
import org.processmining.framework.util.ui.widgets.helper.ProMUIHelper;
import org.processmining.models.graphbased.AbstractGraphElement;
import org.processmining.models.graphbased.directed.AbstractDirectedGraphEdge;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.graphviz.dot.Dot;
import org.processmining.plugins.graphviz.dot.Dot.GraphDirection;
import org.processmining.plugins.graphviz.dot.DotEdge;
import org.processmining.plugins.graphviz.dot.DotElement;
import org.processmining.plugins.graphviz.dot.DotNode;
import org.processmining.plugins.graphviz.visualisation.DotPanel;
import org.processmining.plugins.graphviz.visualisation.NavigableSVGPanel;
import org.processmining.plugins.graphviz.visualisation.listeners.DotElementSelectionListener;
import org.processmining.plugins.graphviz.visualisation.listeners.SelectionChangedListener;
import org.processmining.xesalignmentextension.XAlignmentExtension.MoveType;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;

import com.google.common.collect.ImmutableSet;
import com.kitfox.svg.Group;
import com.kitfox.svg.RenderableElement;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.xml.StyleAttribute;

public final class NetVisualizationPanel extends JPanel {

	private final class ShowTikzAction extends AbstractAction {

		private static final long serialVersionUID = 1L;

		private ShowTikzAction() {
			super("Show TikZ code");
		}

		public void actionPerformed(ActionEvent e) {
			TikZUtil tikZUtil = new TikZUtil();
			tikZUtil.setDecoration(currentDecoration.getAttributes());
			String tikzCode = tikZUtil.transformToTikZ(explorerModel.getModel(), explorerModel.getInitialMarking(),
					explorerModel.getFinalMarkings() != null ? explorerModel.getFinalMarkings()[0] : new Marking());
			explorerContext.getUserQuery().showCustom(new JTextArea(tikzCode), "TikZ code", ModalityType.MODELESS);
		}

	}

	private final class MouseClickedOnWhitespaceListener extends MouseAdapter {

		private final SelectionChangedListener<DotElement> listener;

		private MouseClickedOnWhitespaceListener(SelectionChangedListener<DotElement> listener) {
			this.listener = listener;
		}

		public void mouseClicked(MouseEvent e) {
			Point point = e.getPoint();
			if (dotPanel.getDotPanel().isInImage(point)) {
				Point2D pointImageCoordinates = dotPanel.getDotPanel().transformUser2Image(point);
				try {
					@SuppressWarnings("unchecked")
					List<List<RenderableElement>> elements = dotPanel.getSVG().pick(pointImageCoordinates, false, null);
					//clear selected alignments on click on white space
					if (!hasDotElements(elements)) {
						listener.selectionChanged(ImmutableSet.<DotElement>of());
					}
				} catch (SVGException e1) {
				}
			}
		}

		private boolean hasDotElements(List<List<RenderableElement>> elements) throws SVGException {
			StyleAttribute classAttribute = new StyleAttribute("class");
			for (List<RenderableElement> path : elements) {
				for (RenderableElement element : path) {
					if (element instanceof Group) {
						Group group = (Group) element;

						//get the class
						group.getPres(classAttribute);

						if (classAttribute.getStringValue().equals("node")
								|| classAttribute.getStringValue().equals("edge")) {
							//we have found a node or edge
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	private final class ToggleVariablesAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			if (explorerModel.getVariableDisplayMode() == VariableDisplayMode.AUTO_LAYOUT) {
				explorerModel.setVariableDisplayMode(VariableDisplayMode.HIDDEN);
			} else {
				explorerModel.setVariableDisplayMode(VariableDisplayMode.AUTO_LAYOUT);
			}
			updateData(currentDecoration);
			updatePanel();
		}
	}

	private final class ToggleGuardsAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			if (explorerModel.getGuardDisplayMode() == GuardDisplayMode.HIDDEN) {
				explorerModel.setGuardDisplayMode(GuardDisplayMode.EDGES);
			} else {
				explorerModel.setGuardDisplayMode(GuardDisplayMode.HIDDEN);
			}
			updateData(currentDecoration);
			updatePanel();
		}
	}

	private final class TogglePlacesAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			if (explorerModel.getPlaceDisplayMode() == PlaceDisplayMode.BASIC) {
				explorerModel.setPlaceDisplayMode(PlaceDisplayMode.DETAILED);
			} else {
				explorerModel.setPlaceDisplayMode(PlaceDisplayMode.BASIC);
			}
			updateData(currentDecoration);
			updatePanel();
		}
	}

	private final class FreezeGraphAction extends AbstractAction {
		private static final long serialVersionUID = -1540697035814396137L;

		public void actionPerformed(ActionEvent e) {
			NavigableSVGPanel svgPanel;
			try {

				SVGDiagram svgDiagram = dotPanel.getSVG();
				SVGUniverse universe = svgDiagram.getUniverse();
				Field field = universe.getClass().getDeclaredField("cachedReader");
				field.setAccessible(true);
				field.set(universe, null);

				SVGUniverse frozenUniverse = universe.duplicate();
				svgPanel = new NavigableSVGPanel(frozenUniverse.getDiagram(dotPanel.getSVG().getXMLBase()));
				JFrame frame = new JFrame();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setTitle("Snapshot: " + DateFormat.getDateTimeInstance().format(new Date()));
				frame.setBackground(Color.WHITE);
				frame.add(svgPanel);
				frame.pack();
				showFrame(frame);
			} catch (ClassNotFoundException | IOException | IllegalArgumentException | IllegalAccessException
					| NoSuchFieldException | SecurityException e1) {
				ProMUIHelper.showErrorMessage(dotPanel, e1.toString(), "Error duplicating SVG");
			}
		}

		private void showFrame(JFrame frame) {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice[] gs = ge.getScreenDevices();
			if (gs.length > 1) {
				for (int i = 0; i < gs.length; i++) {
					if (gs[i] != dotPanel.getGraphicsConfiguration().getDevice()) {
						JFrame dummy = new JFrame(gs[i].getDefaultConfiguration());
						frame.setLocationRelativeTo(dummy);
						frame.setExtendedState(Frame.MAXIMIZED_BOTH);
						frame.setAlwaysOnTop(true);
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
			frame.setVisible(true);
		}
	}

	private final static class ElementSelectionListenerColorImpl implements DotElementSelectionListener {

		private final DotPanel dotPanel;

		private String defaultStrokeWidth;
		private String defaultStrokeDashArray;

		private ElementSelectionListenerColorImpl(DotPanel dotPanel, DotElement node) {
			this.dotPanel = dotPanel;
			defaultStrokeWidth = DotPanel.getAttributeOf(DotPanel.getSVGElementOf(dotPanel.getSVG(), node),
					"stroke-width");
			defaultStrokeDashArray = DotPanel.getAttributeOf(DotPanel.getSVGElementOf(dotPanel.getSVG(), node),
					"stroke-dasharray");
		}

		public void colorSelectedElement(final DotPanel dotPanel, DotElement element) {
			defaultStrokeWidth = DotPanel.setCSSAttributeOf(dotPanel.getSVG(), element, "stroke-width", "3");
			defaultStrokeDashArray = DotPanel.setCSSAttributeOf(dotPanel.getSVG(), element, "stroke-dasharray", "5,5");
		}

		public void selected(DotElement element, SVGDiagram image) {
			colorSelectedElement(dotPanel, element);
		}

		public void deselected(DotElement element, SVGDiagram image) {
			DotPanel.setCSSAttributeOf(dotPanel.getSVG(), element, "stroke-width", defaultStrokeWidth);
			DotPanel.setCSSAttributeOf(dotPanel.getSVG(), element, "stroke-dasharray", defaultStrokeDashArray);
		}
	}

	private static final long serialVersionUID = -27813170597114136L;

	private final ExplorerModel explorerModel;
	private final ExplorerContext explorerContext;

	private final RatioAwareDotPanel dotPanel;
	private final Dot defaultDot;

	private DPNAsDot dpnAsDot;
	private Set<PetrinetNode> currentlySelectedNodes = new HashSet<>();
	private String defaultStrokeDashArray = "";
	private ModelDecorationData currentDecoration;

	private SVGDiagram svgDiagram;

	private boolean isUpdating = false;

	public NetVisualizationPanel(final ExplorerUpdater updatableExplorer, ExplorerContext explorerContext,
			final ExplorerModel explorerModel) {
		super();
		this.explorerContext = explorerContext;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		this.explorerModel = explorerModel;

		defaultDot = new Dot();
		defaultDot.addNode("loading ...");
		defaultDot.setDirection(GraphDirection.leftRight);

		dotPanel = new RatioAwareDotPanel(defaultDot) {

			private static final long serialVersionUID = 7643473633290028596L;

			public void changeDot(Dot dot, SVGDiagram diagram, boolean resetView) {
				if (dot != this.getDot()) {
					super.changeDot(dot, diagram, resetView);
					addColoringSelectionListeners(dotPanel);
				}
				// Re-construct old selection
				try {
					isUpdating = true;
					for (DirectedGraphNode node : currentlySelectedNodes) {
						DotNode element = dpnAsDot.getMapping().get(node);
						// Might be a new DPN
						if (element != null) {
							dotPanel.select(element);
						}
					}
				} finally {
					isUpdating = false;
				}
			}

		};
		dotPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

		dotPanel.getHelperControlsExplanations().add("freeze in new window");
		dotPanel.getHelperControlsShortcuts().add("ctrl n");
		dotPanel.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_MASK),
				"newWindow");
		dotPanel.getActionMap().put("newWindow", new FreezeGraphAction());

		dotPanel.getHelperControlsExplanations().add("show/hide variables");
		dotPanel.getHelperControlsShortcuts().add("ctrl v");
		dotPanel.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK),
				"toggleVariables");
		dotPanel.getActionMap().put("toggleVariables", new ToggleVariablesAction());

		dotPanel.getHelperControlsExplanations().add("show/hide guards");
		dotPanel.getHelperControlsShortcuts().add("ctrl g");
		dotPanel.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK),
				"toggleGuards");
		dotPanel.getActionMap().put("toggleGuards", new ToggleGuardsAction());

		dotPanel.getHelperControlsExplanations().add("show/hide place labels");
		dotPanel.getHelperControlsShortcuts().add("ctrl p");
		dotPanel.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_MASK),
				"togglePlaceLabels");
		dotPanel.getActionMap().put("togglePlaceLabels", new TogglePlacesAction());

		dotPanel.getHelperControlsExplanations().add("show TikZ code");
		dotPanel.getHelperControlsShortcuts().add("ctrl t");
		dotPanel.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK),
				"showTikz");
		dotPanel.getActionMap().put("showTikz", new ShowTikzAction());

		addColoringSelectionListeners(dotPanel);
		add(dotPanel);
	}

	private void addColoringSelectionListeners(final RatioAwareDotPanel dotPanel) {
		for (final DotNode node : dotPanel.getDotPanel().getNodes()) {
			node.addSelectionListener(new ElementSelectionListenerColorImpl(dotPanel.getDotPanel(), node));
		}
	}

	public void updateData(ModelDecorationData decoration) {
		this.currentDecoration = decoration;

		// Remember current selection
		currentlySelectedNodes.clear();
		for (DotElement element : dotPanel.getSelectedElements()) {
			PetrinetNode node = dpnAsDot.getMapping().inverse().get(element);
			currentlySelectedNodes.add(node);
		}

		dpnAsDot = DPNGraphvizConverter.convertDPN(explorerModel.getModel(), explorerModel.getInitialMarking(),
				explorerModel.getFinalMarkings(), explorerModel.getGuardDisplayMode(), // guards are added later by the visualization
				explorerModel.getVariableDisplayMode(), explorerModel.getPlaceDisplayMode(), GraphDirection.leftRight,
				decoration.getAttributes());

		addExtraElements(decoration);

		for (final Entry<AbstractGraphElement, List<JMenuItem>> menuEntry : decoration.getMenuItems().entrySet()) {
			DotNode node = dpnAsDot.getMapping().get(menuEntry.getKey());

			final JPopupMenu menu = new JPopupMenu();
			for (JMenuItem item : menuEntry.getValue()) {
				menu.add(item);
			}

			node.addMouseListener(new MouseAdapter() {

				@Override
				public void mousePressed(MouseEvent e) {
					if (e.isPopupTrigger()) {
						showMenu(e);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (e.isPopupTrigger()) {
						showMenu(e);
					}
				}

				private void showMenu(MouseEvent e) {
					menu.show(e.getComponent(), e.getX(), e.getY());
				}

			});
		}

		svgDiagram = dotPanel.generateSVG(dpnAsDot.getDot());

	}

	private void addExtraElements(ModelDecorationData decoration) {
		for (AbstractGraphElement element : decoration.getExtraElements()) {
			if (element instanceof AbstractDirectedGraphEdge) {
				AbstractDirectedGraphEdge<?, ?> edge = (AbstractDirectedGraphEdge<?, ?>) element;
				DotNode sourceNode = dpnAsDot.getMapping().get(edge.getSource());
				DotNode targetNode = dpnAsDot.getMapping().get(edge.getTarget());
				DotEdge dotEdge = dpnAsDot.getDot().addEdge(sourceNode, targetNode);
				Map<DecorationKey, Object> edgeDecoration = decoration.getAttributes().get(element);
				if (edgeDecoration != null) {
					for (Entry<DecorationKey, Object> entry : edgeDecoration.entrySet()) {
						entry.getKey().decorate(dotEdge, entry.getValue());
					}
				}
			}
		}
	}

	public void colorSelection() {
		for (DotEdge edge : dpnAsDot.getEdgeMapping().values()) {
			DotPanel.setCSSAttributeOf(svgDiagram, edge, "stroke-dasharray", defaultStrokeDashArray);
		}
		for (XAlignment alignment : explorerModel.getSelectedAlignments()) {
			for (XAlignmentMove move : alignment) {
				if (move.getType() == MoveType.MODEL || move.getType() == MoveType.SYNCHRONOUS) {
					Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = explorerModel
							.getInEdgeMap().get(move.getActivityId());
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : inEdges) {
						DotEdge dotEdge = dpnAsDot.getEdgeMapping().get(edge);
						if (dotEdge != null) {
							Group element = DotPanel.getSVGElementOf(svgDiagram, dotEdge);
							DotPanel.setCSSAttributeOf(element, "stroke-dasharray", "5,5");
						} else {
							System.out.println("Cannot find edge from " + edge.getSource() + " to " + edge.getTarget());
						}
					}
					Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = explorerModel
							.getOutEdgeMap().get(move.getActivityId());
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : outEdges) {
						DotEdge dotEdge = dpnAsDot.getEdgeMapping().get(edge);
						if (dotEdge != null) {
							Group element = DotPanel.getSVGElementOf(svgDiagram, dotEdge);
							DotPanel.setCSSAttributeOf(element, "stroke-dasharray", "5,5");
						} else {
							System.out.println("Cannot find edge from " + edge.getSource() + " to " + edge.getTarget());
						}
					}
				}
			}
		}
	}

	public void updatePanel() {
		if (dpnAsDot != null) {
			if (dotPanel.getDot() == defaultDot) {
				dotPanel.changeDot(dpnAsDot.getDot(), svgDiagram, false);
				try {
					dotPanel.resetView();
				} catch (NoninvertibleTransformException e) {
				}
			} else {
				dotPanel.changeDot(dpnAsDot.getDot(), svgDiagram, false);
			}
		} else {
			throw new RuntimeException("updateData not called before updating UI!");
		}
	}

	public Set<Object> getSelectedNodes() {
		if (dotPanel != null) {
			Set<Object> selectedNodes = new HashSet<>();
			Set<DotElement> selectedElements = dotPanel.getSelectedElements();
			for (DotElement element : selectedElements) {
				PetrinetNode node = dpnAsDot.getMapping().inverse().get(element);
				if (node != null) {
					selectedNodes.add(node);
				} else {
					throw new IllegalStateException("Invalid mapping between UI and data!");
				}
			}
			return selectedNodes;
		} else {
			return Collections.<Object>emptySet();
		}
	}

	public void addUserSelectionChangedListener(final SelectionChangedListener<DotElement> selectionChangedListener) {
		dotPanel.addSelectionChangedListener(new SelectionChangedListener<DotElement>() {

			public void selectionChanged(Set<DotElement> selectedElements) {
				if (!isUpdating) {
					selectionChangedListener.selectionChanged(selectedElements);
					repaint();
				}
			}
		});
	}

	public void addMouseClearSelectionListener(final SelectionChangedListener<DotElement> selectionChangedListener) {
		dotPanel.getDotPanel().addMouseListener(new MouseClickedOnWhitespaceListener(selectionChangedListener));
	}

	public DPNAsDot getDpnAsDot() {
		return dpnAsDot;
	}

	public void setDpnAsDot(DPNAsDot dpnAsDot) {
		this.dpnAsDot = dpnAsDot;
	}

	public SVGDiagram getSvgDiagram() {
		return svgDiagram;
	}

	public void setSvgDiagram(SVGDiagram svgDiagram) {
		this.svgDiagram = svgDiagram;
	}

	public void showProgress() {
	}

	public void hideProgress() {
	}

}