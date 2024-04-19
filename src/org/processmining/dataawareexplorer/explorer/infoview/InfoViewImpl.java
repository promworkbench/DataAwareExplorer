package org.processmining.dataawareexplorer.explorer.infoview;

import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.events.ModelSelectionChangedEvent;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.Info;
import org.processmining.framework.util.ui.widgets.ProMScrollPane;

import com.google.common.eventbus.Subscribe;

public final class InfoViewImpl implements InfoView {

	private final JPanel infoPanel;
	private InfoData infoData;

	private InfoTable selectedInfoTable = new InfoTable();
	private InfoTable globalInfoTable = new InfoTable();
	private JLabel selectedHeader;

	public InfoViewImpl(ExplorerUpdater updater) {
		super();
		updater.getEventBus().register(this);

		infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.X_AXIS));

		infoPanel.add(Box.createHorizontalStrut(10));

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		selectedHeader = createHeading("NO SELECTION");
		selectedHeader.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		selectedHeader.setVisible(true);
		rightPanel.add(selectedHeader);
		ProMScrollPane selectedNodesScrollPane = new ProMScrollPane(selectedInfoTable.getTable());
		selectedNodesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		rightPanel.add(selectedNodesScrollPane);
		infoPanel.add(rightPanel);

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		ProMScrollPane scollPane = new ProMScrollPane(globalInfoTable.getTable());
		scollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		leftPanel.add(scollPane);
		infoPanel.add(leftPanel);
	}

	@Subscribe
	public void modelSelectionUpdated(ModelSelectionChangedEvent event) {
		updateLocalInfo(event.getSelectedNodes());
	}

	private void updateLocalInfo(Set<Object> selectedNodes) {
		selectedInfoTable.clear();
		if (!selectedNodes.isEmpty()) {
			selectedHeader.setVisible(false);
			selectedInfoTable.getTable().setVisible(true);
			for (Object node : selectedNodes) {
				List<Info> localStatisticsList = infoData.getLocalInfo().get(node);
				selectedInfoTable
						.addRow(new String[] { "<HTML><FONT SIZE=\"4\">" + node.toString() + "</FONT></HTML>", "" });
				selectedInfoTable.addStatistics(localStatisticsList);
			}
		} else {
			selectedInfoTable.getTable().setVisible(false);
			selectedHeader.setVisible(true);
		}
		selectedInfoTable.packTable();
	}

	public void updateUI(InfoData newData, Set<Object> selectedNodes) {
		infoData = newData;

		updateGlobalInfo();
		updateLocalInfo(selectedNodes);

		infoPanel.validate();
	}

	private void updateGlobalInfo() {
		globalInfoTable.clear();
		globalInfoTable.addStatistics(infoData.getGlobalInfo());
		globalInfoTable.packTable();
	}

	public Component getComponent() {
		return infoPanel;
	}

	private static JLabel createHeading(String label) {
		JLabel title = new JLabel(label);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		title.setForeground(null);
		return title;
	}

}