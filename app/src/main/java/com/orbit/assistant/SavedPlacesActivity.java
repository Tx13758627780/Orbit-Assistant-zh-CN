package com.orbit.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Manager for reusable named locations used by Routine location automation. */
public final class SavedPlacesActivity extends Activity {
    private static final int REQ_LOCATION = 913;
    private LinearLayout placeList;
    private boolean openAddAfterPermission;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiKit.syncTheme(this);
        Window w = getWindow();
        w.setStatusBarColor(UiKit.BG);
        w.setNavigationBarColor(UiKit.BG);
        View content = buildContent();
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        navigation = OrbitPredictiveBack.install(this);
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        refresh();
    }

    @Override protected void onPause() {
        UiPresence.leave(this);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION && openAddAfterPermission && RoutineLocationTriggerScheduler.hasFineLocation(this)) {
            openAddAfterPermission = false;
            showPlaceDialog(null, true);
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);
        scroll.setForceDarkAllowed(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 20);
        page.setPadding(p, UiKit.dp(this, 26), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_back, OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setOnClickListener(v -> navigation.performBack());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48));
        backLp.rightMargin = UiKit.dp(this, 12);
        header.addView(back, backLp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_saved_places_594844, "Saved places"), 26, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_reusable_routine_locations_556511, "Reusable Routine locations"), 12, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_save_places_such_as_home_work_or_gym_once_02ca0c, "Save places such as Home, Work, or Gym once, then choose them while creating location triggers or location-based IF conditions. Saved coordinates stay local to Orbit."),
                14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 14));
        page.addView(intro, introLp);

        Button addCurrent = primaryButton(OrbitText.get(R.string.ui_add_my_current_location_66f728, "Add my current location"));
        addCurrent.setOnClickListener(v -> addFromCurrentLocation());
        page.addView(addCurrent, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 50)));

        Button addManual = secondaryButton(OrbitText.get(R.string.ui_add_place_manually_0c4424, "Add place manually"));
        addManual.setOnClickListener(v -> showPlaceDialog(null, false));
        LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48));
        manualLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        page.addView(addManual, manualLp);

        TextView section = UiKit.text(this, OrbitText.get(R.string.ui_saved_places_8dc8cf, "SAVED PLACES"), 11, UiKit.MUTED, true);
        section.setLetterSpacing(.13f);
        section.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 22), 0, UiKit.dp(this, 8));
        page.addView(section);

        placeList = new LinearLayout(this);
        placeList.setOrientation(LinearLayout.VERTICAL);
        page.addView(placeList);
        return scroll;
    }

    private void refresh() {
        if (placeList == null) return;
        placeList.removeAllViews();
        List<SavedPlaceStore.Place> places = SavedPlaceStore.list(this);
        if (places.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, OrbitText.get(R.string.ui_no_saved_places_yet_f426c6, "No saved places yet"), 16, UiKit.TEXT, true));
            TextView note = UiKit.text(this,
                    OrbitText.get(R.string.ui_add_your_current_location_or_enter_coordin_7f5271, "Add your current location or enter coordinates for a place you want to reuse in Routines."),
                    13, UiKit.MUTED, false);
            note.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(note);
            placeList.addView(empty, cardLp());
            return;
        }
        for (SavedPlaceStore.Place place : places) {
            LinearLayout c = card();
            c.addView(UiKit.text(this, place.name, 16, UiKit.TEXT, true));
            TextView coords = UiKit.text(this,
                    coordinate(place.latitude) + ", " + coordinate(place.longitude), 12, UiKit.MUTED, false);
            coords.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 10));
            c.addView(coords);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = secondaryButton(OrbitText.get(R.string.ui_edit_464c4f, "Edit"));
            edit.setOnClickListener(v -> showPlaceDialog(place, false));
            actions.addView(edit, new LinearLayout.LayoutParams(0, UiKit.dp(this, 42), 1));
            Button delete = secondaryButton(OrbitText.get(R.string.ui_delete_e2d0a5, "Delete"));
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 42), 1);
            deleteLp.leftMargin = UiKit.dp(this, 8);
            actions.addView(delete, deleteLp);
            delete.setOnClickListener(v -> confirmDelete(place));
            c.addView(actions);
            placeList.addView(c, cardLp());
        }
    }

    private void addFromCurrentLocation() {
        if (!RoutineLocationTriggerScheduler.hasFineLocation(this)) {
            openAddAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (!RoutineLocationTriggerScheduler.isLocationEnabled(this)) {
            Toast.makeText(this, OrbitText.get(R.string.ui_turn_on_android_location_first_747416, "Turn on Android location first."), Toast.LENGTH_SHORT).show();
            RoutineLocationTriggerScheduler.openLocationServices(this);
            return;
        }
        showPlaceDialog(null, true);
    }

    private void showPlaceDialog(SavedPlaceStore.Place old, boolean fillCurrent) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 5), UiKit.dp(this, 20), 0);

        TextView nameLabel = label(OrbitText.get(R.string.ui_place_name_eed256, "Place name"));
        form.addView(nameLabel);
        EditText name = field(OrbitText.get(R.string.ui_home_work_gym_31f287, "Home, Work, Gym…"), old == null ? "" : old.name, false);
        form.addView(name, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 52)));

        form.addView(label(OrbitText.get(R.string.ui_latitude_238a67, "Latitude")));
        EditText lat = field("0.000000", old == null ? "" : coordinate(old.latitude), true);
        form.addView(lat, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 52)));
        form.addView(label(OrbitText.get(R.string.ui_longitude_6d8045, "Longitude")));
        EditText lon = field("0.000000", old == null ? "" : coordinate(old.longitude), true);
        form.addView(lon, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 52)));

        Button useCurrent = secondaryButton(OrbitText.get(R.string.ui_use_my_current_location_dc9080, "Use my current location"));
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 44));
        currentLp.setMargins(0, UiKit.dp(this, 10), 0, 0);
        form.addView(useCurrent, currentLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(old == null ? OrbitText.get(R.string.ui_add_saved_place_01e967, "Add saved place") : OrbitText.get(R.string.ui_edit_saved_place_9e0b92, "Edit saved place"))
                .setView(form)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(old == null ? OrbitText.get(R.string.ui_add_9fd728, "Add") : OrbitText.get(R.string.ui_save_1509f5, "Save"), null)
                .create();
        UiKit.styleOrbitDialog(dialog, this, false,
                UiKit.outlined(UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 55), 22, this),
                .66f, () -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String placeName = SavedPlaceStore.sanitizeName(name.getText().toString());
                Double latitude = parseDouble(lat.getText().toString());
                Double longitude = parseDouble(lon.getText().toString());
                if (placeName.isEmpty()) {
                    Toast.makeText(this, OrbitText.get(R.string.ui_give_this_place_a_name_eab332, "Give this place a name."), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (latitude == null || longitude == null || !SavedPlaceStore.validCoordinates(latitude, longitude)) {
                    Toast.makeText(this, OrbitText.get(R.string.ui_use_valid_latitude_and_longitude_coordinat_cc832c, "Use valid latitude and longitude coordinates."), Toast.LENGTH_LONG).show();
                    return;
                }
                SavedPlaceStore.Place place = old == null
                        ? SavedPlaceStore.create(placeName, latitude, longitude)
                        : new SavedPlaceStore.Place(old.id, placeName, latitude, longitude, old.createdAt);
                if (!SavedPlaceStore.upsert(this, place)) {
                    Toast.makeText(this, OrbitText.get(R.string.ui_a_saved_place_with_that_name_already_exist_b00681, "A saved place with that name already exists."), Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                refresh();
            });
            if (fillCurrent) {
                form.postDelayed(() -> fillCurrentLocation(lat, lon, useCurrent), 125L);
            }
        });
        useCurrent.setOnClickListener(v -> fillCurrentLocation(lat, lon, useCurrent));
        dialog.show();
    }

    private void fillCurrentLocation(EditText lat, EditText lon, Button button) {
        if (!RoutineLocationTriggerScheduler.hasFineLocation(this)) {
            Toast.makeText(this, OrbitText.get(R.string.ui_allow_precise_location_first_eb5a2a, "Allow precise location first."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!RoutineLocationTriggerScheduler.isLocationEnabled(this)) {
            Toast.makeText(this, OrbitText.get(R.string.ui_turn_on_android_location_first_747416, "Turn on Android location first."), Toast.LENGTH_SHORT).show();
            RoutineLocationTriggerScheduler.openLocationServices(this);
            return;
        }
        Location cached = RoutineLocationTriggerScheduler.bestLastKnownLocation(this);
        if (cached != null && System.currentTimeMillis() - cached.getTime() <= 120_000L) {
            lat.setText(coordinate(cached.getLatitude()));
            lon.setText(coordinate(cached.getLongitude()));
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        String provider = null;
        try { if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER; } catch (Exception ignored) {}
        try { if (provider == null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER; } catch (Exception ignored) {}
        if (provider == null) {
            if (cached != null) {
                lat.setText(coordinate(cached.getLatitude()));
                lon.setText(coordinate(cached.getLongitude()));
            } else Toast.makeText(this, OrbitText.get(R.string.ui_could_not_get_a_current_location_fix_6dca07, "Could not get a current location fix."), Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        final Location fallback = cached;
        final String chosen = provider;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(chosen, null, getMainExecutor(), location -> runOnUiThread(() -> {
                    button.setEnabled(true);
                    Location result = location != null ? location : fallback;
                    if (result == null) Toast.makeText(this, OrbitText.get(R.string.ui_could_not_get_a_current_location_fix_6dca07, "Could not get a current location fix."), Toast.LENGTH_SHORT).show();
                    else {
                        lat.setText(coordinate(result.getLatitude()));
                        lon.setText(coordinate(result.getLongitude()));
                    }
                }));
            } else {
                lm.requestSingleUpdate(chosen, new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        runOnUiThread(() -> {
                            button.setEnabled(true);
                            Location result = location != null ? location : fallback;
                            if (result == null) Toast.makeText(SavedPlacesActivity.this, OrbitText.get(R.string.ui_could_not_get_a_current_location_fix_6dca07, "Could not get a current location fix."), Toast.LENGTH_SHORT).show();
                            else {
                                lat.setText(coordinate(result.getLatitude()));
                                lon.setText(coordinate(result.getLongitude()));
                            }
                        });
                    }
                    @Override public void onProviderDisabled(String provider) {
                        runOnUiThread(() -> {
                            button.setEnabled(true);
                            if (fallback != null) {
                                lat.setText(coordinate(fallback.getLatitude()));
                                lon.setText(coordinate(fallback.getLongitude()));
                            } else Toast.makeText(SavedPlacesActivity.this, OrbitText.get(R.string.ui_could_not_get_a_current_location_fix_6dca07, "Could not get a current location fix."), Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                }, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
            button.setEnabled(true);
            if (fallback != null) {
                lat.setText(coordinate(fallback.getLatitude()));
                lon.setText(coordinate(fallback.getLongitude()));
            }
        }
    }

    private void confirmDelete(SavedPlaceStore.Place place) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_delete_85941f, "Delete ") + place.name + "?")
                .setMessage(OrbitText.get(R.string.ui_existing_routine_triggers_and_if_condition_3e7937, "Existing Routine triggers and IF conditions keep their saved coordinates. This only removes the reusable place preset."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_delete_e2d0a5, "Delete"), (d, w) -> {
                    SavedPlaceStore.remove(this, place.id);
                    refresh();
                }).create();
        UiKit.styleOrbitDialog(dialog, this, true,
                UiKit.outlined(UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 55), 22, this),
                .66f, null);
        dialog.show();
    }

    private void tintDialogText(View view) {
        if (view == null) return;
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(UiKit.TEXT);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintDialogText(group.getChildAt(i));
        }
    }

    private EditText field(String hint, String value, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(UiKit.MUTED);
        e.setText(value == null ? "" : value);
        e.setTextColor(UiKit.TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), 0);
        e.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, Color.rgb(53,58,72), UiKit.accent(this), 15, this));
        e.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    private TextView label(String s) {
        TextView t = UiKit.text(this, s, 12, UiKit.MUTED, true);
        t.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 9), 0, UiKit.dp(this, 6));
        return t;
    }

    private Double parseDouble(String raw) {
        try { return Double.parseDouble(raw.trim()); }
        catch (Exception ignored) { return null; }
    }

    private String coordinate(double value) { return String.format(Locale.US, "%.6f", value); }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 16), UiKit.dp(this, 18), UiKit.dp(this, 16));
        c.setBackground(UiKit.outlined(UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 38), 22, this));
        c.setElevation(UiKit.dp(this, 2));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        return lp;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(UiKit.onAccent(this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        b.setBackground(UiKit.ripple(UiKit.accent(this), UiKit.onAccent(this), 15, this));
        UiKit.pressScale(b);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(UiKit.TEXT);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 14, this));
        UiKit.pressScale(b);
        return b;
    }

    private ImageButton iconButton(int res, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        b.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.accent(this), 18, this));
        b.setContentDescription(desc);
        b.setPadding(UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11));
        UiKit.pressScale(b);
        return b;
    }
}
