package in.shreybanga.DownloadAccelerator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class DownloadsTable {
	public static final String TABLE_NAME = "download_info";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_URL = "url";
    public static final String KEY_FILE_DIR = "directory";
    public static final String KEY_FILE_NAME = "name";
    public static final String KEY_CREATED = "created";
    public static final String KEY_ELAPSED_TIME = "elapsed_time";
    public static final String KEY_DOWNLOADED = "downloaded";
    public static final String KEY_TOTAL = "total";
    public static final String KEY_THREADS = "threads";
    public static final String KEY_STATE = "state";
    
    public static final String[] columns = new String[] {
    	KEY_ROWID, KEY_URL, KEY_FILE_DIR, KEY_FILE_NAME, KEY_CREATED, 
    	KEY_ELAPSED_TIME, KEY_DOWNLOADED, KEY_TOTAL, KEY_THREADS, KEY_STATE
    };

    /**
     * Database creation sql statement
     */
    public static final String TABLE_CREATE =
        "create table " + TABLE_NAME + "(" 
    		+ KEY_ROWID + " integer primary key autoincrement, "
	        + KEY_URL + " text not null, "
	        + KEY_FILE_DIR + " text not null, "
	        + KEY_FILE_NAME + " text not null, "
	        + KEY_CREATED + " text not null, "
	        + KEY_ELAPSED_TIME + " text not null, "
	        + KEY_DOWNLOADED + " text,"
	        + KEY_TOTAL + " text,"
	        + KEY_THREADS + " int,"
	        + KEY_STATE + " int"
        + ");";

    public static void populateValues(ContentValues values, DownloadInfo info) {
        values.put(KEY_URL, info.url);
        values.put(KEY_FILE_DIR, info.fileDir);
        values.put(KEY_FILE_NAME, info.fileName);
        values.put(KEY_CREATED, info.created);
        values.put(KEY_ELAPSED_TIME, info.elapsedTime);
        values.put(KEY_DOWNLOADED, info.downloaded);
        values.put(KEY_TOTAL, info.total);
        values.put(KEY_THREADS, info.threads);
        values.put(KEY_STATE, info.state.toInt());
    }
}

class ThreadsTable {
	public static final String TABLE_NAME = "thread_info";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_FIRST_BYTE = "first_byte";
    public static final String KEY_LAST_BYTE = "last_byte";
    public static final String KEY_DOWNLOADED = "downloaded";
    public static final String KEY_PAUSED = "paused";
    public static final String KEY_DOWNLOAD_ROWID = "download_id";
    
    public static final String[] columns = new String[] {
    	KEY_ROWID, KEY_FIRST_BYTE, KEY_LAST_BYTE, KEY_DOWNLOADED, KEY_PAUSED, KEY_DOWNLOAD_ROWID
    };

    /**
     * Database creation sql statement
     */
    public static final String TABLE_CREATE =
        "create table " + TABLE_NAME + "(" 
    		+ KEY_ROWID + " integer primary key autoincrement, "
	        + KEY_FIRST_BYTE + " text not null, "
	        + KEY_LAST_BYTE + " text not null, "
	        + KEY_DOWNLOADED + " text not null, "
	        + KEY_PAUSED + " text not null, "
	        + KEY_DOWNLOAD_ROWID + " integer not null, "
	        + "FOREIGN KEY(" + KEY_DOWNLOAD_ROWID + ") REFERENCES " + DownloadsTable.TABLE_NAME + "(" + DownloadsTable.KEY_ROWID + ")"
        + ");";

    public static void populateValues(ContentValues values, PartInfo part, DownloadInfo info) {
        values.put(KEY_DOWNLOADED, String.valueOf(part.downloaded));
        values.put(KEY_FIRST_BYTE, String.valueOf(part.firstByte));
        values.put(KEY_LAST_BYTE, String.valueOf(part.lastByte));
        values.put(KEY_PAUSED, String.valueOf(part.paused));
        values.put(KEY_DOWNLOAD_ROWID, info.rowId);    	
    }
}

public class DownloadsDbAdapter {
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    private static final String DATABASE_NAME = "data";
    private static final int DATABASE_VERSION = 2;

    private final Context mCtx;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DownloadsTable.TABLE_CREATE);
            db.execSQL(ThreadsTable.TABLE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(App.TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + DownloadsTable.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ThreadsTable.TABLE_NAME);
            onCreate(db);
        }
    }

    public DownloadsDbAdapter(Context ctx) {
    	mCtx = ctx;
	}

    /**
     * Open the downloads database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     * 
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public DownloadsDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        mDbHelper.close();
    }


    /**
     * Create a new download using the info provided. If the download is
     * successfully created return the new rowId for that download, 
     * otherwise return -1
     * 
     * @param info the DownloadInfo object describing this download
     * @return rowId or -1 if failed
     */
    public long insertDownload(DownloadInfo info) {
        ContentValues values = new ContentValues();
        DownloadsTable.populateValues(values, info);

        info.rowId = mDb.insert(DownloadsTable.TABLE_NAME, null, values);

        return info.rowId;
    }

    /**
     * Insert a new part of a download. If the part is successfully created
     * return the new rowId for that part, otherwise return -1
     * 
     * @param info the info object of the download
     * @param part the info object of the part
     */
    public long insertPart(DownloadInfo info, PartInfo part) {
    	ContentValues values = new ContentValues();
        ThreadsTable.populateValues(values, part, info);

        part.rowId = mDb.insert(ThreadsTable.TABLE_NAME, null, values);
        return part.rowId;
    }

    /**
     * Delete the download with the given rowId
     * 
     * @param rowId id of download to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteDownload(long rowId) {
    	boolean fine = true;
    	if(mDb.delete(ThreadsTable.TABLE_NAME, ThreadsTable.KEY_DOWNLOAD_ROWID + "=" + rowId, null) == 0)
    		fine = false;
    	if(mDb.delete(DownloadsTable.TABLE_NAME, DownloadsTable.KEY_ROWID + "=" + rowId, null) == 0)
    		fine = false;
    	return fine;
    }

    /**
     * Deletes all downloads
     * 
     * @return true if deleted, false otherwise
     */
    public boolean deleteAll() {
    	boolean fine = mDb.delete(DownloadsTable.TABLE_NAME, null, null) > 0;
    	if(mDb.delete(ThreadsTable.TABLE_NAME, null, null) == 0)
    		fine = false;
    	return fine;
    }

    /**
     * Return a Cursor over the list of all notes in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllDownloads() {
		return mDb.query(DownloadsTable.TABLE_NAME, DownloadsTable.columns, null, null, null, null, null);
    }

    /**
     * Return a Cursor positioned at the download that matches the given rowId
     * 
     * @param rowId id of download to retrieve
     * @return Cursor positioned to matching download, if found
     * @throws SQLException if download could not be found/retrieved
     */
    public Cursor fetchDownload(long rowId) throws SQLException {
        Cursor mCursor =
            mDb.query(true, DownloadsTable.TABLE_NAME, DownloadsTable.columns, DownloadsTable.KEY_ROWID + "=" + rowId, null,
                    null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    /**
     * Update the download using the DownloadInfo object.
     * 
     * @param info the updated DownloadInfo object
     * @return true if the download was successfully updated, false otherwise
     */
    public boolean updateDownload(DownloadInfo info) {
        ContentValues values = new ContentValues();
        DownloadsTable.populateValues(values, info);

        return mDb.update(DownloadsTable.TABLE_NAME, values, DownloadsTable.KEY_ROWID + "=" + info.rowId, null) > 0;
    }

    /**
     * Update the part using the PartInfo object.
     * 
     * @param info the updated PartInfo object
     * @return true if the download was successfully updated, false otherwise
     */
    public boolean updatePart(PartInfo part, DownloadInfo info) {
        ContentValues values = new ContentValues();
        ThreadsTable.populateValues(values, part, info);

        return mDb.update(ThreadsTable.TABLE_NAME, values, ThreadsTable.KEY_ROWID + "=" + part.rowId, null) > 0;
    }

//    /**
//     * Only update the download's downloaded bytes column.
//     * The download to be updated is specified using the rowId.
//     * 
//     * @param rowId id of download to update
//     * @param downloaded the number of bytes downloaded
//     * @return true if the download was successfully updated, false otherwise
//     */
//    public boolean updateDownloadedBytes(DownloadInfo info) {
//        ContentValues values = new ContentValues();
//        values.put(DownloadsTable.KEY_DOWNLOADED, info.downloaded);
//
//        return mDb.update(DownloadsTable.TABLE_NAME, values, DownloadsTable.KEY_ROWID + "=" + info.rowId, null) > 0;
//    }

    /**
     * Fetch all parts of a download
     * 
     * @param rowId id of the download
     * @return array or PartInfo objects if successful, null otherwise
     */
    public Cursor fetchAllParts(long rowId) {
		return mDb.query(ThreadsTable.TABLE_NAME, ThreadsTable.columns, ThreadsTable.KEY_DOWNLOAD_ROWID + "=" + rowId, null, null, null, null);
    }
}
