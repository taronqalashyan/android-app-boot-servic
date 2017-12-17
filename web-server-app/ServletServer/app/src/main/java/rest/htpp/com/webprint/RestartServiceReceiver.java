package rest.htpp.com.webprint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * The broadcast receiver registered in manifest file which will listen/handle on device restart event
 * The class is public to be accessible by android system
 *
 */
public class RestartServiceReceiver extends BroadcastReceiver {

    final String TOAST_TEXT = "Web Server is connected on Restart";

    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, TOAST_TEXT, Toast.LENGTH_LONG).show();
        context.startService(new Intent(context.getApplicationContext(), WebServerService.class));
    }
}