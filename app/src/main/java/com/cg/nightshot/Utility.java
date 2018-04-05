package com.cg.nightshot;

import android.app.Application;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Range;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

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
public static int nextpoweroftwo(int value){

    int highestOneBit = Integer.highestOneBit(value);
    if (value == highestOneBit) {
        return value;
    }
    return highestOneBit << 1;

}

}

