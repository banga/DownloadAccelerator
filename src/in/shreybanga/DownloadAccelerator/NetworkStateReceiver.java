package in.shreybanga.DownloadAccelerator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkStateReceiver extends BroadcastReceiver {
	private DownloadsList list;

	public NetworkStateReceiver(DownloadsList list) {
		this.list = list;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if (ni != null && ni.isConnected()) {
			list.isConnected = true;
			list.setTitle("Download Accelerator - Connected to " + ni.getTypeName());
		} else {
			list.isConnected = false;
			list.setTitle("Download Accelerator - Disconnected");
		}
	}
}
