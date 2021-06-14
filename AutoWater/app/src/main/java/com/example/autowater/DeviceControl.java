package com.example.autowater;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class DeviceControl extends AppCompatActivity {

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private Handler btHandler;
    private ConnectedThread ct;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_device_control);

        //call the widgets
        final Button InstantWatering = (Button) findViewById(R.id.instant_watering);
        final Button Discnt = (Button) findViewById(R.id.dis_btn);
        final Button UpdateBtn = (Button) findViewById(R.id.update_btn);
        final ProgressBar SoilHumidity = (ProgressBar) findViewById(R.id.soil_humidity);
        final ProgressBar Humidity = (ProgressBar) findViewById(R.id.humidity);
        final TextView Temperature = (TextView) findViewById(R.id.temperature);
        final TextView SoilHumidityText = (TextView) findViewById(R.id.soil_humidity_text);
        final TextView HumidityText = (TextView) findViewById(R.id.humidity_text);
        final SeekBar StartSeekBar = (SeekBar) findViewById(R.id.start_seek_bar);
        final SeekBar StopSeekBar = (SeekBar) findViewById(R.id.stop_seek_bar);

        new ConnectBT().execute(); //Call the class to connect

        StartSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > StopSeekBar.getProgress()) {
                    StopSeekBar.setProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        StopSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < StartSeekBar.getProgress()) {
                    StartSeekBar.setProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //commands to be sent to bluetooth
        InstantWatering.setOnClickListener(v -> {
            sendVariables("P0\0".getBytes());
            // sendVariables(new byte[] {0x3, 0x0, 0x7F});
        });

        Discnt.setOnClickListener(v -> {
            Disconnect(); //close connection
        });

        UpdateBtn.setOnClickListener(v -> {
            sendVariables(("A" + String.format("%3d%3d\0", StartSeekBar.getProgress(), StopSeekBar.getProgress())).getBytes());
        });

        btHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                // super.handleMessage(msg);
                if (msg.what == ConnectedThread.RESPONSE_MESSAGE) {
                    String txt = (String) msg.obj;
                    Log.println(Log.INFO, "MSG", txt);

                    try {
                        char opcode = txt.charAt(0);
                        float data = Float.valueOf(txt.substring(1));
                        String str;

                        if (opcode == 'T') {
                            str = (Float.toString(data) + "°C");
                            Temperature.setText(str);
//                            Log.println(Log.INFO, "Temperature", str);
                        } else if (opcode == 'S') {
                            str = (Float.toString(data) + "%");
                            SoilHumidity.setProgress(Math.round(data));
                            SoilHumidityText.setText(str);
//                            Log.println(Log.INFO, "SoilHumidity", str);
                        } else if (opcode == 'H') {
                            str = (Float.toString(data) + "%");
                            Humidity.setProgress(Math.round(data));
                            HumidityText.setText(str);
//                            Log.println(Log.INFO, "Humidity", str);
                        }
                    } catch (Exception e) {

                    }
                }
            }
        };
    }

    private void Disconnect()
    {
        msg("Disconnect");

        try
        {
            ct.cancel(); //close connection
        }
        catch (Exception e)
        {
            msg("Error");
        }
        finish(); //return to the first layout

    }

    private void sendVariables(byte[] variables) {
        try {
            Log.println(Log.INFO, "SEND", variables.toString());
            btSocket.getOutputStream().write(variables);
        } catch (Exception e) {
            msg("Error");
        }
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    public class ConnectedThread extends AsyncTask<Void, Void, Void> {
        private final BluetoothSocket socket;
        private InputStream btInStream;
        private OutputStream btOutStream;
        public static final int RESPONSE_MESSAGE = 10;
        Handler uih;

        public ConnectedThread(BluetoothSocket socket, Handler uih) {
            this.socket = socket;
            this.uih = uih;

            try {
                btInStream = socket.getInputStream();
                btOutStream = socket.getOutputStream();
            } catch (IOException e) {
                msg("Error");
            }

            try {
                btOutStream.flush();
            } catch (IOException e) {
                msg("Error");
                return;
            }
        }

        public void write(byte[] bytes) {
            if (socket.isConnected()) {
                try {
                    btOutStream.flush();
                    btOutStream.write(bytes);
                } catch (IOException e) {

                }
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {

            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            BufferedReader br = new BufferedReader(new InputStreamReader(btInStream));

            while (true) {
                try {
                    String resp = br.readLine();
                    Message msg = new Message();
                    msg.what = RESPONSE_MESSAGE;
                    msg.obj = resp;
                    uih.dispatchMessage(msg);
                } catch (Exception e) {
                    msg("Error");
                    break;
                }
            }

            return null;
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(DeviceControl.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }

            progress.dismiss();

            ct = new ConnectedThread(btSocket, btHandler);
            ct.execute();
        }
    }
}
