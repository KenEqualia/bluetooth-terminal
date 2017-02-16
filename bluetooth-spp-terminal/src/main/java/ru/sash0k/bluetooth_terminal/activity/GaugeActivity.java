package ru.sash0k.bluetooth_terminal.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MenuItem;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import ru.sash0k.bluetooth_terminal.R;
import ru.sash0k.bluetooth_terminal.Utils;
import ru.sash0k.bluetooth_terminal.bluetooth.DeviceConnector;

import static ru.sash0k.bluetooth_terminal.activity.BaseActivity.MESSAGE_STATE_CHANGE;

/**
 * Created by kbigler on 11/1/2016.
 */
public class GaugeActivity extends Activity {
    private static DeviceConnector connector;

    private String res = "00";
    private int counter = 0;

    Timer timer;
    TimerTask task;

    TextView speedText;
    TextView rangeText;

    //runs without a timer by reposting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            res = sendHBCommand("HoverBoardSpeed()");
            speedText.setText(res);
            counter += 1;

            if (counter > 200) {
                return;
            }

            timerHandler.postDelayed(this, 500);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gauge_screen);

        final ActionBar bar = getActionBar();
        bar.setHomeButtonEnabled(true);
        bar.setDisplayHomeAsUpEnabled(true);

        speedText = (TextView) findViewById(R.id.currentSpeed);
        rangeText = (TextView) findViewById(R.id.currentRange);

        speedText.setText("24");
        rangeText.setText("12");

        timerHandler.postDelayed(timerRunnable, 0);
    }
    // ============================================================================

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    // ============================================================================

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ==========================================================================

    /**
     * Readiness - Checks Connection
     */
    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    // ==========================================================================
    /**
     * Sends the message to the hoverboard
     * @param commandString
     */
    public String sendHBCommand(String commandString) {
        String result = "";

        byte[] command = commandString.getBytes();
        command = Utils.concat(command, "\r\n".getBytes());
        if (isConnected()) {
            connector.write(command);
        }
        return result;
    }

    /**
     * @param message  - Text to display
     * @return         - Return message received
     */
    String appendLog(String message) {
        // Remove newline characters: \r \n
        message = message.replace("\r", "").replace("\n", "");
        return message;
    }

    // ==========================================================================

    public static class BluetoothResponseHandler extends Handler {
        private WeakReference<GaugeActivity> mActivity;

        public BluetoothResponseHandler(GaugeActivity activity) {
            mActivity = new WeakReference<GaugeActivity>(activity);
        }

        public void setTarget(GaugeActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<GaugeActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            GaugeActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:

                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                //bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                                //bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                //bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                        }
                        activity.invalidateOptionsMenu();
                        break;

                    case DeviceControlActivity.MESSAGE_READ:
                        final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage);
                        }
                        break;

                    case DeviceControlActivity.MESSAGE_DEVICE_NAME:
                        break;

                    case DeviceControlActivity.MESSAGE_WRITE:
                        // stub
                        break;

                    case DeviceControlActivity.MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }
}