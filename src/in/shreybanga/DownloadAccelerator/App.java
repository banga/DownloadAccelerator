package in.shreybanga.DownloadAccelerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class App extends Application {
	public static final String TAG = "DownloadAccelerator";

	/*
	 * Database columns
	 */
	public static final String KEY_URL = "url";
	public static final String KEY_FILE_DIR = "directory";
	public static final String KEY_FILE_NAME = "file_name";
	public static final String KEY_THREADS = "threads";

	/*
	 * Download Editor
	 */
	public static final int DOWNLOAD_NEW = 1;
	public static final int DOWNLOAD_DETAILS = 2;

	/*
	 * Downloads Information
	 */
	public static ArrayList<DownloadInfo> infos = new ArrayList<DownloadInfo>();

	/*
	 * Preferences
	 */
	public static final int MAX_THREADS_ALLOWED = 32;

	public static final String PREF_THREADS = "prefThreads";
	public static final String PREF_SAVE_TO = "prefSaveTo";
	public static final String PREF_AUTO_START = "prefAutoStart";

	public static final String DEFAULT_SAVE_TO = "/mnt/sdcard/downloads/";
	public static final String DEFAULT_THREADS = "8";
	public static final boolean DEFAULT_AUTO_START = false;

	private static DownloadsDbAdapter dbAdapter;

	public static class PreferenceWrapper {
		private SharedPreferences preferences;

		public PreferenceWrapper(Context context) {
			preferences = PreferenceManager.getDefaultSharedPreferences(context);
		}

		public String getSaveTo() {
			return preferences.getString(PREF_SAVE_TO, DEFAULT_SAVE_TO);
		}

		public int getThreads() {
			return Integer.parseInt(preferences.getString(PREF_THREADS, DEFAULT_THREADS));
		}

		public boolean getAutoStart() {
			return preferences.getBoolean(PREF_AUTO_START, DEFAULT_AUTO_START);
		}

		public void loadDefaults() {
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(PREF_SAVE_TO, DEFAULT_SAVE_TO).putString(PREF_THREADS, DEFAULT_THREADS).putBoolean(
					PREF_AUTO_START, DEFAULT_AUTO_START).commit();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "Download Accelerator App onCreate()");
	}

	/**
	 * Initializes the database containing downloads information
	 * @param context context for the database
	 */
	public static void initDB(Context context) {
		dbAdapter = new DownloadsDbAdapter(context);
		dbAdapter.open();

		loadDownloadsFromDB();
	}

	public static DownloadInfo newDownload(String url, String fileName, String fileDir, int threads) {
		DownloadInfo info = new DownloadInfo(url, fileName, fileDir, threads);
		synchronized (App.infos) {
			dbAdapter.insertDownload(info);
			infos.add(0, info);
		}
		return info;
	}

	public static PartInfo newDownloadPart(DownloadInfo info, long firstByte, long lastByte) {
		PartInfo part = new PartInfo(firstByte, lastByte);
		synchronized (App.infos) {
			part.info = info;
			dbAdapter.insertPart(info, part);
		}
		return part;
	}

	private static PartInfo[] loadPartsFromDB(long rowId) {
		Cursor c = dbAdapter.fetchAllParts(rowId);
		if (c.getCount() < 1) {
			c.close();
			return null;
		}

		int iRowId = c.getColumnIndex(ThreadsTable.KEY_ROWID);
		int iFirstByte = c.getColumnIndex(ThreadsTable.KEY_FIRST_BYTE);
		int iLastByte = c.getColumnIndex(ThreadsTable.KEY_LAST_BYTE);
		int iDownloaded = c.getColumnIndex(ThreadsTable.KEY_DOWNLOADED);
		int iPaused = c.getColumnIndex(ThreadsTable.KEY_PAUSED);

		long pRowId, firstByte, lastByte, downloaded;
		boolean paused;

		PartInfo[] parts = new PartInfo[c.getCount()];
		c.moveToFirst();
		for (int i = 0; i < parts.length; i++) {
			pRowId = c.getLong(iRowId);
			firstByte = Long.parseLong(c.getString(iFirstByte));
			lastByte = Long.parseLong(c.getString(iLastByte));
			downloaded = Long.parseLong(c.getString(iDownloaded));
			paused = Boolean.parseBoolean(c.getString(iPaused));
			parts[i] = new PartInfo(pRowId, rowId, firstByte, lastByte, downloaded, paused, downloaded < (lastByte-firstByte));
			c.moveToNext();
		}
		c.close();
		return parts;
	}

	private static void loadDownloadsFromDB() {
		Cursor c = dbAdapter.fetchAllDownloads();
		if (c.getCount() <= 0) {
			c.close();
			return;
		}

		int iRowId = c.getColumnIndex(DownloadsTable.KEY_ROWID);
		int iUrl = c.getColumnIndex(DownloadsTable.KEY_URL);
		int iFileDir = c.getColumnIndex(DownloadsTable.KEY_FILE_DIR);
		int iFileName = c.getColumnIndex(DownloadsTable.KEY_FILE_NAME);
		int iCreated = c.getColumnIndex(DownloadsTable.KEY_CREATED);
		int iElapsedTime = c.getColumnIndex(DownloadsTable.KEY_ELAPSED_TIME);
		int iDownloaded = c.getColumnIndex(DownloadsTable.KEY_DOWNLOADED);
		int iTotal = c.getColumnIndex(DownloadsTable.KEY_TOTAL);
		int iThreads = c.getColumnIndex(DownloadsTable.KEY_THREADS);
		int iState = c.getColumnIndex(DownloadsTable.KEY_STATE);

		String url, fileName, fileDir;
		long rowId, created, downloaded, total, elapsedTime;
		int threads, state;

		infos.clear();

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			rowId = c.getLong(iRowId);
			url = c.getString(iUrl);
			fileName = c.getString(iFileName);
			fileDir = c.getString(iFileDir);
			threads = Integer.parseInt(c.getString(iThreads));
			created = Long.parseLong(c.getString(iCreated));
			downloaded = Long.parseLong(c.getString(iDownloaded));
			total = Long.parseLong(c.getString(iTotal));
			elapsedTime = Long.parseLong(c.getString(iElapsedTime));
			state = Integer.parseInt(c.getString(iState));

			PartInfo[] parts = loadPartsFromDB(rowId);
			if (parts == null) {
				Log.e(TAG, "Error reading parts for download with rowId = " + rowId);
				dbAdapter.deleteDownload(rowId);
				continue;
			}
			DownloadInfo info = new DownloadInfo(rowId, url, fileName, fileDir, threads, created, downloaded, total,
					elapsedTime, state, parts);
			for (int i = 0; i < parts.length; i++)
				parts[i].info = info;
			infos.add(info);
		}

		c.close();
	}
	

	/**
	 * Saves all downloads to the DB
	 * 
	 * This is called often, usually when the activity is pausing.
	 * It needs to be as fast and reliable as possible.
	 */
	public static void saveDownloads() {
		synchronized (App.infos) {
			for (Iterator<DownloadInfo> i = App.infos.iterator(); i.hasNext();) {
				DownloadInfo info = i.next();
				if (info.isDirty()) {
					if (dbAdapter.updateDownload(info)) {
						if (info.parts != null) {
							for (int j = 0; j < info.parts.length; j++) {
								PartInfo part = info.parts[j];
								if (part.isDirty() && dbAdapter.updatePart(part, info))
									part.setDirty(false);
							}
						}
					} else {
						dbAdapter.insertDownload(info);
					}
					info.setDirty(false);
				}
			}
		}
	}

	public static void deleteDownload(DownloadInfo info) {
		synchronized (App.infos) {
			if (infos.contains(info))
				infos.remove(info);
			dbAdapter.deleteDownload(info.rowId);
		}
	}

	public static void clearDownloads() {
		synchronized (App.infos) {
			dbAdapter.deleteAll();
			infos.clear();
		}
	}

	/**
	 * Utility method to find activity for viewing a file Makes sure the current
	 * activity is not returned
	 * 
	 * @param ctx
	 *            Context
	 * @param name
	 *            File name
	 */
	public static ResolveInfo findActivityForFile(Context ctx, String name) {
		String ext = name.substring(name.lastIndexOf(".") + 1);
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(new File(name)), MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext));
		List<ResolveInfo> l = ctx.getPackageManager().queryIntentActivities(intent, 0);
		for (Iterator<ResolveInfo> it = l.iterator(); it.hasNext();) {
			ResolveInfo ri = (ResolveInfo) it.next();
			if (!ri.activityInfo.name.equalsIgnoreCase(DownloadsList.CLASS_NAME)) {
				return ri;
			}
		}
		return null;
	}

	/**
	 * Utility method for launching a file
	 * 
	 * @param ctx
	 *            Context
	 * @param fileDir
	 *            Parent directory of the file. Expects path separator to be the
	 *            last character
	 * @param fileName
	 *            Name of the file to launch
	 */
	public static boolean launchFile(Context ctx, String fileDir, String fileName) {
		String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(new File(fileDir + fileName)), MimeTypeMap.getSingleton()
				.getMimeTypeFromExtension(ext));
		try {
			ctx.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			new AlertDialog.Builder(ctx).setMessage("Could not find an app that opens " + fileName).setNeutralButton(
					"OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					}).create().show();
			return false;
		}
		return true;
	}
}
