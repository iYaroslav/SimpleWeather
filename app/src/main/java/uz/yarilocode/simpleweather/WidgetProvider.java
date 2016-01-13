package uz.yarilocode.simpleweather;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.survivingwithandroid.weather.lib.WeatherClient;
import com.survivingwithandroid.weather.lib.WeatherConfig;
import com.survivingwithandroid.weather.lib.client.volley.WeatherClientDefault;
import com.survivingwithandroid.weather.lib.exception.WeatherLibException;
import com.survivingwithandroid.weather.lib.exception.WeatherProviderInstantiationException;
import com.survivingwithandroid.weather.lib.model.CurrentWeather;
import com.survivingwithandroid.weather.lib.model.Weather;
import com.survivingwithandroid.weather.lib.provider.openweathermap.OpenweathermapProviderType;
import com.survivingwithandroid.weather.lib.request.WeatherRequest;

/**
 * Created by Yaroslav on 12.01.16.
 * Copyright 2016 iYaroslav LLC.
 */
public class WidgetProvider extends AppWidgetProvider {

	public static String FORCE_WIDGET_UPDATE = "ForceWidgetUpdate";

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		for (int appWidgetId : appWidgetIds) {
			Intent action = new Intent(context, WidgetProvider.class);
			action.setAction(FORCE_WIDGET_UPDATE);
			PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, 0, action, 0);

			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
			views.setOnClickPendingIntent(R.id.refresh, actionPendingIntent);

			setLoading(views, true);
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}

		refresh(context);
	}

	private static void setLoading(RemoteViews views, boolean loading) {
		setSuccess(views, true);
		views.setViewVisibility(R.id.loader, loading ? View.VISIBLE : View.GONE);
		views.setViewVisibility(R.id.views, loading ? View.GONE : View.VISIBLE);
	}
	private static void setSuccess(RemoteViews views, boolean success) {
		views.setViewVisibility(R.id.success_bg, success ? View.VISIBLE : View.GONE);
		views.setViewVisibility(R.id.error_bg, success ? View.GONE : View.VISIBLE);
		views.setViewVisibility(R.id.weather, success ? View.VISIBLE : View.GONE);
		views.setViewVisibility(R.id.error, success ? View.GONE : View.VISIBLE);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		if (FORCE_WIDGET_UPDATE.equals(action)) {
			refresh(context);
		}

		super.onReceive(context, intent);
	}

	private void refresh(Context context) {
		context.startService(new Intent(context, UpdateService.class));
	}

	public static class UpdateService extends IntentService {

		private WeatherConfig config;
		private WeatherClient client;

		private ComponentName widget;
		private AppWidgetManager manager;
		private RemoteViews views;

		public UpdateService() {
			super("WeatherWidgetService");
		}

		@Override
		protected void onHandleIntent(Intent intent) {
			widget = new ComponentName(this, WidgetProvider.class);
			manager = AppWidgetManager.getInstance(this);
			views = new RemoteViews(this.getPackageName(), R.layout.widget_layout);

			WidgetProvider.setLoading(views, true);
			updateWidget();

			config = new WeatherConfig();
			config.lang = "ru";
			config.maxResult = 5;
			config.numDays = 3;
			config.unitSystem = WeatherConfig.UNIT_SYSTEM.M;
			config.ApiKey = "a97824bc48bbb0a16923bd9a98c53b45";

			try {
				client = new WeatherClient.ClientBuilder()
						.attach(this)
						.config(new WeatherConfig())
						.provider(new OpenweathermapProviderType())
						.httpClient(WeatherClientDefault.class)
						.build();

				client.updateWeatherConfig(config);

				String cityId = "1512569";
				client.getCurrentCondition(new WeatherRequest(cityId), new WeatherClient.WeatherEventListener() {
					@Override
					public void onWeatherRetrieved(CurrentWeather weather) {
						views.setViewVisibility(R.id.loader, View.GONE);
						views.setViewVisibility(R.id.views, View.VISIBLE);
						manager.updateAppWidget(widget, views);

						Toast.makeText(UpdateService.this, "Receive", Toast.LENGTH_SHORT).show();
						buildWeather(weather.weather);
					}

					@Override
					public void onWeatherError(WeatherLibException wle) {
						buildFail();
					}

					@Override
					public void onConnectionError(Throwable t) {
						buildFail();
					}
				});
			} catch (WeatherProviderInstantiationException e) {
				e.printStackTrace();
				buildFail();
			}
		}

		private void buildFail() {
			WidgetProvider.setLoading(views, false);
			WidgetProvider.setSuccess(views, false);
			apply();
		}

		private void buildWeather(Weather weather) {
			WidgetProvider.setLoading(views, false);

			views.setTextViewText(R.id.temp, ((int) weather.temperature.getTemp()) + "˚");
			views.setTextViewText(R.id.temp_max, ((int) weather.temperature.getMaxTemp()) + "˚");
			views.setTextViewText(R.id.temp_min, ((int) weather.temperature.getMinTemp()) + "˚");

			views.setTextViewText(R.id.condition, weather.currentCondition.getCondition() + " (" + weather.currentCondition.getDescr() + ")");
			views.setTextViewText(R.id.city, weather.location.getCity() + ", " + weather.location.getCountry());

			apply();
		}

		private void updateWidget() {
			manager.updateAppWidget(widget, views);
		}

		private void apply() {
			updateWidget();
			stopSelf();
		}

		@Override
		public IBinder onBind(Intent intent){
			return null;
		}
	}
}