package in.shreybanga.DownloadAccelerator;

import in.shreybanga.DownloadAccelerator.R;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ThreadsAdapter extends BaseAdapter {
	private Context context;
	private FilePartDownloader[] parts;

	private class ViewHolder {
		ProgressBar pbDownloaded;
		TextView txtStatus;
	}

	public ThreadsAdapter(Context context, FilePartDownloader[] parts) {
		this.context = context;
		this.parts = parts;
	}

	@Override
	public int getCount() {
		return parts.length;
	}

	@Override
	public Object getItem(int position) {
		return parts[position];
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder vh;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.threads_row, parent, false);
			vh = new ViewHolder();
//			vh.pbDownloaded = (ProgressBar) convertView.findViewById(R.id.pbDownloaded);
//			vh.txtStatus = (TextView) convertView.findViewById(R.id.txtStatus);
			convertView.setTag(vh);
		} else {
			vh = (ViewHolder) convertView.getTag();
		}

		FilePartDownloader fpd = parts[position];
		if(fpd == null) {
			vh.pbDownloaded.setIndeterminate(true);
			vh.txtStatus.setText("");
		} else {
			vh.pbDownloaded.setIndeterminate(false);
			vh.pbDownloaded.setProgress((int) (100 * (fpd.info.downloaded / (float) fpd.info.total)));
			vh.txtStatus.setText(Formatter.formatFileSize(context, fpd.info.downloaded) + "/" + Formatter.formatFileSize(context, fpd.info.total));
		}
		return convertView;
	}
}
