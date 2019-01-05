package wreighn.org.sftpexplorer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.gson.JsonObject;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Statics {
    private Statics() {
    }

    public static DB db = null;

    public static ArrayList<JsonObject> getKeys() {
        return Statics.db.getJsonObjectList("keys");
    }

    public static void setKeys(ArrayList<JsonObject> keys) {
        Statics.db.putJsonObjectList("keys", keys);
    }

    public static ArrayList<JsonObject> getHosts() {
        ArrayList<JsonObject> list = Statics.db.getJsonObjectList("hosts");
        if (list.size() > 0) {
            Collections.sort(list, new Comparator<JsonObject>() {
                @Override
                public int compare(final JsonObject object1, final JsonObject object2) {
                    return object1.get("name").getAsString().compareTo(object2.get("name").getAsString());
                }
            });
        }
        return list;
    }

    public static JsonObject getHost(long id) {
        for (JsonObject x : getHosts()) {
            if (x.get("id").getAsLong() == id)
                return x;
        }
        return null;
    }

    public static JsonObject getHost(String name) {
        for (JsonObject x : getHosts()) {
            if (x.get("name").getAsString().equals(name))
                return x;
        }
        return null;
    }

    public static void setHosts(ArrayList<JsonObject> hosts) {
        Statics.db.putJsonObjectList("hosts", hosts);
    }

    public static void getWritePermission(Activity that) {
        if (ContextCompat.checkSelfPermission(that,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(that,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Statics.alert(that, "Permission warning", "This app needs the write permission in order to function", true);
            } else {
                ActivityCompat.requestPermissions(that,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile(String filePath) throws Exception {
        File fl = new File(filePath);
        if (!fl.exists()) return null;
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        fin.close();
        return ret;
    }

    public static void alertHold(final Context that, String title, String text) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(that);
        builder1.setCancelable(false);
        builder1.setTitle(title);
        builder1.setMessage(text);
        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public static void alert(final Context that, String title, String text, final boolean wait) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(that);
        builder1.setCancelable(false);
        builder1.setTitle(title);
        builder1.setMessage(text);
        builder1.setPositiveButton(
                "Ok",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        if (wait)
                            ((Activity) that).finish();
                    }
                });
        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public static String formatFileSize(long size) {
        String hrSize = null;
        double b = size;
        double k = size / 1024.0;
        double m = ((size / 1024.0) / 1024.0);
        double g = (((size / 1024.0) / 1024.0) / 1024.0);
        double t = ((((size / 1024.0) / 1024.0) / 1024.0) / 1024.0);
        DecimalFormat dec = new DecimalFormat("0.00");
        if (t > 1) {
            hrSize = dec.format(t).concat(" TB");
        } else if (g > 1) {
            hrSize = dec.format(g).concat(" GB");
        } else if (m > 1) {
            hrSize = dec.format(m).concat(" MB");
        } else if (k > 1) {
            hrSize = dec.format(k).concat(" KB");
        } else {
            hrSize = dec.format(b).concat(" Bytes");
        }
        return hrSize;
    }

    public static String getFileType(char c) {
        switch (c) {
            case '-':
                return "Regular file";
            case 'd':
                return "Directory";
            case 'c':
                return "Character device file";
            case 'b':
                return "Block device file";
            case 's':
                return "Local socket file";
            case 'p':
                return "Named pipe";
            case 'l':
                return "Symbolic link";
            default:
                return "Unknown";
        }
    }

    public static String join(String d, List<String> list) {
        String ret = "";
        for(String s : list)
            ret += s + d;
        return ret.substring(0, ret.length() - 1);
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);
        fileOrDirectory.delete();
    }

    public static void stringToFile(File file, String txt) {
        try {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(txt);
            myOutWriter.close();
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            Log.d("mojtagerr", e.toString());
        }
    }

    public static String removeTrailing(String txt, String trail) {
        while (txt.endsWith(trail) && txt.length() > 0)
            txt = txt.substring(0, txt.length() - 1);
        return txt;
    }
}
