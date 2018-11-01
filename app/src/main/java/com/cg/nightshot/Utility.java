package com.cg.nightshot;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import android.widget.LinearLayout;

import com.squareup.picasso.Picasso;
import java.io.File;

public class Utility {
    public static ArrayList<Uri> Caricamento(ArrayList<Uri>ListaUri){
        ListaUri = new ArrayList<Uri>();
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ "/DCIM/NightShot/temp/");
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                Uri uri = Uri.fromFile(f);
                ListaUri.add(uri);
            }
        }
        return ListaUri;}

    public void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
    }


    public static Bitmap changeBitmapContrastBrightness(Bitmap bmp, float contrast)
    {
        //contrast from 0 to 10, 1 is default
        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, 1,
                        0, contrast, 0, 0, 1,
                        0, 0, contrast, 0, 1,
                        0, 0, 0, 1, 0
                });



        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bmp, 0, 0, paint);

        return bmp;
    }

}

