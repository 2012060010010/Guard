package com.vdisk.CloudFileChoose;
import java.util.ArrayList;

import com.idwtwt.extrabackup.R;
import com.vdisk.android.fileChoose.DisplayUtil;
import com.vdisk.net.VDiskAPI.Entry;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;

public class VdiskFileDialog extends Dialog {

	/**
	 * 以打开文件模式打开文件对话框，有可能是文件夹也有可能是文件,可多选，最终返回值为一个File对象列表。
	 */
	public static final int FILE_MODE_OPEN_MULTI = 0;

	/**
	 * 以打开文件模式打开文件对话框，只能选择文件夹而不是文件，可多选，最终返回值为一个File对象列表。
	 */
	public static final int FILE_MODE_OPEN_FOLDER_MULTI = 1;

	/**
	 * 以打开文件模式打开文件对话框，只能选择文件而不是文件夹，可多选，最终返回值为一个File对象列表。
	 */
	public static final int FILE_MODE_OPEN_FILE_MULTI = 2;

	/**
	 * 以打开文件模式打开文件对话框，有可能是文件夹也有可能是文件,最终返回值为一个长度为1的File对象列表。
	 */
	public static final int FILE_MODE_OPEN_SINGLE = 3;

	/**
	 * 以打开文件模式打开文件对话框，只能选择文件夹而不是文件，最终返回值为一个长度为1的File对象列表。
	 */
	public static final int FILE_MODE_OPEN_FOLDER_SINGLE = 4;

	/**
	 * 以打开文件模式打开文件对话框，只能选择文件而不是文件夹，最终返回值为一个长度为1的File对象列表。
	 */
	public static final int FILE_MODE_OPEN_FILE_SINGLE = 5;

	public VdiskFileDialog(Context context) {
		super(context);
	}

	public VdiskFileDialog(Context context, int theme) {
		super(context, theme);
	}

	public VdiskFileDialog(Context context, boolean cancelable,
			OnCancelListener cancelListener) {
		super(context, cancelable, cancelListener);
	}

	public interface VdiskFileDialogListener {
		public void onFileSelected(ArrayList<Entry> filesEntry);

		public void onFileCanceled();
	}

	public static class Builder {
		private int fileMode = VdiskFileDialog.FILE_MODE_OPEN_MULTI;
		private String initialPath = "/backup" ;                   //初始文件地址
		private VdiskFileDialogListener fileSelectListener;
		private EntryFileDialogView dialogView;
		private Context context;
		private boolean canceledOnTouchOutside = true;
		private boolean cancelable = true;
		private String title = "选择文件";

		public Builder(Context context) {
			this.context = context;
		}

		public Builder setCanceledOnTouchOutside(boolean flag) {
			canceledOnTouchOutside = flag;
			return this;
		}

		public Builder setCancelable(boolean flag) {
			cancelable = flag;
			return this;
		}

		public Builder setFileMode(int fileMode) {
			this.fileMode = fileMode;
			return this;
		}

		public Builder setInitialPath(String initialPath) {
			this.initialPath = initialPath;
			return this;
		}

		public Builder setTitle(String title) {
			this.title = title;
			return this;
		}

		public Builder setFileSelectListener(
				VdiskFileDialogListener fileSelectListener) {
			this.fileSelectListener = fileSelectListener;
			return this;
		}

		/**
		 * 必须强制设置dialog的大小，因为ListView大小必须确定，否则ListView的Adapter的getView会执行很多遍,
		 * 次数取决于listview最终能显示多少项。
		 * 
		 * @return
		 */
		public VdiskFileDialog create(int width, int height) {
			final VdiskFileDialog dialog = new VdiskFileDialog(context);
			dialogView = new EntryFileDialogView(context);
			dialogView.setFileMode(fileMode);
			dialogView.setInitialPath(initialPath);
			dialogView.openFolder();
			dialog.setTitle(title);
			dialog.setCancelable(cancelable);
			dialog.setCanceledOnTouchOutside(canceledOnTouchOutside);
			dialog.setContentView(dialogView, new LayoutParams(
					LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
			if (width > 0 && height > 0) {
				dialog.getWindow().setLayout(width, height);
			}
			Button okButton = (Button) dialogView
					.findViewById(R.id.button_dialog_file_ok);
			Button cancelButton = (Button) dialogView
					.findViewById(R.id.button_dialog_file_cancel);
			okButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
				
					if (fileSelectListener != null) {
						if (dialogView.getSelectedFiles().size() > 0) {
							fileSelectListener.onFileSelected(dialogView
									.getSelectedFiles());
						} else {
							fileSelectListener.onFileCanceled();
						}
					}
					dialog.dismiss();
				}
			});
			cancelButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
				
					if (fileSelectListener != null) {
						fileSelectListener.onFileCanceled();
					}
					dialog.dismiss();
				}
			});
			return dialog;
		}

		/**
		 * 使得FileDialog大小和activity一样,在Activity创建完成之前，返回的数字可能不对
		 * 
		 * @param activity
		 * @return
		 */
		public VdiskFileDialog create(Activity activity) {
			int width = DisplayUtil.getWindowWidth(activity);
			int height = DisplayUtil.getWindowHeight(activity);
			return create(width, height);
		}

	}

}
