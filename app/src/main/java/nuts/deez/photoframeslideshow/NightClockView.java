package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Low-refresh, fullscreen bedside clock and month calendar. No animation or image decoding. */
public class NightClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlight = new RectF();
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Locale locale = Locale.ENGLISH;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd/MM/yyyy", locale);
    private Calendar now = Calendar.getInstance();
    private String time = "", date = "", month = "";
    private String[] weekdays;
    public NightClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dateFormat.setDateFormatSymbols(new java.text.DateFormatSymbols(AppLanguage.locale(context)));
        weekdays = "vi".equals(AppLanguage.code(context))
                ? new String[]{"T2", "T3", "T4", "T5", "T6", "T7", "CN"}
                : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        setBackgroundColor(Color.BLACK); updateMinute();
    }
    void updateMinute() {
        now = Calendar.getInstance();
        timeFormat.setTimeZone(now.getTimeZone()); dateFormat.setTimeZone(now.getTimeZone());
        time = timeFormat.format(now.getTime()); date = dateFormat.format(now.getTime());
        month = new SimpleDateFormat("MMMM · yyyy", AppLanguage.locale(getContext())).format(now.getTime());
        setContentDescription(time + ", " + date); invalidate();
    }
    private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean thin) {
        paint.setTypeface(thin ? light : regular); paint.setTextSize(size); paint.setColor(color);
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, x, y, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (w > h * 1.15f) {
            text(canvas, time, w * .27f, h * .51f, Math.min(w * .17f, h * .30f), 0xffaaa394, true);
            text(canvas, date, w * .27f, h * .60f, Math.min(w * .020f, h * .042f), 0xff777368, false);
            calendar(canvas, w * .56f, h * .16f, w * .38f, h * .68f);
        } else {
            text(canvas, time, w * .5f, h * .29f, Math.min(w * .25f, h * .18f), 0xffaaa394, true);
            text(canvas, date, w * .5f, h * .35f, w * .035f, 0xff777368, false);
            calendar(canvas, w * .10f, h * .46f, w * .80f, Math.min(h * .43f, w * .80f));
        }
    }
    private void calendar(Canvas canvas, float left, float top, float width, float height) {
        float cellW = width / 7f, rowH = height / 8.5f;
        text(canvas, month, left + width / 2, top + rowH * .6f, cellW * .32f, 0xff8d887c, false);
        for (int day = 0; day < 7; day++) text(canvas, weekdays[day], left + cellW * (day + .5f), top + rowH * 1.9f, cellW * .26f, 0xff57554d, false);
        Calendar first = (Calendar) now.clone(); first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH), today = now.get(Calendar.DAY_OF_MONTH);
        for (int number = 1; number <= days; number++) {
            int index = offset + number - 1, column = index % 7, row = index / 7;
            float x = left + cellW * (column + .5f), y = top + rowH * (3f + row);
            if (number == today) {
                float radius = Math.min(cellW, rowH) * .40f;
                highlight.set(x - radius, y - rowH * .32f - radius, x + radius, y - rowH * .32f + radius);
                paint.setStyle(Paint.Style.FILL); paint.setColor(0xff292820);
                canvas.drawRoundRect(highlight, radius * .4f, radius * .4f, paint);
            }
            text(canvas, Integer.toString(number), x, y, cellW * .34f, number == today ? 0xffc2b89f : 0xff767266, false);
        }
    }
}
