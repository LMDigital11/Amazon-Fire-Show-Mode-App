package com.homeframe.showmode;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigActivity extends Activity implements ImageLoader.Callback {

    private static final int PICK_IMAGES = 100;
    private static final int PICK_FOLDER = 101;

    private LinearLayout thumbStrip, folderStrip;
    private TextView photoCount, labelSpeed, labelFreq, labelDur, labelBlank;
    private TextView labelSleepStart, labelSleepEnd, labelInstalledVersion;
    private SeekBar speedBar, freqBar, durBar, blankBar;
    private EditText cityEdit, updateEdit;
    private RadioGroup unitGroup;
    private Switch motionSwitch, sleepSwitch;

    private final List<String> photoUris = new ArrayList<>();
    private final List<String> folderUris = new ArrayList<>();
    private final List<String> combined = new ArrayList<>();
    private int scanGeneration;
    private ImageLoader thumbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        thumbStrip = findViewById(R.id.thumbStrip);
        folderStrip = findViewById(R.id.folderStrip);
        photoCount = findViewById(R.id.photoCount);
        labelSpeed = findViewById(R.id.labelSpeed);
        labelFreq = findViewById(R.id.labelFreq);
        labelDur = findViewById(R.id.labelDur);
        speedBar = findViewById(R.id.speedBar);
        freqBar = findViewById(R.id.freqBar);
        durBar = findViewById(R.id.durBar);
        blankBar = findViewById(R.id.blankBar);
        motionSwitch = findViewById(R.id.motionSwitch);
        sleepSwitch = findViewById(R.id.sleepSwitch);
        labelSleepStart = findViewById(R.id.labelSleepStart);
        labelSleepEnd = findViewById(R.id.labelSleepEnd);
        labelBlank = findViewById(R.id.labelBlank);
        cityEdit = findViewById(R.id.cityEdit);
        updateEdit = findViewById(R.id.updateEdit);
        labelInstalledVersion = findViewById(R.id.labelInstalledVersion);
        unitGroup = findViewById(R.id.unitGroup);

        Button choose = findViewById(R.id.btnChoosePhotos);
        Button chooseFolder = findViewById(R.id.btnChooseFolder);
        Button clear = findViewById(R.id.btnClearPhotos);
        Button done = findViewById(R.id.btnDone);

        thumbs = new ImageLoader(this);

        photoUris.clear();
        photoUris.addAll(AppConfig.getPhotoUris(this));
        folderUris.clear();
        folderUris.addAll(AppConfig.getFolders(this));

        cityEdit.setText(AppConfig.getCity(this));
        updateEdit.setText(AppConfig.getUpdateUrl(this));
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            String name = pi.versionName == null ? "?" : pi.versionName;
            labelInstalledVersion.setText("Installed: v" + name + " (" + pi.versionCode + ")");
        } catch (Exception ignored) {
            labelInstalledVersion.setText("Installed: unknown");
        }
        unitGroup.check(AppConfig.isCelsius(this) ? R.id.unitC : R.id.unitF);

        speedBar.setProgress(AppConfig.getSpeedSeconds(this) - AppConfig.SPEED_MIN);
        freqBar.setProgress(AppConfig.getWeatherFreq(this) - AppConfig.FREQ_MIN);
        durBar.setProgress(AppConfig.getWeatherDuration(this) - AppConfig.DUR_MIN);
        blankBar.setProgress(AppConfig.getBlankMinutes(this) - AppConfig.BLANK_MIN);
        motionSwitch.setChecked(AppConfig.isMotionWake(this));
        sleepSwitch.setChecked(AppConfig.isSleepEnabled(this));
        updateLabels();
        updateSleepLabels();

        cityEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                AppConfig.setCity(ConfigActivity.this, s.toString());
            }
        });

        updateEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                AppConfig.setUpdateUrl(ConfigActivity.this, s.toString());
            }
        });

        Button checkUpdate = findViewById(R.id.btnCheckUpdate);
        checkUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdate();
            }
        });

        unitGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                AppConfig.setCelsius(ConfigActivity.this, checkedId == R.id.unitC);
            }
        });

        speedBar.setOnSeekBarChangeListener(new SimpleSeek(AppConfig.SPEED_MIN) {
            @Override
            void onChanged(int value) {
                AppConfig.setSpeedSeconds(ConfigActivity.this, value);
                updateLabels();
            }
        });

        freqBar.setOnSeekBarChangeListener(new SimpleSeek(AppConfig.FREQ_MIN) {
            @Override
            void onChanged(int value) {
                AppConfig.setWeatherFreq(ConfigActivity.this, value);
                updateLabels();
            }
        });

        durBar.setOnSeekBarChangeListener(new SimpleSeek(AppConfig.DUR_MIN) {
            @Override
            void onChanged(int value) {
                AppConfig.setWeatherDuration(ConfigActivity.this, value);
                updateLabels();
            }
        });

        blankBar.setOnSeekBarChangeListener(new SimpleSeek(AppConfig.BLANK_MIN) {
            @Override
            void onChanged(int value) {
                AppConfig.setBlankMinutes(ConfigActivity.this, value);
                updateLabels();
            }
        });

        motionSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                AppConfig.setMotionWake(ConfigActivity.this, isChecked);
            }
        });

        sleepSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                AppConfig.setSleepEnabled(ConfigActivity.this, isChecked);
            }
        });

        labelSleepStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker(true);
            }
        });

        labelSleepEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker(false);
            }
        });

        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPhotoPicker();
            }
        });

        chooseFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFolderPicker();
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                photoUris.clear();
                folderUris.clear();
                savePhotos();
            }
        });

        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rebuildFolderChips();
        refreshCombined();
    }

    private void startPhotoPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, PICK_IMAGES);
        } catch (Exception e) {
            Toast.makeText(this, "No photo picker available", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(i, PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "No folder picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_FOLDER) {
            if (data.getData() == null) return;
            String tree = data.getData().toString();
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            if (!folderUris.contains(tree)) folderUris.add(tree);
            savePhotos();
            Toast.makeText(this, "Added folder. Looking for photos\u2026",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (requestCode != PICK_IMAGES) return;

        Set<String> picked = new LinkedHashSet<>();
        if (data.getData() != null) {
            picked.add(data.getData().toString());
        } else if (data.getClipData() != null) {
            int n = data.getClipData().getItemCount();
            for (int i = 0; i < n; i++) {
                Uri u = data.getClipData().getItemAt(i).getUri();
                if (u != null) picked.add(u.toString());
            }
        } else {
            return;
        }

        int before = photoUris.size();
        for (String s : picked) {
            Uri u = Uri.parse(s);
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            if (!photoUris.contains(s)) photoUris.add(s);
        }
        if (photoUris.size() == before) {
            Toast.makeText(this, "Selected photos were already in the list",
                    Toast.LENGTH_SHORT).show();
        }
        savePhotos();
    }

    private void savePhotos() {
        AppConfig.setPhotos(this, photoUris);
        AppConfig.setFolders(this, folderUris);
        rebuildFolderChips();
        refreshCombined();
    }

    private void refreshCombined() {
        final int gen = ++scanGeneration;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> result = new ArrayList<>(photoUris);
                for (String f : folderUris) {
                    result.addAll(FolderScanner.collectImages(ConfigActivity.this, f));
                }
                java.util.LinkedHashSet<String> dedup = new java.util.LinkedHashSet<>(result);
                final List<String> list = new ArrayList<>(dedup);
                final int folderCount = folderUris.size();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != scanGeneration) return;
                        combined.clear();
                        combined.addAll(list);
                        rebuildThumbs();
                        rebuildFolderChips();
                        int total = combined.size();
                        String suffix = folderCount > 0
                                ? " photos (incl. " + folderCount + " folder" + (folderCount == 1 ? "" : "s") + ")"
                                : " photo" + (total == 1 ? "" : "s") + " selected";
                        photoCount.setText(total + suffix);
                    }
                });
            }
        }).start();
    }

    private void rebuildFolderChips() {
        folderStrip.removeAllViews();
        if (folderUris.isEmpty()) return;
        for (final String f : folderUris) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            chip.setBackgroundResource(R.drawable.chip_bg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, dp(8), 0);
            chip.setPadding(dp(6), dp(4), dp(6), dp(4));
            chip.setLayoutParams(clp);

            TextView name = new TextView(this);
            name.setText(FolderScanner.folderDisplayName(f));
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(14);
            chip.addView(name);

            TextView count = new TextView(this);
            int n = 0;
            for (String u : combined) {
                if (u.startsWith(f.replace("tree/", "document/") + "/")) n++;
            }
            count.setText((combined.isEmpty() ? " ?" : "  " + n) + "  ");
            count.setTextColor(0x99FFFFFF);
            count.setTextSize(14);
            chip.addView(count);

            TextView remove = new TextView(this);
            remove.setText("\u00d7");
            remove.setTextColor(0xFFFF8A80);
            remove.setTextSize(16);
            remove.setClickable(true);
            remove.setPadding(dp(2), 0, dp(2), 0);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    folderUris.remove(f);
                    savePhotos();
                }
            });
            chip.addView(remove);

            folderStrip.addView(chip);
        }
    }

    private void rebuildThumbs() {
        thumbStrip.removeAllViews();
        photoCount.setText(combined.size() + (combined.size() == 1 ? " photo selected" : " photos selected"));
        int show = Math.min(combined.size(), 24);
        for (int i = 0; i < show; i++) {
            final String key = combined.get(i);
            ImageView iv = new ImageView(this);
            int sz = dp(64);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(dp(2), dp(2), dp(6), dp(2));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF252F3D);
            iv.setTag(key);
            thumbStrip.addView(iv);
            thumbs.load(key, this);
        }
    }

    @Override
    public void onLoaded(String key, Bitmap bmp) {
        if (bmp == null) return;
        for (int i = 0; i < thumbStrip.getChildCount(); i++) {
            View v = thumbStrip.getChildAt(i);
            if (key.equals(v.getTag())) {
                ((ImageView) v).setImageBitmap(bmp);
                break;
            }
        }
    }

    private void updateLabels() {
        labelSpeed.setText("Show each photo for " + AppConfig.getSpeedSeconds(this) + " sec");
        labelFreq.setText("Show weather every " + AppConfig.getWeatherFreq(this) + " photo(s)");
        labelDur.setText("Weather shown for " + AppConfig.getWeatherDuration(this) + " sec");
        int m = AppConfig.getBlankMinutes(this);
        if (m == 1) {
            labelBlank.setText("Blank screen after 1 min");
        } else if (m == 60) {
            labelBlank.setText("Blank screen after 1 hour");
        } else {
            labelBlank.setText("Blank screen after " + m + " min");
        }
    }

    private void showTimePicker(final boolean isStart) {
        int minutes = isStart ? AppConfig.getSleepStart(this) : AppConfig.getSleepEnd(this);
        boolean use24 = android.text.format.DateFormat.is24HourFormat(this);
        TimePickerDialog d = new TimePickerDialog(this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker tp, int hour, int minute) {
                        int mins = hour * 60 + minute;
                        if (isStart) {
                            AppConfig.setSleepStart(ConfigActivity.this, mins);
                        } else {
                            AppConfig.setSleepEnd(ConfigActivity.this, mins);
                        }
                        updateSleepLabels();
                    }
                }, minutes / 60, minutes % 60, use24);
        d.show();
    }

    private void updateSleepLabels() {
        int start = AppConfig.getSleepStart(this);
        int end = AppConfig.getSleepEnd(this);
        labelSleepStart.setText("Start " + timeText(start));
        labelSleepEnd.setText("End " + timeText(end));
    }

    private String timeText(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (android.text.format.DateFormat.is24HourFormat(this)) {
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m);
        }
        String ampm = h >= 12 ? " PM" : " AM";
        int hh = h % 12;
        if (hh == 0) hh = 12;
        return String.format(java.util.Locale.getDefault(), "%d:%02d%s", hh, m, ampm);
    }

    private void checkForUpdate() {
        final String url = AppConfig.getUpdateUrl(this);
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Set an update URL first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = httpGet(url);
                    final JSONObject o = new JSONObject(json);
                    final int remoteCode = o.optInt("versionCode", 0);
                    final String remoteName = o.optString("versionName", "");
                    final String apkUrl = o.optString("apkUrl", "");
                    android.content.pm.PackageInfo pi =
                            getPackageManager().getPackageInfo(getPackageName(), 0);
                    final int localCode = pi.versionCode;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (remoteCode > localCode && !apkUrl.isEmpty()) {
                                Toast.makeText(ConfigActivity.this,
                                        "New version " + remoteName + " found, downloading...",
                                        Toast.LENGTH_SHORT).show();
                                startDownload(apkUrl);
                            } else if (remoteCode <= localCode) {
                                Toast.makeText(ConfigActivity.this,
                                        "Already up to date", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ConfigActivity.this,
                                        "Update URL missing apkUrl", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ConfigActivity.this,
                                    "Update check failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void startDownload(String apkUrl) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
        req.setTitle("Show Mode update");
        req.setDescription("Downloading new version");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                "ShowMode-update.apk");
        final long id = dm.enqueue(req);
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                long done = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (done != id) return;
                Uri uri = dm.getUriForDownloadedFile(id);
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(uri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(install);
                } catch (Exception ignored) {
                }
                try {
                    unregisterReceiver(this);
                } catch (Exception ignored) {
                }
            }
        };
        registerReceiver(br, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private String httpGet(String urlStr) throws Exception {
        java.net.HttpURLConnection c =
                (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "ShowMode/1.0");
        java.io.InputStream in = c.getInputStream();
        java.io.BufferedReader r =
                new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final int min;

        SimpleSeek(int min) {
            this.min = min;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) onChanged(progress + min);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        abstract void onChanged(int value);
    }
}