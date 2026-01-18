package ro.pub.cs.systems.eim.weatherforecast.main;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import ro.pub.cs.systems.eim.weatherforecast.R;
import ro.pub.cs.systems.eim.weatherforecast.network.ClientThread;
import ro.pub.cs.systems.eim.weatherforecast.network.ServerThread;

public class MainActivity extends AppCompatActivity {

    // 1. Declarăm componentele vizuale și referința către Server
    private EditText serverPortEditText, clientAddressEditText, clientPortEditText, cityEditText;
    private Spinner informationTypeSpinner;
    private TextView weatherForecastTextView;
    private ServerThread serverThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Asigură-te că fișierul se numește activity_main.xml

        // 2. Inițializăm componentele (le legăm de ID-urile din XML)
        serverPortEditText = findViewById(R.id.portServerInput);
        clientAddressEditText = findViewById(R.id.addressInput);
        clientPortEditText = findViewById(R.id.portClientInput);
        cityEditText = findViewById(R.id.cityInput);
        informationTypeSpinner = findViewById(R.id.dropdown_menu);
        weatherForecastTextView = findViewById(R.id.result);



        Button connectButton = findViewById(R.id.connect);
        Button getWeatherButton = findViewById(R.id.button2);

        // 3. Logica pentru butonul de pornire SERVER
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String serverPort = serverPortEditText.getText().toString();

                if (serverPort.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Completează portul serverului!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Creăm și pornim thread-ul de server
                serverThread = new ServerThread(Integer.parseInt(serverPort));
                if (serverThread.getServerSocket() == null) {
                    Toast.makeText(getApplicationContext(), "Eroare la crearea serverului!", Toast.LENGTH_SHORT).show();
                    return;
                }
                serverThread.start();
                Toast.makeText(getApplicationContext(), "Server pornit pe portul " + serverPort, Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Logica pentru butonul GET FORECAST (Clientul)
        getWeatherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Luăm valorile din câmpurile de text
                String clientAddress = clientAddressEditText.getText().toString();
                String clientPort = clientPortEditText.getText().toString();
                String city = cityEditText.getText().toString();
                String informationType = informationTypeSpinner.getSelectedItem().toString();

                // Verificăm dacă sunt completate câmpurile de conexiune
                if (clientAddress.isEmpty() || clientPort.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Adresa și portul clientului lipsesc!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Verificăm dacă serverul este pornit
                if (serverThread == null || !serverThread.isAlive()) {
                    Toast.makeText(getApplicationContext(), "Serverul nu rulează!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Verificăm dacă avem orașul
                if (city.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Introdu un oraș!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Resetăm zona de rezultat și pornim cererea către server
                weatherForecastTextView.setText("");

                // ClientThread se va ocupa de comunicarea propriu-zisă
                ClientThread clientThread = new ClientThread(
                        clientAddress,
                        Integer.parseInt(clientPort),
                        city,
                        informationType,
                        weatherForecastTextView
                );
                clientThread.start();
            }
        });
    }

    // 5. Metoda apelată când închizi aplicația
    @Override
    protected void onDestroy() {
        // Oprim serverul ca să nu lăsăm portul ocupat
        if (serverThread != null) {
            serverThread.stopThread();
        }
        super.onDestroy();
    }
}