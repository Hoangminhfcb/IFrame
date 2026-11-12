package nuts.deez.photoframeslideshow;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.AdapterView;
import androidx.appcompat.app.AppCompatActivity;

/** Dedicated, extensible settings screen. Each choice is persisted as soon as it changes. */
public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private int fallbackInterval = 60;
    private TextView memory;
    private final StatusBarGuard statusBarGuard = new StatusBarGuard();
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String activeLanguage;
    @Override protected void attachBaseContext(android.content.Context base) {
        activeLanguage = AppLanguage.code(base);
        super.attachBaseContext(AppLanguage.wrap(base));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = FramePreferences.get(this);
        fallbackInterval = getIntent().getIntExtra("imageTime", 60);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(0xff10151c);
        getWindow().setNavigationBarColor(0xff10151c);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xff10151c);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(32));
        scroll.addView(content);
        setContentView(scroll);
        Button back = new Button(this);
        back.setText(this.getString(R.string.text_back_to_frame)); back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        content.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        text(content, this.getString(R.string.text_frame_settings), 30, 0xfff3f6fa, true);
        text(content, this.getString(R.string.text_make_the_frame_your_own_changes_save_automatically), 15, 0xffaab9c9, false);

        LinearLayout language = card(getString(R.string.language_title), getString(R.string.language_hint));
        stringChoice(language, "English / Tiếng Việt", "language", new String[]{"en", "vi"},
                new String[]{"English", "Tiếng Việt"}, "en");

        LinearLayout library = card(this.getString(R.string.text_photos_videos), this.getString(R.string.text_read_public_media_from_internal_storage_and_memory_cards));
        stringChoice(library, this.getString(R.string.text_slideshow_source), "mediaSource", new String[]{"all", "pictures"},
                new String[]{this.getString(R.string.text_entire_device_library), this.getString(R.string.text_pictures_folder_including_subfolders)}, "all");
        toggle(library, this.getString(R.string.text_include_videos), "includeVideos", true);
        toggle(library, this.getString(R.string.text_enable_video_sound), "videoSound", false);
        text(library, this.getString(R.string.text_videos_play_on_their_own_page_and_advance_when_finished_gifs_show), 14, 0xffaab9c9, false);
        Button scan = new Button(this); scan.setText(this.getString(R.string.text_rescan_library)); scan.setAllCaps(false);
        scan.setOnClickListener(v -> {
            prefs.edit().putLong("mediaRevision", System.currentTimeMillis()).apply();
            scan.setText(this.getString(R.string.text_library_will_rescan_when_you_return_to_the_frame));
        });
        library.addView(scan);
        Button permissions = new Button(this); permissions.setText(this.getString(R.string.text_photo_video_permissions)); permissions.setAllCaps(false);
        permissions.setOnClickListener(v -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName()))));
        library.addView(permissions);

        LinearLayout playback = card(this.getString(R.string.text_slideshow), this.getString(R.string.text_timing_and_photo_arrangement));
        choice(playback, this.getString(R.string.text_time_per_page), "imageTime", new int[]{3,5,10,15,20,30,45,60,90,120,180,300,600},
                new String[]{this.getString(R.string.text_3_seconds),this.getString(R.string.text_5_seconds),this.getString(R.string.text_10_seconds),this.getString(R.string.text_15_seconds),this.getString(R.string.text_20_seconds),this.getString(R.string.text_30_seconds),this.getString(R.string.text_45_seconds),this.getString(R.string.text_1_minute),this.getString(R.string.text_90_seconds),this.getString(R.string.text_2_minutes),this.getString(R.string.text_3_minutes),this.getString(R.string.text_5_minutes),this.getString(R.string.text_10_minutes)}, fallbackInterval);
        choice(playback, this.getString(R.string.text_photos_per_page), "photoCount", new int[]{1,2,3,4,0},
                new String[]{this.getString(R.string.text_1_photo_full_frame),this.getString(R.string.text_2_photos_side_by_side),this.getString(R.string.text_3_photos_one_large_two_small),this.getString(R.string.text_4_photos_2_2_grid),this.getString(R.string.text_random_60_single_photo_preference)}, 1);
        toggle(playback, this.getString(R.string.text_shuffle_photos), "shuffle", getIntent().getBooleanExtra("shuffle", true));
        stringChoice(playback, this.getString(R.string.text_transition_effect), "effect", new String[]{"none","fade","slide","zoom","random"},
                new String[]{this.getString(R.string.text_none),this.getString(R.string.text_soft_fade),this.getString(R.string.text_horizontal_slide),this.getString(R.string.text_subtle_zoom),this.getString(R.string.text_random)}, "fade");
        choice(playback, this.getString(R.string.text_transition_duration), "effectDuration", new int[]{300,600,1000,1500},
                new String[]{this.getString(R.string.text_0_3_seconds_fast),this.getString(R.string.text_0_6_seconds_smooth),this.getString(R.string.text_1_second_relaxed),this.getString(R.string.text_1_5_seconds_slow)}, 600);

        LinearLayout display = card(this.getString(R.string.text_display), this.getString(R.string.text_fit_the_whole_photo_or_fill_each_cell));
        toggle(display, this.getString(R.string.text_fill_photo_cells_crop_edges), "crop", false);
        toggle(display, this.getString(R.string.text_blurred_photo_background), "blur", true);
        toggle(display, this.getString(R.string.text_show_date_and_time), "showClock", true);
        toggle(display, this.getString(R.string.text_show_photo_information), "showInfo", true);
        toggle(display, this.getString(R.string.text_show_slideshow_progress), "showProgress", true);

        LinearLayout kiosk = card(this.getString(R.string.text_frame_mode), this.getString(R.string.text_prevent_accidental_notification_gestures_while_using_the_app));
        Switch notificationGuard = toggle(kiosk, this.getString(R.string.text_block_notification_pull_down), "blockNotificationShade", true);
        notificationGuard.setEnabled(StatusBarGuard.supported());
        notificationGuard.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("blockNotificationShade", enabled).apply();
            statusBarGuard.start(this);
        });
        text(kiosk, StatusBarGuard.supported()
                ? this.getString(R.string.text_uses_overlay_permission_to_block_the_top_edge_the_overlay_is_remo)
                : this.getString(R.string.text_android_8_and_later_require_device_kiosk_provisioning_to_fully_lo), 14, 0xffaab9c9, false);
        if (StatusBarGuard.supported()) {
            Button overlay = new Button(this); overlay.setText(this.getString(R.string.text_notification_blocking_permission)); overlay.setAllCaps(false);
            overlay.setOnClickListener(v -> StatusBarGuard.openPermission(this)); kiosk.addView(overlay);
        }

        LinearLayout schedule = card(this.getString(R.string.text_operating_hours), this.getString(R.string.text_runs_all_day_by_default_sleeps_when_the_room_is_dark));
        toggle(schedule, this.getString(R.string.text_limit_operating_hours), "useSchedule", false);
        int[] hours = new int[24]; String[] hourLabels = new String[24];
        for (int i = 0; i < 24; i++) { hours[i] = i; hourLabels[i] = String.format(java.util.Locale.getDefault(), "%02d:00", i); }
        choice(schedule, this.getString(R.string.text_start), "scheduleStart", hours, hourLabels, getIntent().getIntExtra("scheduleStart", 7));
        choice(schedule, this.getString(R.string.text_end), "scheduleEnd", hours, hourLabels, getIntent().getIntExtra("scheduleEnd", 23));
        text(schedule, this.getString(R.string.text_turn_off_the_limit_for_all_day_playback_equal_start_and_end_hours), 14, 0xffaab9c9, false);

        LinearLayout night = card(this.getString(R.string.text_night_clock), this.getString(R.string.text_fullscreen_clock_and_monthly_calendar_in_a_dark_room));
        SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        boolean hasLight = manager != null && manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
        Switch sleep = toggle(night, this.getString(R.string.text_show_calendar_automatically_in_darkness), "autoDark", true);
        sleep.setEnabled(hasLight);
        if (!hasLight) text(night, this.getString(R.string.text_this_device_has_no_light_sensor), 14, 0xffdfbb86, false);
        choice(night, this.getString(R.string.text_darkness_threshold), "darkLux", new int[]{1,2,5,10,20},
                new String[]{"1 lux","2 lux","5 lux","10 lux","20 lux"}, 2);
        choice(night, this.getString(R.string.text_light_stabilization_delay), "lightDelay", new int[]{3,5,10,30},
                new String[]{this.getString(R.string.text_3_seconds_73),this.getString(R.string.text_5_seconds_74),this.getString(R.string.text_10_seconds_75),this.getString(R.string.text_30_seconds_76)}, 5);
        choice(night, this.getString(R.string.text_night_clock_brightness), "nightBrightness", new int[]{1,3,5,10,20},
                new String[]{this.getString(R.string.text_1_very_dim), this.getString(R.string.text_3_gentle), "5%", "10%", "20%"}, 3);
        text(night, this.getString(R.string.text_photos_and_videos_pause_the_clock_and_calendar_update_once_per_mi), 14, 0xffaab9c9, false);

        LinearLayout performance = card(this.getString(R.string.text_performance_memory), this.getString(R.string.text_preload_pages_for_smoother_transitions));
        choice(performance, this.getString(R.string.text_photo_cache_budget), "cachePercent", new int[]{35,55,70},
                new String[]{this.getString(R.string.text_balanced_35_of_heap),this.getString(R.string.text_high_performance_55_of_heap),this.getString(R.string.text_maximum_70_of_heap)}, 70);
        text(performance, this.getString(R.string.text_maximum_mode_caches_decoded_photos_and_collages_memory_is_reserve), 14, 0xffaab9c9, false);
        memory = text(performance, PhotoLoader.memorySummary(this), 14, 0xff77d7c9, false);
        Button clear = new Button(this); clear.setText(this.getString(R.string.text_clear_photo_cache)); clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            prefs.edit().putLong("cacheRevision", System.currentTimeMillis()).apply();
            memory.setText(this.getString(R.string.text_the_cache_will_be_cleared_when_you_return_to_the_frame));
        });
        performance.addView(clear);
        text(content, this.getString(R.string.text_tap_left_right_or_swipe_to_change_pages_hold_to_pause_play_double), 14, 0xffaab9c9, false);
    }
    @Override protected void onResume() { super.onResume(); statusBarGuard.start(this); }
    @Override protected void onPause() { statusBarGuard.stop(); super.onPause(); }
    @Override protected void onDestroy() { statusBarGuard.stop(); super.onDestroy(); }
    private LinearLayout card(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xff1b242f); background.setCornerRadius(dp(16));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(20);
        content.addView(box, params);
        text(box, title, 21, 0xfff3f6fa, true);
        text(box, subtitle, 14, 0xffaab9c9, false);
        return box;
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(6), 0, dp(10)); parent.addView(view); return view;
    }
    private Switch toggle(LinearLayout parent, String label, String key, boolean fallback) {
        Switch view = new Switch(this); view.setText(label); view.setTextColor(0xffe2eaf2); view.setTextSize(16);
        view.setTag(key); view.setPadding(0, dp(14), 0, dp(14));
        view.setChecked(prefs.getBoolean(key, fallback));
        view.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Spinner spinner(LinearLayout parent, String label, String key, String[] labels, int selected) {
        text(parent, label, 15, 0xffc4d2e0, false);
        Spinner spinner = new Spinner(this); spinner.setTag(key);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            private View style(View view) {
                ((TextView) view).setTextColor(0xfff3f6fa); ((TextView) view).setTextSize(16);
                view.setPadding(dp(12), dp(14), dp(12), dp(14));
                view.setBackgroundColor(0xff243241); return view;
            }
            @Override public View getView(int position, View reuse, ViewGroup parent) { return style(super.getView(position, reuse, parent)); }
            @Override public View getDropDownView(int position, View reuse, ViewGroup parent) { return style(super.getDropDownView(position, reuse, parent)); }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12);
        parent.addView(spinner, params); return spinner;
    }
    private void choice(LinearLayout parent, String label, String key, int[] values, String[] labels, int fallback) {
        int value = prefs.getInt(key, fallback), selected = 0;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - value) < Math.abs(values[selected] - value)) selected = i;
        Spinner view = spinner(parent, label, key, labels, selected);
        view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View row, int position, long id) {
                prefs.edit().putInt(key, values[position]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }
    private void stringChoice(LinearLayout parent, String label, String key, String[] values, String[] labels, String fallback) {
        String value = prefs.getString(key, fallback); int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) selected = i;
        Spinner view = spinner(parent, label, key, labels, selected);
        view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View row, int position, long id) { String next = values[position];
                boolean changed = !next.equals(prefs.getString(key, fallback));
                prefs.edit().putString(key, next).apply();
                if (changed && "language".equals(key)) recreate();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }
}
