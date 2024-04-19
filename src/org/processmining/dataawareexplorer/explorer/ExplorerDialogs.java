package org.processmining.dataawareexplorer.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.processmining.dataawareexplorer.explorer.ExplorerInterface.CustomQueryResult;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.QueryResult;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.ResultOption;
import org.processmining.dataawareexplorer.explorer.ExplorerInterface.StandardQueryResult;
import org.processmining.framework.util.ui.widgets.ProMScrollPane;

public final class ExplorerDialogs {

	private static final int DEFAULT_HEIGHT = 800;
	private static final int DEFAULT_WIDTH = 1000;

	private ExplorerDialogs() {
		super();
	}

	public static QueryResult queryOkCancel(Component parent, String queryTitle, JComponent queryComponent) {
		ProMScrollPane scrollPane = new ProMScrollPane(queryComponent);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		if (queryComponent.getPreferredSize().getHeight() > DEFAULT_HEIGHT
				|| queryComponent.getPreferredSize().getWidth() > DEFAULT_WIDTH) {
			scrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		}
		scrollPane.setMaximumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		scrollPane.setAlignmentX(JComponent.TOP_ALIGNMENT);
		int result = JOptionPane.showConfirmDialog(parent, scrollPane, queryTitle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {
			return new StandardQueryResult(ResultOption.OK);
		} else { // also close
			return new StandardQueryResult(ResultOption.CANCEL);
		}
	}

	public static QueryResult queryYesNo(Component parentPanel, String queryTitle, JComponent queryComponent) {
		ProMScrollPane scrollPane = new ProMScrollPane(queryComponent);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		scrollPane.setPreferredSize(null);
		scrollPane.setMaximumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		scrollPane.setAlignmentX(JComponent.TOP_ALIGNMENT);
		int result = JOptionPane.showConfirmDialog(parentPanel, scrollPane, queryTitle, JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			return new StandardQueryResult(ResultOption.YES);
		} else { // also close
			return new StandardQueryResult(ResultOption.NO);
		}
	}

	public static QueryResult queryCustom(Component parentPanel, String queryTitle, JComponent queryComponent,
			String[] options) {
		ProMScrollPane scrollPane = new ProMScrollPane(queryComponent);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		scrollPane.setPreferredSize(null);
		scrollPane.setMaximumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		scrollPane.setAlignmentX(JComponent.TOP_ALIGNMENT);
		if (queryComponent.getPreferredSize().getHeight() > DEFAULT_HEIGHT
				|| queryComponent.getPreferredSize().getWidth() > DEFAULT_WIDTH) {
			scrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		}
		int result = JOptionPane.showOptionDialog(parentPanel, scrollPane, queryTitle, JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		return new CustomQueryResult(result);
	}

	public static void showInfo(Component parent, String queryTitle, JComponent queryComponent) {
		ProMScrollPane scrollPane = new ProMScrollPane(queryComponent);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		if (queryComponent.getPreferredSize().getHeight() > DEFAULT_HEIGHT
				|| queryComponent.getPreferredSize().getWidth() > DEFAULT_WIDTH) {
			scrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		}
		scrollPane.setMaximumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		scrollPane.setAlignmentX(JComponent.TOP_ALIGNMENT);
		JOptionPane.showMessageDialog(parent, scrollPane, queryTitle, JOptionPane.INFORMATION_MESSAGE);
	}
	

	public static void showCustom(JComponent component, String dialogTitle, ModalityType modalityType) {
		ProMScrollPane scrollPane = new ProMScrollPane(component);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		if (component.getPreferredSize().getHeight() > DEFAULT_HEIGHT
				|| component.getPreferredSize().getWidth() > DEFAULT_WIDTH) {
			scrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		}
		scrollPane.setMaximumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		scrollPane.setAlignmentX(JComponent.TOP_ALIGNMENT);
		Object[] options = { "OK" };
		JOptionPane optionPane = new JOptionPane(scrollPane, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
				options, options[0]);
		JDialog dialog = optionPane.createDialog(dialogTitle);
		dialog.setResizable(true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setModalityType(modalityType);
		dialog.pack();
		dialog.setVisible(true);
	}

}
