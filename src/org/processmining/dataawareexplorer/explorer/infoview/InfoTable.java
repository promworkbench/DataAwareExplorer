package org.processmining.dataawareexplorer.explorer.infoview;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.processmining.dataawareexplorer.explorer.infoview.InfoData.Info;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.framework.util.ui.widgets.ProMTableWithoutPanel;

import com.google.common.collect.Lists;

public class InfoTable {
	
	private static final class WrappingCellRenderer extends JTextArea implements TableCellRenderer {

		private static final long serialVersionUID = -1765141380547807845L;

		public WrappingCellRenderer() {
			setLineWrap(true);
			setWrapStyleWord(true);
			setEditable(false);
			setBackground(null);
			setMargin(new Insets(1, 1, 1, 1));
			setRows(1);
		}

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			setText((String) value);
			if (isSelected) {
				setBackground(table.getSelectionBackground());
				setForeground(table.getSelectionForeground());
			} else {
				setBackground(null);
				setForeground(null);
			}
			// Important sets correct width
			TableColumn tableColumn = table.getColumnModel().getColumn(column);
			setSize(new Dimension(tableColumn.getWidth(), Short.MAX_VALUE));
			return this;
		}

	}

	/**
	 * Only to be used on event thread!
	 */
	static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();

	/**
	 * Only to be used on event thread!
	 */
	static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance();

	/**
	 * Only to be used on event thread!
	 */
	static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance();

	/**
	 * Only to be used on event thread!
	 */
	static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();

	static {
		PERCENT_FORMAT.setMaximumFractionDigits(1);
		NUMBER_FORMAT.setMaximumFractionDigits(2);
	}

	private final DefaultTableModel model;
	private final ProMTableWithoutPanel table;

	public InfoTable() {
		super();

		model = new DefaultTableModel(new String[] { "Property", "Value" }, 0) {

			private static final long serialVersionUID = -4264134361143928349L;

			public boolean isCellEditable(int row, int column) {
				return false;
			}

		};
		table = new ProMTableWithoutPanel(model);
		setupTable(table);
	}

	public void addRow(String[] data) {
		model.addRow(data);
	}

	public void addStatistics(List<Info> localStatisticsList) {
		for (Info statistic : Lists.reverse(localStatisticsList)) {
			addRow(new String[] { statistic.getLabel(), convert(statistic.getData(), statistic.getType()) });
		}			
	}

	public void clear() {
		model.getDataVector().clear();
	}

	private String convert(Object data, InfoType type) {
		switch (type) {
			case INTEGER :
				return formatInteger(((Number) data).longValue());
			case NUMBER :
				return formatNumber(((Number) data).doubleValue());
			case PERCENTAGE :
				return formatPercentage(((Number) data).doubleValue());
			case TIME :
				return formatTime((Date) data);
			default :
				return data.toString();
		}
	}

	/**
	 * 
	 * @param number
	 * @return
	 */
	private String formatNumber(double number) {
		return NUMBER_FORMAT.format(number);
	}

	/**
	 * @param number
	 * @return
	 */
	private String formatInteger(long number) {
		return INTEGER_FORMAT.format(number);
	}

	/**
	 * @param time
	 * @return
	 */
	private String formatTime(Date time) {
		return DATE_FORMAT.format(time);
	}

	/**
	 * @param number
	 * @return
	 */
	private String formatPercentage(double number) {
		return PERCENT_FORMAT.format(number);
	}

	public void packTable() {
		packTable(table);
	}
	
	private void setupTable(ProMTableWithoutPanel table) {
		table.setSelectionBackground(new Color(60, 60, 60));
		table.setIntercellSpacing(new Dimension(2, 5));
		table.setGridColor(Color.GRAY);
		table.setShowGrid(true);
		table.setShowVerticalLines(true);
		table.setShowHorizontalLines(true);
		table.setBorder(BorderFactory.createLineBorder(Color.GRAY));
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		//table.getColumnModel().getColumn(0).setMaxWidth(200);
		table.getColumnModel().getColumn(1).setCellRenderer(new WrappingCellRenderer());
		table.setCellSelectionEnabled(true);
		table.setTableHeader(null);
	}

	private static void packTable(ProMTableWithoutPanel table) {
		for (int row = 0; row < table.getRowCount(); row++) {
			int rowHeight = table.getRowHeight();
			for (int column = 0; column < table.getColumnCount(); column++) {
				Component comp = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
				rowHeight = Math.max(rowHeight, comp.getPreferredSize().height);
			}
			table.setRowHeight(row, rowHeight + 3);
		}
	}

	public JTable getTable() {
		return table;
	}
}
