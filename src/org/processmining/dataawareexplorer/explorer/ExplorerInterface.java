package org.processmining.dataawareexplorer.explorer;

import java.awt.Dialog.ModalityType;

import javax.swing.JComponent;

public interface ExplorerInterface {

	public interface QueryResult {

		public ResultOption getStandard();

		public int getCustom();

	}

	public static final class StandardQueryResult implements QueryResult {

		private ResultOption option;

		public StandardQueryResult(ResultOption option) {
			this.option = option;
		}

		public ResultOption getStandard() {
			return option;
		}

		public int getCustom() {
			return -1;
		}
	}

	public static final class CustomQueryResult implements QueryResult {

		private int option;

		public CustomQueryResult(int option) {
			this.option = option;
		}

		public ResultOption getStandard() {
			return ResultOption.CUSTOM;
		}

		public int getCustom() {
			return option;
		}
	}

	public enum ResultOption {
		OK, CANCEL, YES, NO, CUSTOM
	}

	public void showMessage(String message, String title);
	
	public void showError(String errorTitle, Exception e);
	
	public void showError(String errorMessage, String errorTitle, Exception e);

	public void showWarning(String warningMessage, String warningTitle);
	
	public void showCustom(JComponent component, String dialogTitle, ModalityType modalityType);

	public QueryResult queryOkCancel(String queryTitle, JComponent queryComponent);

	public QueryResult queryYesNo(String queryTitle, JComponent queryComponent);

	public QueryResult queryCustom(String queryTitle, JComponent queryComponent, String[] options);

	public String queryString(String query, String initialLabel);


}
