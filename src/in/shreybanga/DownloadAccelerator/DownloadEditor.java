package in.shreybanga.DownloadAccelerator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import in.shreybanga.DownloadAccelerator.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class DownloadEditor extends Activity {
	EditText edtURL, edtLocation;
	TextView txtThreads;
	SeekBar sbThreads;
	Button btnDownload;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download_editor);
		edtURL = (EditText) findViewById(R.id.edtURL);
		edtLocation = (EditText) findViewById(R.id.edtLocation);
		txtThreads = (TextView) findViewById(R.id.txtThreads);
		sbThreads = (SeekBar) findViewById(R.id.sbThreads);
		btnDownload = (Button) findViewById(R.id.btnDownload);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String url = extras.getString(App.KEY_URL);
			String fileDir = extras.getString(App.KEY_FILE_DIR);
			String fileName = extras.getString(App.KEY_FILE_NAME);
			int threads = extras.getInt(App.KEY_THREADS);

			if (url != null)
				edtURL.setText(url);
			if (fileDir != null) {
				if (fileName != null) {
					edtLocation.setText(fileDir + fileName);
				} else {
					edtLocation.setText(fileDir);
				}
			}

			sbThreads.setProgress(threads - 1);
			txtThreads.setText(String.valueOf(threads));
			sbThreads.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					txtThreads.setText(String.valueOf(progress + 1));
				}
			});
		}

		btnDownload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				final Intent intent = new Intent();
				final Bundle extras = new Bundle();

				final int threads = sbThreads.getProgress()+1;

				// Validate url location
				String url = edtURL.getText().toString(); 
				try {
					new URL(url);
				} catch(MalformedURLException e) {
					Toast.makeText(DownloadEditor.this, "Invalid URL", Toast.LENGTH_LONG).show();
					edtURL.requestFocus();
					return;
				}

				extras.putString(App.KEY_URL, url);

				// Validate file save location
				String filePath = edtLocation.getText().toString();
				final File file = new File(filePath);
				if (file.exists()) {
					AlertDialog.Builder builder = new AlertDialog.Builder(DownloadEditor.this);
					builder.setMessage(file.getName() + " already exists at this location. Overwrite?")
					.setCancelable(false)
					.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							finish(intent, extras, file, threads);
						}})
					.setNegativeButton("No", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}});
					builder.create().show();
					return;
				}
				File parent = file.getParentFile();
				if (!parent.exists() && !parent.mkdirs()) {
					Toast.makeText(DownloadEditor.this, "Invalid path in Save to", Toast.LENGTH_LONG).show();
					edtLocation.requestFocus();
					return;
				}

				finish(intent, extras, file, threads);
			}
		});
	}

	private void finish(Intent intent, Bundle extras, File file, int threads) {
		extras.putString(App.KEY_FILE_NAME, file.getName());
		extras.putString(App.KEY_FILE_DIR, file.getParent() + File.separator);
		extras.putInt(App.KEY_THREADS, threads);
		intent.putExtras(extras);
		setResult(RESULT_OK, intent);
		finish();
	}
}
