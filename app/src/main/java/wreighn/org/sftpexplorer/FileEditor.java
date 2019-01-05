package wreighn.org.sftpexplorer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.File;

public class FileEditor extends AppCompatActivity {
    private FileEditor that;
    private String title, originalText, from, to;
    private TextView name;
    private EditText text;
    private ChannelSftp sftp;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_editor_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reload:
                text.setText(originalText);
                return true;
            case R.id.save:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File local = new File(from);
                            local.delete();
                            Statics.stringToFile(local, text.getText().toString());
                            sftp.put(from, to, new SftpProgressMonitor() {
                                @Override
                                public void init(int op, String src, String dest, long max) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Statics.alertHold(that, "Saving file", title + " is being saved");
                                        }
                                    });
                                }

                                @Override
                                public boolean count(long count) {
                                    return false;
                                }

                                @Override
                                public void end() {
                                    that.finish();
                                }
                            });
                        } catch (SftpException e) {
                            Log.d("mojtagerr", e.toString());
                        }
                    }
                }).start();
                return true;
            case R.id.close:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);
        that = this;
        Intent intent = getIntent();
        title = intent.getStringExtra("name");
        originalText = intent.getStringExtra("text");
        from = intent.getStringExtra("from");
        to = intent.getStringExtra("to");
        sftp = SFTPSession.staticSftp.get(intent.getLongExtra("sftp", -1));
        SFTPSession.staticSftp.remove(intent.getLongExtra("sftp", -1));
        name = findViewById(R.id.name);
        text = findViewById(R.id.text);
        name.setText(title);
        text.setText(originalText);
    }
}
