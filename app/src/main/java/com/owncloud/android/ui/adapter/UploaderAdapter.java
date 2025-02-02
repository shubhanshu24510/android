/*
 *   ownCloud Android client application
 *
 *   @author Tobias Kaminsky
 *   Copyright (C) 2016 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.nextcloud.client.account.User;
import com.nextcloud.client.preferences.DarkMode;
import com.owncloud.android.R;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.SyncedFolderProvider;
import com.owncloud.android.datamodel.ThumbnailsCacheManager;
import com.owncloud.android.datamodel.ThumbnailsCacheManager.AsyncThumbnailDrawable;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.MimeTypeUtil;
import com.owncloud.android.utils.theme.ViewThemeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploaderAdapter extends SimpleAdapter {

    private final Context mContext;
    private final User user;
    private final FileDataStorageManager mStorageManager;
    private final LayoutInflater inflater;
    private final ViewThemeUtils viewThemeUtils;
    private SyncedFolderProvider syncedFolderProvider;

    public UploaderAdapter(Context context,
                           List<? extends Map<String, ?>> data,
                           int resource,
                           String[] from,
                           int[] to,
                           FileDataStorageManager storageManager,
                           User user,
                           SyncedFolderProvider syncedFolderProvider,
                           ViewThemeUtils viewThemeUtils) {
        super(context, data, resource, from, to);
        this.user = user;
        mStorageManager = storageManager;
        mContext = context;
        this.syncedFolderProvider = syncedFolderProvider;
        inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.viewThemeUtils = viewThemeUtils;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View vi = convertView;
        if (convertView == null) {
            vi = inflater.inflate(R.layout.uploader_list_item_layout, parent, false);
        }

        HashMap<String, OCFile> data = (HashMap<String, OCFile>) getItem(position);
        OCFile file = data.get("dirname");

        TextView filename = vi.findViewById(R.id.filename);
        filename.setText(file.getFileName());

        ImageView fileIcon = vi.findViewById(R.id.thumbnail);
        fileIcon.setTag(file.getFileId());

        TextView lastModV = vi.findViewById(R.id.last_mod);
        lastModV.setText(DisplayUtils.getRelativeTimestamp(mContext, file.getModificationTimestamp()));

        TextView fileSizeV = vi.findViewById(R.id.file_size);
        TextView fileSizeSeparatorV = vi.findViewById(R.id.file_separator);

        if(!file.isFolder()) {
            fileSizeV.setVisibility(View.VISIBLE);
            fileSizeSeparatorV.setVisibility(View.VISIBLE);
            fileSizeV.setText(DisplayUtils.bytesToHumanReadable(file.getFileLength()));
        } else {
            fileSizeV.setVisibility(View.GONE);
            fileSizeSeparatorV.setVisibility(View.GONE);
        }

        if (file.isFolder()) {
            boolean isAutoUploadFolder = SyncedFolderProvider.isAutoUploadFolder(syncedFolderProvider, file, user);
            boolean isDarkModeActive = syncedFolderProvider.getPreferences().isDarkModeEnabled();

            Integer overlayIconId = file.getFileOverlayIconId(isAutoUploadFolder);
            final LayerDrawable icon = MimeTypeUtil.getFileIcon(isDarkModeActive, overlayIconId, mContext, viewThemeUtils);
            fileIcon.setImageDrawable(icon);
        } else {
            // get Thumbnail if file is image
            if (MimeTypeUtil.isImage(file) && file.getRemoteId() != null) {
                // Thumbnail in Cache?
                Bitmap thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(
                        String.valueOf(file.getRemoteId())
                );
                if (thumbnail != null && !file.isUpdateThumbnailNeeded()) {
                    fileIcon.setImageBitmap(thumbnail);
                } else {
                    // generate new Thumbnail
                    if (ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, fileIcon)) {
                        final ThumbnailsCacheManager.ThumbnailGenerationTask task =
                                new ThumbnailsCacheManager.ThumbnailGenerationTask(fileIcon, mStorageManager, user);
                        if (thumbnail == null) {
                            if (MimeTypeUtil.isVideo(file)) {
                                thumbnail = ThumbnailsCacheManager.mDefaultVideo;
                            } else {
                                thumbnail = ThumbnailsCacheManager.mDefaultImg;
                            }
                        }
                        final AsyncThumbnailDrawable asyncDrawable = new AsyncThumbnailDrawable(
                                mContext.getResources(),
                                thumbnail,
                                task
                        );
                        fileIcon.setImageDrawable(asyncDrawable);
                        task.execute(new ThumbnailsCacheManager.ThumbnailGenerationTaskObject(file, file.getRemoteId()));
                    }
                }
            } else {
                final Drawable icon = MimeTypeUtil.getFileTypeIcon(file.getMimeType(),
                                                                   file.getFileName(),
                                                                   mContext,
                                                                   viewThemeUtils);
                fileIcon.setImageDrawable(icon);
            }
        }

        return vi;
    }
}
