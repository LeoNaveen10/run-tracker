package com.runtracker.app;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, non-exported provider: access is granted to the user-selected sharing app. */
public class ShareProvider extends ContentProvider {
 @Override public boolean onCreate() { return true; }
 private File file(Uri uri) throws FileNotFoundException {
  String name=uri.getLastPathSegment();
  if(!"com.runtracker.app.share".equals(uri.getAuthority()) || uri.getPathSegments().size()!=1 || name==null || !name.matches("run-[a-f0-9-]+\\.png")) throw new FileNotFoundException("Invalid share image");
  File file=new File(new File(getContext().getCacheDir(),"shares"),name);
  if(!file.isFile()) throw new FileNotFoundException("Share image is unavailable");return file;
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
  if(!"r".equals(mode)) throw new FileNotFoundException("Read-only image");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public String getType(Uri uri) { return "image/png"; }
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String order) {
  try {
   File image=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
   MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
   for(int i=0;i<columns.length;i++) row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?image.getName():OpenableColumns.SIZE.equals(columns[i])?image.length():null;
   cursor.addRow(row);return cursor;
  } catch(FileNotFoundException error) { return null; }
 }
 @Override public Uri insert(Uri u,ContentValues values) { throw new UnsupportedOperationException("Read only"); }
 @Override public int update(Uri u,ContentValues values,String s,String[] a) { throw new UnsupportedOperationException("Read only"); }
 @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException("Read only"); }
}
