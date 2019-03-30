package com.opentouchgaming.saffal;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class UtilsSAF
{
    static String TAG = "UtilsSAF";

    static Context context;

    static String mimeType = "plain";

    /*
        TODO: Only one tree root possible at the moment, this should be extented to a list so we can have
        multiple roots E.G internal phone flash, and SD Card
    */
    static TreeRoot treeRoot;

    /*
        Holds the URI returned from ACTION_OPEN_DOCUMENT_TREE (important!)
        Also the File system 'root' this should point to E.G could be '/storage/emulated/0' for internal files
     */
    public static class TreeRoot
    {
        Uri uri;
        String rootPath;
        public TreeRoot(Uri uri, String rootPath)
        {
            this.uri = uri;
            this.rootPath = rootPath;
        }
    }

    /**
     * Set a Context so all operations don't need to pass in a new one
     *
     * @param ctx the Context.
     */
    public static void setContext(@NonNull Context ctx)
    {
        context = ctx;
    }

    /**
     * Set a new URI and root path for the URI
     *
     * @param treeRoot the uri and root path.
     */
    public static void setTreeRoot(@NonNull TreeRoot treeRoot)
    {
        UtilsSAF.treeRoot = treeRoot;
    }

    /**
     * Get ContentResolver
     *
     * @return ContentResolver
     */
    public static ContentResolver getContentResolver()
    {
        return context.getContentResolver();
    }

    /**
     * Launch the Select Document screen. You should give some pictures about how to select the internal storage
     *
     * @param activity Your Activity
     * @param code Code return on onActivityResult
     */
    public static void openDocumentTree(@NonNull Activity activity, int code)
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        activity.startActivityForResult(intent, code);
    }

    /**
     * Save the currently set URI and root path so loadTreeRoot can work
     *
     * @param ctx A Context
     */
    public static void saveTreeRoot(Context ctx)
    {
        if( treeRoot != null && treeRoot.uri != null && treeRoot.rootPath != null )
        {
            SharedPreferences prefs = ctx.getSharedPreferences("utilsSAF", 0);
            SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.putString("uri", treeRoot.uri.toString());
            prefsEdit.putString("rootPath", treeRoot.rootPath.toString());
            prefsEdit.commit();
        }
    }

    /**
     * Load the last save URI and root
     *
     * @param ctx A Context
     */
    public static boolean loadTreeRoot(Context ctx)
    {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("utilsSAF", 0);

            String url = prefs.getString("uri", null);
            if( url != null )
            {
                Uri treeUri = null;
                treeUri = Uri.parse(url);
                String rootPath = prefs.getString("rootPath", null);
                if(rootPath != null)
                {
                    treeRoot = new TreeRoot(treeUri, rootPath);
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Returns true when SAF files are ready to be accessed. Note does not tell you the correct URI was chosen by the user.
     *
     * @return True if ready
     */
    public static boolean ready()
    {
        if( treeRoot != null && treeRoot.uri != null && treeRoot.rootPath != null && context != null )
            return true;
        else
            return false;
    }

    /**
     * Returns true if the path is in the SAF controlled space
     *
     * @return True if in SAF space
     */
    public static boolean isInSAFRoot(String path)
    {
        return path.startsWith(treeRoot.rootPath);
    }

    static InputStream getInputStream(DocumentFile docFile) throws FileNotFoundException
    {
        return context.getContentResolver().openInputStream(docFile.getUri());
    }

    static ParcelFileDescriptor getParcelDescriptor(DocumentFile docFile,boolean write) throws IOException
    {
        DBG("getFd read = " + docFile.canRead() + " write = " + docFile.canWrite() + " name = " + docFile.getName());

        ParcelFileDescriptor filePfd = context.getContentResolver().openFileDescriptor(docFile.getUri(), write ? "rw":"r");

        return filePfd;
    }

    static DocumentFile createFile(@NonNull final String filePath)
    {
        // First check if it already exists
        DocumentFile checkDoc = UtilsSAF.getDocumentFile(filePath);
        if (checkDoc != null)
            return checkDoc;

        // Just used to parse path
        File file = new File(filePath);
        String parent = file.getParent();
        String newFile = file.getName();

        DBG("createFile: parent = " + parent + ", newDir = " + newFile);

        DocumentFile parentDoc = UtilsSAF.getDocumentFile(parent);
        DocumentFile newDirDoc = null;

        if (parentDoc != null)
        {
            newDirDoc = parentDoc.createFile(mimeType, newFile);
        } else
        {
            DBG("createFile: could not find parent path");
        }
        return newDirDoc;
    }

    static DocumentFile createPath(@NonNull final String filePath)
    {
        // First check if it already exists
        DocumentFile checkDoc = UtilsSAF.getDocumentFile(filePath);
        if (checkDoc != null)
            return checkDoc;

        // Just used to parse path
        File file = new File(filePath);
        String parent = file.getParent();
        String newDir = file.getName();

        DBG("createPath: parent = " + parent + ", newDir = " + newDir);

        DocumentFile parentDoc = UtilsSAF.getDocumentFile(parent);
        DocumentFile newDirDoc = null;

        if (parentDoc != null)
        {
            newDirDoc = parentDoc.createDirectory(newDir);
        } else
        {
            DBG("createPath: could not find parent path");
        }
        return newDirDoc;
    }

    static DocumentFile createPaths(@NonNull final String filePath)
    {
        DocumentFile document = DocumentFile.fromTreeUri(context, treeRoot.uri);

        String[] parts = getParts(filePath);

        for (int i = 0; i < parts.length; i++)
        {
            DocumentFile nextDocument = document.findFile(parts[i]);
            if (nextDocument == null) // Not found, try to create new folder
            {
                nextDocument = document.createDirectory(parts[i]);
                if (nextDocument == null) // Did not create for some reason..
                {
                    return null;
                }
            } else // Dir OR file exists, check it is a directory, otherwise error
            {
                if (!nextDocument.isDirectory())
                {
                    return null;
                }
            }
            document = nextDocument;
        }

        return document;
    }

    static DocumentFile getDocumentFile(@NonNull final String filePath)
    {
        /* THIS SEEMS TO WORK BUT NEED TO FIND OUT IF USABLE ACROSS DEVICES
        // content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3AOpenTouch%2FDelta%2Fhexdd.wad
        String relativePath = filePath.substring(treeRoot.rootPath.length() + 1);
        relativePath = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3A" + relativePath.replace("/", "%2F");
        DBG("uri = " + relativePath);
        Uri uri = Uri.parse(relativePath);

        DocumentFile ret = DocumentFile.fromSingleUri(context, uri);

        if (ret.exists())
        {
            return ret;
        } else
        {
            return null;
        }
        */

        // start with root of SD card and then parse through document tree.
        DocumentFile document = DocumentFile.fromTreeUri(context, treeRoot.uri);

        String[] parts = getParts( filePath );

        for (int i = 0; i < parts.length; i++)
        {
            DBG("getDocumentFile: part[" + i + "] = " + parts[i]);

            DocumentFile nextDocument = document.findFile(parts[i]);
            if (nextDocument == null)
            {
                return null;
            }
            document = nextDocument;
        }

        return document;
    }

    private static String[] getParts(String fullPath)
    {
        if (!fullPath.startsWith(treeRoot.rootPath))
        {
            DBG("getParts: ERROR, filePath (" + fullPath + ") must start with the rootPath (" + treeRoot.rootPath + ")");
            return null;
        }

        String[] parts;

        if (fullPath.length() > treeRoot.rootPath.length())
        {
            String relativePath = fullPath.substring(treeRoot.rootPath.length() + 1);
            parts = relativePath.split("\\/", -1);
        } else // When at the root return an array of 0, 'split' will return and array of 1 with and empty string
        {
            parts = new String[0];
        }

        return parts;
    }

    private static void DBG(String str)
    {
        Log.d(TAG, str);
    }
}