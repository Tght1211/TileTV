package com.tiletv.app.util;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * 读取 assets 目录下文件内容的工具类
 */
public class AssetUtil {

    public static String readAsset(Context context, String fileName) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
