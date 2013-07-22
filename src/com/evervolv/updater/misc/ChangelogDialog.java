/*
 * Copyright (C) 2013 The Evervolv Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evervolv.updater.misc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.evervolv.updater.R;
import com.evervolv.updater.db.ManifestEntry;

public class ChangelogDialog extends DialogFragment {

    private final ManifestEntry mEntry;
    private Activity mActivity;

    public ChangelogDialog(ManifestEntry entry, Activity activity) {
        mEntry = entry;
        mActivity = activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Resources res = getResources();
        LayoutInflater inflater = mActivity.getLayoutInflater();

        View v = inflater.inflate(R.layout.update_changelog, null, false);
        WebView wv = (WebView) v.findViewById(R.id.changelog_webview);
        wv.getSettings().setTextZoom(res.getInteger(R.integer.updates_webview_text_zoom));

        if (mEntry.getType().equals(Constants.BUILD_TYPE_NIGHTLIES)) {
            String url = Constants.FETCH_URL + "changelog-" + mEntry.getDate() + ".html";
            wv.loadUrl(url);
            /* Hold on redirect */
            wv.setWebViewClient(new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String url){
                    view.loadUrl(url);
                    return false;
                }
            });
        } else {
            String body = "<html><body><h3>" + mEntry.getDate()
                    + "</h3><p>" + mEntry.getMessage() + "</p></body></html>";
            wv.loadData(body,"text/html",null);
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(mActivity);
        dialog.setTitle(R.string.changelog_info_dialog_title);
        dialog.setView(v);
        dialog.setPositiveButton(R.string.okay,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    dismiss();
                }
        });
        return dialog.create();

    }

}
