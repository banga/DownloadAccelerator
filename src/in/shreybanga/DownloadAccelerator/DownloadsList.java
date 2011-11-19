package in.shreybanga.DownloadAccelerator;

import in.shreybanga.DownloadAccelerator.App.PreferenceWrapper;
import in.shreybanga.DownloadAccelerator.DownloadInfo.DownloadState;

import java.io.File;
import java.util.Iterator;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import in.shreybanga.DownloadAccelerator.R;

public class DownloadsList extends ListActivity implements OnItemClickListener, OnItemLongClickListener {
	public static final boolean DEBUG = true;

	public static final String CLASS_NAME = DownloadsList.class.getName();
	private NetworkStateReceiver receiver;
	private PreferenceWrapper preferences;
	public DownloadsAdapter listAdapter;
	public boolean isConnected;

	/**
	 * Menu items
	 */
	public int count = 0; /* DEBUG only */
	public static final int INSERT_ID = Menu.FIRST;
	public static final int CLEAR_ID = Menu.FIRST + 1;
	private static final int SETTINGS_ID = Menu.FIRST + 2;

	private class UIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			updateUI();
		}
	}

	private UIHandler UIhandler;

	private void updateUI() {
		getListView().invalidateViews();
		UIhandler.sendEmptyMessageDelayed(0, 300);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.downloads_list);
		preferences = new PreferenceWrapper(this);

		getListView().addHeaderView(LayoutInflater.from(this).inflate(R.layout.downloads_list_header, null));
		Animation btnThrobber = AnimationUtils.loadAnimation(this, R.anim.new_throbber);
		findViewById(R.id.main_btnNew).startAnimation(btnThrobber);
		findViewById(R.id.main_btnSettings).startAnimation(btnThrobber);

		// Load data from DB
		initDownloadsFromDB();
		registerForContextMenu(getListView());
		handleIntent(getIntent());

		ListView lv = getListView();
		lv.setOnItemClickListener(this);
		lv.setOnItemLongClickListener(this);
		
		// Check network connectivity
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		isConnected = (ni != null && ni.isConnected());

		// Network connectivity listener
		receiver = new NetworkStateReceiver(this);

		UIhandler = new UIHandler();
		UIhandler.sendEmptyMessage(0);
	}

	/*
	 * List item events
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,	long id) {
		launchDownloadDetailsActivity(position);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if(position > 0) {
			final DownloadInfo info = (DownloadInfo)listAdapter.getItem(position-1);
			if(info.state == DownloadState.STOPPED) {
				showDownloadStoppedDialog(position, info);
			} else if(info.state == DownloadState.FINISHED) {
				showDownloadFinishedDialog(position, info);
			} else {
				showDownloadStartedDialog(position, info);
			}
			return true;
		}
		return false;
	}

	/**
	 * Open download details when a list item is clicked:
	 * @param position of the list item
	 */
	private void launchDownloadDetailsActivity(int position) {
		// Ignore the header item
		if(position > 0) {
			Intent intent = new Intent(DownloadsList.this, DownloadDetails.class);
			intent.putExtra(DownloadDetails.KEY_INFO, position-1);
			startActivity(intent);
		}
	}

	/**
	 * Dialog confirming delete
	 * Also asks if file should be deleted from disk
	 */
	private void showDeleteDownloadDialog(final DownloadInfo info) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Also delete file from disk?")
		.setCancelable(false)
	    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	       public void onClick(DialogInterface dialog, int id) {
	    	   boolean deleted = new File(info.fileDir+info.fileName).delete();
	    	   Toast.makeText(DownloadsList.this, 
	    			   "File " + (deleted ? "deleted" : "could not be deleted"), Toast.LENGTH_SHORT).show();
	    	   App.deleteDownload(info);
	    	   listAdapter.notifyDataSetChanged();
	       }
	    })
	    .setNeutralButton("No", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				App.deleteDownload(info);
			}
		})
	    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	    	public void onClick(DialogInterface dialog, int id) {
	    		dialog.cancel();
	    	}
	    });
		builder.create().show();	
	}

	/**
	 * Dialog to show while downloading
	 */
	private void showDownloadStartedDialog(final int position, final DownloadInfo info) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Options");
		final boolean paused = (info.state == DownloadState.PAUSED);
		final CharSequence[] items_started = {"Details...", paused ? "Resume" : "Pause", "Cancel", "Delete...", "Re-download"};
		builder.setItems(items_started, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		    	switch(item) {
		    	case 0: launchDownloadDetailsActivity(position); break;
		    	case 1: if(paused) {
		    				info.downloader.resumeDownload();
				    	} else {
				    		info.downloader.pauseDownload();
				    	}
		    			break;
		    	case 2: info.downloader.cancel(false); break;
		    	case 3: showDeleteDownloadDialog(info); break;
		    	case 4: info.downloader.restartDownload(); break;
		    	}
		    }
		});
		builder.create().show();
	}

	/**
	 * Dialog to show when download is finished
	 */
	private void showDownloadFinishedDialog(final int position, final DownloadInfo info) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Options");
		final CharSequence[] items_finished = {"Details...", "Open...", "Delete...", "Re-download"};
		builder.setItems(items_finished, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		    	switch(item) {
		    	case 0: launchDownloadDetailsActivity(position); break;
		    	case 1: App.launchFile(DownloadsList.this, info.fileDir, info.fileName); break;
		    	case 2: showDeleteDownloadDialog(info); break;
		    	case 3: info.downloader.restartDownload(); break;
		    	}
		    }
		});
		builder.create().show();
	}

	/**
	 * Dialog to show when download is canceled
	 */
	private void showDownloadStoppedDialog(final int position, final DownloadInfo info) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder = new AlertDialog.Builder(this);
		builder.setTitle("Options");
		final CharSequence[] items_canceled = {"Details", "Delete...", "Re-download"};
		builder.setItems(items_canceled, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		    	switch(item) {
		    	case 0: launchDownloadDetailsActivity(position); break;
		    	case 1: showDeleteDownloadDialog(info); break;
		    	case 2: info.downloader.restartDownload(); break;
		    	}
		    }
		});
		builder.create().show();
	}

//	@Override
//	protected void onSaveInstanceState(Bundle outState) {
//		App.saveDownloads();
//		super.onSaveInstanceState(outState);
//	}

	@Override
	protected void onPause() {
		new Thread() {
			@Override
			public void run() {
				App.saveDownloads();
			}
		}.start();
		unregisterReceiver(receiver);
		UIhandler.removeMessages(0);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(receiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
		UIhandler.sendEmptyMessage(0);
	}

	@Override
	protected void onNewIntent(Intent intent) {
	}

	/*
	 * Menu item events
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, INSERT_ID, 0, R.string.menu_insert);
		menu.add(0, CLEAR_ID, 0, R.string.menu_clear);
		menu.add(0, SETTINGS_ID, 0, R.string.menu_settings);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case INSERT_ID:
			if(DEBUG) {
				Intent i = new Intent(this, DownloadEditor.class);
				i.putExtra(App.KEY_URL, "http://www.biometrics.gov/Documents/fingerprintrec.pdf");
				i.putExtra(App.KEY_FILE_DIR, preferences.getSaveTo());
				i.putExtra(App.KEY_FILE_NAME, "file" + (++count) + ".pdf");
				i.putExtra(App.KEY_THREADS, preferences.getThreads());
				startActivityForResult(i, App.DOWNLOAD_NEW);
			} else {
				onNewClicked(null);
			}
			return true;
		case CLEAR_ID:
			clearDownloads();
			return true;
		case SETTINGS_ID:
			onSettingsClicked(null);
			return true;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	/**
	 * Clear button
	 */
	public void onClearClicked(View view) {
		clearDownloads();
	}

	/**
	 * New button
	 */
	public void onNewClicked(View view) {
		Intent i = new Intent(this, DownloadEditor.class);
		i.putExtra(App.KEY_FILE_DIR, preferences.getSaveTo());
		i.putExtra(App.KEY_THREADS, preferences.getThreads());
		startActivityForResult(i, App.DOWNLOAD_NEW);
	}

	/**
	 * Settings button
	 */
	public void onSettingsClicked(View view) {
		Intent i = new Intent(this, PreferencesEditor.class);
		startActivity(i);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if(resultCode == RESULT_OK) {
			Bundle extras = intent.getExtras();
			
			switch(requestCode) {
			case App.DOWNLOAD_NEW:
				String url = extras.getString(App.KEY_URL);
				String fileDir = extras.getString(App.KEY_FILE_DIR);
				String fileName = extras.getString(App.KEY_FILE_NAME);
				int threads = extras.getInt(App.KEY_THREADS);
				startDownload(url, fileName, fileDir, threads);
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if(Intent.ACTION_VIEW.equals(action)) {
        	Uri uri = intent.getData();
        	if (uri.getScheme().equals("http")) {
        		String name = uri.getLastPathSegment();

        		if(preferences.getAutoStart()) {
        			startDownload(uri.toString(), name, preferences.getSaveTo(), preferences.getThreads());
        		} else {
	        		// TODO: Check if file should actually be downloaded
	    			Intent i = new Intent(this, DownloadEditor.class);
	    			i.putExtra(App.KEY_URL, uri.toString());
	    			i.putExtra(App.KEY_FILE_DIR, preferences.getSaveTo());
	    			i.putExtra(App.KEY_FILE_NAME, name);
	    			i.putExtra(App.KEY_THREADS, preferences.getThreads());
	    			startActivityForResult(i, App.DOWNLOAD_NEW);
        		}
        	}
        }
        Log.i(App.TAG,"Action: " + action);
	}

	/**
	 * Create an adapter to read from DB and populate the view
	 * and create DownloadInfo objects corresponding to each download.
	 */
	private void initDownloadsFromDB() {
		listAdapter = new DownloadsAdapter(this, App.infos);
		setListAdapter(listAdapter);
		App.initDB(this);
		for(Iterator<DownloadInfo> i = App.infos.iterator(); i.hasNext();) {
			final DownloadInfo info = i.next();
			info.downloader = new FileDownloader(info, this);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					info.downloader.execute();
				}
			});
		}
		listAdapter.notifyDataSetChanged();
	}

	public void startDownload(String url, String fileName, String fileDir, int threads) {
		final DownloadInfo info = App.newDownload(url, fileName, fileDir, threads);
		listAdapter.notifyDataSetChanged();
		info.downloader = new FileDownloader(info, this);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				info.downloader.execute();
			}
		});
	}

	public void clearDownloads() {
		for(Iterator<DownloadInfo> i = App.infos.iterator(); i.hasNext();) {
			DownloadInfo info = i.next();
			info.downloader.cancel(false);
		}
		App.clearDownloads();
		listAdapter.notifyDataSetChanged();
	}
}