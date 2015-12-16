package com.example.jianting.bandtest;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.logging.LogRecord;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandIOException;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandDistanceEvent;
import com.microsoft.band.sensors.BandDistanceEventListener;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandGyroscopeEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.HeartRateQuality;
import com.microsoft.band.sensors.MotionType;
import com.microsoft.band.sensors.SampleRate;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;

import android.view.View;
import android.app.Activity;
import android.os.AsyncTask;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    private BandClient client = null;
    Button btnStart;
    Button btnStop;
    Button btnBind;
    Button btnUnbind;
    private TextView txtStatus;
    private TextView txtSkinTemp;
    private TextView txtHeartRate;
    private TextView txtDistance;
    private TextView txtTime;
    private TextView txtGyroscope;
    private boolean isalive;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter mIF;

    private CollectDataService collectDataService;

    private ServiceConnection connection = new ServiceConnection() {
        //获取服务对象时的操作
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            collectDataService = ((CollectDataService.ServiceBinder)iBinder).getService();
        }

        //无法获取服务对象时的操作
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            collectDataService = null;
        }
    };

    public static String TimeStamp = "";
    public static String Time = "";
    public static String Accelerometer = "";
    public static String Gyroscope = "";
    public static String mMotionType = "";
    public static float Speed;
    public static String SkinTemperature = "";
    public static String HeartRate = "";
    static long Distance;
    Handler handlerUI = new Handler();
    Runnable runnableUI = new Runnable() {
        @Override
        public void run() {
            try {
                handlerUI.postDelayed(this, 1000);
                appendToUIDistance(String.format("Distance = %.2f %s\nSpeed = %.2f %s\n", Distance / 100.0, "m",
                        Speed / 100, "m/s") + "MotionType = " + mMotionType);
                appendToUIGyroscope(Gyroscope);
                appendToUIHeartRate(HeartRate);
                appendToUISkin(SkinTemperature);
                appendToUITime(Time);
                appendToUI(Accelerometer);
            }
            catch (Exception e){

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnBind = (Button) findViewById(R.id.btnBind);
        btnUnbind = (Button) findViewById(R.id.btnUnbind);
        txtSkinTemp = (TextView) findViewById(R.id.txtSkinTemp);
        txtHeartRate = (TextView) findViewById(R.id.HeartRate);
        txtDistance = (TextView) findViewById(R.id.txtDistance);
        txtTime = (TextView) findViewById(R.id.txtTime);
        txtGyroscope = (TextView) findViewById(R.id.txtGyroscope);
        long sysTime = System.currentTimeMillis();

        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                Toast.makeText(getBaseContext(), "Band isn't paired with your phone.", Toast.LENGTH_LONG).show();
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        }else{
            Log.i("client_null", "client_null");
        }
        btnStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                txtStatus.setText("Accelerate");
                txtSkinTemp.setText("SkinTemp");
                txtHeartRate.setText("HeartRate");
                if(client.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
                    client.getSensorManager().requestHeartRateConsent(MainActivity.this, heartRateConsentListener);
                }else{

                }
                if(client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                    Intent intent = new Intent(MainActivity.this,CollectDataService.class);
                    startService(intent);
                    handlerUI.postDelayed(runnableUI,0);
                }
            }
        });

        btnStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CollectDataService.class );
                stopService(intent);
                collectDataService = null;
            }
        });

        btnBind.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CollectDataService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE );
            }
        });

        btnUnbind.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                unbindService(connection);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*txtStatus.setText("Accelerate");
        txtSkinTemp.setText("SkinTemp");
        txtHeartRate.setText("HeartRate");
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            File sdCardDir = Environment.getExternalStorageDirectory();//获取SDCard目录
            File saveFile = new File(sdCardDir,TimeFileName + "-RawData.txt");
            try {
                outStream = new FileOutputStream(saveFile,true);
                //utStream = this.openFileOutput("RawData.txt", Context.MODE_APPEND);
            }catch (FileNotFoundException e){
                txtTime.setText(e.getMessage());
            }
        }*/
        isalive = true;
        //handlerUI.postDelayed(runnableUI,0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isalive = false;
        /*if (client != null) {
            try {
                //appendToUI("onPause");
                client.getSensorManager().unregisterAccelerometerEventListeners();
                client.getSensorManager().unregisterSkinTemperatureEventListener(mBandSkinTemperatureEventListener);
                client.getSensorManager().unregisterHeartRateEventListener(mBandHeartRateEventListener);
                client.getSensorManager().unregisterDistanceEventListener(mBandDistanceEventListener);
                client.getSensorManager().unregisterGyroscopeEventListener(bandGyroscopeEventListener);
                //outStream.close();
            } catch (BandIOException e) {
                //appendToUI(e.getMessage());
            }
        }*/
    }


    //更新UI
    public void appendToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatus.setText(string);
            }
        });
    }

    public void appendToUISkin(final String string){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtSkinTemp.setText(string);
            }
        });
    }

    public  void appendToUIHeartRate(final String string){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtHeartRate.setText(string);
            }
        });
    }
    public void appendToUIDistance(final String string){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtDistance.setText(string);
            }
        });
    }

    public void appendToUITime(final String string){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtTime.setText(string);
            }
        });
    }

    public void appendToUIGyroscope(final String string){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtGyroscope.setText(string);
            }
        });
    }


    private HeartRateConsentListener heartRateConsentListener = new HeartRateConsentListener() {
        @Override
        public void userAccepted(boolean b) {
            if(b == true){
                //startHRListener();
            }
            else{
                //appendToUIHeartRate("user do not consent");
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
