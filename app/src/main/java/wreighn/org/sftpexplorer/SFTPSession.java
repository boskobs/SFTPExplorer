package wreighn.org.sftpexplorer;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class SFTPSession extends AppCompatActivity {
    private SFTPSession that;
    private LinearLayout all;
    private JsonObject host;
    private ChannelSftp sftp;
    private JSch jsch;
    private com.jcraft.jsch.Session session = null;
    private TextView currentDir;
    private ArrayList<String> dirHistory;
    private Menu menu;

    public static HashMap<Long, ChannelSftp> staticSftp;
    private int transfers = 0;

    @Override
    public void onBackPressed() {
        if ((MainActivity.share != null && menu.size() > 0) || transfers == 0)
            super.onBackPressed();
        else
            Statics.alert(that, "Ongoing transfers", "Wait for the file transfer/s to finish", false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.sftp_session, menu);
        if (MainActivity.share != null) menu.removeGroup(R.id.default_group);
        else menu.removeGroup(R.id.share_group);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.upload:
                transfer(null, false);
                return true;
            case R.id.refresh:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        populate(currentDir.getText().toString());
                    }
                }).start();
                return true;
            case R.id.disconnect:
                if (transfers == 0)
                    disconnect(true);
                else
                    Statics.alert(that, "Ongoing transfers", "Wait for the file transfer/s to finish", false);
                return true;
            case R.id.touch:
                getNewFileDialog();
                return true;
            case R.id.share:
                shareTransfer();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sftpsession);
        //
        that = this;
        staticSftp = new HashMap<>();
        dirHistory = new ArrayList<>();
        all = findViewById(R.id.all);
        currentDir = findViewById(R.id.currentDir);
        currentDir.setSelected(true);
        Intent intent = getIntent();
        host = Statics.getHost(intent.getStringExtra("host"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                jsch = new JSch();
                jsch.setConfig("StrictHostKeyChecking", "no");
                try {
                    session = jsch.getSession(host.get("user").getAsString(), host.get("host").getAsString(), host.get("port").getAsInt());
                    if (!host.get("key").getAsString().equals("")) {
                        if (host.get("passphraseask").getAsBoolean()) {
                            getPassDialog(false);
                        } else {
                            if (!host.get("passphrase").getAsString().equals("")) {
                                jsch.addIdentity(host.get("name").getAsString(), host.get("key").getAsString().getBytes(), new byte[0], host.get("passphrase").getAsString().getBytes());
                                connect();
                            } else {
                                jsch.addIdentity(host.get("name").getAsString(), host.get("key").getAsString().getBytes(), new byte[0], new byte[0]);
                                connect();
                            }
                        }
                    } else {
                        if (!host.get("password").getAsString().equals("")) {
                            session.setPassword(host.get("password").getAsString());
                            connect();
                        } else {
                            getPassDialog(true);
                        }
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Statics.alert(that, "Error", e.toString().substring(e.toString().indexOf(":") + 2), true);
                        }
                    });
                    disconnect(false);
                }
            }
        }).start();
    }

    private void connect() {
        try {
            session.connect();
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Statics.alert(that, "Error", e.toString().substring(e.toString().indexOf(":") + 2), true);
                }
            });
            disconnect(false);
            return;
        }
        try {
            Thread.sleep(100);
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            String home = host.get("defaultpath").getAsString().equals("") ? sftp.getHome() : host.get("defaultpath").getAsString();
            //
            List<String> l = new LinkedList<>(Arrays.asList(home.split("/")));
            while (l.size() > 1) {
                dirHistory.add(0, Statics.join("/", l));
                l.remove(l.size() - 1);
            }
            dirHistory.add(0, "/");
            dirHistory.remove(dirHistory.size() - 1);
            //
            populate(home);
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Statics.alert(that, "Error", e.toString().substring(e.toString().indexOf(":") + 2), true);
                }
            });
            disconnect(false);
        }
    }

    private void populate(String dir) {
        if (dir.endsWith("/") && dir.length() > 1)
            dir = dir.substring(0, dir.length() - 1);
        try {
            if (dir.equals("..")) {
                if (dirHistory.size() > 1) {
                    sftp.cd(dirHistory.get(dirHistory.size() - 2));
                    dirHistory.remove(dirHistory.size() - 1);
                }
            } else {
                sftp.cd(dir);
                dirHistory.add(sftp.pwd());
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        currentDir.setText(sftp.pwd());
                    } catch (SftpException e) {
                        Log.d("mojtagerr", e.toString());
                    }
                }
            });
            Vector<ChannelSftp.LsEntry> filesRaw = sftp.ls(sftp.pwd());
            ArrayList<ChannelSftp.LsEntry> files = new ArrayList<>();
            ChannelSftp.LsEntry back = null;
            for (ChannelSftp.LsEntry x : filesRaw) {
                if (x.getAttrs().isDir() && !x.getFilename().equals(".") && !x.getFilename().equals(".."))
                    files.add(x);
                if (x.getFilename().equals("..")) back = x;
            }
            if (MainActivity.share == null)
                for (ChannelSftp.LsEntry x : filesRaw)
                    if (!x.getAttrs().isDir())
                        files.add(x);
            files.add(0, back);
            // Populate
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    all.removeAllViews();
                }
            });
            for (final ChannelSftp.LsEntry x : files) {
                final View fileContainer = LayoutInflater.from(this).inflate(R.layout.file_entry, null);
                TextView fileName = fileContainer.findViewById(R.id.name);
                TextView ownerGroupPermissions = fileContainer.findViewById(R.id.ownerGroupPermissions);
                ImageView fileImg = fileContainer.findViewById(R.id.fileImg);
                ImageView isLink = fileContainer.findViewById(R.id.isLink);
                LinearLayout clickable = fileContainer.findViewById(R.id.clickable);
                Button menu = fileContainer.findViewById(R.id.menu);
                if (MainActivity.share != null)
                    menu.setVisibility(View.GONE);
                char linkType = ' ';
                if (x.getAttrs().isDir())
                    fileImg.setImageDrawable(getResources().getDrawable(R.drawable.directory));
                else if (x.getAttrs().isLink()) {
                    ChannelSftp.LsEntry tracked = traceLink(x, currentDir.getText().toString());
                    if (tracked == null) {
                        linkType = 'n';
                        fileImg.setImageDrawable(getResources().getDrawable(R.drawable.link));
                    } else if (tracked.getAttrs().isDir()) {
                        linkType = 'd';
                        isLink.setVisibility(View.VISIBLE);
                        fileImg.setImageDrawable(getResources().getDrawable(R.drawable.directory));
                    } else {
                        linkType = '-';
                        isLink.setVisibility(View.VISIBLE);
                        fileImg.setImageDrawable(getResources().getDrawable(R.drawable.file));
                    }
                } else
                    fileImg.setImageDrawable(getResources().getDrawable(R.drawable.file));
                final char finalLinkType = linkType;
                menu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu popup = new PopupMenu(that, v);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.file_entry, popup.getMenu());
                        popup.show();
                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                if (item.getItemId() == R.id.open) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                if (x.getAttrs().isDir() || finalLinkType == 'd')
                                                    if (x.getFilename().equals(".."))
                                                        populate("..");
                                                    else populate(x.getFilename());
                                                else if (finalLinkType != 'n') {
                                                    final long id = System.nanoTime();
                                                    final File cache = new File(that.getApplicationContext().getCacheDir().getAbsolutePath() + "/" + id);
                                                    cache.mkdir();
                                                    sftp.get(currentDir.getText().toString() + "/" + x.getFilename(), cache.getAbsolutePath(), new SftpProgressMonitor() {
                                                        @Override
                                                        public void init(int op, String src, String dest, long max) {
                                                            transfers++;
                                                        }

                                                        @Override
                                                        public boolean count(long count) {
                                                            return true;
                                                        }

                                                        @Override
                                                        public void end() {
                                                            transfers--;
                                                            Intent intent = new Intent(that, FileEditor.class);
                                                            intent.putExtra("name", x.getFilename());
                                                            String txt = null;
                                                            try {
                                                                txt = Statics.getStringFromFile(cache.getAbsolutePath() + "/" + x.getFilename());
                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                            }
                                                            staticSftp.put(id, sftp);
                                                            intent.putExtra("name", x.getFilename());
                                                            intent.putExtra("text", txt);
                                                            intent.putExtra("sftp", id);
                                                            intent.putExtra("from", cache.getAbsolutePath() + "/" + x.getFilename());
                                                            intent.putExtra("to", currentDir.getText().toString());
                                                            startActivity(intent);
                                                        }
                                                    });

                                                }
                                            } catch (SftpException e) {
                                                Log.d("mojtagerr", e.toString());
                                            }
                                        }
                                    }).start();
                                } else if (item.getItemId() == R.id.remove) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(that);
                                    builder.setTitle("File delete");
                                    builder.setMessage("Are you sure you want to delete " + x.getFilename() + "?");
                                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        sftp.rm(currentDir.getText().toString() + File.separator + x.getFilename());
                                                        populate(currentDir.getText().toString());
                                                    } catch (SftpException e) {
                                                        Log.d("mojtagerr", e.toString());
                                                    }
                                                }
                                            }).start();
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                } else if (item.getItemId() == R.id.properties) {
                                    AlertDialog.Builder builder1 = new AlertDialog.Builder(that);
                                    View layout = LayoutInflater.from(that).inflate(R.layout.file_properties, null, false);
                                    builder1.setView(layout);
                                    builder1.setTitle("Properties");
                                    ((TextView) layout.findViewById(R.id.name)).setText(x.getFilename());
                                    ((TextView) layout.findViewById(R.id.type)).setText(Statics.getFileType(x.getAttrs().getPermissionsString().charAt(0)));
                                    ((TextView) layout.findViewById(R.id.size)).setText(Statics.formatFileSize(x.getAttrs().getSize()));
                                    ((TextView) layout.findViewById(R.id.accessed)).setText(x.getAttrs().getAtimeString());
                                    ((TextView) layout.findViewById(R.id.modified)).setText(x.getAttrs().getMtimeString());
                                    String[] wOSpaces = x.getLongname().replaceAll("  +", "_").replace(" ", "_").split("_");
                                    ((TextView) layout.findViewById(R.id.owner)).setText(wOSpaces[2]);
                                    ((TextView) layout.findViewById(R.id.group)).setText(wOSpaces[3]);
                                    boolean ur = x.getAttrs().getPermissionsString().charAt(1) == 'r';
                                    boolean uw = x.getAttrs().getPermissionsString().charAt(2) == 'w';
                                    boolean ux = x.getAttrs().getPermissionsString().charAt(3) == 'x';
                                    boolean gr = x.getAttrs().getPermissionsString().charAt(4) == 'r';
                                    boolean gw = x.getAttrs().getPermissionsString().charAt(5) == 'w';
                                    boolean gx = x.getAttrs().getPermissionsString().charAt(6) == 'x';
                                    boolean or = x.getAttrs().getPermissionsString().charAt(7) == 'r';
                                    boolean ow = x.getAttrs().getPermissionsString().charAt(8) == 'w';
                                    boolean ox = x.getAttrs().getPermissionsString().charAt(9) == 'x';
                                    ((TextView) layout.findViewById(R.id.permissions)).setText(((ur ? 4 : 0) + (uw ? 2 : 0) + (ux ? 1 : 0)) + "" + ((gr ? 4 : 0) + (gw ? 2 : 0) + (gx ? 1 : 0)) + "" + ((or ? 4 : 0) + (ow ? 2 : 0) + (ox ? 1 : 0)));
                                    ((CheckBox) layout.findViewById(R.id.ur)).setChecked(ur);
                                    ((CheckBox) layout.findViewById(R.id.uw)).setChecked(uw);
                                    ((CheckBox) layout.findViewById(R.id.ux)).setChecked(ux);
                                    ((CheckBox) layout.findViewById(R.id.gr)).setChecked(gr);
                                    ((CheckBox) layout.findViewById(R.id.gw)).setChecked(gw);
                                    ((CheckBox) layout.findViewById(R.id.gx)).setChecked(gx);
                                    ((CheckBox) layout.findViewById(R.id.or)).setChecked(or);
                                    ((CheckBox) layout.findViewById(R.id.ow)).setChecked(ow);
                                    ((CheckBox) layout.findViewById(R.id.ox)).setChecked(ox);
                                    builder1.setPositiveButton(
                                            "Ok",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            });
                                    AlertDialog alert11 = builder1.create();
                                    alert11.show();
                                }
                                return true;
                            }
                        });
                    }
                });
                fileName.setText(x.getFilename());
                String[] wOSpaces = x.getLongname().replaceAll("  +", "_").replace(" ", "_").split("_");
                ownerGroupPermissions.setText(wOSpaces[2] + ":" + wOSpaces[3] + " " + x.getAttrs().getPermissionsString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        all.addView(fileContainer);
                    }
                });
                if (x.getAttrs().isDir() || linkType == 'd')
                    clickable.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if (x.getFilename().equals(".."))
                                        populate("..");
                                    else populate(x.getFilename());
                                }
                            }).start();
                        }
                    });
                else if (linkType != 'n')
                    clickable.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            transfer(x.getFilename(), true);
                        }
                    });
            }
        } catch (SftpException e) {
            Log.d("mojtagerr", e.toString());
        }
    }

    private void shareTransfer() {
        final Notification noti = new Notification(that, MainActivity.shareName, false, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sftp.put(MainActivity.share, currentDir.getText().toString() + "/" + MainActivity.shareName, new SftpProgressMonitor() {
                        @Override
                        public void init(int op, String src, String dest, long max) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    menu.removeGroup(R.id.share_group);
                                }
                            });
                            noti.update(0, (int) max);
                            that.moveTaskToBack(true);
                        }

                        @Override
                        public boolean count(long count) {
                            noti.update((int) count, true);
                            return !Notification.all.get(Notification.all.indexOf(noti)).isInterrupted();
                        }

                        @Override
                        public void end() {
                            noti.done();
                            try {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!noti.isInterrupted())
                                            Toast.makeText(that.getApplicationContext(), MainActivity.shareName + " has finished uploading", Toast.LENGTH_SHORT).show();
                                        that.finish();
                                        MainActivity.that.finish();
                                    }
                                });
                            } catch (Exception e) {
                                Log.d("mojtagerr", e.toString());
                            }
                        }
                    });
                } catch (SftpException e) {
                    Log.d("mojtagerr", e.toString());
                }
            }
        }).start();
    }

    private ChannelSftp.LsEntry traceLink(ChannelSftp.LsEntry x, String oldPath) throws SftpException {
        String line = sftp.readlink(oldPath + "/" + x.getFilename());
        String name = line.substring(line.lastIndexOf("/") + 1);
        String path = line.substring(0, line.lastIndexOf("/"));
        ChannelSftp.LsEntry entry = null;
        Vector<ChannelSftp.LsEntry> fls = sftp.ls(path);
        for (ChannelSftp.LsEntry y : fls) {
            if (y.getFilename().equals(name)) {
                entry = y;
                break;
            }
        }
        if (entry == null) return null;
        if (entry.getAttrs().isLink())
            entry = traceLink(entry, path);
        return entry;
    }

    private void transfer(final String fileName, final boolean down) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ChooserDialog dialog = new ChooserDialog(that).withStartFile(Environment.getExternalStorageDirectory().getPath());
                if (down)
                    dialog.withFilter(true, true);
                dialog.withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(final String path, final File pathFile) {
                        final Notification noti = new Notification(that, down ? fileName : pathFile.getName(), down, false);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    SftpProgressMonitor mon = new SftpProgressMonitor() {
                                        @Override
                                        public void init(int op, String src, String dest, long max) {
                                            transfers++;
                                            noti.update(0, (int) max);
                                        }

                                        @Override
                                        public boolean count(long count) {
                                            noti.update((int) count, true);
                                            return !Notification.all.get(Notification.all.indexOf(noti)).isInterrupted();
                                        }

                                        @Override
                                        public void end() {
                                            transfers--;
                                            noti.done();
                                            try {
                                                if (!noti.isInterrupted())
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(that.getApplicationContext(), fileName + " has finished " + (down ? "downloading" : "uploading"), Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                                if (!down)
                                                    populate(sftp.pwd());
                                            } catch (Exception e) {
                                                Log.d("mojtagerr", e.toString());
                                            }
                                        }
                                    };
                                    if (down)
                                        sftp.get(fileName, path, mon);
                                    else
                                        sftp.put(pathFile.getAbsolutePath(), sftp.pwd(), mon);
                                } catch (SftpException e) {
                                    e.printStackTrace();
                                    Log.d("mojtagera", e.toString());
                                }
                            }
                        }).start();
                    }
                });
                dialog.build().show();
            }
        });
    }

    private String runCmd(String cmd) {
        String result = null;
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            channel.setOutputStream(stream);
            channel.setCommand(cmd);
            channel.connect(1000);
            Thread.sleep(100);
            channel.disconnect();
            result = stream.toString();
        } catch (JSchException e) {
            Log.d("mojtagerr", e.toString());
            disconnect(true);
        } catch (InterruptedException e) {
            Log.d("mojtagerr", e.toString());
            disconnect(true);
        }
        return result;
    }

    private void getNewFileDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(that);
        builder.setTitle("New file");
        builder.setMessage("Enter the file name");
        View viewInflated = LayoutInflater.from(that).inflate(R.layout.text_input, null, false);
        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(viewInflated);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                session.setPassword(input.getText().toString());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (input.getText().toString().equals(""))
                            dialog.dismiss();
                        else {
                            runCmd("cd \"" + currentDir.getText().toString() + "\";touch \"" + input.getText().toString().replace("\"", "").replace("/", "") + "\"");
                            populate(currentDir.getText().toString());
                        }
                    }
                }).start();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.show();
            }
        });
    }


    private void getPassDialog(final boolean password) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(that);
        builder.setTitle("Enter " + (password ? "password" : "passphrase"));
        View viewInflated = LayoutInflater.from(that).inflate(R.layout.text_input, null, false);
        final EditText input = viewInflated.findViewById(R.id.input);
        builder.setView(viewInflated);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                session.setPassword(input.getText().toString());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (password)
                                session.setPassword(input.getText().toString());
                            else
                                jsch.addIdentity(host.get("name").getAsString(), host.get("key").getAsString().getBytes(), new byte[0], input.getText().toString().getBytes());
                        } catch (JSchException e) {
                            Log.d("mojtagerr", e.toString());
                            disconnect(true);
                        }
                        connect();
                    }
                }).start();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                disconnect(true);
                dialog.cancel();
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.show();
            }
        });
    }

    public void disconnect(boolean finish) {
        if (session != null)
            session.disconnect();
        if (finish)
            finish();
    }
}
