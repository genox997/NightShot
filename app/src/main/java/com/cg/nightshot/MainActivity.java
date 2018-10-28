package com.cg.nightshot;
import android.app.ActionBar;
import android.app.DialogFragment;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.os.Bundle;
import android.Manifest;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

//open cv
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;


import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.LogRecord;



public class MainActivity extends AppCompatActivity {
File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/NightShot/temp/");
    int o=deleteRecursive(dir);
    private static int FILE_SELECT_CODE = 1;
    List<String> imagesEncodedList;
    ArrayList<Uri> mArrayUri;
    int Numeroimg=0;                                        //numero di immagini caricate
    public Context context =this;
    private static final int MY_PERMISSIONS_REQUEST=1;
    String fname;
    MatOfPoint CornersprimaBitmap;
    int maxWidth,maxHeight;
    View decorView;



    ///notifiche

    private NotificationManager mNotifyManager;
    private Builder mBuilder;
    int id = 1;




    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d("ERROR", "Unable to load OpenCV");
        } else {
            Log.d("SUCCESS", "OpenCV loaded");
        }
    }
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        decorView = getWindow().getDecorView();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.DialogTheme);
        setContentView(R.layout.activity_main);

        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2BasicFragment.newInstance())
                    .commit();
        }
        //permessi
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)&&(ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)) {

            if ((ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))&&(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA))) {
            Toast.makeText(this,"Impossibile salvare il risultato senza il permesso di accesso alla memoria",Toast.LENGTH_LONG).show();


            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST);
            }
        }


        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(MainActivity.this);
        mBuilder.setContentTitle("Night Shot");




}
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    public  void Esecuzione() {
        mArrayUri = new ArrayList<Uri>();

        Numeroimg=0;
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                Uri uri = Uri.fromFile(f);
                mArrayUri.add(uri);
                Numeroimg++;
                Log.d("n:",Integer.toString(Numeroimg));
            }
        }
        new AveragingTask().execute();
    }







    private class AveragingTask extends AsyncTask<String, Integer, Bitmap> {
        int N;
        ArrayList<Uri> ListaUri;

        AveragingTask() {
            this.N = Numeroimg;
            this.ListaUri = mArrayUri;
        }

        @Override
        protected void onPreExecute() {


            mBuilder.setProgress(100, 0, false);
            mBuilder.setContentTitle("Night Shot");
            mBuilder.setContentText("Operazione in corso");
            mBuilder.setAutoCancel(true);
            mBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
            mNotifyManager.notify(id, mBuilder.build());

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            // Update progress
            mBuilder.setProgress(100, values[0], false);
            mNotifyManager.notify(id, mBuilder.build());
            super.onProgressUpdate(values);
        }
        @Override
        protected Bitmap doInBackground(String... params) {

            try {

                Bitmap primo = MediaStore.Images.Media.getBitmap(context.getContentResolver(), ListaUri.get(0)); //carica la prima bitmap
                maxWidth = primo.getWidth();                                                                 //dimensioni dell'immagine
                maxHeight = primo.getHeight();
                int p;
                double alpha,beta;
                Bitmap finale = Bitmap.createBitmap(maxWidth, maxHeight, primo.getConfig());                     //crea la bitmap finale

                CornersprimaBitmap=Alignment.TrackFeatures(primo);//traccia le features nella prima immagine;

                Mat matRisultato = new Mat(primo.getHeight(), primo.getWidth(), CvType. CV_64FC3);

                Utils.bitmapToMat(primo,matRisultato);
                for (int i = 1; i < N; i++) {                                                                    //ciclo con sovrapposizione delle immagini corrette
                    p=(int)((100/(double)N)*((double)i));
                   // progressDialog.setProgress(p);
                    Log.d("progress", Integer.toString(p));
                    publishProgress(p);
                    alpha=1-(1/((double)i+1.0));
                    beta=1-alpha;
                    Core.addWeighted(matRisultato,alpha,Alignment.alignImages(primo,MediaStore.Images.Media.getBitmap(context.getContentResolver(), ListaUri.get(i)),CornersprimaBitmap),beta,0.0,matRisultato);
                }
                Utils.matToBitmap(matRisultato,finale);
                Utility.changeBitmapContrastBrightness(finale,1.13f);


                System.gc();
                mArrayUri=null;
                if (dir.isDirectory()) {
                    String[] children = dir.list();
                    for (int i = 0; i < children.length; i++) {
                        new File(dir, children[i]).delete();
                    }


                }
                return finale;
            } catch (OutOfMemoryError e) {


                return null;

            } catch (IOException e2) {

                return null;
            }

        }



        @Override
        protected void onPostExecute(Bitmap finale) {
            mBuilder.setContentText("Operazione conclusa");
            // Removes the progress bar
            mBuilder.setProgress(0,0,false);
            mBuilder.setSmallIcon(android.R.drawable.btn_star);
            mNotifyManager.cancelAll();

            if (finale != null) {
                //salvataggio
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                File myDir = new File(root + "/DCIM/NightShot");
                myDir.mkdirs();
                Calendar c= Calendar.getInstance();
                SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd_HHmmSS");
                String data=sdf.format(c.getTime());
                fname= "IMG"+data+".jpg";
                File file = new File(myDir, fname);




                if (file.exists())
                    file.delete();
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    finale.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    Picasso.with(getApplicationContext()).load(new File(Environment.getExternalStorageDirectory().toString()+"/DCIM/NightShot/"+fname)).fetch();

                    try {
                        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");
                        exif.saveAttributes();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    out.flush();
                    out.close();
                    finale.recycle();
                            System.gc();
                } catch (Exception e) {

                    e.printStackTrace();

                }

                // Tell the media scanner about the new file so that it is
                // immediately available to the user.
                MediaScannerConnection.scanFile(context, new String[]{file.toString()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i("ExternalStorage", "Scanned " + path + ":");
                                Log.i("ExternalStorage", "-> uri=" + uri);
                            }
                        });

                Intent Intentrisultato = new Intent(MainActivity.this, Risultato.class);

                Intentrisultato.putExtra("fname", fname); //Optional parameters
                Intentrisultato.putExtra("width", Integer.toString(maxWidth)); //Optional parameters
                Intentrisultato.putExtra("height", Integer.toString(maxHeight)); //Optional parameters

                MainActivity.this.startActivity(Intentrisultato);
                //progressDialog.dismiss();


                System.gc();

            } //else
                //Toast.makeText(context, "Errore", Toast.LENGTH_SHORT).show();
                //progressDialog.dismiss();

            // might want to change "executed" for the returned string passed
            // into onPostExecute() but that is upto you
        }
    }
        @Override
        public void onRequestPermissionsResult(int requestCode,
                                               String permissions[], int[] grantResults) {
            switch (requestCode) {
                case MY_PERMISSIONS_REQUEST: {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        // permesso dato

                    } else {

                        // permission negato
                    }
                    return;
                }
            }
        }
    protected void onResume() {
        super.onResume();
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }
    public int deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
        int i=1;
        return i;
    }
    public void hideStatusBar()
    {
        View decorView = getWindow().getDecorView();
// Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
// Remember that you should never show the action bar if the
// status bar is hidden, so hide that too if necessary.
        ActionBar actionBar = getActionBar();
        if (actionBar!= null)
            actionBar.hide();
    }

    public void showStatusBar()
    {
        View decorView = getWindow().getDecorView();
// Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
        decorView.setSystemUiVisibility(uiOptions);
// Remember that you should never show the action bar if the
// status bar is hidden, so hide that too if necessary.
        ActionBar actionBar = getActionBar();
        if (actionBar!= null)
            actionBar.show();
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

}






