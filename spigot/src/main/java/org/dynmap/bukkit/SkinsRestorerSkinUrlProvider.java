package org.dynmap.bukkit;

import org.dynmap.SkinUrlProvider;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import skinsrestorer.bukkit.SkinsRestorer;
import skinsrestorer.bukkit.SkinsRestorerBukkitAPI;
import skinsrestorer.shared.utils.ReflectionUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SkinsRestorerSkinUrlProvider implements SkinUrlProvider {
    private JSONParser mJsonParser;
    private SkinsRestorerBukkitAPI mSkinsRestorerApi;

    SkinsRestorerSkinUrlProvider(SkinsRestorer skinsRestorer) {
        mJsonParser = new JSONParser();
        mSkinsRestorerApi = skinsRestorer.getSkinsRestorerBukkitAPI();
    }

    @Override
    public URL getSkinUrl(String playerName) {
        String skinName = mSkinsRestorerApi.getSkinName(playerName);

        Object skinDataProperty = mSkinsRestorerApi.getSkinData(skinName == null ? playerName : skinName);

        if (skinDataProperty == null)
            return null;

        String skinDataPropertyValue;

        try {
            skinDataPropertyValue = (String) ReflectionUtil.invokeMethod(skinDataProperty, "getValue");
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        byte[] skinDataBytes = Base64.getDecoder().decode(skinDataPropertyValue);

        JSONObject skinData;

        try {
            skinData = (JSONObject) mJsonParser.parse(new String(skinDataBytes, StandardCharsets.UTF_8));
        } catch (ParseException ex) {
            ex.printStackTrace();
            return null;
        }

        try {
            return new URL((String) ((JSONObject) ((JSONObject) skinData.get("textures")).get("SKIN")).get("url"));
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }

        return null;
    }
}