package wreighn.org.sftpexplorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;

public class DB {
    private SharedPreferences preferences;

    public DB(Context cont) {
        preferences = PreferenceManager.getDefaultSharedPreferences(cont);
    }

    public void putJsonObject(String key, JsonObject value) {
        preferences.edit().putString(key, value.toString()).apply();
    }

    public void putJsonObjectList(String key, ArrayList<JsonObject> value) {
        JsonArray tmp = new JsonArray();
        for(JsonObject x : value)
            tmp.add(x);
        preferences.edit().putString(key, tmp.toString()).apply();
    }

    public JsonObject getJsonObject(String key) {
        return new JsonParser().parse(preferences.getString(key, "{}")).getAsJsonObject();
    }

    public ArrayList<JsonObject> getJsonObjectList(String key) {
        JsonArray tmp = new JsonParser().parse(preferences.getString(key, "[]")).getAsJsonArray();
        ArrayList<JsonObject> toReturn = new ArrayList<>();
        for (JsonElement x : tmp)
            toReturn.add(x.getAsJsonObject());
        return toReturn;
    }
}
