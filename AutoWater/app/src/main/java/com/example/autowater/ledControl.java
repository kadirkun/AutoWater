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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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

public class ledControl extends AppCompatActivity {
    // Button btnOn, btnOff, btnDis;
    Button InstantWatering, Discnt;
    ProgressBar SoilHumidity, Humidity;
    TextView Temperature;

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
        setContentView(R.layout.activity_led_control);

        //call the widgets
        InstantWatering = (Button) findViewById(R.id.instance_watering);
        Discnt = (Button) findViewById(R.id.dis_btn);
        SoilHumidity = (ProgressBar) findViewById(R.id.soil_humidity);
        Humidity = (ProgressBar) findViewById(R.id.humidity);
        Temperature = (TextView) findViewById(R.id.temperature);

        new ConnectBT().execute(); //Call the class to connect

        //commands to be sent to bluetooth
        InstantWatering.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                instantWater();      //method to turn on
            }
        });

        Discnt.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });

        btHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                // super.handleMessage(msg);
                if (msg.what == ConnectedThread.RESPONSE_MESSAGE) {
                    String txt = (String) msg.obj;
                    setter(txt);
                }
            }

            private void setter(String msg) {
                Character opcode = msg.charAt(0);
                Float data = Float.valueOf(msg.substring(1));

                switch (opcode) {
                    case 'T':
                        Temperature.setText(data.toString() + "°C");
                        break;
                    case 'S':
                        SoilHumidity.setProgress(Math.round(data));
                        break;
                    case 'H':
                        Humidity.setProgress(Math.round(data));
                        break;
                }
            }
        };

        ct = new ConnectedThread(btSocket, btHandler);
        ct.run();
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout

    }

    private void sendVariables(String variables) {
        if (btSocket != null) {
            try {
                btSocket.getOutputStream().write(variables.toString().getBytes());
            } catch (IOException e) {
                msg("Error" + e.getMessage());
            }
        }
    }

    private void instantWater()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("P".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectedThread extends Thread {
        private BluetoothSocket socket;
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

        public void run() {
            BufferedReader br = new BufferedReader(new InputStreamReader(btInStream));

            while (true) {
                try {
                    String resp = br.readLine();
                    Message msg = new Message();
                    msg.what = RESPONSE_MESSAGE;
                    msg.obj = resp;
                    uih.sendMessage(msg);
                } catch (IOException e) {
                    msg("Error");
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                btOutStream.write(bytes);
            } catch (IOException e) {

            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {

            }
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(ledControl.this, "Connecting...", "Please wait!!!");  //show a progress dialog
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
        }
    }
}
