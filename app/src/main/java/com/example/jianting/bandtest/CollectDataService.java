package com.example.jianting.bandtest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
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
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.MotionType;
import com.microsoft.band.sensors.SampleRate;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CollectDataService extends Service {
    private static final String TAG1 = "service_life";
    private BandClient client = null;
    private FileOutputStream outStream;
    private Handler handler;
    private Context context;
    private final int WRITERATE = 10000;
    private boolean flag;
    private static final int NOTIFICATION_ID = 1;
    private static final Class<?>[] mStartForegrounSignature = new Class[] {int.class, Notification.class};
    private static final Class<?>[] mStopForegroundSignature = new Class[] {boolean.class};
    private NotificationManager mNM;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];

    public CollectDataService() {
    }

    @Override
    public void onCreate(){
        super.onCreate();
        context = this;
        flag = true;
        Log.d(TAG1, "oncreate is calling");
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            File sdCardDir = Environment.getExternalStorageDirectory();//获取SDCard目录
            File saveFile = new File(sdCardDir,"RawData.txt");
            try {
                outStream = new FileOutputStream(saveFile,true);
                //utStream = this.openFileOutput("RawData.txt", Context.MODE_APPEND);
            }catch (FileNotFoundException e){
                //txtTime.setText(e.getMessage());
            }
        }
        handler = new Handler();
        new appTask().execute();
        Runnable runnableWrite = new Runnable() {
            @Override
            public void run() {
                try {
                    if(flag)
                        handler.postDelayed(this,WRITERATE);
                    String Result = MainActivity.TimeStamp + "," + MainActivity.Time + "," + MainActivity.Accelerometer + "," + MainActivity.Gyroscope + "," +
                            MainActivity.mMotionType + "," + MainActivity.Speed + "," + MainActivity.SkinTemperature + "," + MainActivity.HeartRate + "\n";
                    try{
                        outStream.write(Result.getBytes());
                    }catch (IOException e){
                        //txtTime.setText(e.getMessage());
                    }
                }
                catch (Exception e){

                }
            }
        };
        handler.postDelayed(runnableWrite, 0);

        mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try{
            mStartForeground = getClass().getMethod("startForeground", mStartForegrounSignature);
            mStopForeground = getClass().getMethod("stopForeground", mStopForegroundSignature);
        }catch (Exception e){

        }
        Notification.Builder builder = new Notification.Builder(this);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this,MainActivity.class),0);
        builder.setContentIntent(contentIntent);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setTicker("Foreground Service Start");
        builder.setContentTitle("Foreground Service");
        builder.setContentText("Make this service run in the foreground.");
        Notification notification = builder.build();
        startForegroundCompat(NOTIFICATION_ID,notification);
    }

    void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // Should not happen.
            Log.w("ApiDemos", "Unable to invoke method", e);
        } catch (IllegalAccessException e) {
            // Should not happen.
            Log.w("ApiDemos", "Unable to invoke method", e);
        }
    }

    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }
    }
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mStopForeground, mStopForegroundArgs);
            return;
        }
    }
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId){
        Log.i("service_life", "onstartcommand is calling");
        return Service.START_STICKY;
    }
    @Override
    public void onDestroy(){
        Log.i("service_life", "ondestroy is called");
        flag = false;
        if(client != null){
            try {
                client.getSensorManager().unregisterAccelerometerEventListeners();
                client.getSensorManager().unregisterSkinTemperatureEventListener(mBandSkinTemperatureEventListener);
                client.getSensorManager().unregisterHeartRateEventListener(mBandHeartRateEventListener);
                client.getSensorManager().unregisterDistanceEventListener(mBandDistanceEventListener);
                client.getSensorManager().unregisterGyroscopeEventListener(bandGyroscopeEventListener);
            }catch (BandIOException e){
                //do nothing
            }
        }
        stopForegroundCompat(NOTIFICATION_ID);
    }

    private class appTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    //Toast.makeText(context,"Band is connected!\n",Toast.LENGTH_SHORT);
                    client.getSensorManager().registerAccelerometerEventListener(mAccelerometerEventListener, SampleRate.MS128);
                    client.getSensorManager().registerSkinTemperatureEventListener(mBandSkinTemperatureEventListener);
                    //添加获取用户同意监听心跳事件
                    if(client.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED){
                        //client.getSensorManager().requestHeartRateConsent(MainActivity.this,heartRateConsentListener);
                    }else{
                        startHRListener();
                    }
                    client.getSensorManager().registerDistanceEventListener(mBandDistanceEventListener);
                    client.getSensorManager().registerGyroscopeEventListener(bandGyroscopeEventListener, SampleRate.MS128);
                } else {
                    //Toast.makeText(context,"Band isn't connected. Please make sure bluetooth is on and the band is in range.\\n",Toast.LENGTH_SHORT);
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage();
                        break;
                }

            } catch (Exception e) {

            }
            return null;
        }
    }

    private BandAccelerometerEventListener mAccelerometerEventListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(final BandAccelerometerEvent event) {
            if (event != null) {
                //appendToUI(String.format("Accelerate =\nX = %.3f \nY = %.3f\nZ = %.3f", event.getAccelerationX(),
                        //event.getAccelerationY(), event.getAccelerationZ()));
                MainActivity.Accelerometer = String.format("X = %.3f Y = %.3f Z = %.3f", event.getAccelerationX(),
                        event.getAccelerationY(), event.getAccelerationZ());
            }
        }
    };

    private BandSkinTemperatureEventListener mBandSkinTemperatureEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(BandSkinTemperatureEvent bandSkinTemperatureEvent) {
            if(bandSkinTemperatureEvent != null){
                //appendToUISkin(String.format("SkinTemp = %.1f",bandSkinTemperatureEvent.getTemperature()));
                MainActivity.SkinTemperature = "" + bandSkinTemperatureEvent.getTemperature();
            }
        }
    };

    private BandHeartRateEventListener mBandHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(BandHeartRateEvent bandHeartRateEvent) {
            if(bandHeartRateEvent != null){
                //appendToUIHeartRate(String.format("HeartRate = %d", bandHeartRateEvent.getHeartRate()));
                MainActivity.HeartRate = "" + bandHeartRateEvent.getHeartRate();
            }
        }
    };

    private BandDistanceEventListener mBandDistanceEventListener = new BandDistanceEventListener() {
        @Override
        public void onBandDistanceChanged(BandDistanceEvent bandDistanceEvent) {
            if(bandDistanceEvent != null){
                SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss");
                MainActivity.Distance = bandDistanceEvent.getTotalDistance();
                String motionTypeString = "Unknown";
                MotionType motionType = bandDistanceEvent.getMotionType();
                switch (motionType){
                    case WALKING: motionTypeString = "Walking"; break;
                    case JOGGING: motionTypeString = "Jogging"; break;
                    case RUNNING: motionTypeString = "Running"; break;
                    case IDLE: motionTypeString = "Idle"; break;
                    case UNKNOWN: motionTypeString = "Unknown"; break;
                    default: //do nothing
                }
                //appendToUIDistance(String.format("Distance = %.2f %s\nSpeed = %.2f %s\n",Distance/100.0, "m",
                        //bandDistanceEvent.getSpeed() / 100,"m/s") + "MotionType = " + motionTypeString + "\n" + "Time = " + format.format(new Date(bandDistanceEvent.getTimestamp())));
                MainActivity.TimeStamp = "" + bandDistanceEvent.getTimestamp();
                MainActivity.Time = format.format(new Date(bandDistanceEvent.getTimestamp()));
                MainActivity.mMotionType = motionTypeString;
                MainActivity.Speed = bandDistanceEvent.getSpeed();
            }
        }
    };

    private HeartRateConsentListener heartRateConsentListener = new HeartRateConsentListener() {
        @Override
        public void userAccepted(boolean b) {
            if(b == true){
                startHRListener();
            }
            else{
                //appendToUIHeartRate("user do not consent");
            }
        }
    };

    private BandGyroscopeEventListener bandGyroscopeEventListener = new BandGyroscopeEventListener() {
        @Override
        public void onBandGyroscopeChanged(BandGyroscopeEvent bandGyroscopeEvent) {
            if(bandGyroscopeEvent != null){
                /*appendToUIGyroscope(String.format("Gyroscope =\nX_An = %.3f Y_An = %.3f Z_An = %.3f",
                        bandGyroscopeEvent.getAngularVelocityX(),bandGyroscopeEvent.getAngularVelocityY(),bandGyroscopeEvent.getAngularVelocityZ()));*/
                MainActivity.Gyroscope = String.format("X_An = %.3f Y_An = %.3f Z_An = %.3f",
                        bandGyroscopeEvent.getAngularVelocityX(),bandGyroscopeEvent.getAngularVelocityY(),bandGyroscopeEvent.getAngularVelocityZ());
            }
        }
    };

    public void startHRListener() {
        try {
            // register HR sensor event listener
            client.getSensorManager().registerHeartRateEventListener(mBandHeartRateEventListener);
        } catch (BandIOException ex) {
            //appendToUI(ex.getMessage());
        } catch (BandException e) {
            String exceptionMessage="";
            switch (e.getErrorType()) {
                case UNSUPPORTED_SDK_VERSION_ERROR:
                    exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                    break;
                case SERVICE_ERROR:
                    exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                    break;
                default:
                    exceptionMessage = "Unknown error occurred: " + e.getMessage();
                    break;
            }
            //appendToUI(exceptionMessage);

        } catch (Exception e) {
            //appendToUI(e.getMessage());
        }
    }

    //连接手环
    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                Toast.makeText(context,"Your band is not connected!",Toast.LENGTH_SHORT);
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }
        //appendToUI("Band is connecting...\n");
        return ConnectionState.CONNECTED == client.connect().await();
    }



    public class ServiceBinder extends Binder {
        public CollectDataService getService(){
            return CollectDataService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        IBinder iBinder = new ServiceBinder();
        return iBinder;
    }
}
