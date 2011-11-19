package in.shreybanga.DownloadAccelerator;

import java.io.File;

public class DownloadInfo {
	public enum DownloadState {
		TOSTART(0), /* Download has been created but not started yet */
		STARTED(1), /* File is downloading currently */
		PAUSED(2),  /* Download has been paused */
		STOPPED(3), /* Download has been canceled */
		FINISHED(4) /* Download has finished properly */
		;
		
		private int id;
		
		DownloadState(int id) {
			this.id = id;
		}

		public int toInt() {
			return id;
		}
		
		public static DownloadState fromInt(int id) {
			DownloadState[] states = values();
			for(int i = 0; i < states.length; i++)
				if(states[i].id == id)
					return states[i];
			return null;
		}
	}

	public long rowId; /* ID of the row in the DB */
	public String url, fileName, fileDir;
	public long downloaded, total;
	public long created, elapsedTime, lastStarted;
	public int threads;
	public DownloadState state;
	public PartInfo[] parts;

	public FileDownloader downloader = null;
	
	/**
	 * This is true if any data has been changed in this download or its parts
	 */
	private boolean dirty;

	public DownloadInfo(long rowId, String url, String fileName, String fileDir, Integer threads, long created, 
			long downloaded, long total, long elapsedTime, int state, PartInfo[] parts) {
		if(fileDir.lastIndexOf(File.separator) != fileDir.length()-1)
			fileDir = fileDir + File.separator;

		this.rowId = rowId;
		this.url = url;
		this.fileName = fileName;
		this.fileDir = fileDir;
		this.threads = threads;
		this.created = created;
		this.elapsedTime = elapsedTime;
		this.downloaded = downloaded;
		this.total = total;
		this.state = DownloadState.fromInt(state);
		this.parts = parts;
	}

	public DownloadInfo(String url, String fileName, String fileDir, Integer threads) {
		this(Long.MAX_VALUE, url, fileName, fileDir, threads, System.currentTimeMillis(), 0, 0, 0, DownloadState.TOSTART.toInt(), null);
	}

	synchronized public void incrementBytes (int bytes) {
		downloaded += bytes;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}
}