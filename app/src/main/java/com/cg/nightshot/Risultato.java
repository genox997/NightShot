package com.cg.nightshot;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import com.squareup.picasso.Picasso;
import java.io.File;


public class Risultato extends AppCompatActivity {
    String path;
    View decorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DialogTheme);
        decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        setContentView(R.layout.activity_risultato);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");
        TouchImageView myImage = (TouchImageView)findViewById(R.id.imgrisultato);
        Intent intent = getIntent();
        String fname = intent.getStringExtra("fname");
        Integer height = Integer.parseInt(intent.getStringExtra("height"));
        Integer width = Integer.parseInt(intent.getStringExtra("width"));
        Log.d("PATH", Environment.getExternalStorageDirectory().toString());
        String sdcardPath = Environment.getExternalStorageDirectory().toString();
        path=sdcardPath+ "/DCIM/NightShot/"+fname;


        //Bitmap myBitmap = BitmapFactory.decodeFile(path);
        //int x= myBitmap.getWidth();
        //int y=myBitmap.getHeight();
        //myImage.setImageBitmap(myBitmap);
        //Picasso.with(this).load(new File(path)).resize((x/8),(y/8)).into(myImage);
        Picasso.with(this).load(new File(path)).resize(width/2,height/2).into(myImage);


    }

    public void btn_clicked(View v)
    {
        File filedel = new File(path);
        boolean deleted = filedel.delete();
        if (deleted){
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, FileProvider.getUriForFile(this,"com.cg.nightshot.fileprovider",filedel)));


        finish();}
    }

    public void FAB(View v)
    {
        Uri bmpUri = FileProvider.getUriForFile(this,"com.cg.nightshot.fileprovider",new File(path));
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, bmpUri);
        startActivity(Intent.createChooser(shareIntent, "Condividi"));
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
