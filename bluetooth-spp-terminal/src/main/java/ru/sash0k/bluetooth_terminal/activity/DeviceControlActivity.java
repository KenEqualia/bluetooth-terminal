package ru.sash0k.bluetooth_terminal.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.sash0k.bluetooth_terminal.DeviceData;
import ru.sash0k.bluetooth_terminal.GaugeState;
import ru.sash0k.bluetooth_terminal.R;
import ru.sash0k.bluetooth_terminal.Utils;
import ru.sash0k.bluetooth_terminal.bluetooth.DeviceConnector;
import ru.sash0k.bluetooth_terminal.bluetooth.DeviceListActivity;

public class DeviceControlActivity extends BaseActivity implements AdapterView.OnItemSelectedListener {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static final String LOG = "LOG";

    // backlight crc
    private static final String CRC_OK = "#FFFF00";
    private static final String CRC_BAD = "#FF0000";

    private static final SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");

    private static String MSG_NOT_CONNECTED;
    private static String MSG_CONNECTING;
    private static String MSG_CONNECTED;

    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;

    private StringBuilder logHtml;
    private TextView logTextView;
    private EditText commandEditText;
    private Spinner commandSpinner;

    // Application Settings
    private boolean hexMode, checkSum, needClean;
    private boolean show_timings, show_direction;
    private String command_ending;
    private String deviceName;

    GaugeState gaugeState = new GaugeState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);

        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        else mHandler.setTarget(this);

        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected);
        MSG_CONNECTING = getString(R.string.msg_connecting);
        MSG_CONNECTED = getString(R.string.msg_connected);

        setContentView(R.layout.activity_terminal);
        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else getActionBar().setSubtitle(MSG_NOT_CONNECTED);

        this.logHtml = new StringBuilder();
        if (savedInstanceState != null) this.logHtml.append(savedInstanceState.getString(LOG));

        this.logTextView = (TextView) findViewById(R.id.log_textview);
        this.logTextView.setMovementMethod(new ScrollingMovementMethod());
        this.logTextView.setText(Html.fromHtml(logHtml.toString()));

        this.commandEditText = (EditText) findViewById(R.id.command_edittext);
        // soft-keyboard send button
        this.commandEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCommand(null);
                    return true;
                }
                return false;
            }
        });
        // hardware Enter button
        this.commandEditText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_ENTER:
                            sendCommand(null);
                            return true;
                        default:
                            break;
                    }
                }
                return false;
            }
        });

        // spinner element
        this.commandSpinner = (Spinner) findViewById(R.id.spinner);

        // spinner listener
        this.commandSpinner.setOnItemSelectedListener(this);

        // create array and fill with commands
        List<String> commands = new ArrayList<String>();
        commands.add("HoverBoardName");
        commands.add("Gauge");
        commands.add("HoverBoardRangePercent()");
        commands.add("HoverBoardRangeMiles()");
        commands.add("HoverBoardPowerPercent()");
        commands.add("HoverBoardPower()");
        commands.add("HoverBoardSpeedPercent()");
        commands.add("HoverBoardSpeed()");
        commands.add("HoverBoardMaximumTemperature()");
        commands.add("HoverBoardVoltage()");
        commands.add("HoverBoardCurrent()");
        commands.add("HoverBoardAngleMeasured()");
        commands.add("HoverBoardLocked()");
        commands.add("TripMilesTraveled()");
        commands.add("TripWattHoursUsed()");
        commands.add("MotorCurrent()");
        commands.add("MotorPower()");
        commands.add("ChargerStatus()");
        commands.add("CellVoltageLeft");
        commands.add("CellBalancerLeft()");
        commands.add("CellTemperatureLeft");
        commands.add("BatteryCapacityPercentLeft()");
        commands.add("BatteryCellStatusLeft()");
        commands.add("BatteryVoltageLeft()");
        commands.add("BatteryCurrentLeft()");
        commands.add("CellVoltageRight");
        commands.add("CellBalancerRight()");
        commands.add("CellTemperatureRight");
        commands.add("BatteryCapacityPercentRight()");
        commands.add("BatteryCellStatusRight()");
        commands.add("BatteryVoltageRight()");
        commands.add("BatteryCurrentRight()");
        commands.add("ForceSensorOne()");
        commands.add("ForceSensorTwo()");
        commands.add("ForceSensorThree()");
        commands.add("ForceSensorFour()");
        commands.add("ForceSensorSum()");
        commands.add("GroundAngleMeasured()");
        commands.add("HoverBoardSpeedLimit()");
        commands.add("UserSpeedLimit()");
        commands.add("HoverBoardDisplayOption()");
        commands.add("HoverBoardSoundTheme()");
        commands.add("HoverBoardSecurityCode()");
        commands.add("HoverBoardColorTheme()");
        commands.add("DisplayOptionName()");
        commands.add("SoundThemeName()");
        commands.add("ColorThemeName()");

        gaugeState.setListener(new GaugeState.ChangeListener() {
            @Override
            public void onChange() {
                if (gaugeState.getGState() == 1) {
                    sendHBCommand("HoverBoardRangeMiles()");
                    gaugeState.nextGState();
                } else if (gaugeState.getGState() == 2) {
                    sendHBCommand("HoverBoardSpeed()");
                    gaugeState.nextGState();
                } else {
                    return;
                }
            }
        });

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, commands);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.commandSpinner.setAdapter(dataAdapter);


    }
    // ==========================================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DEVICE_NAME, deviceName);
        if (logTextView != null) {
            outState.putString(LOG, logHtml.toString());
        }
    }
    // ============================================================================


    /**
     * Readiness - Checks Connection
     */
    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }
    // ==========================================================================


    /**
     * Disconnect - Stop Connection
     */
    private void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
            deviceName = null;
        }
    }
    // ==========================================================================


    /**
     * The list of devices to connect
     */
    private void startDeviceListActivity() {
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    // ============================================================================


    /**
     * Processing Search button hardware
     *
     * @return
     */
    @Override
    public boolean onSearchRequested() {
        if (super.isAdapterReady()) startDeviceListActivity();
        return false;
    }
    // ==========================================================================


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_control_activity, menu);
        final MenuItem bluetooth = menu.findItem(R.id.menu_search);
        if (bluetooth != null) bluetooth.setIcon(this.isConnected() ?
                R.drawable.ic_action_device_bluetooth_connected :
                R.drawable.ic_action_device_bluetooth);
        return true;
    }
    // ============================================================================


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_search:
                if (super.isAdapterReady()) {
                    if (isConnected()) stopConnection();
                    else startDeviceListActivity();
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return true;

            /*case R.id.menu_gauge_mode:
                final Intent intent1 = new Intent(this, GaugeActivity.class);
                startActivity(intent1);
                return true;*/

            case R.id.menu_clear:
                if (logTextView != null) logTextView.setText("");
                return true;

            case R.id.menu_send:
                if (logTextView != null) {
                    final String msg = logTextView.getText().toString();
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, msg);
                    startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
                }
                return true;

            case R.id.menu_settings:
                final Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // ============================================================================

    /**
     * Checks settings and implements them
     */
    @Override
    public void onStart() {
        super.onStart();

        // hex mode
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
        this.hexMode = "HEX".equals(mode);
        if (hexMode) {
            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            commandEditText.setFilters(new InputFilter[]{new Utils.InputFilterHex()});
        } else {
            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            commandEditText.setFilters(new InputFilter[]{});
        }

        // checksum
        final String checkSum = Utils.getPrefence(this, getString(R.string.pref_checksum_mode));
        this.checkSum = "Modulo 256".equals(checkSum);

        // EOL
        this.command_ending = getCommandEnding();

        // Display Format of the Log Command
        this.show_timings = Utils.getBooleanPrefence(this, getString(R.string.pref_log_timing));
        this.show_direction = Utils.getBooleanPrefence(this, getString(R.string.pref_log_direction));
        this.needClean = Utils.getBooleanPrefence(this, getString(R.string.pref_need_clean));
    }
    // ============================================================================


    /**
     * Process EOLs for the settings
     */
    private String getCommandEnding() {
        String result = Utils.getPrefence(this, getString(R.string.pref_commands_ending));
        if (result.equals("\\r\\n")) result = "\r\n";
        else if (result.equals("\\n")) result = "\n";
        else if (result.equals("\\r")) result = "\r";
        else result = "";
        return result;
    }
    // ============================================================================


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (super.isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Utils.log("BT not enabled");
                }
                break;
        }
    }
    // ==========================================================================


    /**
     * Establishing a connection with the device
     */
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Utils.log("setupConnector failed: " + e.getMessage());
        }
    }
    // ==========================================================================


    /**
     * Sending a device command
     */
    public void sendCommand(View view) {
        if (commandEditText != null) {
            String commandString = commandEditText.getText().toString();
            if (commandString.isEmpty()) return;

            // Update commands in Hex
            if (hexMode && (commandString.length() % 2 == 1)) {
                commandString = "0" + commandString;
                commandEditText.setText(commandString);
            }

            // checksum
            if (checkSum) {
                commandString += Utils.calcModulo256(commandString);
            }

            byte[] command = (hexMode ? Utils.toHex(commandString) : commandString.getBytes());
            if (command_ending != null) command = Utils.concat(command, command_ending.getBytes());
            if (isConnected()) {
                connector.write(command);
                appendLog(commandString, hexMode, true, needClean, false);
            }
        }
    }
    // ==========================================================================


    /**
     * Adding response to the log
     *
     * @param message  - Text to display
     * @param hexMode
     * @param outgoing - Destination
     * @param clean
     * @return         - Return message received
     */
    String appendLog(String message, boolean hexMode, boolean outgoing, boolean clean, boolean readMode) {

        StringBuilder msg = new StringBuilder();
        if (show_timings) msg.append("[").append(timeformat.format(new Date())).append("]");
        if (show_direction) {
            final String arrow = (outgoing ? " << " : " >> ");
            msg.append(arrow);
        } else msg.append(" ");

        // Remove newline characters: \r \n
        message = message.replace("\r", "").replace("\n", "");

        // Checking for checksum response
        String crc = "";
        boolean crcOk = false;
        if (checkSum) {
            int crcPos = message.length() - 2;
            crc = message.substring(crcPos);
            message = message.substring(0, crcPos);
            crcOk = outgoing || crc.equals(Utils.calcModulo256(message).toUpperCase());
            if (hexMode) crc = Utils.printHex(crc.toUpperCase());
        }

        // Log in HTML
        msg.append("<b>")
                .append(hexMode ? Utils.printHex(message) : message)
                .append(checkSum ? Utils.mark(crc, crcOk ? CRC_OK : CRC_BAD) : "")
                .append("</b>")
                .append("<br>");

        logHtml.append(msg);
        logTextView.append(Html.fromHtml(msg.toString()));

        final int scrollAmount = logTextView.getLayout().getLineTop(logTextView.getLineCount()) - logTextView.getHeight();
        if (scrollAmount > 0)
            logTextView.scrollTo(0, scrollAmount);
        else logTextView.scrollTo(0, 0);

        if (clean) commandEditText.setText("");

        if (readMode) {
            if (gaugeState.getGState() == 0) {
                try{ Thread.sleep(300); }catch(InterruptedException e){ }
                gaugeState.nextGState();
            }
        }

        return message;
    }

    // =========================================================================


    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        getActionBar().setSubtitle(deviceName);
    }
    // =========================================================================

    /**
     * Sends the message to the hoverboard
     * @param commandString
     */
    public String sendHBCommand(String commandString) {
        String result = "";
        // Update commands in Hex
        if (hexMode && (commandString.length() % 2 == 1)) {
            commandString = "0" + commandString;
            commandEditText.setText(commandString);
        }

        // checksum
        if (checkSum) {
            commandString += Utils.calcModulo256(commandString);
        }

        byte[] command = (hexMode ? Utils.toHex(commandString) : commandString.getBytes());
        if (command_ending != null) command = Utils.concat(command, command_ending.getBytes());
        if (isConnected()) {
            connector.write(command);
            result = appendLog(commandString, hexMode, true, needClean, false);
        }

        return result;
    }

    /**
     * Spinner Listener that calls a hoverboard command
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // On selecting a spinner item
        String commandString = parent.getItemAtPosition(position).toString();

        if (commandString.isEmpty()) return;

        if (commandString == "HoverBoardName") {
            String res = "";
            int i = 0;
            while (res != "\0" && i < 32) {
                res = sendHBCommand(commandString + "(" + i + ")");
                i += 1;
            }
        } else if ( commandString == "CellVoltageLeft" ||
                    commandString == "CellTemperatureLeft" ||
                    commandString == "CellVoltageRight" ||
                    commandString == "CellTemperatureRight") {
            for (int i = 1; i <= 12; i += 1) {
                sendHBCommand(commandString + "(" + i + ")");
                try{ Thread.sleep(100); }catch(InterruptedException e){ }
            }
        } else if (commandString == "Gauge") {
            gaugeState.startGState();
        } else {
            sendHBCommand(commandString);
        }
    }

    /**
     * Spinner Listener that cancels call
     * @param parent
     */
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    // ==========================================================================

    /**
     * Processing received data from the bluetooth stream
     */
    public static class BluetoothResponseHandler extends Handler {
        private WeakReference<DeviceControlActivity> mActivity;

        public BluetoothResponseHandler(DeviceControlActivity activity) {
            mActivity = new WeakReference<DeviceControlActivity>(activity);
        }

        public void setTarget(DeviceControlActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<DeviceControlActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            DeviceControlActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:

                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                                bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                        }
                        activity.invalidateOptionsMenu();
                        break;

                    case MESSAGE_READ:
                        final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false, false, activity.needClean, true);
                        }
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }
    // ==========================================================================
}