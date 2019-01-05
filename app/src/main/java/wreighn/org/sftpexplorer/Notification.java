package wreighn.org.sftpexplorer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Notification {
    public static List<Notification> all = new ArrayList<>();
    public static NotificationManager notificationManager = null;
    private int notificationId, progress, lastProgress;
    private boolean down, indeterminate;
    private boolean interrupted = false;
    private int max = -1;
    private String fileName;
    private NotificationCompat.Builder mBuilder;
    private Context cont;

    public Notification(Context cont, String fileName, boolean down, boolean indeterminate) {
        this.cont = cont;
        this.down = down;
        this.indeterminate = indeterminate;
        this.fileName = fileName;
        notificationId = new Random().nextInt();
        notificationId = notificationId < 0 ? notificationId * -1 : notificationId;
        mBuilder = new NotificationCompat.Builder(cont, "default")
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("SFTP Explorer")
                .setContentText((down ? "Downloading " : "Uploading ") + fileName)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, indeterminate);
        Intent notifyButton = new Intent();
        notifyButton.setAction("STOP_TRANSFER");
        notifyButton.putExtra("id", notificationId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(cont, notificationId, notifyButton, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.addAction(R.drawable.close, "Stop transfer", pendingIntent);
        notificationManager.notify(notificationId, mBuilder.build());
        all.add(this);
    }

    public void update(int curr, int max) {
        if (max == -1) indeterminate = true;
        progress = curr;
        this.max = max;
        mBuilder.setProgress(this.max, progress, indeterminate);
        notificationManager.notify(notificationId, mBuilder.build());
    }

    public void update(int curr, boolean add) {
        if (add) progress += curr;
        else progress = curr;
        if (progress < lastProgress + max / 20)
            return;
        lastProgress = progress;
        mBuilder.setProgress(this.max, progress, indeterminate);
        notificationManager.notify(notificationId, mBuilder.build());
    }

    public void done() {
        mBuilder.setProgress(0, 0, false);
        mBuilder.mActions.clear();
        if (isInterrupted())
            mBuilder.setContentText((down ? "Download" : "Upload") + " canceled");
        else
            mBuilder.setContentText(fileName + " has finished " + (down ? "downloading" : "uploading"));
        notificationManager.notify(notificationId, mBuilder.build());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                    notificationManager.cancel(notificationId);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        all.remove(this);
    }

    public int getNotificationId() {
        return notificationId;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void interrupt() {
        interrupted = true;
    }
}
