package com.swarmnyc.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;

public class SlimWeatherWatchFaceService extends WeatherWatchFaceService {
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends WeatherWatchFaceEngine {
        private static final int TIME_DATE_DISTANCE = 5;
        float mColonXOffset;
        float mDateYOffset;
        float mDebugInfoYOffset;
        float mTemperatureSuffixYOffset;
        float mTemperatureYOffset;
        float mTimeYOffset;
        int mDateColor;
        int mDateDefaultColor;
        int mTemperatureColor;
        int mTemperatureDefaultColor;
        float mTemperature_picture_size;

        private Engine() {
            UPDATE_RATE_MS = 500;
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            log("onAmbientModeChanged: " + inAmbientMode);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mTimePaint.setAntiAlias(antiAlias);
                mDatePaint.setAntiAlias(antiAlias);
                mTemperaturePaint.setAntiAlias(antiAlias);
                mTemperatureSuffixPaint.setAntiAlias(antiAlias);
            }

            if (inAmbientMode) {
                mBackgroundPaint.setColor(mBackgroundDefaultColor);
                mDatePaint.setColor(mDateDefaultColor);
                mTemperaturePaint.setColor(mTemperatureDefaultColor);
                mTemperatureSuffixPaint.setColor(mTemperatureDefaultColor);
            } else {
                mBackgroundPaint.setColor(mBackgroundColor);
                mDatePaint.setColor(mDateColor);
                mTemperaturePaint.setColor(mTemperatureColor);
                mTemperatureSuffixPaint.setColor(mTemperatureColor);
            }

            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            isRound = insets.isRound();

            float timeTextSize = mResources.getDimension(isRound ?
                    R.dimen.modern_time_size_round : R.dimen.modern_time_size);

            float dateTextSize = mResources.getDimension(isRound ?
                    R.dimen.modern_date_size_round : R.dimen.modern_date_size);

            float tempTextSize = mResources.getDimension(isRound ?
                    R.dimen.modern_temperature_size_round : R.dimen.modern_temperature_size);

            float tempSuffixTextSize = mResources.getDimension(isRound ?
                    R.dimen.modern_temperature_suffix_size_round : R.dimen.modern_temperature_suffix_size);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mTemperaturePaint.setTextSize(tempTextSize);
            mTemperatureSuffixPaint.setTextSize(tempSuffixTextSize);

            mTimeYOffset += (mTimePaint.descent() + mTimePaint.ascent()) / 2;
            mColonXOffset = mTimePaint.measureText(Consts.COLON_STRING) / 2;
            mDateYOffset = (mDatePaint.descent() + mDatePaint.ascent()) / 2;
            mTemperatureYOffset = (mTemperaturePaint.descent() + mTemperaturePaint.ascent()) / 2;
            mTemperatureSuffixYOffset = (mTemperatureSuffixPaint.descent() + mTemperatureSuffixPaint.ascent()) / 2;
            mDebugInfoYOffset = 5 + mDebugInfoPaint.getTextSize() + (mDebugInfoPaint.descent() + mDebugInfoPaint.ascent()) / 2;
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mBackgroundColor = mBackgroundDefaultColor = mResources.getColor(R.color.modern_bg_color);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundDefaultColor);

            Typeface timeFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.modern_time_font));
            Typeface dateFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.modern_date_font));
            Typeface tempFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.modern_temperature_font));

            mDateColor = mDateDefaultColor = mResources.getColor(R.color.modern_date_color);
            mTemperatureColor = mTemperatureDefaultColor = mResources.getColor(R.color.modern_temperature_color);

            mTimePaint = createTextPaint(mResources.getColor(R.color.modern_time_color), timeFont);
            mDatePaint = createTextPaint(mDateColor, dateFont);
            mTemperaturePaint = createTextPaint(mTemperatureColor, tempFont);
            mTemperatureSuffixPaint = createTextPaint(mTemperatureColor, tempFont);

            mTemperature_picture_size = mResources.getDimension(R.dimen.modern_temperature_picture_size);
        }

        @Override
        public void onDestroy() {
            log("Destroy");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();
            float radius = width / 2;

            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            // Time
            boolean mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            String hourString = String.format("%02d", ConverterUtil.convertHour(mTime.hour, mTimeUnit));
            String minString = String.format("%02d", mTime.minute);

            //For Test
//            hourString = "12";
//            minString = "30";
//            mTemperature = 50;
//            mWeatherCondition = "clear";
//            mWeatherInfoReceivedTime = System.currentTimeMillis();
//            mSunriseTime.set(mWeatherInfoReceivedTime-10000);
//            mSunsetTime.set(mWeatherInfoReceivedTime+10000);

            float hourWidth = mTimePaint.measureText(hourString);

            float x = radius - hourWidth - mColonXOffset;
            float y = radius - mTimeYOffset + TIME_DATE_DISTANCE;
            float suffixY;

            canvas.drawText(hourString, x, y, mTimePaint);

            x = radius - mColonXOffset;

            if (isInAmbientMode() || mShouldDrawColons) {
                canvas.drawText(Consts.COLON_STRING, x, y, mTimePaint);
            }

            x = radius + mColonXOffset;
            canvas.drawText(minString, x, y, mTimePaint);

            //Date
            y = radius + mTimeYOffset + mDateYOffset;

            String dateString = ConverterUtil.convertToMonth(mTime.month).toUpperCase() + " " + String.valueOf(mTime.monthDay);

            float monthWidth = mDatePaint.measureText(dateString);

            x = radius - monthWidth / 2;
            canvas.drawText(dateString, x, y, mDatePaint);

            //WeatherInfo
            long timeSpan = System.currentTimeMillis() - mWeatherInfoReceivedTime;
            if (timeSpan <= WEATHER_INFO_TIME_OUT) {
                // photo
                if (!TextUtils.isEmpty(mWeatherCondition)) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("weather_");
                    stringBuilder.append(mWeatherCondition);

                    if ((mWeatherCondition.equals("cloudy") || mWeatherCondition.equals("clear")) && (Time.compare(mTime, mSunriseTime) < 0 || Time.compare(mTime, mSunsetTime) > 0)) {
                        //cloudy and clear have night picture
                        stringBuilder.append("night");
                    }

                    stringBuilder.append("_gray");

                    String name = stringBuilder.toString();
                    if (!name.equals(mWeatherConditionResourceName)) {
                        log("CreateScaledBitmap: " + name);
                        mWeatherConditionResourceName = name;
                        int id = mResources.getIdentifier(name, "drawable", this.getClass().getPackage().getName());

                        Drawable b = mResources.getDrawable(id);
                        mWeatherConditionDrawable = ((BitmapDrawable) b).getBitmap();
                        float scaledWidth = (mTemperature_picture_size / mWeatherConditionDrawable.getHeight()) * mWeatherConditionDrawable.getWidth();
                        mWeatherConditionDrawable = Bitmap.createScaledBitmap(mWeatherConditionDrawable, (int)scaledWidth,(int)mTemperature_picture_size, true);
                    }

                    canvas.drawBitmap(mWeatherConditionDrawable, radius - mWeatherConditionDrawable.getWidth() / 2, height - mWeatherConditionDrawable.getHeight(), null);
                }

                //temperature
                if (mTemperature != Integer.MAX_VALUE && !isInAmbientMode()) {
                    String temperatureString = String.valueOf(mTemperature);
                    String temperatureScaleString = mTemperatureScale == ConverterUtil.FAHRENHEIT ? ConverterUtil.FAHRENHEIT_STRING : ConverterUtil.CELSIUS_STRING;
                    float temperatureWidth = mTemperaturePaint.measureText(temperatureString);
                    float temperatureRadius = (temperatureWidth + mTemperatureSuffixPaint.measureText(temperatureScaleString)) / 2;
                    x = radius - temperatureRadius;
                    y = bounds.height() * 0.75f;
                    suffixY = y - mTemperatureSuffixYOffset;
                    y -= mTemperatureYOffset;
                    canvas.drawText(temperatureString, x, y, mTemperaturePaint);
                    x += temperatureWidth;
                    canvas.drawText(temperatureScaleString, x, suffixY, mTemperatureSuffixPaint);
                }
            }


            if (BuildConfig.DEBUG) {
                String timeString;
                if (mWeatherInfoReceivedTime == 0) {
                    timeString = "No data received";
                } else if (timeSpan > DateUtils.HOUR_IN_MILLIS) {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.HOUR_IN_MILLIS) + " hours ago";
                } else if (timeSpan > DateUtils.MINUTE_IN_MILLIS) {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.MINUTE_IN_MILLIS) + " mins ago";
                } else {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.SECOND_IN_MILLIS) + " secs ago";
                }

                canvas.drawText(timeString, width - mDebugInfoPaint.measureText(timeString), mDebugInfoYOffset, mDebugInfoPaint);
            }
        }
    }
}
