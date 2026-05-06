package com.app.fakesensor;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS = "app_prefs";
    private static final String KEY_LANG = "language";

    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    public static Context attachBaseContext(Context context) {
        String lang = getLanguage(context);
        return updateResources(context, lang);
    }

    public static String getLanguage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANG, LANG_ZH);
    }

    public static String toggleLanguage(Context context) {
        String current = getLanguage(context);
        String next = LANG_ZH.equals(current) ? LANG_EN : LANG_ZH;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANG, next).apply();
        return next;
    }

    public static String getDisplayLabel(Context context) {
        return LANG_ZH.equals(getLanguage(context)) ? "EN" : "中";
    }

    private static Context updateResources(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
