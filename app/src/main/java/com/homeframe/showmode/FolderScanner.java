package com.homeframe.showmode;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.List;

public final class FolderScanner {

    private static final int DEFAULT_MAX_IMAGES = 3000;
    private static final int DEFAULT_MAX_DEPTH = 8;

    public static List<String> collectImages(Context ctx, String treeUri, int maxImages, int maxDepth) {
        List<String> out = new ArrayList<>();
        try {
            Uri root = Uri.parse(treeUri);
            String treeDocId = DocumentsContract.getTreeDocumentId(root);
            scan(ctx.getContentResolver(), root, treeDocId, out, maxImages, maxDepth, 0);
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<String> collectImages(Context ctx, String treeUri) {
        return collectImages(ctx, treeUri, DEFAULT_MAX_IMAGES, DEFAULT_MAX_DEPTH);
    }

    private static void scan(ContentResolver cr, Uri tree, String docId,
                             List<String> out, int maxImages, int maxDepth, int depth) {
        if (depth > maxDepth || out.size() >= maxImages) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = null;
        try {
            c = cr.query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE},
                    null, null, null);
            if (c == null) return;
            while (c.moveToNext()) {
                if (out.size() >= maxImages) break;
                String childId = c.getString(0);
                String mime = c.getString(1);
                if (mime == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scan(cr, tree, childId, out, maxImages, maxDepth, depth + 1);
                } else if (mime.startsWith("image/")) {
                    out.add(DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString());
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    public static String folderDisplayName(String treeUri) {
        try {
            Uri uri = Uri.parse(treeUri);
            String id = DocumentsContract.getTreeDocumentId(uri);
            String decoded = android.net.Uri.decode(id);
            int idx = decoded.lastIndexOf('/');
            if (idx >= 0 && idx < decoded.length() - 1) return decoded.substring(idx + 1);
            return decoded;
        } catch (Exception e) {
            return "Folder";
        }
    }

    private FolderScanner() {
    }
}