package in.shreybanga.DownloadAccelerator;

import in.shreybanga.DownloadAccelerator.DownloadInfo.DownloadState;
import android.app.Activity;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import in.shreybanga.DownloadAccelerator.R;

public class DownloadDetails extends Activity {
	public static final String KEY_INFO = "info";

	private ImageView imgIcon;
	private TextView txtName, txtStatus, txtURL, txtLocation, txtThreads;
	private DownloadInfo info;
	private LinearLayout grpThreads;
	private ProgressBar[] pbThreads;
	private TextView[] tvThreadStatuses;
	private Button btnStop, btnLaunch, btnRestart;
	private ToggleButton btnPause;

	private class UIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			updateUI();
		}
	}

	private UIHandler handler;

	private void updateUI() {
		txtStatus.setText(info.downloader.status);

		if (info.state == DownloadState.STARTED || info.state == DownloadState.PAUSED) {
			btnPause.setVisibility(View.VISIBLE);
			btnPause.setChecked(info.state == DownloadState.PAUSED);
			btnStop.setVisibility(View.VISIBLE);
			btnRestart.setVisibility(View.VISIBLE);
			btnLaunch.setVisibility(View.GONE);
		} else if (info.state == DownloadState.TOSTART) {
			btnPause.setVisibility(View.GONE);
			btnStop.setVisibility(View.GONE);
			btnLaunch.setVisibility(View.GONE);
			btnRestart.setVisibility(View.GONE);
		} else {
			btnPause.setVisibility(View.GONE);
			btnStop.setVisibility(View.GONE);
			btnRestart.setVisibility(View.VISIBLE);
			if (info.state == DownloadState.FINISHED) {
				btnLaunch.setVisibility(View.VISIBLE);
			}
		}
		updateThreadViews();
		handler.sendEmptyMessageDelayed(0, 200);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download_details);

		imgIcon = (ImageView) findViewById(R.id.imgIcon);
		txtName = (TextView) findViewById(R.id.txtName);
		txtStatus = (TextView) findViewById(R.id.txtStatus);
		txtURL = (TextView) findViewById(R.id.txtURL);
		txtLocation = (TextView) findViewById(R.id.txtLocation);
		txtThreads = (TextView) findViewById(R.id.txtThreads);
		grpThreads = (LinearLayout) findViewById(R.id.grpThreads);
		btnPause = (ToggleButton) findViewById(R.id.pause);
		btnStop = (Button) findViewById(R.id.stop);
		btnRestart = (Button) findViewById(R.id.restart);
		btnLaunch = (Button) findViewById(R.id.launch);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			info = App.infos.get(extras.getInt(KEY_INFO));
			if (info != null) {
				txtName.setText(info.fileName);
				txtStatus.setText(info.downloader.status);
				txtURL.setText(info.url);
				txtLocation.setText(info.fileDir);
				txtThreads.setText(String.valueOf(info.threads));
				addThreadViews();
				btnLaunch.setVisibility(View.GONE);

				ResolveInfo ri = App.findActivityForFile(this, info.fileName);
				if(ri != null)
					imgIcon.setImageDrawable(ri.loadIcon(getPackageManager()));

				handler = new UIHandler();
				handler.sendEmptyMessage(0);
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		handler.removeMessages(0);
	}

	@Override
	protected void onResume() {
		super.onPause();
		handler.sendEmptyMessage(0);
	}

	private void addThreadViews() {
		pbThreads = new ProgressBar[info.threads];
		tvThreadStatuses = new TextView[info.threads];
		LayoutInflater inflater = getLayoutInflater();
		for (int i = 0; i < info.threads; i++) {
			View v = inflater.inflate(R.layout.threads_row, null);
			pbThreads[i] = (ProgressBar) v.findViewById(R.id.progress);
			tvThreadStatuses[i] = (TextView) v.findViewById(R.id.status);
			grpThreads.addView(v);
		}
	}

	private void updateThreadViews() {
		FilePartDownloader[] parts = info.downloader.parts;
		if(parts == null) {
			for (int i = 0; i < info.threads; i++) {
				pbThreads[i].setVisibility(View.GONE);
				tvThreadStatuses[i].setVisibility(View.GONE);
			}
		} else {
			for (int i = 0; i < parts.length; i++) {
				FilePartDownloader part = parts[i];
				ProgressBar pb = pbThreads[i];
				TextView tv = tvThreadStatuses[i];
				if(part == null) {
					pb.setVisibility(View.VISIBLE);
					pb.setIndeterminate(true);
					tv.setVisibility(View.VISIBLE);
					tv.setText("spawning...");
				} else {
					pb.setVisibility(View.VISIBLE);
					tv.setVisibility(View.VISIBLE);
					pb.setIndeterminate(false);
					pb.setProgress((int) (100 * (part.info.downloaded / (float) part.info.total)));
					tv.setText(Formatter.formatShortFileSize(this, part.info.downloaded) + "/"
							+ Formatter.formatShortFileSize(this, part.info.total));
				}
			}
		}
	}

	public void onPauseDownloadClicked(View view) {
		ToggleButton tb = (ToggleButton) view;
		if (info.downloader != null) {
			if (tb.isChecked()) {
				if (!info.downloader.pauseDownload()) {
					tb.setChecked(true);
					Log.w(App.TAG, "Could not pause " + info.fileName);
				} else {
					Log.i(App.TAG, "Pausing " + info.fileName);
				}
			} else {
				if (!info.downloader.resumeDownload()) {
					tb.setChecked(false);
					Log.w(App.TAG, "Could not resume " + info.fileName);
				} else {
					Log.i(App.TAG, "Resuming " + info.fileName);
				}
			}
		}
	}

	public void onStopDownloadClicked(View view) {
		if (info.downloader != null) {
			info.downloader.cancel(true);
			Log.i(App.TAG, "Canceling " + info.fileName);
		}
	}

	public void onLaunchFileClicked(View view) {
		App.launchFile(this, info.fileDir, info.fileName);
	}

	public void onRestartClicked(View view) {
		info.downloader.restartDownload();
	}
}
