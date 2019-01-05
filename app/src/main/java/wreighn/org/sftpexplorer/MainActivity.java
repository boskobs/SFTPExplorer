package wreighn.org.sftpexplorer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

import static com.jcraft.jsch.JSch.setConfig;

public class MainActivity extends AppCompatActivity {
    public static MainActivity that;
    private LinearLayout hostsList;
    private BroadcastReceiver broadcastReceiver;
    public static InputStream share = null;
    public static String shareName = null;

    private void onSharedIntent() {
        Intent receiveIntent = getIntent();
        Uri receiveUri = receiveIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (receiveUri != null) {
            try {
                shareName = receiveUri.getPath().substring(receiveUri.getPath().lastIndexOf("/") + 1);
                findViewById(R.id.floatingActionButton).setVisibility(View.GONE);
                share = getContentResolver().openInputStream(receiveUri);
                setFileName(shareName);
            } catch (FileNotFoundException e) {
                Log.d("mojtagerr", e.toString());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 100: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Statics.alert(that, "Permission warning", "This app needs the write permission in order to function", true);
                }
            }
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        that = this;
        makeChannel();
        defineReciever();
        onSharedIntent();
        Statics.getWritePermission(that);
        Statics.db = new DB(that.getApplicationContext());
        hostsList = findViewById(R.id.hostsList);
        populate();
        for (File x : that.getApplicationContext().getCacheDir().listFiles())
            Statics.deleteRecursive(x);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that.getApplicationContext().unregisterReceiver(broadcastReceiver);
    }

    public void addHost(View v) {
        Intent intent = new Intent(that, Host.class);
        startActivity(intent);
    }

    private void defineReciever() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle e = intent.getExtras();
                for (Notification noti : Notification.all)
                    if (noti.getNotificationId() == e.getInt("id")) {
                        noti.interrupt();
                        break;
                    }
            }
        };
        that.getApplicationContext().registerReceiver(broadcastReceiver, new IntentFilter("STOP_TRANSFER"));
    }

    private void populate() {
        hostsList.removeAllViews();
        for (final JsonObject x : Statics.getHosts()) {
            final View hostContainer = LayoutInflater.from(that).inflate(R.layout.host_entry, null);
            final TextView name = hostContainer.findViewById(R.id.name);
            Button menu = hostContainer.findViewById(R.id.menu);
            if (share != null)
                menu.setVisibility(View.GONE);
            name.setText(x.get("name").getAsString());
            name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), SFTPSession.class);
                    intent.putExtra("host", x.get("name").getAsString());
                    startActivity(intent);
                }
            });
            //
            menu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popup = new PopupMenu(that, v);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.host_entry, popup.getMenu());
                    popup.show();
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            ArrayList<JsonObject> tmpHosts = Statics.getHosts();
                            if (item.getTitle().equals("Edit")) {
                                Intent intent = new Intent(that, Host.class);
                                intent.putExtra("edit", true);
                                intent.putExtra("id", x.get("id").getAsLong());
                                startActivity(intent);
                            } else if (item.getTitle().equals("Remove")) {
                                for (JsonObject x : tmpHosts) {
                                    if (x.get("name").getAsString().equals(name.getText().toString())) {
                                        tmpHosts.remove(x);
                                        Statics.setHosts(tmpHosts);
                                        break;
                                    }
                                }
                                populate();
                            }
                            return true;
                        }
                    });
                }
            });
            hostsList.addView(hostContainer);
        }
    }

    private void makeChannel() {
        if (Notification.notificationManager != null) return;
        Notification.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("default",
                    "SFTPExplorer",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("SFTPExplorerChannel");
            Notification.notificationManager.createNotificationChannel(channel);
        }
    }

    private void setFileName(String preDef) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(that);
        builder.setTitle("File name");
        View viewInflated = LayoutInflater.from(that).inflate(R.layout.text_input, null, false);
        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(preDef);
        builder.setView(viewInflated);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                if (!input.getText().toString().equals(""))
                    shareName = input.getText().toString();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }
}
