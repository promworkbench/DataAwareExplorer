package org.processmining.dataawareexplorer.explorer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.processmining.dataawareexplorer.explorer.events.DataChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.NetViewChangedEvent;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.explorer.work.AlignmentUtil;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryResult;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.plugins.balancedconformance.config.BalancedProcessorConfiguration;
import org.processmining.plugins.utils.ProvidedObjectHelper;

public final class ExplorerControllerImpl implements ExplorerController {

	private final class RemoveAction extends AbstractAction {

		private static final long serialVersionUID = 1L;
		private final DataAwareExplorer explorer;

		private RemoveAction(DataAwareExplorer explorer) {
			this.explorer = explorer;
		}

		public void actionPerformed(ActionEvent e) {
			if (tabbedPane.getTabCount() > 1) {
				int index = explorers.indexOf(explorer);
				tabbedPane.remove(index);
				explorers.remove(index);
			}
		}
	}

	private final static class TitleWithCloseButton extends JPanel {

		private final static class CloseButton extends JButton {

			private static final long serialVersionUID = 1L;

			private final MouseListener buttonMouseListener = new MouseAdapter() {
				public void mouseEntered(MouseEvent e) {
					Component component = e.getComponent();
					if (component instanceof AbstractButton) {
						AbstractButton button = (AbstractButton) component;
						button.setBorderPainted(true);
					}
				}

				public void mouseExited(MouseEvent e) {
					Component component = e.getComponent();
					if (component instanceof AbstractButton) {
						AbstractButton button = (AbstractButton) component;
						button.setBorderPainted(false);
					}
				}
			};

			public CloseButton(final JTabbedPane tabbedPane) {
				int size = 17;
				setPreferredSize(new Dimension(size, size));
				setToolTipText("Close");
				setUI(new BasicButtonUI());
				setContentAreaFilled(false);
				setFocusable(false);
				setBorder(BorderFactory.createEtchedBorder());
				setBorderPainted(false);
				addMouseListener(buttonMouseListener);
				setRolloverEnabled(true);
			}

			public void updateUI() {
			}

			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g.create();
				if (getModel().isPressed()) {
					g2.translate(1, 1);
				}
				g2.setStroke(new BasicStroke(2));
				g2.setColor(Color.BLACK);
				if (getModel().isRollover()) {
					g2.setColor(Color.RED);
				}
				int delta = 6;
				g2.drawLine(delta, delta, getWidth() - delta - 1, getHeight() - delta - 1);
				g2.drawLine(getWidth() - delta - 1, delta, delta, getHeight() - delta - 1);
				g2.dispose();
			}
		}

		private static final long serialVersionUID = 1L;

		public TitleWithCloseButton(final JTabbedPane tabbedPane, final String title, final Action closeAction) {
			setOpaque(false);
			JLabel label = new JLabel(title);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
			add(label, BorderLayout.CENTER);
			JButton closeButton = new CloseButton(tabbedPane);
			closeButton.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					closeAction.actionPerformed(e);
				}
			});
			add(closeButton, BorderLayout.EAST);
		}

	}

	private final static class TitleWithoutCloseButton extends JPanel {

		private static final long serialVersionUID = 1L;

		public TitleWithoutCloseButton(final JTabbedPane tabbedPane, final String title) {
			setOpaque(false);
			JLabel label = new JLabel(title);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
			add(label, BorderLayout.CENTER);
		}

	}

	private final JTabbedPane tabbedPane = new JTabbedPane();

	private final List<DataAwareExplorer> explorers = new ArrayList<>();
	@SuppressWarnings("unused")
	private final DataAwareExplorer baseExplorer;
	@SuppressWarnings("unused")
	private int currentSelection;

	public ExplorerControllerImpl(DataAwareExplorer rootExplorerInstance) {
		super();
		tabbedPane.setOpaque(true);
		tabbedPane.setBackground(Color.WHITE);
		//TODO this might affect other plug-ins
		UIManager.put("TabbedPane.contentAreaColor", Color.WHITE);
		tabbedPane.setUI(new BasicTabbedPaneUI() {

			protected void installDefaults() {
				Color defaultColor = UIManager.getColor("TabbedPane.selected");
				UIManager.put("TabbedPane.selected", Color.WHITE);
				super.installDefaults();
				lightHighlight = Color.BLACK;
				highlight = Color.BLACK;
				shadow = Color.WHITE;
				darkShadow = Color.BLACK;
				focus = Color.WHITE;

				tabInsets = new Insets(0, 4, 0, 4);
				selectedTabPadInsets = new Insets(2, 2, 1, 1);
				tabAreaInsets = new Insets(1, 2, 0, 2);
				contentBorderInsets = new Insets(1, 2, 2, 3);

				UIManager.put("TabbedPane.selected", defaultColor);
			}

		});
		addNonClosableExplorer("Base Model", rootExplorerInstance);
		this.currentSelection = 0;
		this.baseExplorer = rootExplorerInstance;
		tabbedPane.addChangeListener(new ChangeListener() {

			public void stateChanged(ChangeEvent e) {
				currentSelection = tabbedPane.getSelectedIndex();
			}
		});
	}

	private void addNonClosableExplorer(String title, DataAwareExplorer explorer) {
		explorer.setParent(this);
		explorers.add(explorer);
		tabbedPane.addTab(title, explorer.getComponent());
		int index = tabbedPane.indexOfComponent(explorer.getComponent());
		tabbedPane.setTabComponentAt(index, new TitleWithoutCloseButton(tabbedPane, title));
		tabbedPane.setBackgroundAt(index, Color.WHITE);
	}

	public DataAwareExplorer addExplorer(String title, final DataAwareExplorer explorer) {
		explorer.setParent(this);
		explorers.add(explorer);
		tabbedPane.addTab(title, explorer.getComponent());
		int index = tabbedPane.indexOfComponent(explorer.getComponent());
		tabbedPane.setTabComponentAt(index, new TitleWithCloseButton(tabbedPane, title, new RemoveAction(explorer)));
		tabbedPane.setBackgroundAt(index, Color.WHITE);
		return explorer;
	}

	public JComponent getComponent() {
		return tabbedPane;
	}

	public DataAwareExplorer addExplorerWithDiscoveredModel(ExplorerContext explorerContext,
			ExplorerUpdater explorerUpdater, ExplorerModel explorerModel, DiscoveryResult discoveryResult) {
		PluginContext context = explorerContext.getContext();
		DataAwareExplorer newExplorer = new DataAwareExplorer(context, explorerModel.getLog(),
				discoveryResult.getModel());
		newExplorer.setExecutor(explorerContext.getExecutor());
		ExplorerModel newModel = newExplorer.getModel();

		BalancedProcessorConfiguration alignmentConfig = AlignmentUtil.createAlignmentConfig(discoveryResult,
				explorerModel);

		newModel.setViewMode(ViewMode.MODEL);
		newModel.setAlignmentConfiguration(alignmentConfig);

		String initialLabel = discoveryResult.getAlgorithm() + " (minInstances:"
				+ NumberFormat.getNumberInstance().format(discoveryResult.getConfig().getMinPercentageObjectsOnLeaf())
				+ ")";
		String label = null;
		do {
			label = explorerContext.getUserQuery().queryString("Choose a name for the new model", initialLabel);
		} while (label != null && label.isEmpty());

		if (label == null)
			return null;

		discoveryResult.getModel().getAttributeMap().put(AttributeMap.LABEL, label);

		ProvidedObjectHelper.publish(context, discoveryResult.getModel().getLabel(), discoveryResult.getModel(),
				DataPetriNet.class, false);
		ProvidedObjectHelper.setFavorite(context, discoveryResult.getModel());

		newExplorer = addExplorer(discoveryResult.getModel().getLabel(), newExplorer);

		explorerModel.resetDiscoveryResult();
		explorerUpdater.getEventBus().post(new DataChangedEvent() {

			public Object getSource() {
				return ExplorerControllerImpl.this;
			}
		});

		return newExplorer;
	}

	public void switchExplorer(DataAwareExplorer explorer, ViewMode viewMode) {
		int index = tabbedPane.indexOfComponent(explorer.getComponent());
		tabbedPane.setSelectedIndex(index);
		explorer.setCurrentMode(viewMode);
		explorer.getEventBus().post(new NetViewChangedEvent() {

			public Object getSource() {
				return ExplorerControllerImpl.this;
			}
		});
	}

}