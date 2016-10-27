package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForeCastFragment extends Fragment {
  private  ArrayAdapter<String> mForecastAdapter;

    public ForeCastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.forecastfragment,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id=item.getItemId();
        if(id==R.id.action_refresh){
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateWeather(){
        FetchWeatherTask fetchWeatherTask=new FetchWeatherTask();
        SharedPreferences preferences= PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location=preferences.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        fetchWeatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);


        mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList<String>());

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String forecast=mForecastAdapter.getItem(i);
                Intent detailFragment=new Intent(getActivity(),DetailActivity.class).putExtra(Intent.EXTRA_TEXT,forecast);
                startActivity(detailFragment);

            }
        });


        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG=FetchWeatherTask.class.getSimpleName();


        /* The date/time conversion code is going to be moved outside the asynctask later,
             * so for convenience we're breaking it out into its own method now.
             */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {

            SharedPreferences sharedPreferences=PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType=sharedPreferences.getString(getString(R.string.pref_units_key),getString(R.string.pref_units_metric));

            if(unitType.equals(getString(R.string.pref_units_imperial))){
                high=(high * 0.8) + 32;
                low=(low * 0.8) + 32;
            }else if (!unitType.equals(getString(R.string.pref_units_metric))){
                Log.d(LOG_TAG,"unit type not found"+unitType);
            }

            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr,int numDays)throws JSONException{

            final String OWM_LIST="list";
            final String OWM_TEMPERATURE="temp";
            final String OWM_MAX="max";
            final String OWM_MIN="min";
            final String OWM_WEATHER="weather";
            final String OWM_DESCRIPTION="description";

            JSONObject forecastJson=new JSONObject(forecastJsonStr);
            JSONArray weatherArray=forecastJson.getJSONArray(OWM_LIST);

            Time dayTime=new Time();
            dayTime.setToNow();

            int julianStartDay=Time.getJulianDay(System.currentTimeMillis(),dayTime.gmtoff);
            dayTime=new Time();

            String[] resultStrs=new String[numDays];
            for (int i=0;i<weatherArray.length();i++){

                String day;
                String description;
                String highAndlow;

                JSONObject dayForecast=weatherArray.getJSONObject(i);
                long dateTime;

                dateTime=dayTime.setJulianDay(julianStartDay+i);
                day=getReadableDateString(dateTime);

                JSONObject weatherObject=dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description=weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject=dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high=temperatureObject.getDouble(OWM_MAX);
                double low=temperatureObject.getDouble(OWM_MIN);
                highAndlow=formatHighLows(high,low);
                resultStrs[i]=day + " - " + description + " - " + highAndlow;
            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {

            Log.i("========***===>>>>>>>>>",params[0]);
            if(params.length==0){
                return null;
            }
            HttpURLConnection urlConnection = null;
            BufferedReader bufferedReader = null;

            String forecastJsonStr = null;
            String format="json";
            String units="metric";
            int numDays=7;
            String apiKey="fed9e83626058845e10cbc48409360c8";
            try {

                final String FORECAST_BASE_URL="http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM="q";
                final String FORMAT_PARAM="mode";
                final String UNITS_PARAM="units";
                final String DAYS_PARAM="cnt";
                final String API_KEY="appid";

                Uri builtUri= Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM,format)
                        .appendQueryParameter(UNITS_PARAM,units)
                        .appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                        .appendQueryParameter(API_KEY,apiKey)
                        .build();

                URL url=new URL(builtUri.toString());
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                //Read the input stream into a string.

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer stringBuffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }

                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    stringBuffer.append(line + "\n");
                }
                if (stringBuffer.length() == 0) {
                    return null;
                }
                forecastJsonStr = stringBuffer.toString();
                Log.i("forecastJsonStr=====>>>",forecastJsonStr);

            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, "MalformedURLException", e);
                return null;
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "IOException", ioe);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }


            try {
                return getWeatherDataFromJson(forecastJsonStr,numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG,e.getMessage(),e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if(result!=null){
                mForecastAdapter.clear();
                for(String dayForecastStr:result){
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }
    }


}
