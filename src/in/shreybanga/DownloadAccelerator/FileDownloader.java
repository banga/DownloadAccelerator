package in.shreybanga.DownloadAccelerator;

import in.shreybanga.DownloadAccelerator.DownloadInfo.DownloadState;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.os.AsyncTask;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;


public class FileDownloader extends AsyncTask<Void, Void, DownloadInfo> {
	public static String userAgent = "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.2.3) Gecko/20100401";

	private DownloadsList list;
	private DownloadInfo info;
	private Thread[] threads;
	public FilePartDownloader[] parts;
	public String status;

	private boolean restarting = false;

	public FileDownloader(DownloadInfo info, DownloadsList list) {
		this.info = info;
		this.list = list;
		status = "Starting...";
	}

	@Override
	protected void onPreExecute() {
	}

	private boolean setStatus(String str) {
		info.setDirty(true);
		if(isCancelled()) {
			status = "Cancelled";
			info.state = DownloadState.STOPPED;
			return true;
		}
		status = str;
		return false;
	}

	/*
	 * Partitions the file into multiple parts if it has not already been partitioned
	 * (such as after loading from a database)
	 * TODO: There should be consistency checks when resuming from old partitions saved in DB
	 */
	private void makeParts() {
		if(info.parts == null) {
			info.parts = new PartInfo[info.threads];

			long bytesPerThread = info.total / info.threads, firstByte = 0, lastByte = 0;
	
			for (int i = 0; i < info.threads; i++) {
				lastByte = firstByte + bytesPerThread;
				if (i == info.threads - 1)
					lastByte = info.total - 1;

				info.parts[i] = App.newDownloadPart(info, firstByte, lastByte);
				firstByte = lastByte + 1;
			}
		}
	}

	private int getContentLength(String urlString) {
		URL url;
		try {
			url = new URL(urlString);
			while (true) {
				// Check network connection first
				if(!list.isConnected) {
					if(setStatus("Waiting for a network connection..."))
						return -1;
					while(!list.isConnected)
						;
				}

				try {
					if(setStatus("Connecting to " + url.getHost()))
						return -1;

					URLConnection connection = url.openConnection();
					connection.setRequestProperty("User-Agent", userAgent);
					connection.connect();
					return connection.getContentLength();
				} catch (IOException e) {
					PrintError("Could not open connection", e);
					setStatus("Could not connect. Retrying...");
				}
			}
		} catch (MalformedURLException e) {
			return -1;
		}
	}

	@Override
	protected DownloadInfo doInBackground(Void... params) {
		// Check for canelled/finished downloads being loaded from database
		if(info.state == DownloadState.FINISHED || info.state == DownloadState.STOPPED) {
			return info;
		}

		File file = new File(info.fileDir, info.fileName);

		/**
		 * Make a connection and partition the download.
		 * When recovering from the DB, parts are already existing so we can skip this part
		 * TODO: we must check if the file has changed on the server
		 */
		if(info.parts == null) {
			if(setStatus("Getting file size..."))
				return info;
			info.total = getContentLength(info.url);
			if (info.total == -1) {
				PrintError("File size not returned by server", null);
				return null;
			}
			
			if(setStatus("Checking multi-part support..."))
				return info;
			try {
				URL url = new URL(info.url);
				URLConnection connection = url.openConnection();
				connection.setRequestProperty("Range", "bytes=0-127");
				connection.connect();
				int count = connection.getContentLength();
				if(count != 128) {
					PrintError("Multi-part downloads not supported by server", null);
					return null;
				}
			} catch (IOException e1) {
			}
			
			Log.w(App.TAG, "File is " + info.total + " bytes");
	
			if(setStatus("Creating " + info.fileName + "...")) 
				return info;
	
			try {
				createEmptyFile(file, info.total);
			} catch (IOException e) {
				PrintError("Could not create output file. Check space and permissions.", e);
				return null;
			}
		}

		if(setStatus("Spawning threads..."))
			return info;

		if(info.state == DownloadState.TOSTART || info.state == DownloadState.STARTED) {
			info.state = DownloadState.STARTED;
			info.lastStarted = System.currentTimeMillis();
		}

		makeParts();
		URL url;
		try {
			url = new URL(info.url);
		} catch (MalformedURLException e1) {
			return null;
		}
		parts = new FilePartDownloader[info.threads];
		threads = new Thread[info.threads];
		for(int i = 0; i < info.threads; i++) {
			parts[i] = new FilePartDownloader(list, info.parts[i], info, url, file);
			threads[i] = new Thread(parts[i]);
			threads[i].start();
		}

		while (info.downloaded < info.total
				&& (info.state != DownloadState.FINISHED && info.state != DownloadState.STOPPED)) {
			if (info.state == DownloadState.PAUSED) {
				if(setStatus("Download paused " + Formatter.formatFileSize(list, info.downloaded) + "/"
						+ Formatter.formatFileSize(list, info.total)))
					return info;
				while(info.state == DownloadState.PAUSED) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
					}
				}
			} else {
				int downloaded = 0;
				for (int i = 0; i < info.threads; i++)
					downloaded += parts[i].info.downloaded;
				info.downloaded = downloaded;
				if(setStatus(Formatter.formatFileSize(list, info.downloaded) + "/" + 
						Formatter.formatFileSize(list, info.total) + " "	+ getFormattedSpeed() + " " + getETA()))
					return info;
			}
		}

		if(info.state == DownloadState.STARTED) {
			info.elapsedTime += System.currentTimeMillis() - info.lastStarted;
			info.lastStarted = 0;
			info.state = DownloadState.FINISHED;
		}

		if(isCancelled())
			setStatus("Canceled");

		return info;
	}

	@Override
	protected void onProgressUpdate(Void... values) {
	}

	@Override
	protected void onPostExecute(DownloadInfo result) {
		if (threads != null) {
			for (int i = 0; i < info.threads; i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					PrintError("Thread interrupted", e);
				}
			}
		}
		if (info.downloaded == info.total) {
			Log.w(App.TAG, "File " + info.fileName + " download finished in " + getFormattedDuration());
			setStatus(Formatter.formatFileSize(list, info.total) + " downloaded in " + getFormattedDuration() + " "
					+ getFormattedSpeed());
			info.state = DownloadState.FINISHED;
		} else {
			Log.w(App.TAG, "File " + info.fileName + " download cancelled");
			setStatus("Canceled");
			info.state = DownloadState.STOPPED;
		}
	}

	/*
	 * Utility methods
	 */
	boolean pauseDownload() {
		if (info.state == DownloadState.STARTED) {
			if (parts != null) {
				for (int i = 0; i < parts.length; i++)
					parts[i].info.paused = true;
			}
			info.state = DownloadState.PAUSED;
			info.elapsedTime = getTotalTime();
			setStatus("Pausing...");
			return true;
		}
		return false;
	}

	boolean resumeDownload() {
		if (info.state == DownloadState.PAUSED) {
			if (parts != null) {
				for (int i = 0; i < parts.length; i++)
					parts[i].info.paused = false;
			}
			info.state = DownloadState.STARTED;
			info.lastStarted = System.currentTimeMillis();
			setStatus("Resuming...");
			return true;
		}
		return false;
	}

	private void startNewDownloader() {
		setStatus("Restarting...");
		info.state = DownloadState.TOSTART;
		info.downloaded = 0;
		info.elapsedTime = 0;
		info.parts = null;
		FileDownloader downloader = new FileDownloader(info, list);
		info.downloader = downloader;
		info.downloader.execute();		
	}

	void restartDownload() {
		restarting = true;
		if(!cancel(false)) {
			startNewDownloader();
		}
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		if (parts != null) {
			for (int i = 0; i < parts.length; i++) {
				if(parts[i] != null) {
					parts[i].info.cancelled = true;
					parts[i].info.paused = false;
				}
			}
		}
		Log.w(App.TAG, "File " + info.fileName + " download cancelled");
		info.elapsedTime = getTotalTime();
		info.state = DownloadState.STOPPED;
		setStatus("Canceled");

		if(restarting) {
			startNewDownloader();
		}
	}

	private static String formatDuration(long millis) {
		if (millis > 60000) {
			return String.format("%.1fmins", millis / 60000.0);
		} else {
			return String.format("%.1fs", millis / 1000.0);
		}
	}

	private long getTotalTime() {
		long total = info.elapsedTime;
		if(info.state == DownloadState.STARTED)
			total += System.currentTimeMillis() - info.lastStarted;
		return total;
	}

	public String getFormattedDuration() {
		return formatDuration(getTotalTime());
	}

	public String getFormattedSpeed() {
		long cur = System.currentTimeMillis();
		if (info.lastStarted >= cur)
			return "";
		long bps = (long) (info.downloaded / (getTotalTime() / 1000.0));
		return "@ " + Formatter.formatShortFileSize(list, bps) + "/s";
	}

	public String getETA() {
		if (info.downloaded == 0)
			return "";
		long eta = (long) ((info.total / (float) info.downloaded - 1) * getTotalTime());
		eta = (eta < 0 ? 0 : eta);
		return "ETA: " + formatDuration(eta);
	}

	public static void createEmptyFile(File file, long size) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(file, "rw");
		try {
			raf.setLength(size);
		} catch (IOException e) {
			throw e;
		} finally {
			raf.close();
		}
	}

	void PrintError(final String reason, Exception e) {
		list.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(list, reason, Toast.LENGTH_SHORT).show();
			}
		});
		Log.e(App.TAG, reason);
		if (e != null)
			e.printStackTrace();
	}
}
