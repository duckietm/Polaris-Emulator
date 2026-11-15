package com.srth.plugin.utilities.utils;

import com.google.gson.Gson;

public class GSON {
    private static Gson gson = new Gson();
    public GSON(){
    }

    public static Gson getGson() {
        return gson;
    }
}
