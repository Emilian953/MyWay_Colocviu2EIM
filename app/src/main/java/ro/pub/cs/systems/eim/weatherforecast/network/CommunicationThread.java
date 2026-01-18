package ro.pub.cs.systems.eim.weatherforecast.network;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;
import ro.pub.cs.systems.eim.practicaltest02.general.Constants;
import ro.pub.cs.systems.eim.weatherforecast.Utilities;
import ro.pub.cs.systems.eim.weatherforecast.WeatherForecastInformation;

public class CommunicationThread extends Thread {

    private final ServerThread serverThread;
    private final Socket socket;

    public CommunicationThread(ServerThread serverThread, Socket socket) {
        this.serverThread = serverThread;
        this.socket = socket;
    }

    @Override
    public void run() {
        // 1. Verificare de siguranță: dacă socket-ul nu este valid, oprim thread-ul
        if (socket == null) {
            Log.e(Constants.TAG, "[COMMUNICATION THREAD] Socket is null!");
            return;
        }

        try {
            // 2. Crearea canalelor de comunicare (Reader pentru intrare, Writer pentru ieșire)
            BufferedReader bufferedReader = Utilities.getReader(socket);
            PrintWriter printWriter = Utilities.getWriter(socket);

            // 3. Citirea datelor trimise de Client prin rețea
            Log.i(Constants.TAG, "[COMMUNICATION THREAD] Waiting for parameters from client...");
            String city = bufferedReader.readLine();            // Clientul trimite numele orașului
            String informationType = bufferedReader.readLine(); // Clientul trimite ce vrea să afle (ex: temp)

            // Validare: dacă nu am primit datele corect, ieșim
            if (city == null || city.isEmpty() || informationType == null || informationType.isEmpty()) {
                Log.e(Constants.TAG, "[COMMUNICATION THREAD] Error receiving parameters from client!");
                return;
            }

            // 4. Accesarea Cache-ului local din ServerThread
            // Luăm HashMap-ul care stochează interogările anterioare pentru a nu apela API-ul inutil
            HashMap<String, WeatherForecastInformation> data = serverThread.getData();
            WeatherForecastInformation weatherForecastInformation;

            if (data.containsKey(city)) {
                // DACĂ există în cache: Luăm obiectul direct din memorie
                Log.i(Constants.TAG, "[COMMUNICATION THREAD] Getting the information from the cache...");
                weatherForecastInformation = data.get(city);
            } else {
                // DACĂ NU există în cache: Trebuie să descărcăm de pe internet
                Log.i(Constants.TAG, "[COMMUNICATION THREAD] Getting the information from the webservice...");
                HttpClient httpClient = new DefaultHttpClient();
                String pageSourceCode = "";

                // Construim URL-ul complet cu parametrii necesari (oraș, cheie API, unități de măsură)
                HttpGet httpGet = new HttpGet(Constants.WEB_SERVICE_ADDRESS + "?q=" + city + "&APPID=" + Constants.WEB_SERVICE_API_KEY + "&units=" + Constants.UNITS);

                // Executăm cererea HTTP GET
                HttpResponse httpGetResponse = httpClient.execute(httpGet);
                HttpEntity httpGetEntity = httpGetResponse.getEntity();

                // Convertim răspunsul serverului (format brut) într-un String (codul sursă JSON)
                if (httpGetEntity != null) {
                    pageSourceCode = EntityUtils.toString(httpGetEntity);
                    Log.i(Constants.TAG, "[JSON RESPONSE]: " + pageSourceCode);
                }

                // --- 5. PARSARE JSON (Transformăm textul primit în date utile) ---
                JSONObject content = new JSONObject(pageSourceCode);

                // Extragem starea vremii (este un array numit "weather")
                JSONArray weatherArray = content.getJSONArray(Constants.WEATHER);
                StringBuilder condition = new StringBuilder();
                for (int i = 0; i < weatherArray.length(); i++) {
                    JSONObject weather = weatherArray.getJSONObject(i);
                    // Concatenăm titlul (ex: "Clouds") și descrierea (ex: "broken clouds")
                    condition.append(weather.getString(Constants.MAIN)).append(" : ").append(weather.getString(Constants.DESCRIPTION));
                    if (i < weatherArray.length() - 1) condition.append(";");
                }

                // Extragem datele din obiectul "main" (temperatură, presiune, umiditate)
                JSONObject main = content.getJSONObject(Constants.MAIN);
                String temperature = main.getString(Constants.TEMP);
                String pressure = main.getString(Constants.PRESSURE);
                String humidity = main.getString(Constants.HUMIDITY);

                // Extragem viteza vântului din obiectul "wind"
                JSONObject wind = content.getJSONObject(Constants.WIND);
                String windSpeed = wind.getString(Constants.SPEED);

                // Creăm obiectul model cu toate aceste informații
                weatherForecastInformation = new WeatherForecastInformation(
                        temperature, windSpeed, condition.toString(), pressure, humidity
                );

                // Salvăm datele noi în cache-ul Serverului (pentru următoarea cerere)
                serverThread.setData(city, weatherForecastInformation);
            }

            // 6. Filtrarea rezultatului în funcție de cerința clientului (Switch)
            String result;
            switch(informationType) {
                case Constants.ALL:
                    result = weatherForecastInformation.toString();
                    break;
                case Constants.TEMPERATURE:
                    result = weatherForecastInformation.getTemperature();
                    break;
                case Constants.WIND_SPEED:
                    result = weatherForecastInformation.getWindSpeed();
                    break;
                case Constants.CONDITION:
                    result = weatherForecastInformation.getCondition();
                    break;
                case Constants.HUMIDITY:
                    result = weatherForecastInformation.getHumidity();
                    break;
                case Constants.PRESSURE:
                    result = weatherForecastInformation.getPressure();
                    break;
                default:
                    result = "[COMMUNICATION THREAD] Wrong information type!";
            }

            // 7. Trimiterea răspunsului final înapoi la client prin Socket
            printWriter.println(result);
            printWriter.flush(); // Ne asigurăm că datele pleacă imediat pe rețea

        } catch (IOException | JSONException ioException) {
            Log.e(Constants.TAG, "[COMMUNICATION THREAD] An exception has occurred: " + ioException.getMessage());
        } finally {
            // 8. ÎNCHIDEREA conexiunii (foarte important pentru a elibera resursele serverului)
            try {
                socket.close();
            } catch (IOException ioException) {
                Log.e(Constants.TAG, "[COMMUNICATION THREAD] Error closing socket: " + ioException.getMessage());
            }
        }
    }

}