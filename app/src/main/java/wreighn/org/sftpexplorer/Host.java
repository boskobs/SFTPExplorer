package wreighn.org.sftpexplorer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.google.gson.JsonObject;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.util.ArrayList;

public class Host extends AppCompatActivity {
    private Host that;
    private JsonObject oldHost = null;
    private boolean edit = false;
    private EditText name, host, user, password, port, key, passphrase, defaultPath;
    private CheckBox passphraseAsk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
        that = this;
        name = findViewById(R.id.name);
        host = findViewById(R.id.host);
        user = findViewById(R.id.user);
        password = findViewById(R.id.password);
        port = findViewById(R.id.port);
        key = findViewById(R.id.key);
        passphrase = findViewById(R.id.passphrase);
        passphraseAsk = findViewById(R.id.passphraseAsk);
        defaultPath = findViewById(R.id.defaultPath);
        Intent intent = getIntent();
        edit = intent.getBooleanExtra("edit", false);
        if (edit) {
            oldHost = Statics.getHost(intent.getLongExtra("id", -1));
            name.setText(oldHost.get("name").getAsString());
            host.setText(oldHost.get("host").getAsString());
            user.setText(oldHost.get("user").getAsString());
            password.setText(oldHost.get("password").getAsString());
            passphrase.setText(oldHost.get("passphrase").getAsString());
            key.setText(oldHost.get("key").getAsString());
            port.setText(oldHost.get("port").getAsInt() + "");
            passphraseAsk.setChecked(oldHost.get("passphraseask").getAsBoolean());
            defaultPath.setText(oldHost.get("defaultpath").getAsString());
        }
    }

    public void findKey(View v) {
        new ChooserDialog(that).withStartFile("/sdcard/")
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String path, File pathFile) {
                        try {
                            key.setText(Statics.getStringFromFile(path));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).build().show();
    }

    public void save(View v) {
        if (name.getText().toString().equals("")) {
            name.setError("The \"Name\" field mustn't be empty.");
            return;
        } else if (host.getText().toString().equals("")) {
            host.setError("The \"Hostname\" field mustn't be empty.");
            return;
        } else if (user.getText().toString().equals("")) {
            user.setError("The \"User\" field mustn't be empty.");
            return;
        }
        JsonObject host = new JsonObject();
        host.addProperty("id", edit ? oldHost.get("id").getAsLong() : System.nanoTime());
        host.addProperty("name", name.getText().toString());
        host.addProperty("host", that.host.getText().toString());
        host.addProperty("user", user.getText().toString());
        host.addProperty("port", port.getText().toString().equals("") ? 22 : Integer.parseInt(port.getText().toString()));
        host.addProperty("password", password.getText().toString());
        host.addProperty("passphrase", passphrase.getText().toString());
        host.addProperty("passphraseask", passphraseAsk.isChecked());
        host.addProperty("defaultpath", defaultPath.getText().toString());
        host.addProperty("key", key.getText().toString());
        ArrayList<JsonObject> tmpHosts = Statics.getHosts();
        if (!edit) {
            for (JsonObject x : tmpHosts) {
                if (x.get("name").getAsString().equals(name.getText().toString())) {
                    name.setError("This host entry already exists");
                    return;
                }
            }
        } else {
            for (JsonObject x : tmpHosts) {
                if (x.get("id").getAsLong() == oldHost.get("id").getAsLong()) {
                    tmpHosts.remove(x);
                    break;
                }
            }
        }
        tmpHosts.add(host);
        Statics.setHosts(tmpHosts);
        finish();
    }
}
