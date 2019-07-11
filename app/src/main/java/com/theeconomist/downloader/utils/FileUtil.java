package com.theeconomist.downloader.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import com.theeconomist.downloader.activity.MainActivity;
import com.theeconomist.downloader.bean.Mp3FileBean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {

    public static String path=Environment.getExternalStorageDirectory().getPath()+File.separator+"The Economist";

    public static String fileName;

    public static String url;

    // 需要解压的文件
    public static File file;

    public static ArrayList<Mp3FileBean> fileList;

    private static int BUFFER_SIZE=4096;

    public static void unZip(File srcFile, String destDirPath, Handler handler) {
        // 判断源文件是否存在
        if (!srcFile.exists()) {
            Log.d("File Unzip",srcFile.getPath() + "所指文件不存在");
        }
        // 开始解压
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(srcFile);
            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                System.out.println("解压" + entry.getName());
                // 如果是文件夹，就创建个文件夹
                if (entry.isDirectory()) {
                    String dirPath = destDirPath + "/" + entry.getName();
                    File dir = new File(dirPath);
                    dir.mkdirs();
                } else {
                    // 如果是文件，就先创建一个文件，然后用io流把内容copy过去
                    File targetFile = new File(destDirPath + "/" + entry.getName());
                    // 保证这个文件的父文件夹必须要存在
                    if(!targetFile.getParentFile().exists()){
                        targetFile.getParentFile().mkdirs();
                    }
                    targetFile.createNewFile();
                    // 将压缩文件内容写入到这个文件中
                    InputStream is = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    byte[] buf = new byte[BUFFER_SIZE];
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    // 关流顺序，先打开的后关闭
                    fos.close();
                    is.close();
                    Message msg=new Message();
                    msg.what= MainActivity.UPDATE_UNZIP_PROGRESS;
                    Bundle bundle=new Bundle();
                    bundle.putLong("File Size",entry.getSize());
                    msg.setData(bundle);
                    handler.sendMessageDelayed(msg,500);
                    try {
                        Thread.sleep(200);
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            Log.d("File Unzip","unzip error from ZipUtils");
        } finally {
            if(zipFile != null){
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        handler.sendEmptyMessageDelayed(MainActivity.DISMISS_UNZIP_DIALOG,1000);
    }

    public static long getZipTrueSize(String filePath) {
        long size = 0;
        try {
            ZipFile f = new ZipFile(filePath);
            Enumeration<? extends ZipEntry> en = f.entries();
            while (en.hasMoreElements()) {
                size += en.nextElement().getSize();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return size;
    }

    public static String getFileSize(long length){
        String size = "";
        DecimalFormat df = new DecimalFormat("#.00");
        if (length < 1024) {
            size = df.format((double) length) + "B";
        } else if (length < 1048576) {
            size = df.format((double) length / 1024) + "KB";
        } else if (length < 1073741824) {
            size = df.format((double) length / 1048576) + "MB";
        } else {
            size = df.format((double) length / 1073741824) +"GB";
        }
        return size;
    }

    public static String getLongTime(long mss) {
        String dateTimes = null;
        long minutes = (mss % ( 60 * 60)) /60;
        long seconds = mss % 60;
        dateTimes=String.format("%02d:", minutes) + String.format("%02d", seconds);
        return dateTimes;
    }

    private static byte[] loadMP3Cover(String path) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(path);
        byte[] cover = mediaMetadataRetriever.getEmbeddedPicture();
        return cover;
    }

    public static boolean getMusicInfo(Context context, Mp3FileBean mp3File) {
        // 查询媒体数据库
        Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, MediaStore.Audio.Media.DATA + "=?",new String[]{mp3File.path},
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        // 遍历媒体数据库
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                // 歌曲的总播放时长：MediaStore.Audio.Media.DURATION
                mp3File.duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))/1000;
                // 歌曲文件的大小 ：MediaStore.Audio.Media.SIZE
                mp3File.fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
                // 歌曲文件显示名字
                mp3File.name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                // 歌曲文件专辑
                mp3File.albumName=cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                mp3File.coverImg=loadMP3Cover(mp3File.path);
                cursor.moveToNext();
            }
            cursor.close();
            return true;
        }else{
            return false;
        }
    }

    public static void loadMP3Info(Mp3FileBean mp3File) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(mp3File.path);
        File file=new File(mp3File.path);
        mp3File.duration=Long.valueOf(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))/1000;
        mp3File.albumName=mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        mp3File.name=mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        mp3File.coverImg = mediaMetadataRetriever.getEmbeddedPicture();
        mp3File.fileSize=file.length();
    }

    public static void deleteMusicFile(Context context, String musicPath){
        // 查询媒体数据库
        Cursor cursor = context.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                null, MediaStore.Files.FileColumns.DATA + "=?",new String[]{musicPath},
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        // 遍历普通文件数据库并删除
        if (cursor!=null) {
            context.getContentResolver().delete(MediaStore.Files.getContentUri("external"),
                    MediaStore.Files.FileColumns.DATA + "=?",new String[]{musicPath});
            cursor.close();
        }
    }

}
