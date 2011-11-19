package in.shreybanga.DownloadAccelerator;

import in.shreybanga.DownloadAccelerator.DownloadInfo.DownloadState;

import java.util.ArrayList;
import java.util.Hashtable;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import in.shreybanga.DownloadAccelerator.R;

public class DownloadsAdapter extends BaseAdapter {
	Context context;
	DownloadsList list;
	private LayoutInflater inflater;
	private ArrayList<DownloadInfo> infos;
	private Hashtable<DownloadInfo,Drawable> icons = new Hashtable<DownloadInfo, Drawable>();
	private Drawable defaultIcon;

	public static class ViewHolder {
		ImageView icon;
		TextView name, status;
		ProgressBar progress;
		DownloadInfo info;
	}

	public DownloadsAdapter(DownloadsList list, ArrayList<DownloadInfo> infos) {
		this.context = list;
		this.list = list;
		this.infos = infos;

		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		Resources res = list.getResources();
		defaultIcon = (Drawable) res.getDrawable(res.getIdentifier("drawable/icon_default", null, list.getPackageName()));
	}

	@Override
	public int getCount() {
		return infos.size();
	}

	@Override
	public Object getItem(int position) {
		return infos.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	private void populateView(ViewHolder holder, int position) {
		DownloadInfo info = (DownloadInfo) getItem(position);
		Drawable icon = null;
		if(!icons.contains(info)) {
			ResolveInfo ri = App.findActivityForFile(context, info.fileName);
			if(ri != null) {
				icon = ri.loadIcon(context.getPackageManager());
			} else {
				icon = defaultIcon;
			}
			icons.put(info, icon);
		} else {
			icon = icons.get(info);
		}
		holder.icon.setImageDrawable(icon);
		holder.name.setText(info.fileName);
		holder.status.setText(info.downloader.status);

		if(info.state == DownloadState.STARTED || info.state == DownloadState.PAUSED) {
			holder.progress.setVisibility(View.VISIBLE);
			holder.progress.setIndeterminate(false);
			holder.progress.setProgress((int)(100 * (info.downloaded / (float)info.total)));
		} else if(info.state == DownloadState.TOSTART) {
			holder.progress.setVisibility(View.VISIBLE);
			holder.progress.setIndeterminate(true);
		} else {
			holder.progress.setVisibility(View.GONE);
		}
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		ViewHolder vh;
		if(view == null) {
			view = inflater.inflate(R.layout.downloads_row, parent, false);
			vh = new ViewHolder();
			vh.icon = (ImageView)view.findViewById(R.id.file_icon);
			vh.name = (TextView)view.findViewById(R.id.file_name);
			vh.status = (TextView)view.findViewById(R.id.status);
			vh.progress = (ProgressBar)view.findViewById(R.id.progress);
			vh.info = infos.get(position);

			// Load file-specific icon
			ResolveInfo ri = App.findActivityForFile(context, vh.info.fileName);
			if(ri != null)
				icons.put(vh.info, ri.loadIcon(context.getPackageManager()));
			else
				icons.put(vh.info, defaultIcon);

			view.setTag(vh);
		} else {
			vh = (ViewHolder)view.getTag();
		}
		populateView(vh, position);
		return view;
	}
}
