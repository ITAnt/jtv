package com.itant.jtv.exo.player;

import android.os.Environment;
import android.util.Log;

import com.miekir.mvvm.context.GlobalContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Path {

    private static final String TAG = Path.class.getSimpleName();

    private static File mkdir(File file) {
        if (!file.exists()) file.mkdirs();
        return file;
    }

    public static boolean exists(String path) {
        return new File(path.replace("file://", "")).exists();
    }

    public static File root() {
        return Environment.getExternalStorageDirectory();
    }

    public static File download() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static File cache() {
        return GlobalContext.getContext().getCacheDir();
    }


    public static File files() {
        return GlobalContext.getContext().getFilesDir();
    }

    public static String rootPath() {
        return root().getAbsolutePath();
    }

    public static String downloadPath() {
        return download().getAbsolutePath();
    }

    public static File tv() {
        return mkdir(new File(root() + File.separator + "TV"));
    }

    public static File so() {
        return mkdir(new File(files() + File.separator + "so"));
    }

    public static File js() {
        return mkdir(new File(cache() + File.separator + "js"));
    }

    public static File py() {
        return mkdir(new File(cache() + File.separator + "py"));
    }

    public static File jar(String name) {
        return mkdir(new File(cache() + File.separator + "jar"));
    }

    public static File doh() {
        return mkdir(new File(cache() + File.separator + "doh"));
    }

    public static File exo() {
        return mkdir(new File(cache() + File.separator + "exo"));
    }

    public static File epg() {
        return mkdir(new File(cache() + File.separator + "epg"));
    }


    public static File restore() {
        return mkdir(new File(cache() + File.separator + "restore"));
    }

    public static File root(String name) {
        return new File(root(), name);
    }

    public static File root(String child, String name) {
        return new File(mkdir(new File(root(), child)), name);
    }

    public static File cache(String name) {
        return new File(cache(), name);
    }

    public static File files(String name) {
        return new File(files(), name);
    }

    public static File epg(String name) {
        return new File(epg(), name);
    }

    public static File js(String name) {
        return new File(js(), name);
    }

    public static File py(String name) {
        return new File(py(), name);
    }



    public static File local(String path) {
        File file1 = new File(path.replace("file:/", ""));
        File file2 = new File(path.replace("file:/", rootPath()));
        return file2.exists() ? file2 : file1.exists() ? file1 : new File(path);
    }

    public static String read(File file) {
        try {
            return read(new FileInputStream(file));
        } catch (Exception e) {
            return "";
        }
    }

    public static String read(String path) {
        try {
            return read(new FileInputStream(local(path)));
        } catch (Exception e) {
            return "";
        }
    }

    public static String read(InputStream is) {
        try {
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static byte[] readToByte(File file) {
        try {
            FileInputStream is = new FileInputStream(file);
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }


    public static void move(File in, File out) {
        copy(in, out);
        clear(in);
    }

    public static void copy(File in, File out) {
        try {
            copy(new FileInputStream(in), out);
        } catch (Exception ignored) {
        }
    }

    public static void copy(InputStream in, File out) {
        try {
            int read;
            byte[] buffer = new byte[8192];
            FileOutputStream fos = new FileOutputStream(create(out));
            while ((read = in.read(buffer)) != -1) fos.write(buffer, 0, read);
            fos.close();
            in.close();
        } catch (Exception ignored) {
        }
    }

    public static File create(File file) throws Exception {
        try {
            if (!file.canWrite()) file.setWritable(true);
            if (!file.exists()) file.createNewFile();
            Shell.exec("chmod 777 " + file);
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return file;
        }
    }

    public static List<File> list(File dir) {
        File[] files = dir.listFiles();
        return files == null ? Collections.emptyList() : Arrays.asList(files);
    }

    public static void clear(File dir) {
        if (dir == null) return;
        if (dir.isDirectory()) for (File file : list(dir)) clear(file);
        if (dir.delete()) Log.d(TAG, "Deleted:" + dir.getAbsolutePath());
    }

}
